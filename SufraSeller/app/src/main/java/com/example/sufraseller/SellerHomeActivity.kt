package com.example.sufraseller

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.GridLayout
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.sufraseller.databinding.ActivitySellerHomeBinding
import com.example.sufraseller.databinding.ItemActionBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch

/**
 * Seller dashboard (Seller app). Avatar shows the S3 profile photo (Milestone 2).
 * Shop name / owner are live-synced with Firebase.
 * Products & orders are wired up in Milestone 3.
 */
class SellerHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySellerHomeBinding
    private lateinit var uid: String

    private data class QuickAction(
        val emoji: String, val title: String, val subtitle: String,
        @ColorRes val color: Int, val onClick: () -> Unit
    )

    private val headerListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val user = User.fromSnapshot(snapshot) ?: return
            if (user.role != AppConfig.APP_ROLE) {   // safety: wrong account type for this app
                FirebaseRefs.auth.signOut(); goToLogin(); return
            }
            binding.tvShopName.text = "🏪 ${user.shopName.ifEmpty { "My Shop" }}"
            binding.tvOwner.text = "${user.shopCategory.ifEmpty { "Shop" }} · Owner: ${user.name}"
            binding.tvAvatarSmall.text = initialsOf(user.name)
            showAvatarPhoto(user.photoUrl)
        }

        override fun onCancelled(error: DatabaseError) {
            toast("Couldn't load your shop: ${error.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySellerHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdge(binding.root, binding.header, padBottom = false)

        val firebaseUser = FirebaseRefs.auth.currentUser
        if (firebaseUser == null) { goToLogin(); return }
        uid = firebaseUser.uid

        buildQuickActions()

        binding.avatarContainer.setOnClickListener { openProfile() }
        binding.btnCompleteProfile.setOnClickListener { openProfile() }

        binding.bottomNav.selectedItemId = R.id.nav_dashboard
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> true
                R.id.nav_profile -> { openProfile(); false }
                else -> { toast("Coming in Milestone 3 🚧"); false }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::uid.isInitialized) FirebaseRefs.userRef(uid).addValueEventListener(headerListener)
    }

    override fun onStop() {
        super.onStop()
        if (::uid.isInitialized) FirebaseRefs.userRef(uid).removeEventListener(headerListener)
    }

    private fun openProfile() = startActivity(Intent(this, ProfileActivity::class.java))

    /** Shows the S3 profile photo in the small avatar circle (Milestone 2). */
    private fun showAvatarPhoto(url: String) {
        if (url.isBlank() || !S3Manager.isConfigured) {
            binding.ivAvatarSmall.visibility = View.GONE
            return
        }
        lifecycleScope.launch {
            try {
                S3Manager.downloadPhoto(url)?.let {
                    binding.ivAvatarSmall.setImageBitmap(it)
                    binding.ivAvatarSmall.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                binding.ivAvatarSmall.visibility = View.GONE   // keep the initials
            }
        }
    }

    private fun buildQuickActions() {
        val soon = { toast("Coming in Milestone 3 🚧") }
        val actions = listOf(
            QuickAction("➕", "Add product", "List a new item", R.color.tile_green, soon),
            QuickAction("📦", "My products", "Edit or remove items", R.color.tile_blue, soon),
            QuickAction("🧾", "Incoming orders", "Accept or reject", R.color.tile_orange, soon),
            QuickAction("🏪", "Shop profile", "Name, category, address", R.color.tile_purple) { openProfile() }
        )

        binding.gridActions.removeAllViews()
        actions.forEach { action ->
            val item = ItemActionBinding.inflate(layoutInflater, binding.gridActions, false)
            item.tvEmoji.text = action.emoji
            item.tvTitle.text = action.title
            item.tvSubtitle.text = action.subtitle
            item.tile.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, action.color))
            item.root.setOnClickListener { action.onClick() }

            val params = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                val m = (6 * resources.displayMetrics.density).toInt()
                setMargins(m, m, m, m)
            }
            binding.gridActions.addView(item.root, params)
        }
    }
}
