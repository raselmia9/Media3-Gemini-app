package com.snttrasel.media3player

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.upstream.DefaultHttpDataSource
import androidx.media3.ui.PlayerView

class MainActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var etStreamUrl: EditText
    private lateinit var btnPlay: Button
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)
        etStreamUrl = findViewById(R.id.etStreamUrl)
        btnPlay = findViewById(R.id.btnPlay)

        btnPlay.setOnClickListener {
            val rawInput = etStreamUrl.text.toString().trim()
            if (rawInput.isNotEmpty()) {
                playMedia(rawInput)
            }
        }
    }

    private fun playMedia(input: String) {
        releasePlayer()

        // পাইপ (|) সাইন দিয়ে হেডার বা DRM আলাদা করা
        val parts = input.split("|")
        val videoUrl = parts[0].trim()
        
        val dataSourceFactory = DefaultHttpDataSource.Factory()
        
        // যদি হেডারে Referer বা Cookie থাকে তা সেট করা
        for (i in 1 until parts.size) {
            val param = parts[i].trim()
            if (param.startsWith("Referer=", true)) {
                val ref = param.substringAfter("=")
                dataSourceFactory.setDefaultRequestProperties(mapOf("Referer" to ref))
            } else if (param.startsWith("Cookie=", true)) {
                val cookie = param.substringAfter("=")
                dataSourceFactory.setDefaultRequestProperties(mapOf("Cookie" to cookie))
            }
        }

        // FFmpeg রেন্ডারার ব্যবহার নিশ্চিত করা যাতে অডিও ডাউনমিক্স হয়ে মিউট না হয়
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        player = ExoPlayer.Builder(this, renderersFactory).build().also { exoPlayer ->
            playerView.player = exoPlayer

            val mediaItemBuilder = MediaItem.Builder().setUri(videoUrl)

            // DRM (Clearkey) চেক ও কনফিগারেশন
            if (input.contains("drmScheme=clearkey", true)) {
                val licenseKey = extractParam(input, "drmLicense")
                if (licenseKey != null) {
                    mediaItemBuilder.setDrmUuid(androidx.media3.common.C.CLEARKEY_UUID)
                    mediaItemBuilder.setDrmLicenseUri(licenseKey)
                }
            }

            // স্ট্রিম টাইপ অনুযায়ী মিডিয়া সোর্স তৈরি
            val mediaItem = mediaItemBuilder.build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    private fun extractParam(url: String, key: String): String? {
        val regex = "$key=([^&|]+)".toRegex(RegexOption.IGNORE_CASE)
        return regex.find(url)?.groupValues?.get(1)
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }
}
