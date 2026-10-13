package com.example.sufrabuyer

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
import com.example.sufrabuyer.databinding.ActivityBuyerHomeBinding
import com.example.sufrabuyer.databinding.ItemCategoryBinding
import com.example.sufrabuyer.databinding.ItemShopCardBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch

/**
 * Buyer home screen (Buyer app). Avatar shows the S3 profile photo (Milestone 2).
 * The header (name, address, initials) is live-synced with Firebase.
 * Categories and shops are sample UI for now – real shops come from sellers in Milestone 3.
 */
class BuyerHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBuyerHomeBinding
    private lateinit var uid: String

    private data class Category(val emoji: String, val label: String, @ColorRes val color: Int)
    private data class ShopPreview(
        val emoji: String, val name: String, val cuisine: String,
        val rating: String, val time: String, val badge: String, @ColorRes val color: Int
    )

    private val categories = listOf(
        Category("🍔", "Burgers", R.color.tile_orange),
        Category("🍕", "Pizza", R.color.tile_red),
        Category("🛒", "Grocery", R.color.tile_green),
        Category("🍰", "Desserts", R.color.tile_pink),
        Category("☕", "Coffee", R.color.tile_brown),
        Category("🥗", "Healthy", R.color.tile_green),
        Category("🍣", "Sushi", R.color.tile_blue),
        Category("💊", "Pharmacy", R.color.tile_purple)
    )

    private val sampleShops = listOf(
        ShopPreview("🍔", "Burger Barn", "Burgers · American", "4.8", "20–30 min", "Free delivery", R.color.tile_orange),
        ShopPreview("🍕", "Napoli Slice", "Pizza · Italian", "4.6", "25–35 min", "20% OFF", R.color.tile_red),
        ShopPreview("🥐", "Golden Crust", "Bakery · Breakfast", "4.9", "15–25 min", "New", R.color.tile_yellow),
        ShopPreview("🛒", "Fresh Mart", "Grocery · Essentials", "4.7", "30–40 min", "Free delivery", R.color.tile_green)
    )

    private val headerListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val user = User.fromSnapshot(snapshot) ?: return
            if (user.role != AppConfig.APP_ROLE) {   // safety: wrong account type for this app
                FirebaseRefs.auth.signOut(); goToLogin(); return
            }
            val firstName = user.name.trim().substringBefore(' ').ifEmpty { "there" }
            binding.tvGreeting.text = "Hi, $firstName 👋"
            binding.tvAddress.text = user.address.ifEmpty { "Set your address" }
            binding.tvAvatarSmall.text = initialsOf(user.name)
            showAvatarPhoto(user.photoUrl)
        }

        override fun onCancelled(error: DatabaseError) {
            toast("Couldn't load your data: ${error.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBuyerHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdge(binding.root, binding.header, padBottom = false)

        val firebaseUser = FirebaseRefs.auth.currentUser
        if (firebaseUser == null) { goToLogin(); return }
        uid = firebaseUser.uid

        buildCategories()
        buildShops()

        binding.avatarContainer.setOnClickListener { openProfile() }
        binding.searchCard.setOnClickListener { toast("🔍 Search is coming in Milestone 3") }

        binding.bottomNav.selectedItemId = R.id.nav_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
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

    private fun buildCategories() {
        binding.gridCategories.removeAllViews()
        categories.forEach { category ->
            val item = ItemCategoryBinding.inflate(layoutInflater, binding.gridCategories, false)
            item.tvEmoji.text = category.emoji
            item.tvLabel.text = category.label
            item.tile.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, category.color))
            item.root.setOnClickListener { toast("${category.emoji} ${category.label} shops coming in Milestone 3") }

            // equal-width columns
            val params = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply { width = 0 }
            binding.gridCategories.addView(item.root, params)
        }
    }

    private fun buildShops() {
        binding.shopsContainer.removeAllViews()
        sampleShops.forEach { shop ->
            val card = ItemShopCardBinding.inflate(layoutInflater, binding.shopsContainer, false)
            card.tvEmoji.text = shop.emoji
            card.tvName.text = shop.name
            card.tvCuisine.text = shop.cuisine
            card.tvMeta.text = "${shop.rating}  •  ${shop.time}"
            card.tvBadge.text = shop.badge
            card.cover.setBackgroundColor(ContextCompat.getColor(this, shop.color))
            card.root.setOnClickListener { toast("${shop.name} menu coming in Milestone 3") }
            binding.shopsContainer.addView(card.root)
        }
    }
}
