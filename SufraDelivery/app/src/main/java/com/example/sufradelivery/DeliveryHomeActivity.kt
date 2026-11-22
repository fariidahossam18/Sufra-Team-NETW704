package com.example.sufradelivery

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.sufradelivery.databinding.ActivityDeliveryHomeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

/**
 * Home of Sufra Delivery, the volunteer drivers' app (Milestone 4 bonus).
 *
 *  - Signed in with the volunteer's existing BUYER account (AppConfig.APP_ROLE = buyer)
 *  - Header: name, points, deliveries done and credit, live from users/{uid}
 *  - Tabs (swipe): Available | My deliveries | Rewards
 */
class DeliveryHomeActivity : AppCompatActivity() {

    companion object {
        const val TAB_AVAILABLE = 0
        const val TAB_MINE = 1
        const val TAB_REWARDS = 2
    }

    private lateinit var binding: ActivityDeliveryHomeBinding
    private lateinit var uid: String

    private val headerListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val user = User.fromSnapshot(snapshot) ?: return
            if (user.role != AppConfig.APP_ROLE) {         // safety: only buyer accounts
                FirebaseRefs.auth.signOut(); goToLogin(); return
            }
            val points = (snapshot.child("deliveryPoints").value as? Number)?.toLong() ?: 0L
            val done = (snapshot.child("deliveriesDone").value as? Number)?.toLong() ?: 0L
            val credit = (snapshot.child("credit").value as? Number)?.toDouble() ?: 0.0
            binding.tvGreeting.text = "Hi, ${user.name.substringBefore(' ').ifEmpty { "there" }} 🛵"
            binding.tvPoints.text = "⭐ $points points"
            binding.tvDelivered.text = "🛵 $done delivered"
            binding.tvCredit.text = "💰 ${formatPrice(credit)}"
        }

        override fun onCancelled(error: DatabaseError) {
            toast("Couldn't load your profile: ${error.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDeliveryHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdge(binding.root, binding.header)

        val firebaseUser = FirebaseRefs.auth.currentUser
        if (firebaseUser == null) { goToLogin(); return }
        uid = firebaseUser.uid

        binding.viewPager.adapter = DeliveryPagerAdapter(this)
        binding.viewPager.offscreenPageLimit = 2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                TAB_MINE -> { tab.text = "Mine"; tab.setIcon(R.drawable.ic_orders) }
                TAB_REWARDS -> { tab.text = "Rewards"; tab.setIcon(R.drawable.ic_star) }
                else -> { tab.text = "Available"; tab.setIcon(R.drawable.ic_delivery) }
            }
        }.attach()

        binding.btnLogout.setOnClickListener { confirmLogout() }
    }

    fun openTab(position: Int) {
        binding.viewPager.currentItem = position
    }

    override fun onStart() {
        super.onStart()
        if (::uid.isInitialized) FirebaseRefs.userRef(uid).addValueEventListener(headerListener)
    }

    override fun onStop() {
        super.onStop()
        if (::uid.isInitialized) FirebaseRefs.userRef(uid).removeEventListener(headerListener)
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Log out?")
            .setMessage("You can sign in again with your Sufra account.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Log out") { _, _ ->
                FirebaseRefs.auth.signOut()
                goToLogin()
            }
            .show()
    }
}
