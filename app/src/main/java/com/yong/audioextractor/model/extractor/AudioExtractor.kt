package com.yong.audioextractor.model.extractor

import android.content.res.AssetFileDescriptor
import android.media.MediaExtractor
import android.media.MediaFormat

class AudioExtractor {
    private lateinit var mediaExtractor: MediaExtractor

    fun extractAudio(videoFd: AssetFileDescriptor) {
        initExtractor(videoFd)

        val trackNum = getAudioTrack() ?: throw Exception("No Audio Track")
    }

    // MediaExtractor 초기화
    private fun initExtractor(videoFd: AssetFileDescriptor) {
        mediaExtractor = MediaExtractor()
        // Video FD에서 파일을 읽어 Source로 지정
        mediaExtractor.setDataSource(videoFd.fileDescriptor, videoFd.startOffset, videoFd.length)
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