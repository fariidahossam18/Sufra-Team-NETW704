package com.example.sufrabuyer

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.sufrabuyer.databinding.ActivitySignupBinding
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.ServerValue

/**
 * Registration screen (role-specific, Milestone 2).
 * The role is fixed by the app (AppConfig.APP_ROLE): the Buyer app creates buyers,
 * the Seller app creates sellers (who also enter shop name + category).
 * 1. Validate the inputs.
 * 2. Firebase Authentication creates the account (email + password).
 * 3. The profile is saved immediately to /users/{uid}.
 */
class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private val isSellerApp = AppConfig.APP_ROLE == User.ROLE_SELLER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdge(binding.root, binding.header)

        // Shop name + category only exist in the Seller app
        binding.sellerSection.visibility = if (isSellerApp) View.VISIBLE else View.GONE

        binding.btnSignUp.setOnClickListener { attemptSignUp() }
        binding.btnBack.setOnClickListener { finish() }
        binding.tvGoToLogin.setOnClickListener { finish() }
    }

    private fun attemptSignUp() {
        val name = binding.etName.value()
        val email = binding.etEmail.value()
        val phone = binding.etPhone.value()
        val address = binding.etAddress.value()
        val shopName = binding.etShopName.value()
        val shopCategory = binding.actvShopCategory.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        val confirm = binding.etConfirmPassword.text?.toString().orEmpty()

        // ---- clear old errors ----
        listOf(
            binding.tilName, binding.tilEmail, binding.tilPhone, binding.tilAddress,
            binding.tilShopName, binding.tilShopCategory, binding.tilPassword, binding.tilConfirmPassword
        ).forEach { it.error = null }

        // ---- validate ----
        var valid = true
        if (name.length < 3) { binding.tilName.error = "Please enter your full name"; valid = false }
        if (!isValidEmail(email)) { binding.tilEmail.error = "Enter a valid email address"; valid = false }
        if (!isValidPhone(phone)) { binding.tilPhone.error = "Enter a valid phone number (8–15 digits)"; valid = false }
        if (address.isEmpty()) { binding.tilAddress.error = "Address is required"; valid = false }
        if (isSellerApp && shopName.isEmpty()) { binding.tilShopName.error = "Shop name is required"; valid = false }
        if (isSellerApp && shopCategory.isEmpty()) { binding.tilShopCategory.error = "Choose a category"; valid = false }
        if (password.length < 6) { binding.tilPassword.error = "At least 6 characters"; valid = false }
        if (confirm != password) { binding.tilConfirmPassword.error = "Passwords don't match"; valid = false }
        if (!valid) return

        setLoading(true)

        // ---- 1) create the account in Firebase Authentication ----
        FirebaseRefs.auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val firebaseUser = result.user ?: run {
                    setLoading(false); toast("Sign up failed. Please try again."); return@addOnSuccessListener
                }

                firebaseUser.updateProfile(
                    UserProfileChangeRequest.Builder().setDisplayName(name).build()
                )

                // ---- 2) save the profile (JSON) under /users/{uid} ----
                val profile = hashMapOf<String, Any>(
                    "uid" to firebaseUser.uid,
                    "role" to AppConfig.APP_ROLE,
                    "name" to name,
                    "email" to email,
                    "phone" to phone,
                    "address" to address,
                    "photoUrl" to "",
                    "createdAt" to ServerValue.TIMESTAMP,
                    "updatedAt" to ServerValue.TIMESTAMP
                )
                if (isSellerApp) {
                    profile["shopName"] = shopName
                    profile["shopCategory"] = shopCategory
                }

                FirebaseRefs.userRef(firebaseUser.uid).setValue(profile)
                    .addOnSuccessListener {
                        toast("Account created 🎉 Welcome, ${name.substringBefore(' ')}!")
                        goToHome()
                    }
                    .addOnFailureListener { e ->
                        setLoading(false)
                        toast("Account created, but saving your profile failed: ${e.localizedMessage}")
                    }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                toast(friendlyAuthError(e))
            }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSignUp.isEnabled = !loading
        binding.btnSignUp.text = if (loading) "Creating account…" else "Create my account"
    }
}
