@file:Suppress("unused")

package com.android.inputmethod.latin.utils

object BinaryDictionaryUtils {
    init {
        System.loadLibrary("jni_latinime")
    }

    @JvmStatic external fun createEmptyDictFileNative(filePath: String, dictVersion: Long, locale: String, keys: Array<String>, values: Array<String>): Boolean
    @JvmStatic external fun calcNormalizedScoreNative(before: IntArray, after: IntArray, score: Int): Float
    @JvmStatic external fun setCurrentTimeForTestNative(currentTime: Int): Int
}

class WordInputEventForPersonalization(
    @JvmField val mTargetWord: IntArray,
    @JvmField val mPrevWordsCount: Int,
    @JvmField val mPrevWordArray: Array<IntArray>,
    @JvmField val mIsPrevWordBeginningOfSentenceArray: BooleanArray,
    @JvmField val mIsValid: Boolean,
    @JvmField val mTimestamp: Int,
)
