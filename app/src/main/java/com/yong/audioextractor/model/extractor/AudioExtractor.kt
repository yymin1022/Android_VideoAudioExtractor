package com.yong.audioextractor.model.extractor

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaExtractor
import android.media.MediaFormat

/**
 * AudioExtractor
 * - 파일의 Audio를 Decode하고 AAC로 Encode해 저장하기 위한 Model
 */
class AudioExtractor {
    // Video 파일 분석을 위한 Media Extractor
    private lateinit var mediaExtractor: MediaExtractor

    // 결과 파일 Muxing을 위한 Muxer
    private lateinit var m4aMuxer: M4aMuxer
    // Audio Track을 PCM 데이터로 Decodiong 하기 위한 Decoder
    private lateinit var pcmDecoder: PcmDecoder

    // Audio를 Extract하기 위한 메소드
    fun extractAudio(context: Context, videoFd: AssetFileDescriptor) {
        // MediaExtractor 초기화
        initExtractor(videoFd)

        // MediaExtractor를 통해 Source 내에서 Audio Track 탐색
        val trackNum = getAudioTrack() ?: throw Exception("No Audio Track")

        // Audio Track을 PCM 데이터로 Decode 하기 위한 Decoder 초기화 및 호출
        initPcmDecoder()
        val pcmData = pcmDecoder.decodePcm()

        // AAC로 Encode하고 파일로 생성하기 위한 Muxer 초기화 및 호출
        initM4aMuxer(trackNum)
        m4aMuxer.writeFile(context, pcmData)
        
        // Muxer 및 Extractor 해제
        m4aMuxer.close()
        mediaExtractor.release()
    }

    // MediaExtractor 초기화
    private fun initExtractor(videoFd: AssetFileDescriptor) {
        mediaExtractor = MediaExtractor()
        // Video FD에서 파일을 읽어 Source로 지정
        mediaExtractor.setDataSource(videoFd.fileDescriptor, videoFd.startOffset, videoFd.length)
    }

    private fun initM4aMuxer(trackNum: Int) {
        m4aMuxer = M4aMuxer(mediaExtractor.getTrackFormat(trackNum))
    }

    private fun initPcmDecoder() {
        pcmDecoder = PcmDecoder(mediaExtractor)
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
}