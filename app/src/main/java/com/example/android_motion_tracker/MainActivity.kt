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

class MainActivity : AppCompatActivity() {

    /*
    TODO:
    - Add a permission requester for camera access
    - Add a preview bind for the camera's view
    - Add responses for the button inputs
    QOL:
    - Change button types to toggle
     */

    private lateinit var viewBinding: ActivityMainBinding
    private var graphicOverlay: GraphicOverlay? = null
    private var previewView: PreviewView? = null
    private var previewUseCase: Preview? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var cameraSelector: CameraSelector? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var processing: Boolean = false

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
            try {
                cameraProvider!!.bindToLifecycle(this, cameraSelector!!, previewUseCase)
            } catch (e: Exception) {
                Log.e(TAG, "Binding Failed: ", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startProcess() {}

    private fun endProcess() {}

    private fun switchView() {}

    private fun bindAllUseCases() {
        if (cameraProvider != null) {
            cameraProvider!!.unbindAll()
            bindPreviewUseCase()
            bindAnalysisUseCase()
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
        cameraProvider!!.bindToLifecycle(this, cameraSelector!!, previewUseCase)
    }

    private fun bindAnalysisUseCase() {}

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