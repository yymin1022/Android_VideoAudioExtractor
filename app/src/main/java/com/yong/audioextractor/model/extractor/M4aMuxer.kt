package com.yong.audioextractor.model.extractor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.nio.ByteBuffer

class M4aMuxer(
    private val format: MediaFormat
) {
    private lateinit var mediaMuxer: MediaMuxer
    private var trackNum = -1

    fun writeFile(context: Context, bufferList: List<ByteBuffer>) {
        initMuxer(context)

        bufferList.forEach { buffer ->
            buffer.position(0)

            val bufferInfo = MediaCodec.BufferInfo().apply {
                size = buffer.remaining()
                presentationTimeUs = 0
                offset = 0
                flags = 0
            }

            mediaMuxer.writeSampleData(trackNum, buffer, bufferInfo)
        }
    }

    fun close() {
        mediaMuxer.stop()
        mediaMuxer.release()
    }

    private fun initMuxer(context: Context) {
        val fileOutput = context.getFileStreamPath("result.m4a")
        mediaMuxer = MediaMuxer(fileOutput.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        format.setInteger(MediaFormat.KEY_BIT_RATE, 128000)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        trackNum = mediaMuxer.addTrack(format)

        mediaMuxer.start()
    }
}