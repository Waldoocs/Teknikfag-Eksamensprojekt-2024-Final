package com.example.myapplication

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.SurfaceView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.text.TextRecognizer
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var surfaceView: SurfaceView
    private lateinit var captureButton: Button
    private lateinit var selectImageButton: Button
    private lateinit var textRecognizer: TextRecognizer
    private lateinit var scannedTextView: TextView
    private lateinit var responseTextView: TextView

    private lateinit var takePictureLauncher: ActivityResultLauncher<Intent>
    private lateinit var selectImageLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.surfaceView)
        surfaceView.setBackgroundColor(Color.WHITE)
        captureButton = findViewById(R.id.captureButton)
        selectImageButton = findViewById(R.id.selectImageButton)
        scannedTextView = findViewById(R.id.scannedTextView)
        responseTextView = findViewById(R.id.responseTextView)

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

        scannedTextView.setOnLongClickListener {
            copyTextToClipboard(scannedTextView.text.toString())
            true
        }

        responseTextView.setOnLongClickListener {
            copyTextToClipboard(responseTextView.text.toString())
            true
        }

        // Initialize ActivityResultLaunchers
        takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val imageBitmap = result.data?.extras?.get("data") as Bitmap
                performOcr(imageBitmap)
            } else {
                showToast("Image capture canceled")
            }
        }

        selectImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val imageUri = result.data?.data
                if (imageUri != null) {
                    try {
                        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                        // Upload the image to Firebase Storage and retrieve the URL
                        uploadImageToFirebaseStorage(imageUri)
                    } catch (e: IOException) {
                        e.printStackTrace()
                        showToast("Failed to load image")
                    }
                }
            } else {
                showToast("Image selection canceled")
            }
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
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            takePictureLauncher.launch(takePictureIntent)
        } else {
            showToast("No camera app found")
        }
    }

    private fun dispatchSelectImageIntent() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        selectImageLauncher.launch(intent)
    }

    private fun performOcr(bitmap: Bitmap) {
        val frame = Frame.Builder().setBitmap(bitmap).build()
        val textBlocks = textRecognizer.detect(frame)
        val detectedText = StringBuilder()
        for (i in 0 until textBlocks.size()) {
            detectedText.append(textBlocks.valueAt(i).value)
            detectedText.append("\n")
        }
        scannedTextView.text = detectedText.toString()
        scannedTextView.gravity = Gravity.CENTER
    }

    private fun uploadImageToFirebaseStorage(imageUri: Uri) {
        val storageRef = Firebase.storage.reference
        val imageRef = storageRef.child("images/${UUID.randomUUID()}.jpg")

        imageRef.putFile(imageUri)
            .addOnSuccessListener { taskSnapshot ->
                // Image uploaded successfully
                // Retrieve the download URL
                imageRef.downloadUrl.addOnSuccessListener { uri ->
                    val imageUrl = uri.toString()
                    // Perform OCR with the retrieved image URL
                    performOcrWithImageUrl(imageUrl)
                }.addOnFailureListener {
                    showToast("Failed to retrieve image URL")
                }
            }
            .addOnFailureListener { e ->
                // Handle unsuccessful uploads
                e.printStackTrace()
                showToast("Failed to upload image")
            }
    }

    private fun encodeUrl(url: String): String {
        return url.replace("/", "%2F")
    }

    private suspend fun shortenUrl(longUrl: String, accessToken: String): String {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val requestBody = JSONObject().put("long_url", longUrl).toString()
            val request = Request.Builder()
                .url("https://api-ssl.bitly.com/v4/shorten")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val responseBody = response.body?.string()
                val jsonObject = JSONObject(responseBody!!)
                jsonObject.getString("link")
            }
        }
    }

    private fun performOcrWithImageUrl(imageUrl: String) {
        val accessToken = "0cdcdccf70dcbe52cb43c921fee3fad3d912c04b"
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val shortenedUrl = shortenUrl(imageUrl, accessToken)
                // Construct the SERP API URL with the shortened URL
                val apiKey = "b42486ae7a4fbe57434cbaa737542c5f49ac2920b7bb97dd726a31ed6c9a0c0a"
                val serpApiUrl = "https://serpapi.com/search.json?engine=google_lens&url=$shortenedUrl&api_key=$apiKey"

                // Make API call to SERP API using the constructed URL
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(serpApiUrl)
                    .build()

                // Perform network operation on IO dispatcher
                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    // Update UI with the API response
                    responseData?.let {
                        updateUiWithApiResponse(it)
                    } ?: showToast("Empty response")
                } else {
                    showToast("Failed to make API call")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to shorten URL")
            }
        }
    }

    private fun updateUiWithApiResponse(responseData: String) {
        // Update UI with the API response
        // For example, you can display it in responseTextView
        runOnUiThread {
            responseTextView.text = responseData
        }
    }

    private fun copyTextToClipboard(text: String) {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("Scanned Text", text)
        clipboardManager.setPrimaryClip(clipData)
        showToast("Text copied to clipboard")
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 100
    }
}
