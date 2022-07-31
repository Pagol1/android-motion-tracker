package com.example.android_motion_tracker

import android.annotation.SuppressLint
import android.graphics.PointF
import android.util.Log
import androidx.annotation.NonNull
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import java.util.HashMap
import kotlin.math.abs


// https://stackoverflow.com/questions/57203678/detecting-contours-of-multiple-faces-via-firebase-ml-kit-face-detection

class FaceDetectionProcessor {
    /*
     TODO:
     - Add Two containers: One for storing the previous value of landmarks, next one for storing the accesses.
     */
    private var faceFeatureMap: HashMap<Int, FaceTrackedFeatures> = HashMap()
    private var activeFaceList: HashMap<Int, Int> = HashMap()

    private var detectorOptions: FaceDetectorOptions = FaceDetectorOptions.Builder()
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .enableTracking()
        .build()
    private var detector: FaceDetector = FaceDetection.getClient(detectorOptions)

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
        Log.i(TAG, "Face Detection Succeeded")
        for (face in faces) {
            // TODO:
            // - Add processing for points here
            // - Approximate Z and find [R|T] matrix
            var movement: Int = MovementDir.MOV_NONE
            try {
                val faceId = face.trackingId
                val newFeatureMap = getFeatureList(face.allContours)
                val newBBArea = face.boundingBox.height().toFloat() * face.boundingBox.width().toFloat()
                val newFaceTrackedFeatures = FaceTrackedFeatures(faceId!!, newFeatureMap, newBBArea)

                // Face already being tracked, detect changes and update values
                if (activeFaceList.containsKey(faceId)) {
                    activeFaceList[faceId] = 1
                    movement = getMovementBasic(faceFeatureMap.get(faceId), newFaceTrackedFeatures)
                    faceFeatureMap.put(faceId, newFaceTrackedFeatures)
                }
                // Start tracking new face, register values
                else {
                    activeFaceList[faceId] = 1
                    faceFeatureMap.put(faceId, newFaceTrackedFeatures)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Face Tracking Failed", e)
            }

            graphicOverlay.add(FaceGraphic(graphicOverlay, face, movement))
        }

        for (id in activeFaceList.keys) {
            if (activeFaceList[id] == 1) { // Face currently in frame <= No changes
                activeFaceList[id] = 0
            }
            else { // Not not in frame, stop tracking
                activeFaceList.remove(id)
                faceFeatureMap[id]!!.del()
                faceFeatureMap.remove(id)
            }
        }
    }

    private fun onFailure(e: Exception, graphicOverlay: GraphicOverlay) {
        graphicOverlay.clear()
        Log.e(TAG, "Face detection failed")
    }

    fun stop() {
        detector.close()
    }

    private fun getFeatureList(faceContour: List<FaceContour>): HashMap<Int, List<PointF>> {
        val featureMap: HashMap<Int, List<PointF>> = HashMap()
        for (contour in faceContour) {
            val cntId = contour.faceContourType
            featureMap[cntId] = contour.points
        }
        return featureMap
    }

    private fun getMovementBasic(old: FaceTrackedFeatures?, new: FaceTrackedFeatures?): Int {
        var retVal = MovementDir.MOV_NONE
        retVal += sel(old!!.bbArea, new!!.bbArea, 4.0F, MovementDir.MOV_BACK, MovementDir.MOV_FRONT, MovementDir.MOV_NONE)
        retVal += sel(old.features[FaceContour.NOSE_BRIDGE]!![0].y, new.features[FaceContour.NOSE_BRIDGE]!![0].y, 2.0F, MovementDir.MOV_UP, MovementDir.MOV_DOWN, MovementDir.MOV_NONE)
        retVal += sel(old.features[FaceContour.NOSE_BRIDGE]!![0].x, new.features[FaceContour.NOSE_BRIDGE]!![0].x, 2.0F, MovementDir.MOV_LEFT, MovementDir.MOV_RIGHT, MovementDir.MOV_NONE)
//        retVal += if (old!!.bbArea > new!!.bbArea) MovementDir.MOV_BACK else MovementDir.MOV_FRONT
//        retVal += if (old.features[FaceContour.NOSE_BRIDGE]!![0].y > new.features[FaceContour.NOSE_BRIDGE]!![0].y) MovementDir.MOV_UP else MovementDir.MOV_DOWN
//        retVal += if (old.features[FaceContour.NOSE_BRIDGE]!![0].x > new.features[FaceContour.NOSE_BRIDGE]!![0].x) MovementDir.MOV_LEFT else MovementDir.MOV_RIGHT

        return retVal
    }

    private fun sel(val1: Float, val2: Float, thresh: Float, retGt: Int, retLt: Int, retNone: Int): Int {
        if (abs(val1 - val2) > thresh) {
            if (val1 > val2) {
                return retGt
            }
            else {
                return retLt
            }
        }
        else {
            return retNone
        }
    }

    companion object {
        private const val TAG = "test-FDC"
    }
}