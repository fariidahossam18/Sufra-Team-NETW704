package com.example.sufrabuyer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.sufrabuyer.databinding.ActivitySplashBinding

/**
 * Launcher screen.
 *  - Not signed in                     -> LoginActivity
 *  - Signed in with THIS app's role    -> home screen (auto-login)
 *  - Signed in with the other role     -> sign out, then LoginActivity
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdge(binding.root)

        Handler(Looper.getMainLooper()).postDelayed({ routeUser() }, 1200)
    }

    private fun routeUser() {
        val currentUser = FirebaseRefs.auth.currentUser
        if (currentUser == null) {
            goToLogin()
            return
        }

        FirebaseRefs.userRef(currentUser.uid).child("role").get()
            .addOnSuccessListener { snapshot ->
                val role = snapshot.value?.toString()
                if (role == AppConfig.APP_ROLE) {
                    goToHome()
                } else {
                    FirebaseRefs.auth.signOut()   // wrong app for this account
                    goToLogin()
                }
            }
            .addOnFailureListener { goToLogin() }
    }
}
