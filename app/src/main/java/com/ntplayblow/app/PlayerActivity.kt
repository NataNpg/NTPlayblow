package com.ntplayblow.app

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

// Player em tela cheia para filmes, episódios e links avulsos. Guarda onde você parou
// e registra o título em "Continuar assistindo".
class PlayerActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private val prefs get() = Store.prefs(this)
    private var current: String? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val q = Library.queue
        if (q.isEmpty()) { finish(); return }
        setContentView(R.layout.activity_player)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        view = findViewById(R.id.player)
        view.resizeMode = when (prefs.getString("resize", "fit")) {
            "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        view.findViewById<View>(R.id.ctl_full)?.visibility = View.GONE
        view.findViewById<TextView>(R.id.ctl_resize)?.let { t ->
            fun label() { t.text = when (view.resizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Preencher"
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Esticar"
                else -> "Original" } }
            label()
            t.setOnClickListener {
                val next = when (view.resizeMode) {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT }
                view.resizeMode = next
                prefs.edit().putString("resize", when (next) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "zoom"
                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "fill"
                    else -> "fit" }).apply()
                label()
            }
        }

        val idx = Library.queueIndex.coerceIn(0, q.size - 1)
        val p = ExoPlayer.Builder(this).setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000).build()
        player = p; view.player = p
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) = updateBar()
            override fun onPlayerError(error: PlaybackException) =
                Toast.makeText(this@PlayerActivity, R.string.err_play, Toast.LENGTH_LONG).show()

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                saveProgress()
                current = item?.localConfiguration?.uri?.toString()
                val i = p.currentMediaItemIndex
                view.findViewById<TextView>(R.id.ctl_title)?.text = q.getOrNull(i)?.first ?: ""
                if (Library.queueType.isNotEmpty() && Library.queueTitle.isNotEmpty()) {
                    Recent.add(prefs, Library.queueType, Library.queueTitle)
                    if (Library.queueType == "series" && current != null)
                        prefs.edit().putString("last_${Library.queueTitle}", current).apply()
                }
                updateBar()
            }
        })
        p.setMediaItems(q.map { MediaItem.fromUri(it.second) }, idx, prefs.getLong(q[idx].second, 0L))
        p.prepare(); p.playWhenReady = true
    }

    private fun updateBar() {
        val p = player ?: return
        val live = p.isCurrentMediaItemLive || p.duration == C.TIME_UNSET
        view.findViewById<View>(R.id.ctl_live)?.visibility = if (live) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.ctl_seekrow)?.visibility = if (live) View.GONE else View.VISIBLE
        val n = p.mediaItemCount
        view.findViewById<TextView>(R.id.ctl_count)?.apply {
            visibility = if (n > 1) View.VISIBLE else View.GONE
            text = "${p.currentMediaItemIndex + 1} / $n"
        }
    }

    private fun saveProgress() {
        val p = player ?: return
        val c = current ?: return
        if (p.duration <= 0) return
        val pos = if (p.currentPosition > p.duration - 5000) 0L else p.currentPosition
        prefs.edit().putLong(c, pos).apply()
    }

    override fun onStop() { saveProgress(); player?.pause(); super.onStop() }
    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }
}
