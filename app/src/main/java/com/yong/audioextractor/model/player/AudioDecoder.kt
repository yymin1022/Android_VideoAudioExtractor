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

class AudioDecoder(
    private val isPaused: () -> Boolean,
    private val isPlaying: () -> Boolean
) {
    // Audio Decoding을 위한 Media Codec
    private lateinit var mediaCodec: MediaCodec
    // Audio 정보 확인을 위한 Media Extractor
    private lateinit var mediaExtractor: MediaExtractor
    // Audio 재생 정보를 담을 Audio Track
    private var audioTrack: AudioTrack? = null

    // Decoding 작업을 수행하기 위한 Coroutine Job
    private var decodeJob: Job? = null

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

    private fun initAudioTrack(trackNum: Int) {
        // Track의 Format 확인
        val trackFormat = mediaExtractor.getTrackFormat(trackNum)

        // AudioTrack 초기화
        // Format을 16bit PCM으로 지정
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        // SampleRate는 Track Format 값에 따라 지정
        val sampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        // Stereo 여부는 Track Fromat 값에 따라 지정
        val channelConfig = if(trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1) AudioFormat.CHANNEL_OUT_MONO
                            else AudioFormat.CHANNEL_OUT_STEREO

        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
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

        // video/avc MIME Type 지정
        mediaCodec = MediaCodec.createDecoderByType(trackFormat.getString(MediaFormat.KEY_MIME) ?: throw Exception("Unknown Audio Track Format"))
        // 탐색한 Track을 지정과 렌더링할 Surface를 지정
        // Crypto와 Flag는 지정하지 않음
        mediaCodec.configure(trackFormat, null, null, 0)
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

    fun startDecoding() {
        mediaCodec.start()
        audioTrack?.play()

        decodeJob = CoroutineScope(Dispatchers.IO).launch {
            val bufferInfo = MediaCodec.BufferInfo()

            while(isPlaying()) {
                if(isPaused()) {
                    delay(100)
                    continue
                }

                val inputIdx = mediaCodec.dequeueInputBuffer(0)
                if(inputIdx >= 0) {
                    val inputBuffer = mediaCodec.getInputBuffer(inputIdx)
                    val sampleSize = mediaExtractor.readSampleData(inputBuffer!!, 0)

                    if(sampleSize < 0) {
                        mediaCodec.queueInputBuffer(inputIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        break
                    }

                    mediaCodec.queueInputBuffer(inputIdx, 0, sampleSize, mediaExtractor.sampleTime, 0)
                    mediaExtractor.advance()
                }

                val outputIdx = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)
                if(outputIdx >= 0) {
                    val outputBuffer = mediaCodec.getOutputBuffer(outputIdx)
                    val chunk = ByteArray(bufferInfo.size)
                    outputBuffer?.get(chunk)
                    outputBuffer?.clear()

                    audioTrack?.write(chunk, 0, chunk.size)
                    mediaCodec.releaseOutputBuffer(outputIdx, false)
                }
            }
        }
    }

    fun stopDecoding() {
        decodeJob?.cancel()

        mediaCodec.stop()
        mediaCodec.release()
        mediaExtractor.release()
        audioTrack?.stop()
        audioTrack?.release()
    }
}