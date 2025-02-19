package com.yong.audioextractor.model

import android.view.Surface
import java.io.FileDescriptor

class VideoDecoder(
    private val videoFd: FileDescriptor,
    private val fileOffset: Long,
    private val fileLength: Long
) {
    fun pauseVideo() {

    }

    fun playVideo(surface: Surface) {

    }

    fun stopVideo() {

    }
}