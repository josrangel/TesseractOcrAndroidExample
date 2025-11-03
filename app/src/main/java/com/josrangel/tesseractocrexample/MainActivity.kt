package com.josrangel.tesseractocrexample

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var textViewResult: TextView
    private lateinit var imageCapture: ImageCapture

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        previewView = findViewById(R.id.previewView)
        textViewResult = findViewById(R.id.textViewResult)
        val btnCapture = findViewById<Button>(R.id.btnCapture)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
        }

        btnCapture.setOnClickListener {
            captureAndRecognizeText()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureAndRecognizeText() {
        val outputOptions = ImageCapture.OutputFileOptions.Builder(createTempFile()).build()
        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val bitmap = BitmapFactory.decodeFile(outputFileResults.savedUri?.path ?: outputFileResults.savedUri?.toString())
                    ?: return

                recognizeTextWithTesseract(bitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(this@MainActivity, "Error capturando imagen", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun recognizeTextWithTesseract(bitmap: Bitmap) {
        val tess = TessBaseAPI()
        val dataPath = filesDir.absolutePath + "/tesseract/"

        prepareTessData(dataPath)

        if (!tess.init(dataPath, "spa")) {
            tess.recycle()
            textViewResult.text = "Error iniciando OCR"
            return
        }

        tess.setImage(bitmap)
        val result = tess.utF8Text
        tess.recycle()

        textViewResult.text = result

        // Opcional: extraer cédula con regex
        val idMatch = Regex("""\b\d{8,10}\b""").find(result)
        idMatch?.let {
            textViewResult.append("\n\nID detectado: ${it.value}")
        }
    }

    private fun prepareTessData(dataPath: String) {
        val tessdataDir = File(dataPath + "tessdata/")
        if (!tessdataDir.exists()) tessdataDir.mkdirs()

        val trainedDataPath = dataPath + "tessdata/spa.traineddata"
        val trainedDataFile = File(trainedDataPath)
        if (!trainedDataFile.exists()) {
            assets.open("tessdata/spa.traineddata").use { input ->
                FileOutputStream(trainedDataPath).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun createTempFile(): File {
        val filename = "capture_${System.currentTimeMillis()}.jpg"
        val file = File(cacheDir, filename)
        if (!file.exists()) file.createNewFile()
        return file
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(this, "Permiso de cámara requerido", Toast.LENGTH_SHORT).show()
        }
    }
}