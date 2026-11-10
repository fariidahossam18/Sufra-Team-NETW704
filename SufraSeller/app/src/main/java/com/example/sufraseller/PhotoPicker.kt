package com.example.sufraseller

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/**
 * Lets the seller pick a product photo from the CAMERA or the GALLERY (Milestone 3).
 *
 * How to use it (in a Fragment or Activity):
 *
 *     private val photoPicker = PhotoPicker(this, { requireContext() }) { uri -> ... }
 *     ...
 *     photoPicker.show()      // asks "Take a photo" or "Choose from gallery"
 *
 * It must be created as a property (like above), because Android needs the
 * registerForActivityResult(...) calls to happen before the screen starts.
 *
 *  - Gallery: Android's photo picker (no permission needed)
 *  - Camera:  the phone's camera app saves the photo into a temporary file of our app,
 *             shared through a FileProvider (see res/xml/file_paths.xml and the manifest)
 */
class PhotoPicker(
    caller: ActivityResultCaller,
    private val context: () -> Context,
    private val onPicked: (Uri) -> Unit
) {

    private var cameraUri: Uri? = null

    private val galleryLauncher =
        caller.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) onPicked(uri)
        }

    private val cameraLauncher =
        caller.registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            val uri = cameraUri
            if (saved && uri != null) onPicked(uri)
        }

    /** Shows a small dialog: Take a photo / Choose from gallery. */
    fun show() {
        MaterialAlertDialogBuilder(context())
            .setTitle("Product photo")
            .setItems(arrayOf("📷  Take a photo", "🖼️  Choose from gallery")) { _, which ->
                if (which == 0) openCamera() else openGallery()
            }
            .show()
    }

    fun openGallery() {
        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun openCamera() {
        val ctx = context()
        try {
            val folder = File(ctx.cacheDir, "camera").apply { mkdirs() }
            val file = File(folder, "photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            cameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "No camera app found. Choose from the gallery instead.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, "Couldn't open the camera: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
