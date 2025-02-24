package com.yong.audioextractor.model.extractor

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer

class PcmDecoder(
    private val mediaExtractor: MediaExtractor
) {
    private lateinit var mediaCodec: MediaCodec
    private lateinit var resultBuffer: MutableList<Pair<Long, ByteBuffer>>

    fun decodePcm(): List<Pair<Long, ByteBuffer>> {
        initDecoder(mediaExtractor.sampleTrackIndex)

        val bufferInfo = MediaCodec.BufferInfo()
        resultBuffer = mutableListOf()

        while(true) {
            getInputBuffer()

            if(!processOutputBuffer(bufferInfo)) {
                break
            }
        }

        mediaCodec.stop()
        mediaCodec.release()

        return resultBuffer
    }

    private fun initDecoder(trackNum: Int) {
        val audioFormat = mediaExtractor.getTrackFormat(trackNum)
        val audioMimeType = audioFormat.getString(MediaFormat.KEY_MIME) ?: throw Exception("Unknown audio type")
        mediaCodec = MediaCodec.createDecoderByType(audioMimeType)

        mediaCodec.configure(audioFormat, null, null, 0)
        mediaCodec.start()
    }

    // Input Buffer 요청
    private fun getInputBuffer(): Boolean {
        val inputIdx = mediaCodec.dequeueInputBuffer(0)
        // Buffer가 읽을 수 있는 상태인 경우
        if(inputIdx >= 0) {
            val inputBuffer = mediaCodec.getInputBuffer(inputIdx)
            // Buffer에서 Sample 데이터 읽기
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

    // 재생할 Output Buffer 읽기
    private fun processOutputBuffer(bufferInfo: MediaCodec.BufferInfo): Boolean {
        val outputIdx = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)
        if(outputIdx >= 0) {
            // 데이터가 유효한 경우 처리
            val outputBuffer = mediaCodec.getOutputBuffer(outputIdx)
            val chunk = ByteArray(bufferInfo.size)
            outputBuffer?.get(chunk)
            outputBuffer?.clear()

            // Result Buffer에 데이터 추가
            resultBuffer.add(Pair(bufferInfo.presentationTimeUs, ByteBuffer.wrap(chunk)))

            // 처리한 Buffer 비우기
            mediaCodec.releaseOutputBuffer(outputIdx, false)

            // Video가 끝난 Flag인 경우 종료
            if(bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                return false
            }
        }

        return true
    }
}