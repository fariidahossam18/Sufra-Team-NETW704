package com.example.sufrabuyer

import android.widget.ImageView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Shows an S3 photo inside an ImageView (Milestone 3).
 *
 * Our bucket is private, so a normal image library can't open the URL by itself.
 * This helper downloads the photo with S3Manager (which signs the request with our keys)
 * and puts it in the ImageView.
 *
 * It is safe to use inside a RecyclerView: the URL is saved in the view's tag, so if the card
 * was reused for another item while downloading, the old photo is NOT shown on the new item.
 */
object ImageLoader {

    fun load(imageView: ImageView, url: String?, scope: CoroutineScope) {
        val link = url.orEmpty()
        imageView.tag = link

        if (link.isBlank() || !S3Manager.isConfigured) {
            imageView.setImageDrawable(null)          // the emoji behind the image stays visible
            return
        }

        val cached = S3Manager.cached(link)
        if (cached != null) {
            imageView.setImageBitmap(cached)
            return
        }

        imageView.setImageDrawable(null)
        scope.launch {
            val bitmap = try {
                S3Manager.downloadPhoto(link)
            } catch (e: CancellationException) {
                throw e                               // the screen was closed: stop quietly
            } catch (e: Exception) {
                null
            }
            if (bitmap != null && imageView.tag == link) imageView.setImageBitmap(bitmap)
        }
    }
}
