package com.generalsea1.tmfm

import java.text.Normalizer
import java.util.Locale

object RadioCatalogPolicy {
    private val forbiddenTokens = listOf(
        "quran", "koran", "islam", "islamic", "muslim", "moslem",
        "hadith", "adhan", "azan", "sunnah", "salafi", "ramadan",
        "قرآن", "القرآن", "اسلام", "إسلام", "إسلامي", "اسلامي",
        "مسلم", "مسلمين", "حديث", "أحاديث", "اذان", "أذان",
        "أذكار", "اذكار", "سنة نبوية"
    )

    fun allow(station: RadioStation): Boolean {
        if (station.isIslamic) return false
        if (station.isChristian) return true

        val text = listOfNotNull(
            station.name,
            station.nameArabic,
            station.nameEnglish,
            station.category,
            station.broadcastType.name,
            station.notes
        ).joinToString(" ")
            .lowercase(Locale.ROOT)

        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
        return forbiddenTokens.none { normalized.contains(it) }
    }

    fun filter(stations: Iterable<RadioStation>): List<RadioStation> =
        stations.filter(::allow)
}
