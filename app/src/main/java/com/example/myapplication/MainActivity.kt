package com.example.myapplication

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.view.SurfaceView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.text.TextRecognizer
import android.Manifest
import android.content.pm.PackageManager
import android.widget.TextView
import android.view.Gravity

class MainActivity : AppCompatActivity() {

    private lateinit var surfaceView: SurfaceView
    private lateinit var captureButton: Button
    private lateinit var selectImageButton: Button // New button
    private lateinit var cameraSource: CameraSource
    private lateinit var textRecognizer: TextRecognizer
    private lateinit var scannedTextView: TextView // TextView to display scanned text

    private val REQUEST_IMAGE_CAPTURE = 1
    private val REQUEST_IMAGE_SELECT = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.surfaceView)
        captureButton = findViewById(R.id.captureButton)
        selectImageButton = findViewById(R.id.selectImageButton)
        scannedTextView = findViewById(R.id.scannedTextView) // Initialize TextView

        textRecognizer = TextRecognizer.Builder(applicationContext).build()

        captureButton.setOnClickListener {
            takePicture()
        }

        selectImageButton.setOnClickListener {
            dispatchSelectImageIntent()
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        } else {
            startCameraSource()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCameraSource()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCameraSource() {
        // Implementation remains the same
    }

    private fun takePicture() {
        // Implementation remains the same
    }

    private fun dispatchSelectImageIntent() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_IMAGE_SELECT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_SELECT && resultCode == RESULT_OK && data != null) {
            val imageUri = data.data
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
            performOcr(bitmap)
        }
    }

    private fun performOcr(bitmap: Bitmap) {
        val frame = Frame.Builder().setBitmap(bitmap).build()
        val textBlocks = textRecognizer.detect(frame)
        val detectedText = StringBuilder()
        for (i in 0 until textBlocks.size()) {
            detectedText.append(textBlocks.valueAt(i).value)
            detectedText.append("\n")
        }
        // Set detected text to TextView and center it
        scannedTextView.text = detectedText.toString()
        scannedTextView.gravity = Gravity.CENTER
    }

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 100
    }
}
