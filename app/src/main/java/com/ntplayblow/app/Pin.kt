package com.ntplayblow.app

import android.app.Activity
import android.app.AlertDialog
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.Toast

// Libera o conteúdo adulto com o PIN do controle parental (vale até fechar o app).
object Pin {
    var unlocked = false

    private fun ask(a: Activity, title: String, ok: (String) -> Unit) {
        val e = EditText(a).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(4)); hint = "4 dígitos"
        }
        AlertDialog.Builder(a).setTitle(title).setView(e).setNegativeButton("Cancelar", null)
            .setPositiveButton("OK") { _, _ -> ok(e.text.toString()) }.show()
    }

    private fun toast(a: Activity, s: String) = Toast.makeText(a, s, Toast.LENGTH_SHORT).show()

    fun require(a: Activity, ok: () -> Unit) {
        if (unlocked) { ok(); return }
        if (!Store.hasPin(a)) {
            ask(a, "Defina um PIN para liberar conteúdo adulto") { p1 ->
                if (p1.length != 4) toast(a, "Use 4 dígitos")
                else ask(a, "Repita o PIN") { p2 ->
                    if (p1 == p2) { Store.setPin(a, p1); Store.setParental(a, true); unlocked = true; ok() }
                    else toast(a, "Os PINs não conferem")
                }
            }
            return
        }
        ask(a, "PIN para conteúdo adulto") { p ->
            if (Store.checkPin(a, p)) { unlocked = true; ok() } else toast(a, "PIN incorreto")
        }
    }
}
