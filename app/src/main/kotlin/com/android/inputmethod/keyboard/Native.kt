@file:Suppress("unused")

package com.android.inputmethod.keyboard

object ProximityInfo {
    init {
        System.loadLibrary("jni_latinime")
    }

    @JvmStatic external fun setProximityInfoNative(
        displayWidth: Int, displayHeight: Int, gridWidth: Int, gridHeight: Int, mostCommonKeyWidth: Int,
        mostCommonKeyHeight: Int, proximityChars: IntArray, keyCount: Int, keyXCoordinates: IntArray,
        keyYCoordinates: IntArray, keyWidths: IntArray, keyHeights: IntArray, keyCharCodes: IntArray,
        sweetSpotCenterXs: FloatArray?, sweetSpotCenterYs: FloatArray?, sweetSpotRadii: FloatArray?,
    ): Long
    @JvmStatic external fun releaseProximityInfoNative(nativeProximityInfo: Long)
}
