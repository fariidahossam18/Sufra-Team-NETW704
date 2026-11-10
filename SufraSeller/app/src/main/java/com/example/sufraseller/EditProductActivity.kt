package com.example.sufraseller

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Temporary screen. This file will be replaced by the real EditProductActivity (Milestone 3). */
class EditProductActivity : AppCompatActivity() {

    companion object {
        // Key used with putExtra / getStringExtra when a product is opened
        const val EXTRA_PRODUCT_ID = "product_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_placeholder)
        findViewById<TextView>(R.id.tvPlaceholder).text = "✏️ Edit product\nComing soon"
    }
}
