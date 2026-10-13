package com.example.sufraseller

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.sufraseller.databinding.ActivityProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Real-time profile screen with profile photo (Milestones 1 + 2).
 *
 * Milestone 1:
 *  - A ValueEventListener on /users/{uid} keeps the screen in sync with Firebase.
 *  - "Edit" unlocks the fields, "Save changes" writes them with updateChildren().
 *
 * Milestone 2 (profile photo):
 *  - 📷 opens the photo picker. The photo is compressed to under 0.5 MB and previewed.
 *  - "Save changes" uploads it to AWS S3 (S3Manager.uploadProfilePhoto) and saves the
 *    S3 URL in Firebase as "photoUrl".
 *  - When the screen opens, the photo is downloaded from S3 using that URL (S3Manager.downloadPhoto).
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var uid: String
    private var currentUser: User? = null
    private var isEditing = false
    private val isSellerApp = AppConfig.APP_ROLE == User.ROLE_SELLER

    /** Compressed photo the user picked but hasn't saved yet (null = no new photo). */
    private var pendingPhoto: ByteArray? = null

    /** URL of the photo currently on screen, so we don't download it twice. */
    private var shownPhotoUrl: String? = null

    /** Android's photo picker (no storage permission needed). */
    private val photoPicker =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) onPhotoPicked(uri)
        }

    private val profileListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val user = User.fromSnapshot(snapshot) ?: return
            currentUser = user
            bindUser(user)
        }

        override fun onCancelled(error: DatabaseError) {
            toast("Couldn't load profile: ${error.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdge(binding.root, binding.header)

        // Only signed-in users can see a profile
        val firebaseUser = FirebaseRefs.auth.currentUser
        if (firebaseUser == null) {
            goToLogin()
            return
        }
        uid = firebaseUser.uid

        binding.sellerSection.visibility = if (isSellerApp) View.VISIBLE else View.GONE
        binding.tvRoleChip.text = if (isSellerApp) "🏪  Seller account" else "🛍️  Buyer account"

        binding.btnBack.setOnClickListener { finish() }
        binding.btnEdit.setOnClickListener { setEditing(true) }
        binding.btnCancel.setOnClickListener { cancelEditing() }
        binding.btnSave.setOnClickListener { saveProfile() }
        binding.btnLogout.setOnClickListener { confirmLogout() }
        binding.btnChangePhoto.setOnClickListener { pickPhoto() }
        binding.ivAvatar.setOnClickListener { pickPhoto() }

        setEditing(false)
    }

    // Start / stop listening for real-time updates with the screen's lifecycle
    override fun onStart() {
        super.onStart()
        if (::uid.isInitialized) FirebaseRefs.userRef(uid).addValueEventListener(profileListener)
    }

    override fun onStop() {
        super.onStop()
        if (::uid.isInitialized) FirebaseRefs.userRef(uid).removeEventListener(profileListener)
    }

    /** Puts the user's data on screen. */
    private fun bindUser(user: User) {
        binding.tvHeaderName.text = user.name.ifEmpty { "Your name" }
        binding.tvHeaderEmail.text = user.email
        binding.tvAvatar.text = initialsOf(user.name)
        binding.tvLastUpdated.text = "Last updated: ${formatTimestamp(user.updatedAt)}"

        // Don't overwrite what the user is typing or the photo they just picked
        if (!isEditing) {
            binding.etName.setText(user.name)
            binding.etPhone.setText(user.phone)
            binding.etAddress.setText(user.address)
            binding.etEmail.setText(user.email)
            binding.etShopName.setText(user.shopName)
            binding.actvShopCategory.setText(user.shopCategory, false)
        }
        if (pendingPhoto == null) showPhoto(user.photoUrl)
    }

    // ------------------------------------------------------------------
    //  Profile photo (Milestone 2)
    // ------------------------------------------------------------------

    private fun pickPhoto() {
        if (!S3Manager.isConfigured) {
            toast("AWS isn't set up: add the team keys to local.properties")
            return
        }
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    /** Compresses the picked photo (< 0.5 MB) and shows a preview. Uploading happens on Save. */
    private fun onPhotoPicked(uri: Uri) {
        setPhotoLoading(true)
        lifecycleScope.launch {
            try {
                val jpeg = withContext(Dispatchers.Default) {
                    ImageUtils.compressForUpload(this@ProfileActivity, uri)
                }
                pendingPhoto = jpeg
                binding.ivAvatar.setImageBitmap(BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size))
                binding.ivAvatar.visibility = View.VISIBLE
                setEditing(true)
                toast("Photo ready (${jpeg.size / 1024} KB). Tap \"Save changes\" to upload it.")
            } catch (e: Exception) {
                toast("Couldn't read this photo: ${e.localizedMessage}")
            } finally {
                setPhotoLoading(false)
            }
        }
    }

    /** Downloads the photo from S3 (using the URL saved in Firebase) and shows it. */
    private fun showPhoto(url: String) {
        if (url.isBlank()) {                      // no photo yet -> show initials
            shownPhotoUrl = null
            binding.ivAvatar.visibility = View.GONE
            return
        }
        if (url == shownPhotoUrl || !S3Manager.isConfigured) return
        shownPhotoUrl = url
        setPhotoLoading(true)
        lifecycleScope.launch {
            try {
                val bitmap = S3Manager.downloadPhoto(url)
                if (bitmap != null && url == shownPhotoUrl) {
                    binding.ivAvatar.setImageBitmap(bitmap)
                    binding.ivAvatar.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                shownPhotoUrl = null
                toast(S3Manager.friendlyError(e))
            } finally {
                setPhotoLoading(false)
            }
        }
    }

    private fun setPhotoLoading(loading: Boolean) {
        binding.photoProgress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnChangePhoto.isEnabled = !loading
    }

    // ------------------------------------------------------------------
    //  Edit / save
    // ------------------------------------------------------------------

    private fun setEditing(editing: Boolean) {
        isEditing = editing
        listOf(binding.etName, binding.etPhone, binding.etAddress, binding.etShopName)
            .forEach { setFieldEditable(it, editing) }
        setFieldEditable(binding.etEmail, false)            // email is the login, never editable here
        binding.tilShopCategory.isEnabled = editing
        binding.editActions.visibility = if (editing) View.VISIBLE else View.GONE
        binding.btnEdit.visibility = if (editing) View.GONE else View.VISIBLE
        listOf(binding.tilName, binding.tilPhone, binding.tilAddress, binding.tilShopName)
            .forEach { it.error = null }
    }

    private fun cancelEditing() {
        pendingPhoto = null                     // forget the picked photo
        shownPhotoUrl = null                    // force the saved photo to show again
        setEditing(false)
        currentUser?.let { bindUser(it) }       // restore the saved values
    }

    private fun setFieldEditable(field: EditText, editable: Boolean) {
        field.isFocusable = editable
        field.isFocusableInTouchMode = editable
        field.isCursorVisible = editable
        field.isLongClickable = editable
    }

    private fun saveProfile() {
        val name = binding.etName.value()
        val phone = binding.etPhone.value()
        val address = binding.etAddress.value()
        val shopName = binding.etShopName.value()
        val shopCategory = binding.actvShopCategory.text?.toString()?.trim().orEmpty()

        var valid = true
        if (name.length < 3) { binding.tilName.error = "Please enter your full name"; valid = false }
        if (!isValidPhone(phone)) { binding.tilPhone.error = "Enter a valid phone number (8–15 digits)"; valid = false }
        if (address.isEmpty()) { binding.tilAddress.error = "Address is required"; valid = false }
        if (isSellerApp && shopName.isEmpty()) { binding.tilShopName.error = "Shop name is required"; valid = false }
        if (!valid) return

        // Only the changed fields are sent -> updateChildren() merges them into /users/{uid}
        val updates = hashMapOf<String, Any>(
            "name" to name,
            "phone" to phone,
            "address" to address,
            "updatedAt" to ServerValue.TIMESTAMP
        )
        if (isSellerApp) {
            updates["shopName"] = shopName
            updates["shopCategory"] = shopCategory
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                // 1) If a new photo was picked, upload it to S3 first and keep its URL
                pendingPhoto?.let { jpeg ->
                    val url = S3Manager.uploadProfilePhoto(uid, jpeg)
                    updates["photoUrl"] = url
                    updates["photoUpdatedAt"] = ServerValue.TIMESTAMP
                    shownPhotoUrl = url
                }
            } catch (e: Exception) {
                setLoading(false)
                toast("Photo upload failed: ${S3Manager.friendlyError(e)}")
                return@launch
            }

            // 2) Save the fields (and the new photo URL) in Firebase
            FirebaseRefs.userRef(uid).updateChildren(updates)
                .addOnSuccessListener {
                    val hadPhoto = pendingPhoto != null
                    pendingPhoto = null
                    setLoading(false)
                    setEditing(false)
                    toast(if (hadPhoto) "Profile and photo updated ✅" else "Profile updated ✅")
                }
                .addOnFailureListener { e ->
                    setLoading(false)
                    toast("Update failed: ${e.localizedMessage}")
                }
        }
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Log out?")
            .setMessage("You'll need to sign in again to see your profile.")
            .setPositiveButton("Log out") { _, _ ->
                FirebaseRefs.auth.signOut()
                goToLogin()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !loading
        binding.btnCancel.isEnabled = !loading
        binding.btnSave.text = if (loading) "Saving…" else "Save changes"
    }
}
