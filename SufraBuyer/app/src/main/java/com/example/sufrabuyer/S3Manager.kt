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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.URL
import java.net.URLDecoder

/**
 * Everything that talks to AWS S3 (Milestone 2).
 *
 * The team credentials are NOT written in the code. They are read from local.properties
 * (which is never uploaded to GitHub) and Gradle puts them into BuildConfig at build time.
 *
 *  - uploadProfilePhoto(): uploads a JPEG to the bucket and returns its URL
 *  - downloadPhoto():      downloads a photo from the bucket using the credentials
 */
object S3Manager {

    private val bucket = BuildConfig.AWS_BUCKET

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

    /** Keeps the last few downloaded photos in memory so screens don't download them again. */
    private val cache = LruCache<String, Bitmap>(10)

    /**
     * Uploads a JPEG to   profile-photos/<uid>/<time>.jpg
     * A new file name is used every time, so the old photo stays in the bucket
     * (the milestone asks to show BOTH uploads and their timestamps in the AWS console).
     * Returns the photo's URL, which we save in Firebase.
     */
    suspend fun uploadProfilePhoto(uid: String, jpeg: ByteArray): String = withContext(Dispatchers.IO) {
        val key = "profile-photos/$uid/${System.currentTimeMillis()}.jpg"
        val metadata = ObjectMetadata().apply {
            contentType = "image/jpeg"
            contentLength = jpeg.size.toLong()
        }
        client.putObject(PutObjectRequest(bucket, key, ByteArrayInputStream(jpeg), metadata))
        val url = client.getUrl(bucket, key).toString()
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.let { cache.put(url, it) }
        url
    }

    /**
     * Downloads the photo saved at [url] using the S3 credentials (getObject).
     * Returns null if it can't be read.
     */
    suspend fun downloadPhoto(url: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(url)?.let { return@withContext it }
        val key = keyFromUrl(url) ?: return@withContext null
        val bitmap = client.getObject(bucket, key).objectContent.use { BitmapFactory.decodeStream(it) }
        if (bitmap != null) cache.put(url, bitmap)
        bitmap
    }

    /**
     * Gets the object key (file path inside the bucket) from the URL saved in Firebase.
     *  https://bucket.s3.region.amazonaws.com/profile-photos/uid/123.jpg  ->  profile-photos/uid/123.jpg
     *  https://s3.region.amazonaws.com/bucket/profile-photos/uid/123.jpg  ->  profile-photos/uid/123.jpg
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
