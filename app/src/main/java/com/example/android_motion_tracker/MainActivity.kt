package com.example.android_motion_tracker

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.Toast
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.core.content.PermissionChecker
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale

import com.example.android_motion_tracker.databinding.ActivityMainBinding
import java.util.ArrayList

// https://agrawalsuneet.github.io/blogs/safe-calls-vs-null-checks-in-kotlin/

class MainActivity : AppCompatActivity() {

    /*
    Issues:
    - Tracking IDs work but are temporal, moving out of the frame gives you a new ID.
    QOL:
    - Change button types to toggle
     */

    private lateinit var viewBinding: ActivityMainBinding
    private var graphicOverlay: GraphicOverlay? = null
//    private var previewView: PreviewView? = null
    private var previewUseCase: Preview? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var cameraSelector: CameraSelector? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var processing: Boolean = false
    private var imageAnalyzer: ImageAnalysis? = null
    private var imageProcessor: FaceDetectionProcessor? = null
    private var needUpdateGraphicOverlay: Boolean = true
    // Unsure if this is the correct one
    private lateinit var cameraExecutor: ExecutorService


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Binding will merge all of the views into an object, do not need to find views by ID
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        graphicOverlay = viewBinding.graphicOverlay
        // findViewById(R.id.graphicOverlay)
        setContentView(viewBinding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Set up the listeners for the buttons
        viewBinding.cameraToggleButton.setOnClickListener { switchView() }
        viewBinding.startCaptureButton.setOnClickListener { if (processing) {endProcess()} else {startProcess()} }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            previewUseCase = Preview.Builder().build().also {
                it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
            }
            cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            // Bind all of these to the camera
            // Assume analysis is OFF at the beginning
            try {
                cameraProvider!!.bindToLifecycle(this, cameraSelector!!, previewUseCase)
            } catch (e: Exception) {
                Log.e(TAG, "Binding Failed: ", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startProcess() {
        processing = true
        bindAllUseCases(true)
//        graphicOverlay!!.add(TestGraphic(graphicOverlay))
//        Toast.makeText(applicationContext, "Starting Process", Toast.LENGTH_SHORT).show()
    }

    private fun endProcess() {
        Log.i(TAG, "Ending process")
        processing = false
        graphicOverlay!!.clear()
        bindAllUseCases(false)
        imageProcessor?.run { this.stop() }
    }

    private fun switchView() {
        val newLensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        val newCameraSelector = CameraSelector.Builder().requireLensFacing(newLensFacing).build()
        try {
            if (cameraProvider!!.hasCamera(newCameraSelector)) {
                cameraSelector = newCameraSelector
                lensFacing = newLensFacing
                needUpdateGraphicOverlay = true
                if (processing) {
                    bindAllUseCases(true)
                }
                else {
                    bindAllUseCases(false)
                }
//                if (!processing) { endProcess() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Camera cannot be changed: ", e)
        }
    }

    private fun bindAllUseCases(bindAnalysis: Boolean) {
        if (cameraProvider != null) {
            cameraProvider!!.unbindAll()
            bindPreviewUseCase()
            if (bindAnalysis) bindAnalysisUseCase()
        }
    }

    private fun bindPreviewUseCase() {
        if (cameraProvider == null) {
            return
        }
        if (previewUseCase != null) {
            cameraProvider!!.unbind(previewUseCase)
        }
        previewUseCase = Preview.Builder().build().also {
            it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
        }
        try {
            cameraProvider!!.bindToLifecycle(this, cameraSelector!!, previewUseCase)
        } catch (e: Exception) {
            Log.e(TAG, "Binding Failed: ", e)
        }
    }

    private fun bindAnalysisUseCase() {
        if (cameraProvider == null) {
            return
        }
        if (imageAnalyzer != null) {
            cameraProvider!!.unbind(imageAnalyzer)
        }
        if (imageProcessor != null) {
            imageProcessor!!.stop()
        }

        imageProcessor = FaceDetectionProcessor()
        imageAnalyzer = ImageAnalysis.Builder().build()

        imageAnalyzer?.setAnalyzer(
            ContextCompat.getMainExecutor(this),
            ImageAnalysis.Analyzer { imageProxy: ImageProxy ->
                if (needUpdateGraphicOverlay) {
                    val imgFlipped = lensFacing == CameraSelector.LENS_FACING_FRONT
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                    if (rotationDegrees == 0 || rotationDegrees == 180) {
                        graphicOverlay!!.setImageSourceInfo(
                            imageProxy.width,
                            imageProxy.height,
                            imgFlipped
                        )
                    } else {
                        graphicOverlay!!.setImageSourceInfo(
                            imageProxy.height,
                            imageProxy.width,
                            imgFlipped
                        )
                    }
                }
                try {
                    imageProcessor!!.processImgProxy(imageProxy, graphicOverlay!!)
//                    Toast.makeText(applicationContext, "Created analyzer", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Could not add image analyzer: ", e)
                    Toast.makeText(applicationContext, "Could not create analyzer", Toast.LENGTH_LONG).show()
                }
            }
        )
        try {
            cameraProvider!!.bindToLifecycle(this, cameraSelector!!, imageAnalyzer)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind analyzer: ", e)
            Toast.makeText(applicationContext, "Could not bind analyzer", Toast.LENGTH_LONG).show()
        }

    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Log.d(TAG,"Permissions not granted by the user.")
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        imageProcessor?.run { this.stop() }
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "test_app"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA
            ).toTypedArray()
    }
}