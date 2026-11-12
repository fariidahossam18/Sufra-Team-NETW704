package com.example.sufrabuyer

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.sufrabuyer.databinding.ActivityShopBinding

/**
 * One shop and its products (Buyer app, Milestone 3).
 *
 * Opened from the Shops tab. The shop's details are sent with Intent.putExtra(...)
 * and read here with getStringExtra(...). The products grid itself is ProductsFragment.
 *
 * At the bottom there is a "View cart" bar (only when the cart has items from this shop).
 * It opens CartActivity on top of this screen, so Back returns to this shop.
 */
class ShopActivity : AppCompatActivity() {

    companion object {
        // Keys used with putExtra / getStringExtra when a shop is opened
        const val EXTRA_SHOP_ID = "shop_id"
        const val EXTRA_SHOP_NAME = "shop_name"
        const val EXTRA_SHOP_CATEGORY = "shop_category"
        const val EXTRA_SHOP_ADDRESS = "shop_address"
    }

    private lateinit var binding: ActivityShopBinding
    private var shopId = ""

    private val cartListener: () -> Unit = { updateCartBar() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityShopBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdge(binding.root, binding.header)

        // 1) Read the data sent by the Shops screen
        shopId = intent.getStringExtra(EXTRA_SHOP_ID).orEmpty()
        val shopName = intent.getStringExtra(EXTRA_SHOP_NAME).orEmpty().ifEmpty { "Shop" }
        val category = intent.getStringExtra(EXTRA_SHOP_CATEGORY).orEmpty()
        val address = intent.getStringExtra(EXTRA_SHOP_ADDRESS).orEmpty()

        if (shopId.isEmpty()) {
            toast("Shop not found")
            finish()
            return
        }

        // 2) Header
        binding.tvShopName.text = "${categoryEmoji(category)} $shopName"
        binding.tvShopInfo.text = listOf(category, address)
            .filter { it.isNotBlank() }
            .joinToString("  ·  ")
            .ifEmpty { "Sufra shop" }
        binding.btnBack.setOnClickListener { finish() }
        binding.cartBar.setOnClickListener { openCart() }

        // 3) Put the products fragment in the container (only the first time, not after rotation)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.productsContainer, ProductsFragment.newInstance(shopId, shopName))
                .commit()
        }
    }

    override fun onStart() {
        super.onStart()
        CartManager.addListener(cartListener)
        updateCartBar()
    }

    override fun onStop() {
        super.onStop()
        CartManager.removeListener(cartListener)
    }

    private fun updateCartBar() {
        val show = !CartManager.isEmpty() && CartManager.shopId == shopId
        binding.cartBar.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            binding.tvCartCount.text = CartManager.itemCount.toString()
            binding.tvCartTotal.text = formatPrice(CartManager.total)
        }
    }

    /**
     * Opens the cart on its own screen (CartActivity) ON TOP of this shop,
     * so the back arrow / Back button returns to this same shop.
     */
    private fun openCart() {
        startActivity(Intent(this, CartActivity::class.java))
    }
}
