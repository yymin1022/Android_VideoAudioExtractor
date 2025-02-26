package com.yong.audioextractor.model.player

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * VideoDecoder
 * - Decoding이 필요한 Video Track이 담긴
 * - MediaExtractor와 Track Number를 전달받고,
 * - Decoding을 진행하는 Model
 */
class VideoDecoder(
    surface: Surface,
    trackNum: Int,
    private val mediaExtractor: MediaExtractor,
    // 재생 및 일시정지 상태를 확인하기 위한 Field 함수
    private val isPaused: () -> Boolean,
    private val isPlaying: () -> Boolean,
    // Video 재생 종료를 처리하기 위한 Callback 함수
    private val onVideoEnd: () -> Unit
) {
    // Decoding을 위한 MediaCodec
    private val mediaCodec: MediaCodec
    // Decoding 할 Track의 Format
    private val trackFormat: MediaFormat = mediaExtractor.getTrackFormat(trackNum)

    // Decoding을 위한 Coroutine Job
    private var decodeJob: Job? = null
    // Decoding 시작 시간 기록
    private var decodeStartTime = 0L
    // Video의 전체 재생 시간 기록
    private var videoTotalTime = trackFormat.getLong(MediaFormat.KEY_DURATION)

    init {
        // MediaCodec 초기화
        mediaCodec = MediaCodec.createDecoderByType(trackFormat.getString(MediaFormat.KEY_MIME) ?: throw Exception("Unknown Audio Format"))

        // MediaCodec Configure 및 시작
        mediaCodec.configure(trackFormat, surface, null, 0)
        mediaCodec.start()
    }

    // Decoding 시작
    fun startDecoding() {
        var isInputEOS = false
        var isOutputEOS = false

        decodeJob?.cancel()
        decodeJob = CoroutineScope(Dispatchers.Default).launch {
            // Decode를 시작한 System Time
            decodeStartTime = System.nanoTime()
            // 직전 Frame의 시간
            var lastFrameTime = 0L

            val bufferInfo = MediaCodec.BufferInfo()
            // 재생 중이며, Output에 EOS가 발생할 때 까지 반복
            while(isPlaying() && !isOutputEOS) {
                // Pause 상태인 경우 Decode 하지 않고 대기
                if(isPaused()) {
                    delay(100)
                    continue
                }

                // Input에 EOS가 발생하지 않았다면 Input Buffer 요청
                if(!isInputEOS && !getInputBuffer()) {
                    isInputEOS = true
                }

                // 현재 Frame이 보여져야 할 시간에 올바르게 보여지는지 재생 경과 시간 확인
                syncTimestamp(lastFrameTime)

                // Output Buffer 처리
                if(!processOutputBuffer(bufferInfo)) {
                    isOutputEOS = true
                    break
                }

                // 보여진 Frame의 시간 업데이트
                lastFrameTime = getVideoSampleTime()
            }
        }
    }

    // Decoding 종료
    suspend fun stopDecoding() {
        // Decoding 작업 종료
        decodeJob?.join()

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

    // 렌더링할 Output Buffer 읽기
    private fun processOutputBuffer(bufferInfo: MediaCodec.BufferInfo): Boolean {
        val outputIdx = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)

        // Output Buffer가 유효한 경우
        if(outputIdx >= 0) {
            // 처리한 Buffer 비우기
            mediaCodec.releaseOutputBuffer(outputIdx, true)
        }

        // End Of Stream Flag인 경우 종료
        if((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            onVideoEnd()
            return false
        }

        return true
    }

    // 현재 Frame이 보여져야 할 시간에 올바르게 보여지는지 재생 경과 시간 확인
    private suspend fun syncTimestamp(lastFrameTime: Long) {
        if(lastFrameTime > 0L) {
            // Decode 시작 시간으로부터 현재까지 경과한 시간 계산
            // 정확도를 위해 Nano Time으로 계산 후 Millis로 변환
            val elapsedTime = (System.nanoTime() - decodeStartTime) / 1000000
            // 현재 Frame이 보여져야 할 시간과 경과 시간의 차 계산
            val delayOffset = getVideoSampleTime() / 1000 - elapsedTime
            // 차이나는 시간만큼 Delay
            if(delayOffset > 0) {
                delay(delayOffset)
            }
        }
    }

    // 현재 재생한 Frame의 Sample Time 반환
    fun getVideoSampleTime(): Long { return mediaExtractor.sampleTime }
    // 현재 재생중인 Video의 진행률 반환
    fun getVideoPlayRate(): Float { return getVideoSampleTime().toFloat() / videoTotalTime * 100 }

}
