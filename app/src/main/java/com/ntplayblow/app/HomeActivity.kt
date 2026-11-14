package com.ntplayblow.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView

class HomeActivity : Activity() {
    private val prefs get() = Store.prefs(this)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_home)
        open(R.id.tile_live) { putExtra("type", "live") }
        open(R.id.tile_movies) { putExtra("type", "movie") }
        open(R.id.tile_series) { putExtra("type", "series") }
        open(R.id.tile_favs) { putExtra("cat", "Favoritos") }
        click(R.id.tile_playlist) { playlistMenu() }
        click(R.id.settings) { startActivity(Intent(this, SettingsActivity::class.java)) }
        click(R.id.profile) { startActivity(Intent(this, ProfileActivity::class.java)) }
        // com mais de um perfil, pergunta quem está assistindo ao abrir o app
        if (!Store.chosen && Store.ids(this).size > 1) startActivity(Intent(this, ProfileActivity::class.java))
    }

    private fun click(id: Int, f: () -> Unit) = findViewById<View>(id).setOnClickListener { f() }

    private fun open(id: Int, extra: Intent.() -> Unit) =
        click(id) { startActivity(Intent(this, MainActivity::class.java).apply(extra)) }

    private fun playlistMenu() {
        AlertDialog.Builder(this).setTitle("Playlist")
            .setItems(arrayOf("Receber pelo celular (QR)", "Escolher arquivo M3U", "Colar link")) { _, i ->
                when (i) {
                    0 -> startActivity(Intent(this, ReceiveActivity::class.java))
                    1 -> startActivity(Intent(this, MainActivity::class.java).putExtra("pick", "list"))
                    else -> linkDialog()
                }
            }.show()
    }

    private fun linkDialog() {
        val e = EditText(this).apply { inputType = InputType.TYPE_TEXT_VARIATION_URI; hint = "http://…"; setSingleLine() }
        AlertDialog.Builder(this).setTitle("Link da lista M3U").setView(e)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Abrir") { _, _ ->
                val s = e.text.toString().trim()
                if (s.startsWith("http://") || s.startsWith("https://"))
                    startActivity(Intent(this, MainActivity::class.java).putExtra("url", s))
            }.show()
    }

    override fun onResume() {
        super.onResume()
        val id = Store.cur(this)
        findViewById<TextView>(R.id.profile).text = Store.AVATARS[Store.av(this, id)].first + " " + Store.name(this, id)
        findViewById<TextView>(R.id.sub_favs).text = (prefs.getStringSet("favs", emptySet())?.size ?: 0).toString() + " itens"
        val dev = (Settings.Secure.getString(contentResolver, "android_id") ?: "?").take(12)
        findViewById<TextView>(R.id.footer).text =
            "Perfil: ${Store.name(this, id)}   |   Usuário: ${Account.user(this) ?: "—"}   |   " +
            "ID do aparelho: $dev   |   Vencimento: ${Account.expires(this) ?: "—"}"
    }

    @Deprecated("back handling kept simple")
    override fun onBackPressed() {
        AlertDialog.Builder(this).setTitle(R.string.exit_title).setMessage(R.string.exit_msg)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.exit) { _, _ -> finish() }.show()
    }
}
