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
import android.widget.Toast

class HomeActivity : Activity() {
    private val prefs get() = Store.prefs(this)
    private var backAt = 0L

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_home)
        click(R.id.tile_live) { startActivity(Intent(this, MainActivity::class.java)) }
        click(R.id.tile_movies) { startActivity(Intent(this, VodActivity::class.java).putExtra("type", "movie")) }
        click(R.id.tile_series) { startActivity(Intent(this, VodActivity::class.java).putExtra("type", "series")) }
        click(R.id.tile_favs) { startActivity(Intent(this, MainActivity::class.java).putExtra("cat", "Favoritos")) }
        click(R.id.tile_playlist) { playlistMenu() }
        click(R.id.settings) { startActivity(Intent(this, SettingsActivity::class.java)) }
        click(R.id.profile) { startActivity(Intent(this, ProfileActivity::class.java)) }
        // com mais de um perfil, pergunta quem está assistindo ao abrir o app
        if (!Store.chosen && Store.ids(this).size > 1) startActivity(Intent(this, ProfileActivity::class.java))
    }

    private fun click(id: Int, f: () -> Unit) = findViewById<View>(id).setOnClickListener { f() }

    private fun playlistMenu() {
        AlertDialog.Builder(this).setTitle("Playlist")
            .setItems(arrayOf("Receber pelo celular (QR)", "Escolher arquivo M3U", "Colar link")) { _, i ->
                when (i) {
                    0 -> startActivity(Intent(this, ReceiveActivity::class.java))
                    1 -> startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                    }, REQ_LIST)
                    else -> linkDialog()
                }
            }.show()
    }

    private fun saveList(src: String) {
        prefs.edit().putString("last_list", src).apply()
        Library.invalidate()
        Library.ensure(this, { }) { }
        Toast.makeText(this, "Lista adicionada", Toast.LENGTH_SHORT).show()
    }

    private fun linkDialog() {
        val e = EditText(this).apply { inputType = InputType.TYPE_TEXT_VARIATION_URI; hint = "http://…"; setSingleLine() }
        AlertDialog.Builder(this).setTitle("Link da lista M3U").setView(e)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Adicionar") { _, _ ->
                val s = e.text.toString().trim()
                if (s.startsWith("http://") || s.startsWith("https://")) saveList(s)
                else Toast.makeText(this, R.string.err_url, Toast.LENGTH_SHORT).show()
            }.show()
    }

    @Deprecated("simple result API is enough here")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val u = data?.data ?: return
        if (req != REQ_LIST || res != RESULT_OK) return
        try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        saveList(u.toString())
    }

    override fun onResume() {
        super.onResume()
        // carrega (ou recarrega, se a lista mudou) a biblioteca em segundo plano
        Library.ensure(this, { }) { }
        val id = Store.cur(this)
        findViewById<TextView>(R.id.profile).text = Store.AVATARS[Store.av(this, id)].first + " " + Store.name(this, id)
        findViewById<TextView>(R.id.sub_favs).text = (prefs.getStringSet("favs", emptySet())?.size ?: 0).toString() + " itens"
        val dev = (Settings.Secure.getString(contentResolver, "android_id") ?: "?").take(12)
        findViewById<TextView>(R.id.footer).text =
            "Perfil: ${Store.name(this, id)}   |   Usuário: ${Account.user(this) ?: "—"}   |   " +
            "ID do aparelho: $dev   |   Vencimento: ${Account.expires(this) ?: "—"}"
    }

    // sem pergunta de saída: toque duas vezes em voltar para fechar
    @Deprecated("back handling kept simple")
    override fun onBackPressed() {
        val now = System.currentTimeMillis()
        if (now - backAt < 2000) finish()
        else { backAt = now; Toast.makeText(this, "Aperte voltar de novo para sair", Toast.LENGTH_SHORT).show() }
    }

    companion object { const val REQ_LIST = 3 }
}
