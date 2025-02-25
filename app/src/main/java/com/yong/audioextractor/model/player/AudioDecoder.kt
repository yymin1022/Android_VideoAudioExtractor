package com.yong.audioextractor.model.player

import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * AudioDecoder
 * - 파일의 Audio Track을 찾고, Decode해 재생하기 위한 Model
 */
class AudioDecoder(
    // 재생 및 일시정지 상태를 확인하기 위한 Field 함수
    private val isPaused: () -> Boolean,
    private val isPlaying: () -> Boolean,
    // Video의 현재 재생 시간을 확인하기 위한 함수
    private val getVideoSampleTime: () -> Long
) {
    // Audio Decoding을 위한 Media Codec
    private lateinit var mediaCodec: MediaCodec
    // Audio 정보 확인을 위한 Media Extractor
    private lateinit var mediaExtractor: MediaExtractor
    // Audio 재생 정보를 담을 Audio Track
    private var audioTrack: AudioTrack? = null

    // Decoding 작업을 수행하기 위한 Coroutine Job
    private var decodeJob: Job? = null

    // 초기화
    fun init(audioFd: AssetFileDescriptor) {
        // MediaExtractor 초기화
        // Video FD에서 파일을 읽어 Source로 지정
        initExtractor(audioFd)

        // MediaExtractor를 통해 Source 내에서 Video Track 탐색
        // Track이 Null인 경우 없는 것이므로 Exception 발생
        val audioTrack = getAudioTrack() ?: throw Exception("No Video Track")
        // 탐색한 Track으로 MediaCodec Decoder 초기화
        initDecoder(audioTrack)
        // 탐색한 Track으로 Audio Track 초기화
        initAudioTrack(audioTrack)
    }

    // AudioTrack 초기화
    private fun initAudioTrack(trackNum: Int) {
        // Track의 Format 확인
        val trackFormat = mediaExtractor.getTrackFormat(trackNum)

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

    // 탐색한 Track으로 MediaCodec Decoder 초기화
    private fun initDecoder(trackNum: Int) {
        // Track의 Format 확인
        val trackFormat = mediaExtractor.getTrackFormat(trackNum)
        // MIME Type 지정
        mediaCodec = MediaCodec.createDecoderByType(trackFormat.getString(MediaFormat.KEY_MIME) ?: throw Exception("Unknown Audio Track Format"))

        // 탐색한 Track을 지정
        // Surface와 Crypto, Flag는 지정하지 않음
        mediaCodec.configure(trackFormat, null, null, 0)
        mediaCodec.start()
    }

    // MediaExtractor 초기화
    private fun initExtractor(audioFd: AssetFileDescriptor) {
        mediaExtractor = MediaExtractor()
        // Video FD에서 파일을 읽어 Source로 지정
        mediaExtractor.setDataSource(audioFd.fileDescriptor, audioFd.startOffset, audioFd.length)
    }

    // MediaExtractor를 통해 Source 내에서 Audio Track 탐색
    private fun getAudioTrack(): Int? {
        // MediaExtractor 내 모든 Track을 탐색
        for(i in 0 until mediaExtractor.trackCount) {
            val trackFormat = mediaExtractor.getTrackFormat(i)
            // Type을 알 수 없는 Track인 경우 건너뛰기
            val trackType = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue

            // Audio MIME Type을 갖는 Track 지정
            if(trackType.startsWith("audio/")) {
                mediaExtractor.selectTrack(i)
                return i
            }
        }

        // 정해진 Track을 찾지 못한 경우 Null 반환
        return null
    }

    // Decoding 시작
    fun startDecoding() {
        // Track 시작
        audioTrack?.play()

        // Decoder Coroutine 작업 생성
        decodeJob = CoroutineScope(Dispatchers.Default).launch {
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

                // Video와의 Time Sync 확인
                syncTimestamp()

                // 재생할 Output Buffer 처리
                processOutputBuffer(bufferInfo)
            }
        }
    }

    // Decoding 종료
    fun stopDecoding() {
        // Decode Coroutine 종료
        decodeJob?.cancel()

        // Media Codec 및 Extractor, Audio Track 해제/종료
        audioTrack?.stop()
        audioTrack?.release()
        mediaCodec.stop()
        mediaCodec.release()
        mediaExtractor.release()
    }

    // Input Buffer 요청
    private fun getInputBuffer(): Boolean {
        val inputIdx = mediaCodec.dequeueInputBuffer(0)
        // Buffer가 읽을 수 있는 상태인 경우
        if(inputIdx >= 0) {
            val inputBuffer = mediaCodec.getInputBuffer(inputIdx)
            // Buffer에서 Sample 데이터 읽기
            val sampleSize = mediaExtractor.readSampleData(inputBuffer!!, 0)

            // 더이상 읽을 Sample 데이터가 없는 경우
            // 즉, 영상을 끝까지 재생한 경우
            if(sampleSize < 0) {
                // End Of Stream Flag 전달 후 종료
                mediaCodec.queueInputBuffer(inputIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                return false
            }


            // 현재 읽은 데이터의 타임스탬프 확인
            val sampleTime = mediaExtractor.sampleTime
            // Decoder에 읽어들인 데이터 추가
            mediaCodec.queueInputBuffer(inputIdx, 0, sampleSize, sampleTime, 0)
            mediaExtractor.advance()
        }

        return true
    }

    // 재생할 Output Buffer 읽기
    private fun processOutputBuffer(bufferInfo: MediaCodec.BufferInfo) {
        val outputIdx = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)
        if(outputIdx >= 0) {
            // 데이터가 유효한 경우 재생
            val outputBuffer = mediaCodec.getOutputBuffer(outputIdx)
            val chunk = ByteArray(bufferInfo.size)
            outputBuffer?.get(chunk)
            outputBuffer?.clear()

            // Track에 재생할 Sample 추가
            audioTrack?.write(chunk, 0, chunk.size)

            // 여전히 재생중이라면 Buffer Release 호출
            if(isPlaying()) {
                mediaCodec.releaseOutputBuffer(outputIdx, false)
            }
        }
    }

    // Video와의 Time Sync 확인
    private suspend fun syncTimestamp() {
        while(isPlaying()) {
            // Audio 및 Video 각각의 Sample Time 확인
            val audioSampleTime = mediaExtractor.sampleTime
            val videoSampleTime = getVideoSampleTime()

            // Sample Time이 올바르지 않은 경우 종료
            if(audioSampleTime <= 0 || videoSampleTime <= 0) break

            // Audio가 10ms 이상 앞서는 경우 Delay
            if(audioSampleTime > videoSampleTime + 10000) delay(5)
            // Video가 10ms 이상 앞서는 경우 Audio Advance 호출
            else if(audioSampleTime < videoSampleTime - 10000) mediaExtractor.advance()
            // Sync가 10ms 이하로 맞는 경우 종료
            else break
        }
    }
}