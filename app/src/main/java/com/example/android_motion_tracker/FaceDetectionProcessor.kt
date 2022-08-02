package com.example.android_motion_tracker

import android.annotation.SuppressLint
import android.graphics.PointF
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlin.math.abs
// https://stackoverflow.com/questions/57203678/detecting-contours-of-multiple-faces-via-firebase-ml-kit-face-detection

class FaceDetectionProcessor {
    /*
     TODO:
     - Find a way to wait for pose detection to complete before movement calculation
     */
//    private var facesDetected
    private var faceFeatureMap: HashMap<Int, FaceTrackedFeatures> = HashMap()
    private var activeFaceList: HashMap<Int, Int> = HashMap()

//    data class poseMap(public val curPose: HashMap<Int, PoseLandmark?>)
    private var posesDetected: HashMap<Int, PoseLandmark?> = hashMapOf(
        PoseLandmark.LEFT_SHOULDER to null,
        PoseLandmark.RIGHT_SHOULDER to null,
        PoseLandmark.LEFT_HIP to null,
        PoseLandmark.RIGHT_HIP to null
    )
    private var updatedPosesDetected: HashMap<Int, PoseLandmark?> = hashMapOf(
        PoseLandmark.LEFT_SHOULDER to null,
        PoseLandmark.RIGHT_SHOULDER to null,
        PoseLandmark.LEFT_HIP to null,
        PoseLandmark.RIGHT_HIP to null
    )
    private var chestDetected: Boolean = true
    private var chestMotionAvaliable: Boolean = false

    private var detectorOptions: FaceDetectorOptions = FaceDetectorOptions.Builder()
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .enableTracking()
        .build()
    private var detector: FaceDetector = FaceDetection.getClient(detectorOptions)

    private var poseDetectorOptions: PoseDetectorOptions = PoseDetectorOptions.Builder()
        .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
        .setPreferredHardwareConfigs(PoseDetectorOptions.CPU)
        .build()
    private var pose_detector: PoseDetector = PoseDetection.getClient(poseDetectorOptions)

    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("UnsafeOptInUsageError")
    fun processImgProxy(imageProxy: ImageProxy, graphicOverlay: GraphicOverlay) {
        val inputImage = InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)
        var failedBoth = true
//        var test = 0
//        var task1 = GlobalScope.async {
            pose_detector.process(inputImage)
                .addOnSuccessListener { task ->
//                val results = task.allPoseLandmarks
                    // If all four are there, store and track across frames
                    failedBoth = false
                    Log.i(TAG, "Pose detection Succeeded")
                    try {
                        onPoseSuccess(task)
                    } catch (e: Exception) {
                        Log.e(TAG, "Pose tracking failed", e)
                    }
                    if (chestDetected) {
                        Log.i(TAG, "Chest detected")
                    }
//                test += 1
                }
                .addOnFailureListener {
                    chestDetected = false
                    chestMotionAvaliable = false
//                test += 2
                    Log.e(TAG, "Pose detection Failed")
                    // Do nothing (switch to face only)
                }
//        }
//        awaitAll(task1)

            detector.process(inputImage)
                .addOnSuccessListener { results ->
                    failedBoth = false
                    Log.i(TAG, "Face detection Succeeded")
                    onFaceSuccess(results, graphicOverlay)
//                test += 4
                    imageProxy.close()
                }
                .addOnFailureListener {
//                onFaceFailure(it, graphicOverlay)
                    Log.e(TAG, "Face detection Failed")
//                test += 8
                    imageProxy.close()
                    onFailure(graphicOverlay)
                }


//        Log.i(TAG, "TEST1: %d".format(test))
        // listeners are async
//        if (failedBoth) onFailure(graphicOverlay)

    }

    private fun onPoseSuccess(detectedPose: Pose?) {
        chestDetected = true
        chestMotionAvaliable = true
        var cPose: PoseLandmark? = detectedPose!!.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        cPose?.let { updatedPosesDetected[PoseLandmark.LEFT_SHOULDER] = it } ?: run { chestDetected= false }

        cPose = detectedPose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        cPose?.let { updatedPosesDetected[PoseLandmark.RIGHT_SHOULDER] = it } ?: run { chestDetected= false }

        cPose = detectedPose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        cPose?.let { updatedPosesDetected[PoseLandmark.LEFT_HIP] = it } ?: run { chestDetected= false }

        cPose = detectedPose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        cPose?.let { updatedPosesDetected[PoseLandmark.RIGHT_HIP] = it } ?: run { chestDetected= false }
//        if (cPose != null) {
//            updatedPosesDetected[PoseLandmark.RIGHT_SHOULDER] = cPose
//        } else {
//            chestDetected = false
//        }
        // Else clear the map <- to be used for next check
        if (!chestDetected) {
            for (key in posesDetected.keys) {
                posesDetected[key] = null
            }
        }
        // Check if two frames are present
        for (value in posesDetected.values) {
            if (value == null) {
                chestMotionAvaliable = false
            }
        }
    }

    private fun onFaceSuccess(faces: List<Face>, graphicOverlay: GraphicOverlay) {
        graphicOverlay.clear()
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
                    movement = getMovementBasicFace(faceFeatureMap.get(faceId), newFaceTrackedFeatures)
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
            if (chestMotionAvaliable) {
                // Override
                movement = getMovementBasicChest(posesDetected, updatedPosesDetected)
            }
            // Update pose | Clear update queue
            for (key in updatedPosesDetected.keys) {
                posesDetected[key] = updatedPosesDetected[key]
                updatedPosesDetected[key] = null
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

    private fun onFailure(graphicOverlay: GraphicOverlay) {
        graphicOverlay.clear()
        Log.e(TAG, "Both detections failed")
    }

    fun stop() {
        detector.close()
        pose_detector.close()
    }

    private fun getFeatureList(faceContour: List<FaceContour>): HashMap<Int, List<PointF>> {
        val featureMap: HashMap<Int, List<PointF>> = HashMap()
        for (contour in faceContour) {
            val cntId = contour.faceContourType
            featureMap[cntId] = contour.points
        }
        return featureMap
    }

    private fun getMovementBasicFace(old: FaceTrackedFeatures?, new: FaceTrackedFeatures?): Int {
        var retVal = MovementDir.MOV_NONE
        retVal += sel(old!!.bbArea, new!!.bbArea, 4.0F, MovementDir.MOV_BACK, MovementDir.MOV_FRONT, MovementDir.MOV_NONE)
        retVal += sel(old.features[FaceContour.NOSE_BRIDGE]!![0].y, new.features[FaceContour.NOSE_BRIDGE]!![0].y, 2.0F, MovementDir.MOV_UP, MovementDir.MOV_DOWN, MovementDir.MOV_NONE)
        retVal += sel(old.features[FaceContour.NOSE_BRIDGE]!![0].x, new.features[FaceContour.NOSE_BRIDGE]!![0].x, 2.0F, MovementDir.MOV_LEFT, MovementDir.MOV_RIGHT, MovementDir.MOV_NONE)
//        retVal += if (old!!.bbArea > new!!.bbArea) MovementDir.MOV_BACK else MovementDir.MOV_FRONT
//        retVal += if (old.features[FaceContour.NOSE_BRIDGE]!![0].y > new.features[FaceContour.NOSE_BRIDGE]!![0].y) MovementDir.MOV_UP else MovementDir.MOV_DOWN
//        retVal += if (old.features[FaceContour.NOSE_BRIDGE]!![0].x > new.features[FaceContour.NOSE_BRIDGE]!![0].x) MovementDir.MOV_LEFT else MovementDir.MOV_RIGHT

        return retVal
    }

    private fun getMovementBasicChest(old: HashMap<Int, PoseLandmark?>, new: HashMap<Int, PoseLandmark?>): Int {
        var retVal = MovementDir.MOV_NONE
        val ls0 = old[PoseLandmark.LEFT_SHOULDER]!!.position
        val rs0 = old[PoseLandmark.RIGHT_SHOULDER]!!.position
        val lh0 = old[PoseLandmark.LEFT_HIP]!!.position
        val rh0 = old[PoseLandmark.RIGHT_SHOULDER]!!.position
        val c0 = PointF((ls0.x+lh0.x+rh0.x+rs0.x)/4, (ls0.y+lh0.y+rh0.y+rs0.y)/4)
        /* Readability */
        val ls1 = old[PoseLandmark.LEFT_SHOULDER]!!.position
        val rs1 = old[PoseLandmark.RIGHT_SHOULDER]!!.position
        val lh1 = old[PoseLandmark.LEFT_HIP]!!.position
        val rh1 = old[PoseLandmark.RIGHT_SHOULDER]!!.position
        val c1 = PointF((ls1.x+lh1.x+rh1.x+rs1.x)/4, (ls1.y+lh1.y+rh1.y+rs1.y)/4)
        // Calculate return value
        retVal += sel(chestBBarea(ls0, rs0, lh0), chestBBarea(ls1, rs1, lh1), 0.4F, MovementDir.MOV_BACK, MovementDir.MOV_FRONT, MovementDir.MOV_NONE)
        retVal += sel(c0.y, c1.y, 0.1F, MovementDir.MOV_UP, MovementDir.MOV_DOWN, MovementDir.MOV_NONE)
        retVal += sel(c0.x, c1.x, 0.1F, MovementDir.MOV_LEFT, MovementDir.MOV_RIGHT, MovementDir.MOV_NONE)
        Log.i("TEST", "%.2f %.2f %.2f".format(chestBBarea(ls0, rs0, lh0)-chestBBarea(ls1, rs1, lh1), c0.y-c1.y, c0.x-c1.x))
        return retVal
    }

    private fun chestBBarea(a: PointF, b: PointF, c: PointF): Float {
        return abs((b.x - a.x)*(c.y - a.y) - (b.y - a.y)*(c.x - a.x))
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