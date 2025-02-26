package com.yong.audioextractor.model.player

import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaExtractor
import android.media.MediaFormat
import android.view.Surface
import com.yong.audioextractor.model.AudioDecoder

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

    private val videoDecoder = VideoDecoder(::isVideoPaused, ::isVideoPlaying, ::stopVideoPlay)

    private lateinit var audioDecoder: AudioDecoder
    private lateinit var audioExtractor: MediaExtractor
    private lateinit var audioTrack: AudioTrack

    // Video Play 시작
    fun startVideoPlay(videoFd: AssetFileDescriptor, surface: Surface) {
        isPlaying = true

        // Audio Play 초기화
        initAudio(videoFd)

        // Audio/Video Decoder 초기화
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

        // Audio Track 종료
        audioTrack.stop()
        audioTrack.release()

        // Audio Extractor 해제
        audioExtractor.release()
    }

    private fun initAudio(videoFd: AssetFileDescriptor) {
        initAudioExtractor(videoFd)

        val audioTrack = getAudioTrackNum() ?: throw Exception("No Audio Track")
        initAudioTrack(audioTrack)

        audioDecoder = AudioDecoder(audioTrack, audioExtractor, ::playAudioBuffer, ::isPaused, ::isPlaying, true, ::getVideoSampleTime)
    }

    // Audio MediaExtractor 초기화
    private fun initAudioExtractor(videoFd: AssetFileDescriptor) {
        audioExtractor = MediaExtractor()
        // Video FD에서 파일을 읽어 Source로 지정
        audioExtractor.setDataSource(videoFd.fileDescriptor, videoFd.startOffset, videoFd.length)
    }

    // AudioTrack 초기화
    private fun initAudioTrack(trackNum: Int) {
        // Track의 Format 확인
        val trackFormat = audioExtractor.getTrackFormat(trackNum)

        // Format을 16bit PCM으로 지정
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        // SampleRate는 Track Format 값에 따라 지정
        val sampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        // Stereo 여부는 Track Format 값에 따라 지정
        val channelConfig = if(trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1) AudioFormat.CHANNEL_OUT_MONO
                            else AudioFormat.CHANNEL_OUT_STEREO

        // AudioTrack 객체 생성 및 초기화
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                // Audio 속성
                AudioAttributes.Builder()
                    // 일반 Media로 지정
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    // Music Type 지정
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                // Audio 형식을 지정된 값들로 초기화
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    // Source Video 내에서 Audio Track 탐색
    private fun getAudioTrackNum(): Int? {
        // 모든 Track 탐색
        for(i in 0 until audioExtractor.trackCount) {
            val trackFormat = audioExtractor.getTrackFormat(i)
            // Type을 알 수 없는 Track인 경우 건너뛰기
            val trackType = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue

            // Audio MIME Type을 갖는 Track 지정
            if(trackType.startsWith("audio/")) {
                audioExtractor.selectTrack(i)
                return i
            }
        }

        // 정해진 Track을 찾지 못한 경우 Null 반환
        return null
    }

    private fun playAudioBuffer(buffer: ByteArray) {
        audioTrack.write(buffer, 0, buffer.size)
    }

    // 현재 재생중인 Video의 진행률 반환
    fun getVideoPlayRate(): Float { return videoDecoder.getVideoPlayRate() }
    // 현재 재생한 Frame의 Sample Time 반환
    private fun getVideoSampleTime(): Long { return videoDecoder.getVideoSampleTime() }
}