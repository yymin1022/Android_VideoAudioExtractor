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

/**
 * VideoDecoder
 * - Video를 Decode하고 재생하기 위한 Model
 */
class VideoPlayer {
    // Video 정보 확인을 위한 Media Extractor
    private lateinit var mediaExtractor: MediaExtractor
    // Video Decode를 위한 Media Codec
    private lateinit var mediaCodec: MediaCodec

    // Decoding 작업을 수행하기 위한 Coroutine Job
    private var decodeJob: Job? = null
    // Render 작업을 수행하기 위한 Coroutine Job
    private var renderJob: Job? = null

    // 재생 중 상태를 표기하기 위한 Field
    var isPaused = false
    var isPlaying = false

    // Video Play 시작
    fun startVideoPlay(videoFd: AssetFileDescriptor, surface: Surface) {
        // MediaExtractor 초기화
        // Video FD에서 파일을 읽어 Source로 지정
        initExtractor(videoFd)

        // MediaExtractor를 통해 Source 내에서 Video Track 탐색
        // Track이 Null인 경우 없는 것이므로 Exception 발생
        val videoTrack = getVideoTrack() ?: throw Exception("No Video Track")
        // 탐색한 Track으로 MediaCodec Decoder 초기화
        initDecoder(videoTrack, surface)

        // Decoder 시작
        mediaCodec.start()
        isPlaying = true

        // Decoder Coroutine 작업 생성
        initVideoDecodeJob()
        // Render Coroutine 작업 생성
        initVideoRenderJob()
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
        // Decoder Coroutine 작업 종료
        decodeJob?.cancel()
        // Render Coroutine 작업 종료
        renderJob?.cancel()
        // Decoder 종료 및 해제
        mediaCodec.stop()
        mediaCodec.release()
        // Extractor 해제
        mediaExtractor.release()
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

    // 탐색한 Track으로 MediaCodec Decoder 초기화
    private fun initDecoder(trackNum: Int, surface: Surface) {
        // video/avc MIME Type 지정
        mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        // 탐색한 Track을 지정과 렌더링할 Surface를 지정
        // Crypto와 Flag는 지정하지 않음
        mediaCodec.configure(mediaExtractor.getTrackFormat(trackNum), surface, null, 0)
    }

    // Decoder Coroutine 작업 생성
    private fun initVideoDecodeJob() {
        decodeJob = CoroutineScope(Dispatchers.Default).launch {
            // Video Play 진행 중 반복
            while(isPlaying) {
                // Pause 상태인 경우 Decode 하지 않고 대기
                if(isPaused) {
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
                        stopVideoPlay()
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

    // Render Coroutine 작업 생성
    private fun initVideoRenderJob() {
        renderJob = CoroutineScope(Dispatchers.Default).launch {
            val bufferInfo = MediaCodec.BufferInfo()
            // Video Play 진행 중 반복
            while(isPlaying) {
                // Pause 상태인 경우 Decode 하지 않고 대기
                if(isPaused) {
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
                    stopVideoPlay()
                    break
                }
            }
        }
    }
}