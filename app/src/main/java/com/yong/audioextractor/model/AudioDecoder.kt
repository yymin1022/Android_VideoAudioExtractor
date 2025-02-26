package com.yong.audioextractor.model

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 * AudioDecoder
 * - Decoding이 필요한 Audio Track이 담긴
 * - MediaExtractor와 Track Number를 전달받고,
 * - Decoding을 진행한 Output으로 Callback 함수를 호출하는 Model
 */
class AudioDecoder(
    private val mediaExtractor: MediaExtractor,
    private val trackNum: Int,
    private val processBuffer: (ByteBuffer) -> Unit
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

    fun startDecoding() {
        var isInputEOS = false
        var isOutputEOS = false

        val bufferInfo = MediaCodec.BufferInfo()
        while(!isOutputEOS) {
            if(!isInputEOS && !getInputBuffer()) {
                isInputEOS = true
            }

            if(!processOutputBuffer(bufferInfo)) {
                isOutputEOS = true
            }
        }
    }

    // Input Buffer 요청
    private fun getInputBuffer(): Boolean {
        val inputIdx = mediaCodec.dequeueInputBuffer(0)
        
        // Input Buffer가 유효한 경우
        if(inputIdx >= 0) {
            val inputBuffer = mediaCodec.getInputBuffer(inputIdx)
            // Buffer에 Sample 데이터 전달
            val sampleSize = mediaExtractor.readSampleData(inputBuffer!!, 0)

            // 더이상 읽을 Sample 데이터가 없는 경우
            // 즉, 영상을 끝까지 재생한 경우
            if(sampleSize < 0) {
                // End Of Stream Flag 전달 후 종료
                mediaCodec.queueInputBuffer(inputIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                return false
            }

            // 현재 읽은 데이터의 타임스탬프 확인
            val sampleTime = mediaExtractor.sampleTime

            // Decoder에 읽어들인 데이터 추가
            mediaCodec.queueInputBuffer(inputIdx, 0, sampleSize, sampleTime, 0)
            mediaExtractor.advance()
        }

        return true
    }

    // Output Buffer 처리
    private fun processOutputBuffer(bufferInfo: MediaCodec.BufferInfo): Boolean {
        val outputIdx = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)

        // Output Buffer가 유효한 경우
        if(outputIdx >= 0) {
            val outputBuffer = mediaCodec.getOutputBuffer(outputIdx)
            val chunk = ByteArray(bufferInfo.size)
            outputBuffer?.get(chunk)
            outputBuffer?.clear()

            // Buffer 처리 함수 호출
            processBuffer(ByteBuffer.wrap(chunk))

            // 처리한 Buffer 비우기
            mediaCodec.releaseOutputBuffer(outputIdx, false)

            // End Of Stream Flag인 경우 종료
            if(bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                return false
            }
        }

        return true
    }
}