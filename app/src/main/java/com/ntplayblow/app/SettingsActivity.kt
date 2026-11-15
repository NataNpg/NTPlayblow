package com.ntplayblow.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private val prefs get() = Store.prefs(this)
    private lateinit var menu: LinearLayout
    private lateinit var content: LinearLayout
    private val items = ArrayList<Button>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_settings)
        menu = findViewById(R.id.menu)
        content = findViewById(R.id.content)
        findViewById<View>(R.id.back).setOnClickListener { finish() }

        addItem("Informação Geral") { showInfo() }
        addItem("Player de Vídeo") { showPlayer() }
        addItem("Receber lista por QR") { startActivity(android.content.Intent(this, ReceiveActivity::class.java)) }
        addItem("Controle parental") { parentalGate() }
        addItem("Limpar assistidos") {
            confirm("Apagar o ponto em que você parou em todos os vídeos?") {
                val e = prefs.edit()
                prefs.all.filter { it.value is Long }.keys.forEach { e.remove(it) }
                e.apply(); toast("Assistidos limpos")
            }
        }
        addItem("Limpar cache") {
            confirm("Limpar o cache do app?") { cacheDir.deleteRecursively(); toast("Cache limpo") }
        }
        addItem("Sair da conta", danger = true) {
            confirm("Sair da conta ${Account.user(this) ?: ""}?") {
                Account.logout(this)
                startActivity(android.content.Intent(this, LoginActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
        }
        addItem("Remover lista", danger = true) {
            confirm("Remover a lista carregada e os favoritos?") {
                prefs.edit().remove("last_list").remove("last_count").remove("favs").apply()
                Library.invalidate()
                toast("Lista removida")
            }
        }
        showInfo()
    }

    private fun addItem(label: String, danger: Boolean = false, onClick: () -> Unit) {
        val btn = Button(this).apply {
            text = label; isAllCaps = false; gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_item); setPadding(32, 24, 32, 24)
            setTextColor(if (danger) Color.parseColor("#FF8A80") else Color.parseColor("#E6EDF2"))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = 12 }
            setOnClickListener { items.forEach { it.isActivated = false }; isActivated = true; onClick() }
        }
        items.add(btn); menu.addView(btn)
    }

    private fun text(t: String, size: Float, color: String, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(Color.parseColor(color))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, 8, 0, 8)
    }

    private fun clear(title: String, sub: String) {
        content.removeAllViews()
        content.addView(text(title, 22f, "#E6EDF2", true))
        content.addView(text(sub, 13f, "#7F8E99"))
    }

    private fun showInfo() {
        clear("Sobre o app", "Informações deste aparelho.")
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "?" }
        val id = Settings.Secure.getString(contentResolver, "android_id") ?: "?"
        val favs = prefs.getStringSet("favs", emptySet())?.size ?: 0
        listOf(
            "USUÁRIO" to (Account.user(this) ?: "—"), "VENCIMENTO" to (Account.expires(this) ?: "—"),
            "VERSÃO" to ver, "PLATAFORMA" to "Android ${Build.VERSION.RELEASE}", "ID DO APARELHO" to id,
            "CANAIS NA LISTA" to prefs.getInt("last_count", 0).toString(), "FAVORITOS" to favs.toString()
        ).forEach { (k, v) ->
            content.addView(text(k, 11f, "#7F8E99"))
            content.addView(text(v ?: "?", 17f, "#E6EDF2", true))
        }
    }

    private fun showPlayer() {
        clear("Configurações do Player", "Vale só neste aparelho.")
        content.addView(text("FORMATO DE TELA", 11f, "#7F8E99"))
        content.addView(text("Original mantém a proporção; Preencher corta as bordas; Esticar deforma para encher a tela.", 13f, "#7F8E99"))
        val cur = prefs.getString("resize", "fit")
        listOf("Original" to "fit", "Preencher" to "zoom", "Esticar" to "fill").forEach { (label, v) ->
            content.addView(Button(this).apply {
                text = label; isAllCaps = false; setBackgroundResource(R.drawable.bg_item)
                setTextColor(Color.parseColor("#E6EDF2")); isActivated = cur == v
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = 10 }
                setOnClickListener { prefs.edit().putString("resize", v).apply(); showPlayer() }
            })
        }
    }

    // ---- controle parental ----
    private fun parentalGate() {
        if (!Store.hasPin(this)) { showParental(); return }
        pinDialog("Digite o PIN") { if (Store.checkPin(this, it)) showParental() else toast("PIN incorreto") }
    }

    private fun pinDialog(title: String, ok: (String) -> Unit) {
        val e = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(4)); hint = "4 dígitos"
        }
        AlertDialog.Builder(this).setTitle(title).setView(e).setNegativeButton("Cancelar", null)
            .setPositiveButton("OK") { _, _ -> ok(e.text.toString()) }.show()
    }

    private fun actionBtn(label: String, onClick: () -> Unit) {
        content.addView(Button(this).apply {
            text = label; isAllCaps = false; setBackgroundResource(R.drawable.bg_item)
            setTextColor(Color.parseColor("#E6EDF2"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = 10 }
            setOnClickListener { onClick() }
        })
    }

    private fun showParental() {
        clear("Controle parental", "Esconde categorias e canais adultos (adulto, +18, xxx…) em todos os perfis.")
        if (!Store.hasPin(this)) {
            content.addView(text("Defina um PIN de 4 dígitos para ativar.", 14f, "#E6EDF2"))
            actionBtn("Definir PIN") {
                pinDialog("Novo PIN") { a ->
                    if (a.length != 4) toast("Use 4 dígitos")
                    else pinDialog("Repita o PIN") { b ->
                        if (a == b) { Store.setPin(this, a); Store.setParental(this, true); toast("PIN definido"); showParental() }
                        else toast("Os PINs não conferem")
                    }
                }
            }
            return
        }
        val on = Store.parentalOn(this)
        content.addView(text(if (on) "Bloqueio LIGADO" else "Bloqueio DESLIGADO", 17f, if (on) "#4DD0E1" else "#FF8A80", true))
        actionBtn(if (on) "Desligar bloqueio" else "Ligar bloqueio") { Store.setParental(this, !on); showParental() }
        actionBtn("Alterar PIN") {
            pinDialog("Novo PIN") { a ->
                if (a.length == 4) { Store.setPin(this, a); toast("PIN alterado") } else toast("Use 4 dígitos")
            }
        }
        actionBtn("Remover PIN") { confirm("Remover o PIN e desligar o bloqueio?") { Store.clearPin(this); showParental() } }
        content.addView(text("A mudança vale na próxima vez que a lista for aberta.", 12f, "#7F8E99"))
    }

    private fun confirm(msg: String, ok: () -> Unit) =
        AlertDialog.Builder(this).setMessage(msg).setNegativeButton("Cancelar", null)
            .setPositiveButton("Confirmar") { _, _ -> ok() }.show()

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
