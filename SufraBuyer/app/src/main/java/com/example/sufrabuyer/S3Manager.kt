package com.example.sufrabuyer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.amazonaws.AmazonClientException
import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.URL
import java.net.URLDecoder

/**
 * Everything that talks to AWS S3 (Milestones 2 and 3).
 *
 * The team credentials are NOT written in the code. They are read from local.properties
 * (which is never uploaded to GitHub) and Gradle puts them into BuildConfig at build time.
 *
 *  - uploadProfilePhoto():  profile-photos/<uid>/<time>.jpg        (Milestone 2)
 *  - uploadProductPhoto():  product-photos/<sellerId>/<time>.jpg   (Milestone 3)
 *  - downloadPhoto():       downloads a photo with the credentials (the bucket is private)
 *  - deletePhotoQuietly():  removes an old product photo so the bucket stays under 0.5 GB
 */
object S3Manager {

    private val bucket = BuildConfig.AWS_BUCKET

    /** Downloaded photos are shrunk so the longest side is about this size (enough for cards). */
    private const val DISPLAY_SIDE = 720

    /** True when all the keys were filled in local.properties. */
    val isConfigured: Boolean
        get() = BuildConfig.AWS_ACCESS_KEY.isNotBlank() &&
                BuildConfig.AWS_SECRET_KEY.isNotBlank() &&
                BuildConfig.AWS_REGION.isNotBlank() &&
                bucket.isNotBlank()

    /** The S3 client, created once (lazy) with the team credentials. */
    private val client: AmazonS3Client by lazy {
        val credentials: AWSCredentials =
            if (BuildConfig.AWS_SESSION_TOKEN.isNotBlank())
                BasicSessionCredentials(BuildConfig.AWS_ACCESS_KEY, BuildConfig.AWS_SECRET_KEY, BuildConfig.AWS_SESSION_TOKEN)
            else
                BasicAWSCredentials(BuildConfig.AWS_ACCESS_KEY, BuildConfig.AWS_SECRET_KEY)
        AmazonS3Client(credentials, Region.getRegion(Regions.fromName(BuildConfig.AWS_REGION)))
    }

    /**
     * Memory cache of downloaded photos (url -> bitmap), so scrolling a grid up and down
     * doesn't download the same photo again. Its size is 1/8 of the app's memory (in KB).
     */
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /** The photo if it was already downloaded, otherwise null. Instant (no internet). */
    fun cached(url: String): Bitmap? = cache.get(url)

    suspend fun uploadProfilePhoto(uid: String, jpeg: ByteArray): String =
        uploadPhoto("profile-photos/$uid", jpeg)

    suspend fun uploadProductPhoto(sellerId: String, jpeg: ByteArray): String =
        uploadPhoto("product-photos/$sellerId", jpeg)

    /**
     * Uploads a JPEG to   <folder>/<time>.jpg   and returns its URL (which we save in Firebase).
     * A new file name is used every time, so the upload time is visible in the AWS console.
     */
    private suspend fun uploadPhoto(folder: String, jpeg: ByteArray): String = withContext(Dispatchers.IO) {
        val key = "$folder/${System.currentTimeMillis()}.jpg"
        val metadata = ObjectMetadata().apply {
            contentType = "image/jpeg"
            contentLength = jpeg.size.toLong()
        }
        client.putObject(PutObjectRequest(bucket, key, ByteArrayInputStream(jpeg), metadata))
        val url = client.getUrl(bucket, key).toString()
        decodeForDisplay(jpeg)?.let { cache.put(url, it) }
        url
    }

    /**
     * Downloads the photo saved at [url] using the S3 credentials (getObject).
     * Returns null if it can't be read.
     */
    suspend fun downloadPhoto(url: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(url)?.let { return@withContext it }
        val key = keyFromUrl(url) ?: return@withContext null
        val bytes = client.getObject(bucket, key).objectContent.use { it.readBytes() }
        val bitmap = decodeForDisplay(bytes)
        if (bitmap != null) cache.put(url, bitmap)
        bitmap
    }

    /** Used for deletes, so they finish even if the screen that asked for them is closed. */
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Deletes a photo from the bucket in the background (e.g. the old photo of an edited product).
     * Errors are ignored: an old photo left behind is not a problem.
     */
    fun deletePhotoQuietly(url: String) {
        if (url.isBlank() || !isConfigured) return
        cache.remove(url)
        backgroundScope.launch {
            try {
                keyFromUrl(url)?.let { client.deleteObject(bucket, it) }
            } catch (e: Exception) {
                // no permission / no internet -> just leave the old photo
            }
        }
    }

    /** Decodes JPEG bytes, shrinking big photos so a grid of 20 photos doesn't fill the memory. */
    private fun decodeForDisplay(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= DISPLAY_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /**
     * Gets the object key (file path inside the bucket) from the URL saved in Firebase.
     *  https://bucket.s3.region.amazonaws.com/product-photos/uid/123.jpg  ->  product-photos/uid/123.jpg
     *  https://s3.region.amazonaws.com/bucket/product-photos/uid/123.jpg  ->  product-photos/uid/123.jpg
     */
    fun keyFromUrl(url: String): String? = try {
        val u = URL(url)
        val path = URLDecoder.decode(u.path.removePrefix("/"), "UTF-8")
        if (u.host.startsWith("s3.") || u.host.startsWith("s3-")) path.substringAfter('/') else path
    } catch (e: Exception) {
        null
    }

    /** Turns AWS errors into readable messages. */
    fun friendlyError(e: Throwable): String = when (e) {
        is AmazonServiceException -> when (e.errorCode) {
            "AccessDenied" -> "AWS refused access. Check the keys and bucket name in local.properties."
            "NoSuchBucket" -> "The bucket name in local.properties doesn't exist."
            "InvalidAccessKeyId", "SignatureDoesNotMatch" -> "The AWS access key or secret key is wrong."
            "RequestTimeTooSkewed" -> "The phone's date/time is wrong. Fix the clock and try again."
            else -> "AWS error: ${e.errorCode ?: e.message}"
        }
        is AmazonClientException -> "Couldn't reach AWS. Check the internet connection."
        else -> e.localizedMessage ?: "Something went wrong with the photo."
    }
}
