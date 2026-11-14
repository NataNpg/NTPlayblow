package com.ntplayblow.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class LoginActivity : Activity() {
    private lateinit var form: View
    private lateinit var msg: TextView
    private lateinit var go: Button

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_login)
        form = findViewById(R.id.form)
        msg = findViewById(R.id.msg)
        go = findViewById(R.id.go)
        val user = findViewById<EditText>(R.id.user)
        val pass = findViewById<EditText>(R.id.pass)
        go.setOnClickListener { doLogin(user.text.toString(), pass.text.toString()) }

        // pedido de acesso por WhatsApp: +55 (42) 99804-0495
        val wa = "https://wa.me/5542998040495?text=" +
            android.net.Uri.encode("Olá! Quero solicitar acesso ao NTPlayblow (aplicativo ou lista).")
        findViewById<android.widget.ImageView>(R.id.wa_qr).setImageBitmap(Store.qr(wa, 400))
        findViewById<View>(R.id.wa_btn).setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(wa))) } catch (_: Exception) { }
        }

        if (Account.loggedIn(this)) {
            // sessão salva: revalida em segundo plano (vencida/bloqueada volta ao login)
            form.visibility = View.GONE
            msg.text = "Entrando…"
            Thread {
                val r = Account.refresh(this)
                runOnUiThread {
                    if (r.ok || r.offline) openHome()
                    else { Account.logout(this); form.visibility = View.VISIBLE; msg.text = r.msg }
                }
            }.start()
        }
    }

    private fun doLogin(u: String, p: String) {
        go.isEnabled = false; msg.text = "Entrando…"
        Thread {
            val r = Account.login(this, u, p)
            runOnUiThread {
                go.isEnabled = true
                if (r.ok) openHome() else msg.text = r.msg
            }
        }.start()
    }

    private fun openHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
