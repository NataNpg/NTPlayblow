package com.ntplayblow.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.TextView

class HomeActivity : Activity() {
    private val prefs get() = Store.prefs(this)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_home)
        go(R.id.tile_lists, null, null)
        go(R.id.tile_favs, "cat", "Favoritos")
        go(R.id.tile_videos, "pick", "video")
        go(R.id.tile_add, "pick", "list")
        go(R.id.add_top, "pick", "list")
        findViewById<android.view.View>(R.id.profile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        // com mais de um perfil, pergunta quem está assistindo ao abrir o app
        if (!Store.chosen && Store.ids(this).size > 1) startActivity(Intent(this, ProfileActivity::class.java))
        findViewById<android.view.View>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    // abre a tela de canais, opcionalmente com uma ação inicial
    private fun go(id: Int, key: String?, value: String?) =
        findViewById<android.view.View>(id).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply { if (key != null) putExtra(key, value) })
        }

    override fun onResume() {
        super.onResume()
        val id = Store.cur(this)
        findViewById<TextView>(R.id.profile).text = Store.AVATARS[Store.av(this, id)].first + " " + Store.name(this, id)
        val n = prefs.getInt("last_count", 0)
        findViewById<TextView>(R.id.sub_lists).text = if (n > 0) "$n canais" else "Nenhuma lista"
        findViewById<TextView>(R.id.sub_favs).text = (prefs.getStringSet("favs", emptySet())?.size ?: 0).toString() + " itens"
    }

    @Deprecated("back handling kept simple")
    override fun onBackPressed() {
        AlertDialog.Builder(this).setTitle(R.string.exit_title).setMessage(R.string.exit_msg)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.exit) { _, _ -> finish() }.show()
    }
}
