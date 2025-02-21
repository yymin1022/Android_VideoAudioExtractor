package com.yong.audioextractor.model.extractor

import android.media.MediaExtractor
import java.nio.ByteBuffer

class PcmDecoder(
    private val extractor: MediaExtractor
) {
    fun decodePcm(): List<ByteBuffer> {
        return emptyList()
    }
}