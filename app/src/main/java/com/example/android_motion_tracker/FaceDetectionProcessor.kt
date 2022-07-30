package com.example.android_motion_tracker

import android.annotation.SuppressLint
import android.util.Log
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark

// https://stackoverflow.com/questions/57203678/detecting-contours-of-multiple-faces-via-firebase-ml-kit-face-detection

class FaceDetectionProcessor {
    /*
     TODO:
     - Add interface with graphics overlay
     - onSuccess() -> Update graphic overlay
     - Override ImageProxy
     */

    private var detectorOptions: FaceDetectorOptions = FaceDetectorOptions.Builder()
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .enableTracking()
        .build()
    private var detector: FaceDetector = FaceDetection.getClient(detectorOptions)

//    @SuppressLint("UnsafeOptInUsageError")
//    override fun analyze(imageProxy: ImageProxy) {
//
//        val inputImage = InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)
//        detector.process(inputImage)
//            .addOnSuccessListener { results ->
//                onSuccess(results)
//                imageProxy.close()
//            }
//            .addOnFailureListener {
//                onFailure(it)
//                imageProxy.close()
//            }
//    }

    @SuppressLint("UnsafeOptInUsageError")
    fun processImgProxy(imageProxy: ImageProxy, graphicOverlay: GraphicOverlay) {
        val inputImage = InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)
        detector.process(inputImage)
            .addOnSuccessListener { results ->
                onSuccess(results, graphicOverlay)
                imageProxy.close()
            }
            .addOnFailureListener {
                onFailure(it, graphicOverlay)
                imageProxy.close()
            }
    }

    private fun onSuccess(faces: List<Face>, graphicOverlay: GraphicOverlay) {
        graphicOverlay.clear()
        for (face in faces) {
            // TODO:
            // - Add processing for points here
            // - Approximate Z and find [R|T] matrix
            graphicOverlay.add(FaceGraphic(graphicOverlay, face))
        }
    }

    private fun onFailure(e: Exception, graphicOverlay: GraphicOverlay) {
        graphicOverlay.clear()
        Log.e(TAG, "Face detection failed")
    }

    fun stop() {
        detector.close()
    }

    companion object {
        private const val TAG = "test-FDC"
    }
}