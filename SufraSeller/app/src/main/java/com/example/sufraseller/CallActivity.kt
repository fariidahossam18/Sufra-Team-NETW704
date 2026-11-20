package com.example.sufraseller

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Temporary screen. This file will be replaced by the real CallActivity (Milestone 4 VoIP). */
class CallActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_placeholder)
        findViewById<TextView>(R.id.tvPlaceholder).text = "📞 VoIP call\nComing soon"
    }
}
