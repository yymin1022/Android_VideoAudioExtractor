package com.yong.audioextractor.model.player

import android.content.res.AssetFileDescriptor
import android.view.Surface

/**
 * VideoPlayer
 * - 파일의 Audio/Video를 Decode하고 재생하기 위한 Model
 */
class VideoPlayer {
    // 재생 중 상태를 표기하기 위한 Field
    private var isPaused = false
    private var isPlaying = false
    fun isVideoPaused() = isPaused
    fun isVideoPlaying() = isPlaying

    private val audioDecoder = AudioDecoder(::isVideoPaused, ::isVideoPlaying, ::getVideoSampleTime)
    private val videoDecoder = VideoDecoder(::isVideoPaused, ::isVideoPlaying, ::onVideoEnded)

    // Video Play 시작
    fun startVideoPlay(videoFd: AssetFileDescriptor, surface: Surface) {
        isPlaying = true
        // Audio/Video Decoder 초기화
        audioDecoder.init(videoFd)
        videoDecoder.init(videoFd, surface)

        // Audio/Video Decoder 시작
        audioDecoder.startDecoding()
        videoDecoder.startDecoding()
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

        // Audio/Video Decoder 작업 종료
        audioDecoder.stopDecoding()
        videoDecoder.stopDecoding()
    }

    private fun onVideoEnded() {
        stopVideoPlay()
    }

    private fun getVideoSampleTime(): Long { return videoDecoder.getVideoSampleTime() }
}