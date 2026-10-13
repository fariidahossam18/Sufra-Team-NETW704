package com.example.sufrabuyer

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.sufrabuyer.databinding.ActivityLoginBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Sign-in screen (role-specific, Milestone 2).
 * 1. Firebase Authentication checks the email + password.
 * 2. We read /users/{uid}/role.
 * 3. Only accounts with THIS app's role (AppConfig.APP_ROLE) may continue.
 *    A seller trying to sign in to the Buyer app (or the opposite) is signed out
 *    and told to use the other app.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdge(binding.root, binding.header)

        binding.btnLogin.setOnClickListener { attemptLogin() }
        binding.tvForgotPassword.setOnClickListener { sendPasswordReset() }
        binding.tvGoToSignUp.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }

    private fun attemptLogin() {
        val email = binding.etEmail.value()
        val password = binding.etPassword.text?.toString().orEmpty()

        binding.tilEmail.error = null
        binding.tilPassword.error = null

        if (!isValidEmail(email)) {
            binding.tilEmail.error = "Enter a valid email address"
            return
        }
        if (password.length < 6) {
            binding.tilPassword.error = "Password must be at least 6 characters"
            return
        }

        setLoading(true)
        FirebaseRefs.auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid == null) {
                    setLoading(false)
                    toast("Sign in failed. Please try again.")
                } else {
                    verifyRoleAndRedirect(uid)
                }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                toast(friendlyAuthError(e))
            }
    }

    /** Reads the saved role and only lets this app's role in. */
    private fun verifyRoleAndRedirect(uid: String) {
        FirebaseRefs.userRef(uid).child("role").get()
            .addOnSuccessListener { snapshot ->
                val savedRole = snapshot.value?.toString()
                when {
                    savedRole.isNullOrEmpty() -> {
                        FirebaseRefs.auth.signOut()
                        setLoading(false)
                        toast("No profile found for this account. Please sign up again.")
                    }
                    savedRole != AppConfig.APP_ROLE -> {
                        FirebaseRefs.auth.signOut()
                        setLoading(false)
                        showWrongAppDialog(savedRole)
                    }
                    else -> {
                        toast("Welcome back! 👋")
                        goToHome()
                    }
                }
            }
            .addOnFailureListener { e ->
                FirebaseRefs.auth.signOut()
                setLoading(false)
                toast("Couldn't load your profile: ${e.localizedMessage}")
            }
    }

    private fun showWrongAppDialog(savedRole: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Wrong app")
            .setMessage(
                "This is a ${roleLabel(savedRole)} account. " +
                    "Please sign in using the ${AppConfig.OTHER_APP_NAME} app."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun sendPasswordReset() {
        val email = binding.etEmail.value()
        if (!isValidEmail(email)) {
            binding.tilEmail.error = "Type your email first, then tap \"Forgot password?\""
            return
        }
        FirebaseRefs.auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { toast("Password reset link sent to $email 📧") }
            .addOnFailureListener { e -> toast(friendlyAuthError(e)) }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
        binding.btnLogin.text = if (loading) "Signing in…" else "Sign in"
    }
}
