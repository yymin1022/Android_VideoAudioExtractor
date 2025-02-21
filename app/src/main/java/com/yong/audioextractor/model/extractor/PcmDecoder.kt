package com.yong.audioextractor.model.extractor

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer

class PcmDecoder(
    private val mediaExtractor: MediaExtractor
) {
    private lateinit var mediaCodec: MediaCodec

    fun decodePcm(): List<ByteBuffer> {
        initDecoder()
        return emptyList()
    }

    private fun initDecoder() {
        val audioTrackNum = mediaExtractor.sampleTrackIndex
        val audioFormat = mediaExtractor.getTrackFormat(audioTrackNum)
        val audioMimeType = audioFormat.getString(MediaFormat.KEY_MIME) ?: throw Exception("Unknown audio type")
        mediaCodec = MediaCodec.createDecoderByType(audioMimeType)

        mediaCodec.configure(audioFormat, null, null, 0)
        mediaCodec.start()
    }
}