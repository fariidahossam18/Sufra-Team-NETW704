package com.example.sufrabuyer

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.sufrabuyer.databinding.ActivityCartBinding

/**
 * The cart as its own screen (Buyer app, Milestone 3).
 *
 * Opened from a shop's "View cart" bar. It shows the same CartFragment as the Cart tab,
 * but with a back arrow: Back (or the arrow) closes it and returns to the same shop.
 */
class CartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCartBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCartBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdge(binding.root, binding.header)

        binding.btnBack.setOnClickListener { finish() }

        // Put the cart fragment in the container (only the first time, not after rotation)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.cartContainer, CartFragment())
                .commit()
        }
    }
}
