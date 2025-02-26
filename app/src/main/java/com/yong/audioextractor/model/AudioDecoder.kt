package com.yong.audioextractor.model

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * AudioDecoder
 * - Decoding이 필요한 Audio Track이 담긴
 * - MediaExtractor와 Track Number를 전달받고,
 * - Decoding을 진행한 Output으로 Callback 함수를 호출하는 Model
 */
class AudioDecoder(
    trackNum: Int,
    private val mediaExtractor: MediaExtractor,
    private val processBuffer: (ByteArray) -> Unit,
    // 재생 및 일시정지 상태를 확인하기 위한 Field 함수
    private val isPaused: () -> Boolean = { false },
    private val isPlaying: () -> Boolean = { true },
    // Time Sync 여부를 확인하기 위한 Field
    private val isTimeSyncNeeded: Boolean = false,
    // Sync하기 위한 시간을 받아오기 위한 Field 함수
    private val getSyncTime: () -> Long = { 0 }
) {
    // Decoding을 위한 MediaCodec
    private val mediaCodec: MediaCodec
    // Decoding 할 Track의 Format
    private val trackFormat: MediaFormat = mediaExtractor.getTrackFormat(trackNum)

    // Decoding을 위한 Coroutine Job
    private var decodeJob: Job? = null

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

        decodeJob?.cancel()
        decodeJob = CoroutineScope(Dispatchers.Default).launch {
            val bufferInfo = MediaCodec.BufferInfo()
            // 재생 중이며, Output에 EOS가 발생할 때 까지 반복
            while(isActive && isPlaying() && !isOutputEOS) {
                // Pause 상태인 경우 Decode 하지 않고 대기
                if(isPaused()) {
                    delay(100)
                    continue
                }

                // Input에 EOS가 발생하지 않았다면 Input Buffer 요청
                if(!isInputEOS && !getInputBuffer()) {
                    isInputEOS = true
                }

                // Video와의 Time Sync가 필요한 경우 호출
                if(isTimeSyncNeeded) syncTimestamp()

                // Output Buffer 처리
                if(!processOutputBuffer(bufferInfo)) {
                    isOutputEOS = true
                }
            }
        }
    }

    fun stopDecoding() {
        // Decoding 작업 종료
        decodeJob?.cancel()

        // Media Codec 정지 및 해제
        mediaCodec.stop()
        mediaCodec.release()
    }

    // Input Buffer 요청
    private fun getInputBuffer(): Boolean {
        val inputIdx = mediaCodec.dequeueInputBuffer(0)

        // Input Buffer가 유효한 경우
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
            processBuffer(chunk)
            // 처리한 Buffer 비우기
            mediaCodec.releaseOutputBuffer(outputIdx, false)
        }

        // End Of Stream Flag인 경우 종료
        if(bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
            return false
        }

        return true
    }

    // Video와의 Time Sync 확인
    private suspend fun syncTimestamp() {
        while(isPlaying()) {
            // Audio 및 Video 각각의 Sample Time 확인
            val audioSampleTime = mediaExtractor.sampleTime
            val videoSampleTime = getSyncTime()

            // Sample Time이 올바르지 않은 경우 종료
            if(audioSampleTime <= 0 || videoSampleTime <= 0) break

            // Audio가 10ms 이상 앞서는 경우 Delay
            if(audioSampleTime > videoSampleTime + 10000) delay(5)
            // Video가 10ms 이상 앞서는 경우 Audio Advance 호출
            else if(audioSampleTime < videoSampleTime - 10000) mediaExtractor.advance()
            // Sync가 10ms 이하로 맞는 경우 종료
            else break
        }
    }
}