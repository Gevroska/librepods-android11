package me.kavishdevar.librepods.presentation.overlays

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.TextureView

/** Composites the animation inside the popup, including its scale, alpha and clipping. */
class IslandVideoView(context: Context, attrs: AttributeSet? = null) : TextureView(context, attrs),
    TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var resource: Uri? = null

    init {
        surfaceTextureListener = this
    }

    fun play(uri: Uri) {
        resource = uri
        if (isAvailable) surfaceTexture?.let(::prepare)
    }

    private fun prepare(texture: SurfaceTexture) {
        val uri = resource ?: return
        releasePlayer()
        val output = Surface(texture)
        surface = output
        val media = MediaPlayer()
        player = media
        try {
            media.setSurface(output)
            media.setDataSource(context, uri)
            media.isLooping = true
            media.setVolume(0f, 0f)
            media.setOnPreparedListener {
                if (player === it) {
                    fitVideo(it.videoWidth, it.videoHeight)
                    it.start()
                }
            }
            media.setOnErrorListener { _, what, extra ->
                Log.w("IslandVideoView", "Popup video unavailable ($what, $extra)")
                releasePlayer()
                true
            }
            media.prepareAsync()
        } catch (error: Exception) {
            Log.w("IslandVideoView", "Cannot prepare popup video", error)
            releasePlayer()
        }
    }

    private fun fitVideo(videoWidth: Int, videoHeight: Int) {
        if (width == 0 || height == 0 || videoWidth == 0 || videoHeight == 0) return
        val scale = minOf(width.toFloat() / videoWidth, height.toFloat() / videoHeight)
        setTransform(Matrix().apply {
            setScale(videoWidth * scale / width, videoHeight * scale / height, width / 2f, height / 2f)
        })
    }

    fun stopPlayback() {
        resource = null
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.apply {
            setOnPreparedListener(null)
            setOnErrorListener(null)
            release()
        }
        player = null
        surface?.release()
        surface = null
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) = prepare(texture)
    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        player?.let { fitVideo(it.videoWidth, it.videoHeight) }
    }
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        releasePlayer()
        return true
    }
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    override fun onDetachedFromWindow() {
        releasePlayer()
        super.onDetachedFromWindow()
    }
}
