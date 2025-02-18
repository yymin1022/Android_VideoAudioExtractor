package com.yong.audioextractor.controller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import com.bluelinelabs.conductor.Controller
import com.google.android.material.button.MaterialButton
import com.yong.audioextractor.R

class PlayController: Controller() {
    private lateinit var btnExtract: MaterialButton
    private lateinit var btnPause: MaterialButton
    private lateinit var btnPlay: MaterialButton
    private lateinit var btnStop: MaterialButton
    private lateinit var progressPlay: ProgressBar
    private lateinit var textureView: TextureView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.controller_play, container, false)
        btnExtract = view.findViewById(R.id.play_btn_extract_audio)
        btnPause = view.findViewById(R.id.play_btn_pause)
        btnPlay = view.findViewById(R.id.play_btn_play)
        btnStop = view.findViewById(R.id.play_btn_stop)
        progressPlay = view.findViewById(R.id.play_progress)
        textureView = view.findViewById(R.id.play_texture_view)

        btnExtract.setOnClickListener(btnListener)
        btnPause.setOnClickListener(btnListener)
        btnPlay.setOnClickListener(btnListener)
        btnStop.setOnClickListener(btnListener)

        return view
    }

    private val btnListener = View.OnClickListener { view ->
        when(view) {
            btnExtract -> {
                // TODO: Go to ExtractController
            }

            btnPause -> {
                // TODO: Pause Video Play
            }

            btnPlay -> {
                // TODO: Start Video Play
            }

            btnStop -> {
                // TODO: Stop Video Play
            }
        }
    }
}