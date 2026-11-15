// The engine registers its natives against these LatinIME class names, so the
// classes keep them. RegisterNatives needs every method in its table declared,
// used or not.
@file:Suppress("unused")

package com.android.inputmethod.latin

import com.android.inputmethod.latin.utils.WordInputEventForPersonalization

object BinaryDictionary {
    init {
        System.loadLibrary("jni_latinime")
    }

    @JvmStatic external fun openNative(sourceDir: String, dictOffset: Long, dictSize: Long, isUpdatable: Boolean): Long
    @JvmStatic external fun createOnMemoryNative(formatVersion: Long, locale: String, keys: Array<String>, values: Array<String>): Long
    @JvmStatic external fun getHeaderInfoNative(dict: Long, outHeaderSize: IntArray, outFormatVersion: IntArray, outKeys: ArrayList<IntArray>, outValues: ArrayList<IntArray>)
    @JvmStatic external fun flushNative(dict: Long, filePath: String): Boolean
    @JvmStatic external fun needsToRunGCNative(dict: Long, mindsBlockByGC: Boolean): Boolean
    @JvmStatic external fun flushWithGCNative(dict: Long, filePath: String): Boolean
    @JvmStatic external fun closeNative(dict: Long)
    @JvmStatic external fun getFormatVersionNative(dict: Long): Int
    @JvmStatic external fun getProbabilityNative(dict: Long, word: IntArray): Int
    @JvmStatic external fun getMaxProbabilityOfExactMatchesNative(dict: Long, word: IntArray): Int
    @JvmStatic external fun getNgramProbabilityNative(dict: Long, prevWords: Array<IntArray?>, isBeginningOfSentence: BooleanArray, word: IntArray): Int
    @JvmStatic external fun getWordPropertyNative(
        dict: Long, word: IntArray, isBeginningOfSentence: Boolean, outCodePoints: IntArray, outFlags: BooleanArray,
        outProbabilityInfo: IntArray, outNgramPrevWordsArray: ArrayList<Array<IntArray>>,
        outNgramPrevWordIsBeginningOfSentenceArray: ArrayList<BooleanArray>, outNgramTargets: ArrayList<IntArray>,
        outNgramProbabilityInfo: ArrayList<IntArray>, outShortcutTargets: ArrayList<IntArray>,
        outShortcutProbabilities: ArrayList<Int>,
    )
    @JvmStatic external fun getNextWordNative(dict: Long, token: Int, outCodePoints: IntArray, outIsBeginningOfSentence: BooleanArray): Int
    @JvmStatic external fun getSuggestionsNative(
        dict: Long, proximityInfo: Long, traverseSession: Long, xCoordinates: IntArray, yCoordinates: IntArray,
        times: IntArray, pointerIds: IntArray, inputCodePoints: IntArray, inputSize: Int, suggestOptions: IntArray,
        prevWords: Array<IntArray?>, isBeginningOfSentence: BooleanArray, prevWordCount: Int,
        outputSuggestionCount: IntArray, outputCodePoints: IntArray, outputScores: IntArray, outputIndices: IntArray,
        outputTypes: IntArray, outputAutoCommitFirstWordConfidence: IntArray, inOutWeightOfLangModelVsSpatialModel: FloatArray,
    )
    @JvmStatic external fun addUnigramEntryNative(
        dict: Long, word: IntArray, probability: Int, shortcutTarget: IntArray?, shortcutProbability: Int,
        isBeginningOfSentence: Boolean, isNotAWord: Boolean, isPossiblyOffensive: Boolean, timestamp: Int,
    ): Boolean
    @JvmStatic external fun removeUnigramEntryNative(dict: Long, word: IntArray): Boolean
    @JvmStatic external fun addNgramEntryNative(dict: Long, prevWords: Array<IntArray?>, isBeginningOfSentence: BooleanArray, word: IntArray, probability: Int, timestamp: Int): Boolean
    @JvmStatic external fun removeNgramEntryNative(dict: Long, prevWords: Array<IntArray?>, isBeginningOfSentence: BooleanArray, word: IntArray): Boolean
    @JvmStatic external fun updateEntriesForWordWithNgramContextNative(dict: Long, prevWords: Array<IntArray?>, isBeginningOfSentence: BooleanArray, word: IntArray, isValidWord: Boolean, count: Int, timestamp: Int): Boolean
    @JvmStatic external fun updateEntriesForInputEventsNative(dict: Long, inputEvents: Array<WordInputEventForPersonalization>, startIndex: Int): Int
    @JvmStatic external fun getPropertyNative(dict: Long, query: String): String
    @JvmStatic external fun isCorruptedNative(dict: Long): Boolean
    @JvmStatic external fun migrateNative(dict: Long, dictFilePath: String, newFormatVersion: Long): Boolean
}

object DicTraverseSession {
    init {
        System.loadLibrary("jni_latinime")
    }

    @JvmStatic external fun setDicTraverseSessionNative(locale: String, dictSize: Long): Long
    @JvmStatic external fun initDicTraverseSessionNative(session: Long, dictionary: Long, previousWord: IntArray?, previousWordLength: Int)
    @JvmStatic external fun releaseDicTraverseSessionNative(session: Long)
}
