package com.example.sufrabuyer

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.sufrabuyer.databinding.ActivityBuyerHomeBinding
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch

/**
 * Buyer home screen (Buyer app).
 *
 *  - Header: greeting, delivery address and avatar, live-synced with Firebase (Milestones 1-2)
 *  - Milestone 3: TabLayout + ViewPager2 with two fragments: Shops and Cart.
 *    The user can tap a tab or swipe left/right. The Cart tab shows a badge with the number of items.
 *
 * Other screens can open a specific tab with   putExtra(EXTRA_OPEN_TAB, TAB_CART)
 */
class BuyerHomeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
        const val TAB_SHOPS = 0
        const val TAB_CART = 1
    }

    private lateinit var binding: ActivityBuyerHomeBinding
    private lateinit var uid: String

    /** Called by CartManager whenever the cart changes -> update the badge on the Cart tab. */
    private val cartListener: () -> Unit = { updateCartBadge() }

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
        applyEdgeToEdge(binding.root, binding.header)

        val firebaseUser = FirebaseRefs.auth.currentUser
        if (firebaseUser == null) { goToLogin(); return }
        uid = firebaseUser.uid

        setupTabs()
        binding.avatarContainer.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        if (savedInstanceState == null) openTabFromIntent(intent)
    }

    /** Connects the TabLayout with the ViewPager2 (same idea as Tutorial 3.1). */
    private fun setupTabs() {
        binding.viewPager.adapter = HomePagerAdapter(this)
        binding.viewPager.offscreenPageLimit = 1
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                TAB_CART -> { tab.text = "Cart"; tab.setIcon(R.drawable.ic_cart) }
                else -> { tab.text = "Shops"; tab.setIcon(R.drawable.ic_store) }
            }
        }.attach()
    }

    /** Lets other screens (e.g. ShopActivity) bring the user back here on a specific tab. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTabFromIntent(intent)
    }

    private fun openTabFromIntent(intent: Intent?) {
        val tab = intent?.getIntExtra(EXTRA_OPEN_TAB, -1) ?: -1
        if (tab >= 0) openTab(tab)
    }

    /** Switches to a tab, e.g. openTab(TAB_SHOPS) from the empty cart. */
    fun openTab(position: Int) {
        binding.viewPager.currentItem = position
    }

    override fun onStart() {
        super.onStart()
        if (!::uid.isInitialized) return
        FirebaseRefs.userRef(uid).addValueEventListener(headerListener)
        CartManager.addListener(cartListener)
        updateCartBadge()
    }

    override fun onStop() {
        super.onStop()
        if (!::uid.isInitialized) return
        FirebaseRefs.userRef(uid).removeEventListener(headerListener)
        CartManager.removeListener(cartListener)
    }

    private fun updateCartBadge() {
        val tab = binding.tabLayout.getTabAt(TAB_CART) ?: return
        val count = CartManager.itemCount
        if (count > 0) {
            tab.orCreateBadge.apply {
                number = count
                backgroundColor = ContextCompat.getColor(this@BuyerHomeActivity, R.color.brand_primary)
                badgeTextColor = ContextCompat.getColor(this@BuyerHomeActivity, R.color.white)
            }
        } else {
            tab.removeBadge()
        }
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
