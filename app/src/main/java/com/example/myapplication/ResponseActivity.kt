package com.example.myapplication

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ResponseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_response)

        // Retrieve the API response from the intent extras
        val apiResponse = intent.getStringExtra("api_response")

        // Find the TextView in the layout
        val textViewApiResponse: TextView = findViewById(R.id.textViewApiResponse)

        // Set the API response text to the TextView
        textViewApiResponse.text = apiResponse

        // Set a long-click listener on the TextView to enable copying text
        textViewApiResponse.setOnLongClickListener {
            copyTextToClipboard(apiResponse ?: "")
            true // Indicate that the long-click event is consumed
        }
    }

    private fun copyTextToClipboard(text: String) {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("API Response", text)
        clipboardManager.setPrimaryClip(clipData)
        showToast("Text copied to clipboard")
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}