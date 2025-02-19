package com.yong.audioextractor.controller

import android.content.res.AssetFileDescriptor
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.google.android.material.button.MaterialButton
import com.yong.audioextractor.R
import com.yong.audioextractor.model.VideoDecoder

/**
 * PlayController
 * - Video 재생 기능을 구현한 Controller
 * - 각 Button을 통해 Video Play Pause Stop 상태 제어
 * - ProgressBar를 통해 Video Play 진행도 표시
 */
class PlayController: Controller() {
    /** UI Elements */
    // Buttons
    private lateinit var btnExtract: MaterialButton
    private lateinit var btnPause: MaterialButton
    private lateinit var btnPlay: MaterialButton
    private lateinit var btnStop: MaterialButton
    // Video 재생 진행도 ProgressBar
    private lateinit var progressPlay: ProgressBar
    // Video가 재생되는 TextureView
    private lateinit var textureView: TextureView

    // Video Decoder Model 선언
    private lateinit var videoDecoder: VideoDecoder
    // Video Asset File Descriptor
    private lateinit var videoFd: AssetFileDescriptor

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        // View 초기화
        val view = inflater.inflate(R.layout.controller_play, container, false)
        btnExtract = view.findViewById(R.id.play_btn_extract_audio)
        btnPause = view.findViewById(R.id.play_btn_pause)
        btnPlay = view.findViewById(R.id.play_btn_play)
        btnStop = view.findViewById(R.id.play_btn_stop)
        progressPlay = view.findViewById(R.id.play_progress)
        textureView = view.findViewById(R.id.play_texture_view)

        // Button OnClickListener 지정
        btnExtract.setOnClickListener(btnListener)
        btnPause.setOnClickListener(btnListener)
        btnPlay.setOnClickListener(btnListener)
        btnStop.setOnClickListener(btnListener)

        // TextureView Surface Listener 지정
        textureView.surfaceTextureListener = textureViewListener

        return view
    }

    // TextureView Surface Listener
    private val textureViewListener = object: TextureView.SurfaceTextureListener {
        // TextureView Init 완료
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            // Raw Resource에서 Video 파일을 열고 FD값 지정
            videoFd = resources?.openRawResourceFd(R.raw.sample_video) ?: throw Exception("No video available")
            videoDecoder = VideoDecoder(videoFd)
        }


        // TextureView 제거
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            // Model 내 재생 중지 및 리소스 해제
            videoDecoder.stopVideoPlay()
            // Video File Close
            videoFd.close()
            return true
        }

        // 구현하지 않는 메소드
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    // Button OnClickListener
    private val btnListener = View.OnClickListener { view ->
        when(view) {
            // Extract Audio
            btnExtract -> {
                // Router에 ExtractController Push
                router.pushController(RouterTransaction.with(ExtractController()))
            }

            // Video Play Pause
            btnPause -> {
                if(videoDecoder.isPlaying) {
                    videoDecoder.pauseVideoPlay()
                }
            }

            // Video Play Start
            btnPlay -> {
                // 재생 중 상태에 따라 새로운 재생 Start 또는 Resume 호출
                if(!videoDecoder.isPlaying) {
                    videoDecoder.startVideoPlay(Surface(textureView.surfaceTexture))
                } else if(videoDecoder.isPaused) {
                    videoDecoder.resumeVideoPlay()
                }
            }

            // Video Play Stop
            btnStop -> {
                if(videoDecoder.isPlaying) {
                    videoDecoder.stopVideoPlay()
                }
            }
        }
    }
}