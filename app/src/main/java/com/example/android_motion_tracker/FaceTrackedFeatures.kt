package com.example.android_motion_tracker

import android.graphics.PointF
import androidx.annotation.NonNull
import java.util.HashMap

data class FaceTrackedFeatures(@NonNull val faceId: Int, var features: HashMap<Int, List<PointF>>, val bbArea: Float) {
    fun del() {
        features.clear()
    }
}
