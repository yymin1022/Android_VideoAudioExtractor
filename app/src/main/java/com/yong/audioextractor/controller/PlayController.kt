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
import com.yong.audioextractor.model.player.VideoPlayer

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

    // Video Asset File Descriptor
    private var videoFd: AssetFileDescriptor? = null
    // Video Decoder Model
    private val videoPlayer = VideoPlayer()

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

    // Controller가 완전히 종료될 때 리소스 해제
    override fun onDestroy() {
        super.onDestroy()
        // Model 내 재생 중지 및 리소스 해제
        videoPlayer.stopVideoPlay()
        // Video File Close
        videoFd?.close()
    }

    // TextureView Surface Listener
    private val textureViewListener = object: TextureView.SurfaceTextureListener {
        // Video의 진행 상황 업데이트
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            if(videoPlayer.isVideoPlaying()) {
                progressPlay.progress = videoPlayer.getVideoPlayRate().toInt()
            }
        }

        // 구현하지 않는 메소드
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { return true }
    }

    // Button OnClickListener
    private val btnListener = View.OnClickListener { view ->
        when(view) {
            // Extract Audio
            btnExtract -> {
                // Video가 재생중인 경우 종료
                if(videoPlayer.isVideoPlaying()) {
                    // Model 내 재생 중지 및 리소스 해제
                    videoPlayer.stopVideoPlay()
                    // Video File Close
                    videoFd?.close()
                }

                // Router에 ExtractController Push
                router.pushController(RouterTransaction.with(ExtractController()))
            }

            // Video Play Pause
            btnPause -> {
                if(videoPlayer.isVideoPlaying()) {
                    videoPlayer.pauseVideoPlay()
                }
            }

            // Video Play Start
            btnPlay -> {
                // 이미 파일이 열려있다면 File Close
                // Raw Resource에서 Video 파일을 열고 FD값 지정
                videoFd = resources?.openRawResourceFd(R.raw.sample_video) ?: throw Exception("No video available")

                videoFd?.use { fd ->
                    // 재생 중 상태에 따라 새로운 재생 Start 또는 Resume 호출
                    if(!videoPlayer.isVideoPlaying()) {
                        videoPlayer.startVideoPlay(fd, Surface(textureView.surfaceTexture))
                    } else if(videoPlayer.isVideoPaused()) {
                        videoPlayer.resumeVideoPlay()
                    }
                }
            }

            // Video Play Stop
            btnStop -> {
                if(videoPlayer.isVideoPlaying()) {
                    // Model 내 재생 중지 및 리소스 해제
                    videoPlayer.stopVideoPlay()
                    // Video File Close
                    videoFd?.close()
                }
            }
        }
    }
}