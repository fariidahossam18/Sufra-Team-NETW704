package com.example.sufrabuyer

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment

/** Temporary screen. This file will be replaced by the real CartFragment (Milestone 3). */
class CartFragment : Fragment(R.layout.fragment_placeholder) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.tvPlaceholder).text = "🛒 Cart\nComing soon"
    }
}
