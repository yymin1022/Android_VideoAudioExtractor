package com.yong.audioextractor.model

import android.view.Surface
import java.io.FileDescriptor

/**
 * VideoDecoder
 * - Video를 Decode하고 재생하기 위한 Model
 */
class VideoDecoder(
    // Video File에 접근하기 위한 FD 파라미터
    private val videoFd: FileDescriptor,
    private val fileOffset: Long,
    private val fileLength: Long
) {
    // 재생 중 상태를 표기하기 위한 Field
    // Pause 상태에서는 true 값음
    var isPlaying = false

    fun playVideo(surface: Surface) {
        isPlaying = true
    }

    fun pauseVideo() {

    }

    fun stopVideo() {
        isPlaying = false
    }
}