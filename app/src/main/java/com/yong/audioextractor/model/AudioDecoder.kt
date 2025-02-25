package com.yong.audioextractor.model

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat

/**
 * AudioDecoder
 * - Decoding이 필요한 Audio Track이 담긴
 * - MediaExtractor와 Track Number를 전달받고,
 * - Decoding을 진행한 Output으로 Callback 함수를 호출하는 Model
 */
class AudioDecoder(
    private val mediaExtractor: MediaExtractor,
    private val trackNum: Int,
    private val processBuffer: () -> Unit
) {
    // Decoding을 위한 MediaCodec
    private val mediaCodec: MediaCodec
    // Decoding 할 Track의 Format
    private val trackFormat: MediaFormat = mediaExtractor.getTrackFormat(trackNum)

    init {
        // MediaCodec 초기화
        mediaCodec = MediaCodec.createDecoderByType(trackFormat.getString(MediaFormat.KEY_MIME) ?: throw Exception("Unknown Audio Format"))
        
        // MediaCodec Configure 및 시작
        mediaCodec.configure(trackFormat, null, null, 0)
        mediaCodec.start()
    }
}