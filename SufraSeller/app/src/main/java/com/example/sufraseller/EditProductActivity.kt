package com.example.sufraseller

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.sufraseller.databinding.ActivityEditProductBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Edit one product (Seller app, Milestone 3).
 *
 * Opened from My products. The product id is sent with putExtra and read with getStringExtra.
 *  - Change the photo (camera or gallery) -> uploaded to S3 when saving
 *  - Change name, description, price, quantity -> saved with updateChildren()
 *  - Delete the product
 * Buyers see every change instantly because they listen to the same node.
 */
class EditProductActivity : AppCompatActivity() {

    companion object {
        // Key used with putExtra / getStringExtra when a product is opened
        const val EXTRA_PRODUCT_ID = "product_id"
    }

    private lateinit var binding: ActivityEditProductBinding
    private lateinit var productRef: DatabaseReference
    private var product: Product? = null
    private var uid = ""

    /** New photo (compressed) waiting to be uploaded, if the seller changed it. */
    private var newPhoto: ByteArray? = null
    private var busy = false

    private val photoPicker = PhotoPicker(this, { this }) { uri -> preparePhoto(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEditProductBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdge(binding.root, binding.header)

        // 1) Read the product id sent by My products
        val productId = intent.getStringExtra(EXTRA_PRODUCT_ID).orEmpty()
        uid = FirebaseRefs.auth.currentUser?.uid.orEmpty()
        if (productId.isEmpty() || uid.isEmpty()) {
            toast("Product not found")
            finish()
            return
        }
        productRef = FirebaseRefs.productRef(uid, productId)

        binding.btnBack.setOnClickListener { finish() }
        binding.photoCard.setOnClickListener { if (!busy) photoPicker.show() }
        binding.btnSave.setOnClickListener { save() }
        binding.btnDelete.setOnClickListener { confirmDelete() }

        loadProduct()
    }

    /** 2) Load the product once and fill the form. */
    private fun loadProduct() {
        productRef.get()
            .addOnSuccessListener { snapshot ->
                val loaded = Product.fromSnapshot(snapshot)
                if (loaded == null) {
                    toast("This product was deleted")
                    finish()
                    return@addOnSuccessListener
                }
                product = loaded
                binding.etName.setText(loaded.name)
                binding.etDescription.setText(loaded.description)
                binding.etPrice.setText(formatNumber(loaded.price))
                binding.etQuantity.setText(loaded.quantity.toString())
                binding.tvUpdated.text = "Last updated: ${formatTimestamp(loaded.updatedAt)}"
                ImageLoader.load(binding.ivPhoto, loaded.imageUrl, lifecycleScope)
                binding.progress.visibility = View.GONE
                binding.form.visibility = View.VISIBLE
            }
            .addOnFailureListener { e ->
                toast("Couldn't load the product: ${e.message}")
                finish()
            }
    }

    /** 45.0 -> "45",  12.5 -> "12.5" (so the price field doesn't show "45.0") */
    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private fun preparePhoto(uri: Uri) {
        lifecycleScope.launch {
            binding.tvPhotoInfo.text = "Preparing photo…"
            try {
                val jpeg = withContext(Dispatchers.Default) {
                    ImageUtils.compressForUpload(this@EditProductActivity, uri)
                }
                newPhoto = jpeg
                binding.ivPhoto.tag = null          // stop any old download from replacing the preview
                binding.ivPhoto.setImageBitmap(BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size))
                binding.tvPhotoInfo.text = "New photo · ${jpeg.size / 1024} KB"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                binding.tvPhotoInfo.text = "📷 Change photo"
                toast("Couldn't read this photo. Try another one.")
            }
        }
    }

    /** 3) Check the inputs, upload the new photo if there is one, then save with updateChildren(). */
    private fun save() {
        val old = product ?: return
        if (busy) return

        val name = binding.etName.value()
        val description = binding.etDescription.value()
        val price = Product.parsePrice(binding.etPrice.value())
        val quantity = Product.parseQuantity(binding.etQuantity.value(), min = 0)

        binding.tilName.error = Product.nameError(name)
        binding.tilDescription.error =
            if (description.length > Product.DESCRIPTION_MAX) "Max ${Product.DESCRIPTION_MAX} characters" else null
        binding.tilPrice.error = if (price == null) "Enter a price from 0.01 to 100,000" else null
        binding.tilQuantity.error = if (quantity == null) "Whole number from 0 to ${Product.QUANTITY_MAX}" else null
        if (binding.tilName.error != null || binding.tilDescription.error != null || price == null || quantity == null) return

        setBusy(true)
        lifecycleScope.launch {
            var imageUrl = old.imageUrl
            val photo = newPhoto
            if (photo != null) {
                if (!S3Manager.isConfigured) {
                    setBusy(false)
                    toast("AWS keys are missing. Add them to local.properties and rebuild.")
                    return@launch
                }
                try {
                    imageUrl = S3Manager.uploadProductPhoto(uid, photo)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    setBusy(false)
                    toast("Photo upload failed: ${S3Manager.friendlyError(e)}")
                    return@launch
                }
            }

            val updates = mapOf<String, Any>(
                "name" to name,
                "description" to description,
                "price" to price,
                "quantity" to quantity,
                "imageUrl" to imageUrl,
                "updatedAt" to ServerValue.TIMESTAMP
            )
            productRef.updateChildren(updates)
                .addOnSuccessListener {
                    // the old photo is not needed any more -> free space in the bucket
                    if (photo != null && old.imageUrl != imageUrl) {
                        S3Manager.deletePhotoQuietly(old.imageUrl)
                    }
                    toast("✅ Changes saved")
                    finish()
                }
                .addOnFailureListener { e ->
                    setBusy(false)
                    toast("Couldn't save: ${e.message}")
                }
        }
    }

    private fun confirmDelete() {
        val current = product ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete ${current.name}?")
            .setMessage("Buyers won't see this product any more. This can't be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> delete(current) }
            .show()
    }

    private fun delete(current: Product) {
        setBusy(true)
        productRef.removeValue()
            .addOnSuccessListener {
                S3Manager.deletePhotoQuietly(current.imageUrl)
                toast("🗑️ ${current.name} was deleted")
                finish()
            }
            .addOnFailureListener { e ->
                setBusy(false)
                toast("Couldn't delete: ${e.message}")
            }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.btnSave.isEnabled = !value
        binding.btnDelete.isEnabled = !value
        binding.btnSave.text = if (value) "Saving…" else "Save changes"
    }
}
