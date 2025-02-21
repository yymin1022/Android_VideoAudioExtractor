package com.yong.audioextractor.model.player

import android.media.MediaCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VideoRenderer(
    private val mediaCodec: MediaCodec,
    private val isPaused: () -> Boolean,
    private val isPlaying: () -> Boolean,
    private val onVideoEnd: () -> Unit
) {
    // Render 작업을 수행하기 위한 Coroutine Job
    private var renderJob: Job? = null

    // Render Coroutine 작업 생성
    fun startRendering() {
        renderJob = CoroutineScope(Dispatchers.Default).launch {
            val bufferInfo = MediaCodec.BufferInfo()
            // Video Play 진행 중 반복
            while(isPlaying()) {
                // Pause 상태인 경우 Decode 하지 않고 대기
                if(isPaused()) {
                    delay(100)
                    continue
                }

                // 렌더링할 Output Buffer 읽기
                val outputIdx = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)
                if(outputIdx >= 0) {
                    // 데이터가 유효한 경우 렌더링
                    mediaCodec.releaseOutputBuffer(outputIdx, true)
                }

                // Video가 끝난 Flag인 경우 종료
                if((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    onVideoEnd()
                    break
                }
            }
        }
    }

    fun stopRendering() {
        renderJob?.cancel()
    }
}