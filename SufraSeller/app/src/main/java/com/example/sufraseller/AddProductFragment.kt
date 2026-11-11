package com.example.sufraseller

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.sufraseller.databinding.FragmentAddProductBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Add product tab (Seller app, Milestone 3).
 *
 *  1) The seller picks a photo from the camera or the gallery (PhotoPicker)
 *  2) The photo is compressed under 0.5 MB (ImageUtils)
 *  3) On "Add product": the inputs are checked, the photo is uploaded to S3
 *     (product-photos/<sellerId>/<time>.jpg) and the product is saved in Firebase under
 *     products/{sellerId}/{productId} together with the photo URL.
 *  4) Buyers see the new product immediately (they listen to the same node).
 */
class AddProductFragment : Fragment() {

    private var _binding: FragmentAddProductBinding? = null
    private val binding get() = _binding!!

    /** The compressed JPEG waiting to be uploaded. */
    private var photoBytes: ByteArray? = null
    private var saving = false

    /** Must be created here (as a property) so Android can register the camera/gallery results. */
    private val photoPicker = PhotoPicker(this, { requireContext() }) { uri -> preparePhoto(uri) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddProductBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.photoCard.setOnClickListener { if (!saving) photoPicker.show() }
        binding.btnSave.setOnClickListener { saveProduct() }
        photoBytes?.let { showPhoto(it) }          // e.g. after the view was re-created
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** Compresses the picked photo in the background and shows a preview. */
    private fun preparePhoto(uri: Uri) {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            showStatus("Preparing photo…")
            try {
                val jpeg = withContext(Dispatchers.Default) { ImageUtils.compressForUpload(appContext, uri) }
                photoBytes = jpeg
                showPhoto(jpeg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Couldn't read this photo. Try another one.")
            } finally {
                hideStatus()
            }
        }
    }

    private fun showPhoto(jpeg: ByteArray) {
        val b = _binding ?: return
        b.ivPhoto.setImageBitmap(BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size))
        b.ivPhoto.visibility = View.VISIBLE
        b.photoHint.visibility = View.GONE
        b.tvPhotoInfo.text = "${jpeg.size / 1024} KB · tap to change"
        b.tvPhotoInfo.visibility = View.VISIBLE
    }

    private fun saveProduct() {
        val b = _binding ?: return
        if (saving) return

        val name = b.etName.value()
        val description = b.etDescription.value()
        val price = Product.parsePrice(b.etPrice.value())
        val quantity = Product.parseQuantity(b.etQuantity.value(), min = 1)
        val jpeg = photoBytes

        // ---- check the inputs ----
        b.tilName.error = Product.nameError(name)
        b.tilDescription.error =
            if (description.length > Product.DESCRIPTION_MAX) "Max ${Product.DESCRIPTION_MAX} characters" else null
        b.tilPrice.error = if (price == null) "Enter a price from 0.01 to 100,000" else null
        b.tilQuantity.error = if (quantity == null) "Whole number from 1 to ${Product.QUANTITY_MAX}" else null
        if (jpeg == null) toast("Please add a photo of the product")

        if (b.tilName.error != null || b.tilDescription.error != null ||
            price == null || quantity == null || jpeg == null
        ) return

        if (!S3Manager.isConfigured) {
            toast("AWS keys are missing. Add them to local.properties and rebuild.")
            return
        }
        val uid = FirebaseRefs.auth.currentUser?.uid ?: return

        setSaving(true)
        viewLifecycleOwner.lifecycleScope.launch {
            // ---- 1) upload the photo to S3 ----
            showStatus("Uploading photo to S3…")
            val imageUrl = try {
                S3Manager.uploadProductPhoto(uid, jpeg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setSaving(false)
                toast("Photo upload failed: ${S3Manager.friendlyError(e)}")
                return@launch
            }

            // ---- 2) save the product in Firebase (push() creates a unique id) ----
            showStatus("Saving product…")
            val ref = FirebaseRefs.sellerProductsRef(uid).push()
            val product = Product(
                id = ref.key.orEmpty(),
                sellerId = uid,
                name = name,
                description = description,
                price = price,
                quantity = quantity,
                imageUrl = imageUrl
            )
            ref.setValue(product.toMap())
                .addOnSuccessListener {
                    setSaving(false)
                    toast("✅ ${product.name} was added")
                    clearForm()
                    (activity as? SellerHomeActivity)?.openTab(SellerHomeActivity.TAB_PRODUCTS)
                }
                .addOnFailureListener { e ->
                    setSaving(false)
                    toast("Couldn't save the product: ${e.message}")
                }
        }
    }

    private fun setSaving(value: Boolean) {
        saving = value
        val b = _binding ?: return
        b.btnSave.isEnabled = !value
        b.btnSave.text = if (value) "Saving…" else "Add product"
        if (!value) hideStatus()
    }

    private fun showStatus(text: String) {
        val b = _binding ?: return
        b.tvStatus.text = text
        b.statusRow.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        _binding?.statusRow?.visibility = View.GONE
    }

    private fun clearForm() {
        val b = _binding ?: return
        photoBytes = null
        b.etName.setText("")
        b.etDescription.setText("")
        b.etPrice.setText("")
        b.etQuantity.setText("")
        b.tilName.error = null
        b.tilPrice.error = null
        b.tilQuantity.error = null
        b.ivPhoto.setImageDrawable(null)
        b.ivPhoto.visibility = View.GONE
        b.tvPhotoInfo.visibility = View.GONE
        b.photoHint.visibility = View.VISIBLE
    }
}
