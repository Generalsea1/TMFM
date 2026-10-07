package com.generalsea1.tmfm

object BundledCatalog {
    val egypt: List<RadioStation> = listOf(
        station("eg-mix-fm-878", "Mix FM", "Mix FM", 87.8, "موسيقى / ترفيه", "https://mixfmegypt.com/", "official"),
        station("eg-radio-hits-882", "راديو هيتس", "Radio Hits", 88.2, "موسيقى / ترفيه", "https://radiohits882.com/en/about", "official"),
        station("eg-radio-masr-887", "راديو مصر", "Radio Masr", 88.7, "أخبار / حوارات / منوعات", "https://www.radiomasr.net/", "current-public-reference"),
        station("eg-middle-east-895", "إذاعة الشرق الأوسط", "Middle East Radio", 89.5, "حوارات / موسيقى", "https://www.maspero.eg/", "current-public-reference", verified = false),
        station("eg-radio-9090-909", "الراديو 9090", "Radio 9090", 90.9, "أخبار / رياضة / حوارات / ترفيه", "https://www.9090.fm/", "official-plus-current-directory", "https://9090streaming.mobtada.com/9090FMEGYPT", "MP3"),
        station("eg-cultural-915", "البرنامج الثقافي", "Cultural Programme", 91.5, "ثقافة / حوارات / موسيقى", "https://www.maspero.eg/", "current-public-reference", verified = false),
        station("eg-nrj-921", "NRJ مصر", "NRJ Egypt", 92.1, "موسيقى / ترفيه", "https://www.nrj.com.eg/", "current-public-reference", verified = false),
        station("eg-mega-fm-927", "ميجا FM", "Mega FM", 92.7, "موسيقى / ترفيه", "https://www.megafm927.com/en/about", "official"),
        station("eg-on-sport-937", "أون سبورت FM", "ON Sport FM", 93.7, "رياضة", "https://www.maspero.eg/", "current-public-reference", verified = false),
        station("eg-sha3by-950", "شعبي FM", "Sha3by FM", 95.0, "موسيقى شعبية / ترفيه", "https://95-fm.com/en/about", "official"),
        station("eg-european-954", "البرنامج الأوروبي", "European Programme", 95.4, "لغات / موسيقى", "https://www.maspero.eg/", "current-public-reference", verified = false, language = "English / Français / Deutsch / Italiano"),
        station("eg-radio-beloghos-955", "راديو بيلوجوس", "Radio Beloghos", 95.5, "موسيقى / منوعات", "https://www.maspero.eg/", "current-directory-reference", verified = false),
        station("eg-alhaya-956", "راديو الحياة FM", "Al Haya FM", 95.6, "موسيقى / منوعات", "https://www.maspero.eg/", "current-directory-reference", verified = false),
        station("eg-musical-988", "البرنامج الموسيقي", "Musical Programme", 98.8, "موسيقى / طرب", "https://www.maspero.eg/", "current-public-reference", verified = false),
        station("eg-elgouna-1000", "الجونة راديو", "El Gouna Radio", 100.0, "موسيقى / ترفيه", "https://www.elgounaradio.com/", "current-directory-reference", verified = false),
        station("eg-nogoum-1006", "نجوم FM", "Nogoum FM", 100.6, "موسيقى / برامج", "https://nogoumfm.net/about-us/", "official"),
        station("eg-greater-cairo-1022", "إذاعة القاهرة الكبرى", "Greater Cairo Radio", 102.2, "حوارات / أخبار محلية", "https://www.maspero.eg/", "current-public-reference", verified = false),
        station("eg-nile-news-1027", "النيل للأخبار", "Nile News", 102.7, "أخبار", "https://www.maspero.eg/", "current-public-reference", verified = false),
        station("eg-nile-fm-1042", "Nile FM", "Nile FM", 104.2, "Music / Entertainment", "https://www.nilefm.com/", "current-broadcaster-reference", language = "English"),
        station("eg-nagham-1053", "نغم FM", "Nagham FM", 105.3, "موسيقى / ترفيه", "https://www.naghamfm1053.com/en/about", "official"),
        station("eg-aghani-1058", "إذاعة الأغاني", "Al Aghani Radio", 105.8, "طرب / موسيقى", "https://www.maspero.eg/", "current-public-reference", verified = false),
        station("eg-voice-arabs-1063", "صوت العرب", "Voice of the Arabs", 106.3, "حوارات / أخبار", "https://www.maspero.eg/", "current-public-reference", verified = false),
        station("eg-general-1074", "البرنامج العام", "General Programme", 107.4, "حوارات / أخبار / منوعات", "https://www.maspero.eg/", "current-public-reference", verified = false),
        station("eg-youth-sport-1080", "إذاعة الشباب والرياضة", "Youth and Sports Radio", 108.0, "رياضة / شباب", "https://www.maspero.eg/", "current-public-reference", verified = false)
    )

    val globalBaseline: List<RadioStation> = listOf(
        RadioStation(
            id = "global-radio-swiss-jazz",
            name = "Radio Swiss Jazz",
            nameEnglish = "Radio Swiss Jazz",
            countryCode = "CH",
            countryName = "سويسرا",
            city = "Basel",
            frequencyMhz = null,
            band = null,
            streamUrl = "https://stream.srg-ssr.ch/m/radioswissjazz/aacp_96",
            streamType = "AAC",
            officialUrl = "https://www.radioswissjazz.ch/",
            language = "Deutsch / Français / Italiano",
            category = "Jazz",
            stationType = "Internet",
            isHardware = false,                        isOnline = true,
            isVerified = false,
            verificationStatus = "stream_pending_external_verification",
            lastVerified = "2026-10-07",
            source = "official-stream",
            logoUrl = null
        )
    )

    private fun station(
        id: String,
        ar: String,
        en: String,
        freq: Double,
        category: String,
        official: String,
        source: String,
        stream: String? = null,
        streamType: String? = null,
        language: String = "العربية",
        verified: Boolean = true
    ) = RadioStation(
        id = id,
        name = ar,
        nameArabic = ar,
        nameEnglish = en,
        countryCode = "EG",
        countryName = "مصر",
        city = "القاهرة",
        frequencyMhz = freq,
        band = "FM",
        streamUrl = stream,
        streamType = streamType,
        officialUrl = official,
        logoUrl = null,
        language = language,
        category = category,
        stationType = "FM",
        broadcastType = if (stream != null) BroadcastType.HYBRID else BroadcastType.HARDWARE_FM,
        frequencyVerified = verified,
        streamVerified = false,
        streamVerifiedAt = null,
        hardwareAccessState = HardwareAccessState.UNKNOWN,
        isOnline = stream != null,
        isVerified = verified,
        verificationStatus = if (verified) "frequency_verified" else "frequency_reference",
        lastVerified = if (verified) "2026-10-07" else null,
        source = source,
        notes = "تردد مرجعي للقاهرة/منطقة الإرسال؛ التغطية الفعلية تختلف حسب الموقع.",
        regionalAvailability = "القاهرة/بحسب منطقة الإرسال"
    )
}
