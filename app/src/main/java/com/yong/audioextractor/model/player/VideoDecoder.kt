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
 * - 파일의 Video Track을 찾고, Decode해 재생하기 위한 Model
 */
class VideoDecoder(
    // 재생 및 일시정지 상태를 확인하기 위한 Field 함수
    private val isPaused: () -> Boolean,
    private val isPlaying: () -> Boolean,
    // Video 재생 종료를 처리하기 위한 Callback 함수
    private val onVideoEnd: () -> Unit
) {
    // Video Decoding을 위한 Media Codec
    private lateinit var mediaCodec: MediaCodec
    // Video 정보 확인을 위한 Media Extractor
    private lateinit var mediaExtractor: MediaExtractor

    // Decoding 작업을 수행하기 위한 Coroutine Job
    private var decodeJob: Job? = null
    // Decoding 시작 시간 기록
    private var decodeStartTime = 0L
    // Video의 전체 재생 시간 기록
    private var videoTotalTime = 0L

    // 초기화
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
        // 현재 Track의 Format 정보 확인
        val videoFormat = mediaExtractor.getTrackFormat(trackNum)
        // Video의 전체 길이 확인
        videoTotalTime = videoFormat.getLong(MediaFormat.KEY_DURATION)

        // video/avc MIME Type 지정
        mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        // 탐색한 Track을 지정과 렌더링할 Surface를 지정
        // Crypto와 Flag는 지정하지 않음
        mediaCodec.configure(videoFormat, surface, null, 0)
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

    // Decoding 시작
    fun startDecoding() {
        // Decoder 시작
        mediaCodec.start()

        // Decoder Coroutine 작업 생성
        decodeJob = CoroutineScope(Dispatchers.Default).launch {
            // Decode를 시작한 System Time
            decodeStartTime = System.nanoTime()
            // 직전 Frame의 시간
            var lastFrameTime = 0L

            // Codec에 지정된 Buffer 정보 확인
            val bufferInfo = MediaCodec.BufferInfo()
            
            // Video Play 진행 중 반복
            while(isPlaying()) {
                // Pause 상태인 경우 Decode 하지 않고 대기
                if(isPaused()) {
                    delay(100)
                    continue
                }

                // Input Buffer 요청
                if(!getInputBuffer()) {
                    // 더이상 읽을 Sample 데이터가 없는 경우
                    // 즉, 영상을 끝까지 재생한 경우에는 종료
                    break
                }

                // 현재 Frame이 보여져야 할 시간에 올바르게 보여지는지 재생 경과 시간 확인
                syncTimestamp(lastFrameTime)

                // 렌더링할 Output Buffer 읽기
                if(!processOutputBuffer(bufferInfo)) {
                    // 더이상 읽을 Sample 데이터가 없는 경우
                    // 즉, 영상을 끝까지 재생한 경우에는 종료
                    break
                }

                // 보여진 Frame의 시간 업데이트
                lastFrameTime = getVideoSampleTime()
            }
        }
    }

    // Decoding 종료
    fun stopDecoding() {
        // Decode Coroutine 종료
        decodeJob?.cancel()

        // Media Codec 및 Extractor 해제/종료
        mediaCodec.stop()
        mediaCodec.release()
        mediaExtractor.release()
    }

    // 현재 재생한 Frame의 Sample Time 반환
    fun getVideoSampleTime(): Long { return mediaExtractor.sampleTime }
    // 현재 재생중인 Video의 진행률 반환
    fun getVideoPlayRate(): Float { return getVideoSampleTime().toFloat() / videoTotalTime * 100 }

    // Input Buffer 요청
    private fun getInputBuffer(): Boolean {
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
                return false
            }

            // 현재 읽은 데이터의 타임스탬프 확인
            val sampleTime = mediaExtractor.sampleTime
            // Decoder에 읽어들인 데이터 추가
            mediaCodec.queueInputBuffer(inputIdx, 0, sampleSize, sampleTime, 0)
            // 다음 Frame으로 이동
            mediaExtractor.advance()
        }

        return true
    }

    // 렌더링할 Output Buffer 읽기
    private fun processOutputBuffer(bufferInfo: MediaCodec.BufferInfo): Boolean {
        val outputIdx = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)
        // 여전히 재생중이고 데이터가 유효하다면 Buffer Release(렌더링) 호출
        if(isPlaying() && outputIdx >= 0) {
            mediaCodec.releaseOutputBuffer(outputIdx, true)
        }

        // Video가 끝난 Flag인 경우 종료
        if((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            onVideoEnd()
            return false
        }

        return true
    }

    // 현재 Frame이 보여져야 할 시간에 올바르게 보여지는지 재생 경과 시간 확인
    private suspend fun syncTimestamp(lastFrameTime: Long) {
        if(lastFrameTime > 0L) {
            // Decode 시작 시간으로부터 현재까지 경과한 시간 계산
            // 정확도를 위해 Nano Time으로 계산 후 Millis로 변환
            val elapsedTime = (System.nanoTime() - decodeStartTime) / 1000000
            // 현재 Frame이 보여져야 할 시간과 경과 시간의 차 계산
            val delayOffset = getVideoSampleTime() / 1000 - elapsedTime
            // 차이나는 시간만큼 Delay
            if(delayOffset > 0) {
                delay(delayOffset)
            }
        }
    }
}
