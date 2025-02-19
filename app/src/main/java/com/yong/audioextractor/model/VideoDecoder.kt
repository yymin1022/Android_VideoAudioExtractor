package com.yong.audioextractor.model

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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

    // Decoding 작업을 수행하기 위한 Coroutine Job
    private var decodeJob: Job? = null
    // Render 작업을 수행하기 위한 Coroutine Job
    private var renderJob: Job? = null

    // 재생 중 상태를 표기하기 위한 Field
    var isPaused = false
    var isPlaying = false

    // Video Play 시작
    fun startVideoPlay(surface: Surface) {
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

    // Decoder Coroutine 작업 생성
    private fun initVideoDecodeJob() {
        decodeJob = CoroutineScope(Dispatchers.IO).launch {
            // Video Play 진행 중 반복
            while(isPlaying) {
                val inputIdx = mediaCodec.dequeueInputBuffer(0)
                if(inputIdx >= 0) {
                    val inputBuffer = mediaCodec.getInputBuffer(inputIdx) ?: throw Exception("Buffer Error")
                    val sampleSize = mediaExtractor.readSampleData(inputBuffer, 0)

                    if(sampleSize < 0) {
                        mediaCodec.queueInputBuffer(inputIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        break
                    }

                    val sampleTime = mediaExtractor.sampleTime
                    mediaCodec.queueInputBuffer(inputIdx, 0, sampleSize, sampleTime, 0)
                    mediaExtractor.advance()
                }
            }
        }
    }

    // Render Coroutine 작업 생성
    private fun initVideoRenderJob() {
        renderJob = CoroutineScope(Dispatchers.IO).launch {
            val bufferInfo = MediaCodec.BufferInfo()
            // Video Play 진행 중 반복
            while(isPlaying) {
                val outputIdx = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)
                if(outputIdx >= 0) {
                    mediaCodec.releaseOutputBuffer(outputIdx, true)
                }

                if((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }
}