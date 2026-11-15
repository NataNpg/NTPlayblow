package com.ntplayblow.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView

class Card(val title: String, val cover: String, val movie: Ch?, val show: Show?)

// Filmes e Séries: categorias à esquerda e grade de capas à direita.
class VodActivity : Activity() {
    private lateinit var type: String          // "movie" ou "series"
    private lateinit var catList: ListView
    private lateinit var grid: GridView
    private lateinit var msg: TextView
    private val prefs get() = Store.prefs(this)
    private var cats: List<String> = emptyList()
    private var counts: Map<String, Int> = emptyMap()
    private var cards: List<Card> = emptyList()
    private var selected = ""

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        type = intent?.getStringExtra("type") ?: "movie"
        setContentView(R.layout.activity_vod)
        findViewById<TextView>(R.id.vtitle).text = if (type == "movie") "Filmes" else "Séries"
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        catList = findViewById(R.id.categories)
        grid = findViewById(R.id.grid)
        msg = findViewById(R.id.msg)
        catList.setOnItemClickListener { _, _, i, _ -> select(cats[i]) }
        grid.setOnItemClickListener { _, _, i, _ -> openCard(cards[i]) }
        msg.visibility = View.VISIBLE
        msg.text = "Carregando lista…"
        Library.ensure(this, { n -> msg.text = "Carregando lista… $n itens" }) { build() }
    }

    override fun onResume() { super.onResume(); if (Library.state == 2 && cats.isNotEmpty()) build() }

    private fun cardOf(title: String): Card? =
        if (type == "movie") Library.movieByTitle[title]?.let { Card(it.title, it.logo, it, null) }
        else Library.shows[title.lowercase()]?.let { Card(it.title, it.cover, null, it) }

    private fun recentCards() = Recent.list(prefs, type).mapNotNull { cardOf(it) }

    private fun favCards(): List<Card> =
        (prefs.getStringSet("vfavs", emptySet()) ?: emptySet())
            .filter { it.startsWith("$type:") }.mapNotNull { cardOf(it.removePrefix("$type:")) }

    private fun build() {
        counts = if (type == "movie") Library.movies.groupingBy { it.group }.eachCount()
                 else Library.shows.values.groupingBy { it.group }.eachCount()
        if (counts.isEmpty()) {
            msg.visibility = View.VISIBLE
            msg.text = Library.error?.let { "Erro ao abrir a lista: $it" } ?: "Nenhum item nesta seção."
            return
        }
        val head = ArrayList<String>()
        if (recentCards().isNotEmpty()) head.add("Continuar assistindo")
        head.add("Favoritos")
        cats = head + Library.order(counts.keys.toList())
        catList.adapter = CatAdapter()
        select(if (selected in cats) selected else cats[0])
    }

    private fun select(cat: String) {
        if (Store.adultGroup(cat) && !Pin.unlocked) { Pin.require(this) { select(cat) }; return }
        selected = cat
        cards = when (cat) {
            "Continuar assistindo" -> recentCards()
            "Favoritos" -> favCards()
            else -> if (type == "movie") Library.movies.filter { it.group == cat }.map { Card(it.title, it.logo, it, null) }
                    else Library.shows.values.filter { it.group == cat }.map { Card(it.title, it.cover, null, it) }
        }
        msg.visibility = if (cards.isEmpty()) View.VISIBLE else View.GONE
        if (cards.isEmpty()) msg.text = if (cat == "Favoritos") "Nenhum favorito ainda." else "Nada aqui."
        grid.adapter = CardAdapter()
        (catList.adapter as? BaseAdapter)?.notifyDataSetChanged()
        val i = cats.indexOf(cat)
        if (i >= 0) catList.setItemChecked(i, true)
    }

    private fun openCard(c: Card) {
        Library.selMovie = c.movie; Library.selShow = c.show
        startActivity(Intent(this, DetailActivity::class.java))
    }

    private inner class CatAdapter : BaseAdapter() {
        override fun getCount() = cats.size
        override fun getItem(p: Int) = cats[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, v: View?, parent: ViewGroup): View {
            val row = v ?: LayoutInflater.from(this@VodActivity).inflate(R.layout.item_category, parent, false)
            val c = cats[p]
            row.findViewById<TextView>(R.id.cname).text = if (Store.adultGroup(c)) "[18+] $c" else c
            row.findViewById<TextView>(R.id.ccount).text = when (c) {
                "Continuar assistindo" -> recentCards().size
                "Favoritos" -> favCards().size
                else -> counts[c] ?: 0
            }.toString()
            row.isActivated = c == selected
            return row
        }
    }

    private inner class CardAdapter : BaseAdapter() {
        override fun getCount() = cards.size
        override fun getItem(p: Int) = cards[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, v: View?, parent: ViewGroup): View {
            val row = v ?: LayoutInflater.from(this@VodActivity).inflate(R.layout.item_card, parent, false)
            val c = cards[p]
            row.findViewById<TextView>(R.id.ctitle).text = c.title
            ImageLoader.load(c.cover, row.findViewById<ImageView>(R.id.poster))
            return row
        }
    }
}
