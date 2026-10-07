package com.generalsea1.tmfm

import android.content.Context

class RecentStore(context: Context) {
    private val prefs = context.getSharedPreferences("tmfm_recent", Context.MODE_PRIVATE)

    fun ids(): List<String> =
        prefs.getString("ids", "").orEmpty().split('|').filter { it.isNotBlank() }

    fun add(id: String) {
        val next = (listOf(id) + ids().filterNot { it == id }).take(20)
        prefs.edit().putString("ids", next.joinToString("|")).apply()
    }

    fun clear() {
        prefs.edit().remove("ids").apply()
    }
}
