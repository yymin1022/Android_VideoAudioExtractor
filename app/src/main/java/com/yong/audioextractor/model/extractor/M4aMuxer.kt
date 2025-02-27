package com.yong.audioextractor.model.extractor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer

/**
 * M4aMuxer
 * - PCM 데이터를 AAC로 인코딩한 후, M4A 파일로 저장하기 위한 Model
 */
class M4aMuxer(
    // PCM 데이터의 MediaFormat
    private val inputFormat: MediaFormat
) {
    // AAC 인코딩을 위한 MediaCodec
    private lateinit var mediaCodec: MediaCodec
    // 인코딩된 데이터를 기록할 MediaMuxer
    private lateinit var mediaMuxer: MediaMuxer
    // muxer에 추가한 오디오 트랙의 인덱스
    private var trackIndex: Int = -1

    private var writeJob: Job? = null

    private var bufferInputIndex = 0

    // 전달받은 PCM 데이터를 AAC로 인코딩한 후, result.m4a 파일로 저장
    fun writeFile(context: Context, pcmData: List<Pair<Long, ByteBuffer>>): Job? {
        // AAC 인코더 초기화
        initEncoder()

        // Muxer 초기화
        val file = context.getFileStreamPath("result.m4a")
        initMuxer(file)

        // Codec에 지정된 Buffer 정보 확인
        val bufferInfo = MediaCodec.BufferInfo()

        var isInputEOS = false
        var isOutputEOS = false

        writeJob?.cancel()
        writeJob = CoroutineScope(Dispatchers.Default).launch {
            // PCM 데이터를 인코더를 통해 AAC 파일로 변환
            while(!isOutputEOS) {
                // Input Buffer 요청
                if(!isInputEOS && !getInputBuffer(pcmData)) {
                    isInputEOS = true
                }

                // Output Buffer 처리
                if(!processOutputBuffer(bufferInfo)) {
                    // 더이상 Sample 데이터가 없는 경우 종료
                    isOutputEOS = true
                    break
                }
            }
        }

        return writeJob
    }

    // Encoder 초기화
    private fun initEncoder() {
        // AAC MIME 타입 지정
        val mimeType = MediaFormat.MIMETYPE_AUDIO_AAC
        
        // 입력 PCM 데이터의 속성 확인
        val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        // AAC 인코더 생성
        mediaCodec = MediaCodec.createEncoderByType(mimeType)
        // AAC 인코딩 MediaFormat 설정
        val format = MediaFormat.createAudioFormat(mimeType, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        // Encoder 시작
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mediaCodec.start()
    }

    // Muxer 초기화
    private fun initMuxer(file: File) {
        // 새 파일 생성
        mediaMuxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        // Muxer에 Track 추가
        trackIndex = mediaMuxer.addTrack(mediaCodec.outputFormat)
        // Muxer 시작
        mediaMuxer.start()
    }

    // Input Buffer 요청
    private fun getInputBuffer(pcmData: List<Pair<Long, ByteBuffer>>): Boolean {
        val inputIdx = mediaCodec.dequeueInputBuffer(0)

        // 더이상 읽을 PCM 데이터가 없는 경우
        if(bufferInputIndex >= pcmData.size) {
            // End Of Stream Flag 전달 후 종료
            mediaCodec.queueInputBuffer(inputIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            return false
        }

        // 현재 Index의 PCM SampleTime 및 Buffer 데이터
        val (sampleTime, buffer) = pcmData[bufferInputIndex]
        // Input Buffer가 유효한 경우
        if(inputIdx >= 0) {
            // Buffer의 앞에서부터 데이터 읽기
            buffer.position(0)
            val sampleSize = buffer.remaining()
            
            val inputBuffer = mediaCodec.getInputBuffer(inputIdx)!!
            inputBuffer.clear()
            inputBuffer.put(buffer)

            // Encoder에 읽어들인 데이터 추가
            mediaCodec.queueInputBuffer(inputIdx, 0, sampleSize, sampleTime, 0)
            bufferInputIndex++
        }

        return true
    }

    // Output Buffer 처리
    private fun processOutputBuffer(bufferInfo: MediaCodec.BufferInfo): Boolean {
        val outputIdx = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)

        // Output Buffer가 유효한 경우
        if(outputIdx >= 0) {
            val outputBuffer = mediaCodec.getOutputBuffer(outputIdx)!!

            // Output Buffer가 유효한 경우
            if(bufferInfo.size > 0) {
                outputBuffer.position(bufferInfo.offset)
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                mediaMuxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
            }

            // 처리한 Buffer 비우기
            mediaCodec.releaseOutputBuffer(outputIdx, false)
        }

        // End Of Stream Flag인 경우 종료
        if(bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
            return false
        }

        return true
    }

    // 모든 리소스 해제
    fun close() {
        // AAC 인코더 정지 및 해제
        mediaCodec.stop()
        mediaCodec.release()
        // Muxer 정지 및 해제
        mediaMuxer.stop()
        mediaMuxer.release()
    }
}
