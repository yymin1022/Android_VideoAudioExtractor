package com.yong.audioextractor.model.player

import android.content.res.AssetFileDescriptor
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VideoDecoder(
    private val isPaused: () -> Boolean,
    private val isPlaying: () -> Boolean,
    private val onVideoEnd: () -> Unit
) {
    // Video Decoding을 위한 Media Codec
    private lateinit var mediaCodec: MediaCodec
    // Video 정보 확인을 위한 Media Extractor
    private lateinit var mediaExtractor: MediaExtractor

    // Decoding 작업을 수행하기 위한 Coroutine Job
    private var decodeJob: Job? = null

    fun init(videoFd: AssetFileDescriptor, surface: Surface) {
        // MediaExtractor 초기화
        // Video FD에서 파일을 읽어 Source로 지정
        initExtractor(videoFd)

        // MediaExtractor를 통해 Source 내에서 Video Track 탐색
        // Track이 Null인 경우 없는 것이므로 Exception 발생
        val videoTrack = getVideoTrack() ?: throw Exception("No Video Track")
        // 탐색한 Track으로 MediaCodec Decoder 초기화
        initDecoder(videoTrack, surface)
    }

    // 탐색한 Track으로 MediaCodec Decoder 초기화
    private fun initDecoder(trackNum: Int, surface: Surface) {
        // video/avc MIME Type 지정
        mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        // 탐색한 Track을 지정과 렌더링할 Surface를 지정
        // Crypto와 Flag는 지정하지 않음
        mediaCodec.configure(mediaExtractor.getTrackFormat(trackNum), surface, null, 0)
    }

    // MediaExtractor 초기화
    private fun initExtractor(videoFd: AssetFileDescriptor) {
        mediaExtractor = MediaExtractor()
        // Video FD에서 파일을 읽어 Source로 지정
        mediaExtractor.setDataSource(videoFd.fileDescriptor, videoFd.startOffset, videoFd.length)
    }

    // MediaExtractor를 통해 Source 내에서 Video Track 탐색
    private fun getVideoTrack(): Int? {
        // MediaExtractor 내 모든 Track을 탐색
        for(i in 0 until mediaExtractor.trackCount) {
            val trackFormat = mediaExtractor.getTrackFormat(i)
            // Type을 알 수 없는 Track인 경우 건너뛰기
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

    // Decoder Coroutine 작업 생성
    fun startDecoding() {
        // Decoder 시작
        mediaCodec.start()

        decodeJob = CoroutineScope(Dispatchers.Default).launch {
            // Video Play 진행 중 반복
            while(isPlaying()) {
                // Pause 상태인 경우 Decode 하지 않고 대기
                if(isPaused()) {
                    delay(100)
                    continue
                }

                // Input Buffer 요청
                val inputIdx = mediaCodec.dequeueInputBuffer(0)
                // Buffer가 읽을 수 있는 상태인 경우
                if(inputIdx >= 0) {
                    val inputBuffer = mediaCodec.getInputBuffer(inputIdx) ?: throw Exception("Buffer Error")
                    // Buffer에서 Sample 데이터 읽기
                    val sampleSize = mediaExtractor.readSampleData(inputBuffer, 0)

                    // 더이상 읽을 Sample 데이터가 없는 경우
                    // 즉, 영상을 끝까지 재생한 경우
                    if(sampleSize < 0) {
                        // End Of Stream Flag 전달 후 종료
                        mediaCodec.queueInputBuffer(inputIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        onVideoEnd()
                        break
                    }

                    // 현재 읽은 데이터의 타임스탬프 확인
                    val sampleTime = mediaExtractor.sampleTime
                    // Decoder에 읽어들인 데이터 추가
                    mediaCodec.queueInputBuffer(inputIdx, 0, sampleSize, sampleTime, 0)
                    // 다음 Frame으로 이동
                    mediaExtractor.advance()
                }
            }
        }
    }

    fun stopDecoding() {
        decodeJob?.cancel()
    }

    fun getMediaCodec(): MediaCodec { return mediaCodec }
}
