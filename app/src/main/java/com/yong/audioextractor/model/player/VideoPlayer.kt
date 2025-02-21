package com.yong.audioextractor.model.player

import android.content.res.AssetFileDescriptor
import android.view.Surface

/**
 * VideoDecoder
 * - Video를 Decode하고 재생하기 위한 Model
 */
class VideoPlayer {
    // 재생 중 상태를 표기하기 위한 Field
    private var isPaused = false
    private var isPlaying = false
    fun isVideoPaused() = isPaused
    fun isVideoPlaying() = isPlaying

    private val videoDecoder = VideoDecoder(::isVideoPaused, ::isVideoPlaying, ::onVideoEnded)
    private lateinit var videoRenderer: VideoRenderer

    // Video Play 시작
    fun startVideoPlay(videoFd: AssetFileDescriptor, surface: Surface) {
        videoDecoder.init(videoFd, surface)
        videoDecoder.startDecoding()

        val mediaCodec = videoDecoder.getMediaCodec()
        videoRenderer = VideoRenderer(mediaCodec, ::isVideoPaused, ::isVideoPlaying, ::onVideoEnded)
        videoRenderer.startRendering()

        isPlaying = true
    }

    // 일시 정지된 Video Play 계속 진행
    fun resumeVideoPlay() {
        isPaused = false
    }

    // Video Play 일시 정지
    fun pauseVideoPlay() {
        isPaused = true
    }

    // Video Play 종료
    fun stopVideoPlay() {
        isPlaying = false

        // Decoder 작업 종료
        videoDecoder.stopDecoding()
        // Render 작업 종료
        videoRenderer.stopRendering()
    }

    private fun onVideoEnded() {
        stopVideoPlay()
    }
}