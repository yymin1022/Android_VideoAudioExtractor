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

    // Decoding 시작
    fun startDecoding(surface: Surface) {
        // MediaExtractor 초기화
        // Video FD에서 파일을 읽어 Source로 지정
        initExtractor()

        // MediaExtractor를 통해 Source 내에서 Video Track 탐색
        // Track이 Null인 경우 없는 것이므로 Exception 발생
        val videoTrack = getVideoTrack() ?: throw Exception("No Video Track")
        // 탐색한 Track으로 MediaCodec Decoder 초기화
        initDecoder(videoTrack, surface)

        // Decoder 시작
        mediaCodec.start()
        isPlaying = true
    }

    // 일시 정지된 Decoding을 계속 진행
    fun resumeDecoding() {
        isPaused = false
    }

    // Decoding 일시 정지
    fun pauseDecoding() {
        isPaused = true
    }

    // Decoding 종료
    fun stopDecoding() {
        // Decoder 종료 및 해제
        isPlaying = false
        mediaCodec.stop()
        mediaCodec.release()
        mediaExtractor.release()
    }

    // MediaExtractor 초기화
    private fun initExtractor() {
        mediaExtractor = MediaExtractor()
        // Video FD에서 파일을 읽어 Source로 지정
        mediaExtractor.setDataSource(videoFd, fileOffset, fileLength)
    }

    // MediaExtractor를 통해 Source 내에서 Video Track 탐색
    private fun getVideoTrack(): Int? {
        // MediaExtractor 내 모든 Track을 탐색
        for(i in 0 until mediaExtractor.trackCount) {
            val trackFormat = mediaExtractor.getTrackFormat(i)
            val trackType = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue

            // video/avc MIME Type을 갖는 Track 지정
            if(trackType == MediaFormat.MIMETYPE_VIDEO_AVC) {
                mediaExtractor.selectTrack(i)
                return i
            }
        }

        // 정해진 Track을 찾지 못한 경우 Null 반환
        return null
    }

    // 탐색한 Track으로 MediaCodec Decoder 초기화
    private fun initDecoder(trackNum: Int, surface: Surface) {
        // video/avc MIME Type 지정
        mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        // 탐색한 Track을 지정과 렌더링할 Surface를 지정
        // Crypto와 Flag는 지정하지 않음
        mediaCodec.configure(mediaExtractor.getTrackFormat(trackNum), surface, null, 0)
    }
}