package com.ntplayblow.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class ProfileActivity : Activity() {
    private var edit = false
    private lateinit var row: LinearLayout
    private lateinit var editBtn: Button

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#05080B")); setPadding(0, 0, 0, 0)
        }
        root.addView(TextView(this).apply {
            text = "NTPlayblow"; textSize = 30f; setTextColor(Color.parseColor("#CED9DE"))
            setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Quem está assistindo?"; textSize = 22f; setTextColor(Color.parseColor("#E6EDF2"))
            gravity = Gravity.CENTER; setPadding(0, (8 * d).toInt(), 0, (20 * d).toInt())
        })
        row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        root.addView(row)
        editBtn = Button(this).apply {
            isAllCaps = false; setOnClickListener { edit = !edit; render() }
        }
        root.addView(editBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = (24 * d).toInt() })
        setContentView(root)
        render()
    }

    private fun card(av: Int, label: String, selected: Boolean, onClick: () -> Unit): View {
        val d = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_item); isActivated = selected
            setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (12 * d).toInt())
            isClickable = true; isFocusable = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = (10 * d).toInt(); marginEnd = (10 * d).toInt() }
            addView(if (av >= 0) Store.avatarView(this@ProfileActivity, av, 88, 38f)
                    else TextView(this@ProfileActivity).apply {
                        text = "+"; textSize = 38f; setTextColor(Color.parseColor("#7F8E99")); gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams((88 * d).toInt(), (88 * d).toInt())
                    })
            addView(TextView(this@ProfileActivity).apply {
                text = label; textSize = 16f; setTextColor(Color.parseColor("#E6EDF2")); gravity = Gravity.CENTER
                setPadding(0, (8 * d).toInt(), 0, 0)
            })
            setOnClickListener { onClick() }
        }
    }

    private fun render() {
        row.removeAllViews()
        for (id in Store.ids(this)) {
            row.addView(card(Store.av(this, id), Store.name(this, id) + if (edit) "  ✎" else "", id == Store.cur(this)) {
                if (edit) editDialog(id) else { Store.setCur(this, id); Store.chosen = true; finish() }
            })
        }
        if (Store.ids(this).size < Store.MAX) row.addView(card(-1, "Novo perfil", false) { editDialog(null) })
        editBtn.text = if (edit) "Concluir" else "Editar perfis"
    }

    private fun editDialog(id: Int?) {
        val d = resources.displayMetrics.density
        var sel = if (id != null) Store.av(this, id) else 0
        val name = EditText(this).apply {
            hint = "Nome do perfil"; setSingleLine(); setText(if (id != null) Store.name(this@ProfileActivity, id) else "")
        }
        val avatars = ArrayList<TextView>()
        fun paint() = avatars.forEachIndexed { i, v -> v.alpha = if (i == sel) 1f else 0.4f }
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        Store.AVATARS.indices.chunked(6).forEach { chunk ->
            grid.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                chunk.forEach { i ->
                    val v = Store.avatarView(this@ProfileActivity, i, 44, 20f)
                    (v.layoutParams as LinearLayout.LayoutParams).setMargins((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
                    v.setOnClickListener { sel = i; paint() }
                    avatars.add(v); addView(v)
                }
            })
        }
        paint()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding((20 * d).toInt(), (8 * d).toInt(), (20 * d).toInt(), 0)
            addView(name); addView(grid)
        }
        val b = AlertDialog.Builder(this).setTitle("Editar perfil").setView(box)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val nm = name.text.toString().trim().ifEmpty { "Perfil ${Store.ids(this).size + 1}" }
                if (id == null) Store.add(this, nm, sel) else Store.save(this, id, nm, sel)
                render()
            }
        if (id != null && Store.ids(this).size > 1) {
            b.setNeutralButton("Excluir") { _, _ ->
                AlertDialog.Builder(this).setMessage("Excluir este perfil com seus favoritos e lista?")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Excluir") { _, _ -> Store.delete(this, id); render() }.show()
            }
        }
        b.show()
    }
}
