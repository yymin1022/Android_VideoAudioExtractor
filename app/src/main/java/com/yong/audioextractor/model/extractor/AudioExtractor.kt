package com.yong.audioextractor.model.extractor

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaExtractor
import android.media.MediaFormat
import com.yong.audioextractor.model.AudioDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * AudioExtractor
 * - 파일의 Audio를 Decode하고 AAC로 Encode해 저장하기 위한 Model
 */
class AudioExtractor {
    // Audio의 Decoding을 위한 Decoder 및 Extractor
    private lateinit var audioDecoder: AudioDecoder
    private lateinit var audioExtractor: MediaExtractor
    // 결과 파일 Muxing을 위한 Muxer
    private lateinit var m4aMuxer: M4aMuxer

    // 각 Audio Sample의 Timestamp와 PCM Buffer를 담기 위한 자료구조
    private var pcmBuffer: MutableList<Pair<Long, ByteBuffer>> = mutableListOf()

    // Audio를 Extract하기 위한 메소드
    suspend fun extractAudio(context: Context, videoFd: AssetFileDescriptor) {
        // MediaExtractor 초기화
        initExtractor(videoFd)

        // MediaExtractor를 통해 Source 내에서 Audio Track 탐색
        val trackNum = getAudioTrackNum() ?: throw Exception("No Audio Track")

        // Decoder (Audio -> PCM) 초기화
        initDecoder(trackNum)
        // Muxer (PCM -> File) 초기화
        initM4aMuxer(trackNum)

        withContext(Dispatchers.Default) {
            // Audio Decoder 호출
            val decodeJob = audioDecoder.startDecoding()
            // Decoding 작업이 끝날 때 까지 대기
            decodeJob?.join()

            // AAC로 Encode하고 파일로 생성하기 위한 Muxer 호출
            val writeJob = m4aMuxer.writeFile(context, pcmBuffer)
            // Write 작업이 끝날 때 까지 대기
            writeJob?.join()

            // Muxer 및 Extractor 해제
            m4aMuxer.close()
            audioExtractor.release()
        }
    }

    private fun initDecoder(trackNum: Int) {
        // Audio Decoder 초기화
        audioDecoder = AudioDecoder(trackNum, audioExtractor, ::getAudioPcmBuffer)
    }

    // MediaExtractor 초기화
    private fun initExtractor(videoFd: AssetFileDescriptor) {
        audioExtractor = MediaExtractor()
        // Video FD에서 파일을 읽어 Source로 지정
        audioExtractor.setDataSource(videoFd.fileDescriptor, videoFd.startOffset, videoFd.length)
    }

    private fun initM4aMuxer(trackNum: Int) {
        m4aMuxer = M4aMuxer(audioExtractor.getTrackFormat(trackNum))
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

    // Decoding된 Audio Buffer 재생
    private fun getAudioPcmBuffer(sampleTime: Long, buffer: ByteArray) {
        // Result Buffer에 데이터 추가
        pcmBuffer.add(Pair(sampleTime, ByteBuffer.wrap(buffer)))
    }
}