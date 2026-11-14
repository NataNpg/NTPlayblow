package com.ntplayblow.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.net.URL

class Ch(val num: Int, val title: String, val url: String, val group: String)

class MainActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private lateinit var url: EditText
    private lateinit var catList: ListView
    private lateinit var chList: ListView
    private lateinit var now: TextView
    private val prefs get() = Store.prefs(this)
    private var current: String? = null

    private var all: List<Ch> = emptyList()
    private var shown: List<Ch> = emptyList()
    private var cats: List<String> = emptyList()   // "Favoritos", "Todos", grupos...
    private var favs: MutableSet<String> = HashSet()
    private var fullscreen = false
    private var startCat: String? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        view = findViewById(R.id.player)
        url = findViewById(R.id.url)
        catList = findViewById(R.id.categories)
        chList = findViewById(R.id.channels)
        now = findViewById(R.id.now)
        view.resizeMode = when (prefs.getString("resize", "fit")) {
            "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        view.findViewById<TextView>(R.id.ctl_resize)?.let { b ->
            fun label() { b.text = when (view.resizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Preencher"
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Esticar"
                else -> "Original" } }
            label()
            b.setOnClickListener {
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
        view.findViewById<View>(R.id.ctl_full)?.setOnClickListener { setFullscreen(!fullscreen) }
        favs = HashSet(prefs.getStringSet("favs", emptySet()) ?: emptySet())

        catList.setOnItemClickListener { _, _, i, _ -> selectCategory(i) }
        chList.setOnItemClickListener { _, _, i, _ -> playChannel(i) }
        view.setFullscreenButtonClickListener { setFullscreen(it) }
        findViewById<Button>(R.id.play).setOnClickListener { playFromField() }
        findViewById<Button>(R.id.open).setOnClickListener { pick("video/*", REQ_FILE) }
        findViewById<Button>(R.id.list).setOnClickListener {
            val s = url.text.toString().trim()
            if (s.startsWith("http://") || s.startsWith("https://")) loadList(s)
            else pick("*/*", REQ_LIST)
        }
        url.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_GO) { playFromField(); true } else false
        }
        val data = intent?.data
        if (data != null) start(data.toString())
        else {
            // listas da conta (servidor) + a última lista enviada/carregada neste perfil
            val srcs = Account.lists(this) + listOfNotNull(prefs.getString("last_list", null))
            if (srcs.isNotEmpty()) loadList(srcs.joinToString("\n"), persist = false)
        }
        when (intent?.getStringExtra("pick")) {
            "video" -> pick("video/*", REQ_FILE)
            "list" -> pick("*/*", REQ_LIST)
        }
        startCat = intent?.getStringExtra("cat")
    }

    private fun pick(mime: String, req: Int) =
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = mime
        }, req)

    private fun playFromField() {
        val s = url.text.toString().trim()
        if (s.startsWith("http://") || s.startsWith("https://")) start(s)
        else Toast.makeText(this, R.string.err_url, Toast.LENGTH_SHORT).show()
    }

    private fun setFullscreen(on: Boolean) {
        fullscreen = on
        val v = if (on) View.GONE else View.VISIBLE
        for (id in intArrayOf(R.id.bar, R.id.categories, R.id.channels, R.id.now)) findViewById<View>(id).visibility = v
    }

    private fun title(s: String) {
        now.text = s
        view.findViewById<TextView>(R.id.ctl_title)?.text = s
    }

    // AO VIVO esconde a barra de progresso; contador "2 / 35" só com playlist
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

    private fun exo(): ExoPlayer = player ?: ExoPlayer.Builder(this)
        .setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000).build().also {
        player = it; view.player = it
        it.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) = updateBar()

            override fun onPlayerError(error: PlaybackException) =
                Toast.makeText(this@MainActivity, R.string.err_play, Toast.LENGTH_LONG).show()

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                current = item?.localConfiguration?.uri?.toString()
                val i = it.currentMediaItemIndex
                if (shown.isNotEmpty() && i in shown.indices) {
                    chList.setItemChecked(i, true); chList.smoothScrollToPosition(i)
                    title(shown[i].title)
                }
            }
        })
    }

    private fun start(uri: String) {
        saveProgress()
        current = uri
        title(uri)
        val p = exo()
        p.setMediaItem(MediaItem.fromUri(uri))
        p.seekTo(prefs.getLong(uri, 0L))
        p.prepare(); p.playWhenReady = true
    }

    private fun playChannel(i: Int) {
        saveProgress()
        val p = exo()
        p.setMediaItems(shown.map { MediaItem.fromUri(it.url) }, i, prefs.getLong(shown[i].url, 0L))
        current = shown[i].url
        title(shown[i].title)
        p.prepare(); p.playWhenReady = true
    }

    // ---- lista M3U: "#EXTINF:-1 group-title="X",Titulo" e a URL na linha seguinte ----
    private fun parseM3u(text: String): List<Ch> {
        val out = ArrayList<Ch>()
        val groupRe = Regex("group-title=\"([^\"]*)\"")
        var title: String? = null
        var group = "Sem categoria"
        for (raw in text.lineSequence()) {
            val l = raw.trim()
            if (l.startsWith("#EXTINF")) {
                title = l.substringAfterLast(',').trim()
                group = groupRe.find(l)?.groupValues?.get(1)?.ifEmpty { null } ?: "Sem categoria"
            } else if (l.isNotEmpty() && !l.startsWith("#")) {
                out.add(Ch(out.size + 1, title?.takeIf { it.isNotEmpty() } ?: l, l, group))
                title = null; group = "Sem categoria"
            }
        }
        return out
    }

    private fun loadList(src: String, persist: Boolean = true) {
        Toast.makeText(this, R.string.loading_list, Toast.LENGTH_SHORT).show()
        Thread {
            val parsed = try {
                // várias listas separadas por "\n" (listas da conta + lista enviada por QR)
                val merged = ArrayList<Ch>()
                for (s in src.split("\n").filter { it.isNotBlank() }) {
                    try {
                        val text = if (s.startsWith("http")) {
                            val c = URL(s).openConnection().apply { connectTimeout = 15000; readTimeout = 30000 }
                            c.getInputStream().bufferedReader().use { it.readText() }
                        } else contentResolver.openInputStream(Uri.parse(s))!!.bufferedReader().use { it.readText() }
                        merged += parseM3u(text)
                    } catch (_: Exception) { /* uma lista com erro não derruba as outras */ }
                }
                merged.mapIndexed { i, c -> Ch(i + 1, c.title, c.url, c.group) }
                    .filterNot { Store.blocked(this, it.group, it.title) }
            } catch (_: Exception) { emptyList() }
            runOnUiThread {
                if (parsed.isEmpty()) {
                    Toast.makeText(this, R.string.err_list, Toast.LENGTH_LONG).show()
                } else {
                    all = parsed
                    if (persist) prefs.edit().putString("last_list", src).apply()
                    cats = listOf("Favoritos", "Todos") + parsed.map { it.group }.distinct()
                    catList.adapter = CatAdapter()
                    chList.adapter = ChAdapter()
                    prefs.edit().putInt("last_count", parsed.size).apply()
                    val first = cats.indexOf(startCat).takeIf { it >= 0 } ?: 1
                    startCat = null
                    selectCategory(first)
                }
            }
        }.start()
    }

    private fun countOf(cat: String) = when (cat) {
        "Favoritos" -> all.count { it.url in favs }
        "Todos" -> all.size
        else -> all.count { it.group == cat }
    }

    private fun selectCategory(i: Int) {
        val c = cats[i]
        shown = when (c) {
            "Favoritos" -> all.filter { it.url in favs }
            "Todos" -> all
            else -> all.filter { it.group == c }
        }
        catList.setItemChecked(i, true)
        (catList.adapter as? BaseAdapter)?.notifyDataSetChanged()
        (chList.adapter as? BaseAdapter)?.notifyDataSetChanged()
    }

    private inner class CatAdapter : BaseAdapter() {
        override fun getCount() = cats.size
        override fun getItem(p: Int) = cats[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, v: View?, parent: ViewGroup): View {
            val row = v ?: LayoutInflater.from(this@MainActivity).inflate(R.layout.item_category, parent, false)
            row.findViewById<TextView>(R.id.cname).text = cats[p]
            row.findViewById<TextView>(R.id.ccount).text = countOf(cats[p]).toString()
            return row
        }
    }

    private inner class ChAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(p: Int) = shown[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, v: View?, parent: ViewGroup): View {
            val row = v ?: LayoutInflater.from(this@MainActivity).inflate(R.layout.item_channel, parent, false)
            val ch = shown[p]
            row.findViewById<TextView>(R.id.num).text = "#" + ch.num
            row.findViewById<TextView>(R.id.name).text = ch.title
            val heart = row.findViewById<TextView>(R.id.heart)
            heart.text = if (ch.url in favs) "♥" else "♡"
            heart.setOnClickListener {
                if (!favs.remove(ch.url)) favs.add(ch.url)
                prefs.edit().putStringSet("favs", HashSet(favs)).apply()
                val fav = cats.indexOf("Favoritos")
                val onFavs = catList.checkedItemPosition == fav
                if (onFavs) selectCategory(fav) else {
                    notifyDataSetChanged(); (catList.adapter as? BaseAdapter)?.notifyDataSetChanged()
                }
            }
            return row
        }
    }

    @Deprecated("simple result API is enough here")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val u: Uri = data?.data ?: return
        if (res != RESULT_OK) return
        try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        if (req == REQ_FILE) start(u.toString()) else if (req == REQ_LIST) loadList(u.toString())
    }

    private fun saveProgress() {
        val p = player ?: return
        val c = current ?: return
        // reinicia do zero se faltar pouco para acabar
        val pos = if (p.duration > 0 && p.currentPosition > p.duration - 5000) 0L else p.currentPosition
        prefs.edit().putLong(c, pos).apply()
    }

    override fun onStop() { saveProgress(); player?.pause(); super.onStop() }
    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }

    @Deprecated("back handling kept simple")
    override fun onBackPressed() {
        if (fullscreen) { setFullscreen(false); return }
        AlertDialog.Builder(this).setTitle(R.string.exit_title).setMessage(R.string.exit_msg)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.exit) { _, _ -> finish() }.show()
    }

    companion object { const val REQ_FILE = 1; const val REQ_LIST = 2 }
}
