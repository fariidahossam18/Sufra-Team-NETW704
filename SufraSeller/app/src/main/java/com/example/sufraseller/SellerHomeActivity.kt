package com.example.sufraseller

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.sufraseller.databinding.ActivitySellerHomeBinding
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch

/**
 * Seller dashboard (Seller app).
 *
 *  - Header: shop name, owner and avatar, live-synced with Firebase (Milestones 1-2)
 *  - Milestone 3: TabLayout + ViewPager2 with three fragments: My products, Add product, Orders.
 *  - Milestone 3: copies the shop's public details to shops/{uid}, so buyers see this shop
 *    (and any change to its name / photo) immediately.
 *  - Live counters of products and orders in the header.
 */
class SellerHomeActivity : AppCompatActivity() {

    companion object {
        const val TAB_PRODUCTS = 0
        const val TAB_ADD = 1
        const val TAB_ORDERS = 2
    }

    private lateinit var binding: ActivitySellerHomeBinding
    private lateinit var uid: String
    private lateinit var ordersQuery: Query

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
            publishShop(user)
        }

        override fun onCancelled(error: DatabaseError) {
            toast("Couldn't load your shop: ${error.message}")
        }
    }

    private val productsCountListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val count = snapshot.childrenCount
            binding.tvStatProducts.text = if (count == 1L) "📦 1 product" else "📦 $count products"
        }

        override fun onCancelled(error: DatabaseError) {}
    }

    private val ordersCountListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val count = snapshot.childrenCount
            binding.tvStatOrders.text = if (count == 1L) "🧾 1 order" else "🧾 $count orders"
        }

        override fun onCancelled(error: DatabaseError) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySellerHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdge(binding.root, binding.header)

        val firebaseUser = FirebaseRefs.auth.currentUser
        if (firebaseUser == null) { goToLogin(); return }
        uid = firebaseUser.uid
        ordersQuery = FirebaseRefs.ordersRef().orderByChild("sellerId").equalTo(uid)

        setupTabs()
        binding.avatarContainer.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    /** Connects the TabLayout with the ViewPager2 (same idea as Tutorial 3.1). */
    private fun setupTabs() {
        binding.viewPager.adapter = HomePagerAdapter(this)
        binding.viewPager.offscreenPageLimit = 2          // keep all 3 pages alive (the Add form keeps its text)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                TAB_ADD -> { tab.text = "Add"; tab.setIcon(R.drawable.ic_add) }
                TAB_ORDERS -> { tab.text = "Orders"; tab.setIcon(R.drawable.ic_orders) }
                else -> { tab.text = "Products"; tab.setIcon(R.drawable.ic_inventory) }
            }
        }.attach()
    }

    /** Switches to a tab, e.g. openTab(TAB_PRODUCTS) after a product is added. */
    fun openTab(position: Int) {
        binding.viewPager.currentItem = position
    }

    override fun onStart() {
        super.onStart()
        if (!::uid.isInitialized) return
        FirebaseRefs.userRef(uid).addValueEventListener(headerListener)
        FirebaseRefs.sellerProductsRef(uid).addValueEventListener(productsCountListener)
        ordersQuery.addValueEventListener(ordersCountListener)
    }

    override fun onStop() {
        super.onStop()
        if (!::uid.isInitialized) return
        FirebaseRefs.userRef(uid).removeEventListener(headerListener)
        FirebaseRefs.sellerProductsRef(uid).removeEventListener(productsCountListener)
        ordersQuery.removeEventListener(ordersCountListener)
    }

    /**
     * Copies the public part of the seller's profile to shops/{uid}.
     * The users node is private (only the owner can read it), so buyers read shops/ instead.
     */
    private fun publishShop(user: User) {
        val shop = mapOf(
            "uid" to uid,
            "shopName" to user.shopName.ifEmpty { "My Shop" },
            "shopCategory" to user.shopCategory,
            "address" to user.address,
            "ownerName" to user.name,
            "photoUrl" to user.photoUrl,
            "updatedAt" to ServerValue.TIMESTAMP
        )
        FirebaseRefs.shopRef(uid).updateChildren(shop)
            .addOnFailureListener { e -> toast("Couldn't publish your shop: ${e.message}") }
    }

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
}
