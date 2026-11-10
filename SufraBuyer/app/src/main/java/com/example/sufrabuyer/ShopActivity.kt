package com.example.sufrabuyer

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Temporary screen. This file will be replaced by the real ShopActivity (Milestone 3). */
class ShopActivity : AppCompatActivity() {

    companion object {
        // Keys used with putExtra / getStringExtra when a shop is opened
        const val EXTRA_SHOP_ID = "shop_id"
        const val EXTRA_SHOP_NAME = "shop_name"
        const val EXTRA_SHOP_CATEGORY = "shop_category"
        const val EXTRA_SHOP_ADDRESS = "shop_address"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_placeholder)
        val name = intent.getStringExtra(EXTRA_SHOP_NAME).orEmpty()
        findViewById<TextView>(R.id.tvPlaceholder).text = "🏪 $name\nProducts coming soon"
    }
}
