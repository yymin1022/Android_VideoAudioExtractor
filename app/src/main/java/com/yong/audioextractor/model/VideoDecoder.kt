package com.yong.audioextractor.model

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
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
    // Video 정보 확인을 위한 Media Extractor
    private lateinit var mediaExtractor: MediaExtractor
    // Video Decode를 위한 Media Codec
    private lateinit var mediaCodec: MediaCodec

    // 재생 중 상태를 표기하기 위한 Field
    var isPaused = false
    var isPlaying = false

    fun startDecoding(surface: Surface) {
        initExtractor()

        val videoTrack = getVideoTrack()
        initDecoder(videoTrack, surface)

        isPlaying = true
    }

    fun resumeDecoding() {
        isPaused = false
    }

    fun pauseDecoding() {
        isPaused = true
    }

    fun stopDecoding() {
        isPlaying = false
    }

    private fun initExtractor() {
        mediaExtractor = MediaExtractor()
        mediaExtractor.setDataSource(videoFd, fileOffset, fileLength)
    }

    private fun getVideoTrack(): Int {
        var trackNum = -1
        for(i in 0 until mediaExtractor.trackCount) {
            val trackFormat = mediaExtractor.getTrackFormat(i)
            val trackType = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue

            if(trackType == MediaFormat.MIMETYPE_VIDEO_AVC) {
                trackNum = i
                mediaExtractor.selectTrack(trackNum)
                break
            }
        }

        return trackNum
    }

    private fun initDecoder(trackNum: Int, surface: Surface) {
        mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        mediaCodec.configure(mediaExtractor.getTrackFormat(trackNum), surface, null, 0)
    }
}