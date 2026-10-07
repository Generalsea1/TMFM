package com.generalsea1.tmfm

import android.os.Build
import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val Walnut = Color(0xFF17120E)
private val Walnut2 = Color(0xFF241B14)
private val Walnut3 = Color(0xFF302319)
private val Brass = Color(0xFFC8A56A)
private val BrassLight = Color(0xFFE1C995)
private val Cream = Color(0xFFF4EADC)
private val Smoke = Color(0xFFA99A86)
private val LightBg = Color(0xFFF4F0E9)
private val Ink = Color(0xFF17120E)

private val TmfmDark = darkColorScheme(
    primary = Brass, secondary = BrassLight, background = Walnut, surface = Walnut2,
    onBackground = Cream, onSurface = Cream
)

private val TmfmLight = lightColorScheme(
    primary = Color(0xFF6F4B25), secondary = Color(0xFF8B6840),
    background = LightBg, surface = Color.White, onBackground = Ink, onSurface = Ink
)

@Composable
fun TmfmRadioApp(
    controllerProvider: () -> androidx.media3.session.MediaController?,
    onStationPlayed: (RadioStation) -> Unit,
    onRecordRequested: (RadioStation) -> Unit,
    onPlayRecording: (RecordingEntity) -> Unit,
    onShareRecording: (RecordingEntity) -> Unit,
    onSleepRequested: (Int) -> Unit,
    onOpenSystemFm: () -> Unit,
    vm: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var theme by remember { mutableStateOf(ThemePrefs.get(context)) }
    val dark = when (theme) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }

    MaterialTheme(colorScheme = if (dark) TmfmDark else TmfmLight) {
        TmfmScaffold(
            vm = vm,
            controllerProvider = controllerProvider,
            onStationPlayed = onStationPlayed,
            onRecordRequested = onRecordRequested,
            onPlayRecording = onPlayRecording,
            onShareRecording = onShareRecording,
            onSleepRequested = onSleepRequested,
            onOpenSystemFm = onOpenSystemFm,
            theme = theme,
            onTheme = {
                ThemePrefs.set(context, it)
                theme = it
            }
        )
    }
}

private enum class Tab { HOME, STATIONS, RECORDINGS, SETTINGS }

@Composable
private fun TmfmScaffold(
    vm: MainViewModel,
    controllerProvider: () -> androidx.media3.session.MediaController?,
    onStationPlayed: (RadioStation) -> Unit,
    onRecordRequested: (RadioStation) -> Unit,
    onPlayRecording: (RecordingEntity) -> Unit,
    onShareRecording: (RecordingEntity) -> Unit,
    onSleepRequested: (Int) -> Unit,
    onOpenSystemFm: () -> Unit,
    theme: ThemeChoice,
    onTheme: (ThemeChoice) -> Unit
) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 14.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    NavItem("الرئيسية", "⌂", tab == Tab.HOME) { tab = Tab.HOME }
                    NavItem("المحطات", "◉", tab == Tab.STATIONS) { tab = Tab.STATIONS }
                    NavItem("تسجيلاتي", "●", tab == Tab.RECORDINGS) { tab = Tab.RECORDINGS }
                    NavItem("الإعدادات", "⚙", tab == Tab.SETTINGS) { tab = Tab.SETTINGS }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.HOME -> HomeScreen(
                    vm, controllerProvider, onStationPlayed, onRecordRequested,
                    onSleepRequested, onOpenSystemFm
                )
                Tab.STATIONS -> StationsScreen(vm, onStationPlayed, onRecordRequested, onSleepRequested)
                Tab.RECORDINGS -> RecordingsScreen(vm, onPlayRecording, onShareRecording)
                Tab.SETTINGS -> SettingsScreen(theme, onTheme, vm)
            }
        }
    }
}

@Composable
private fun NavItem(title: String, glyph: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(82.dp).clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(glyph, color = if (selected) Brass else Smoke, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.labelSmall, color = if (selected) Brass else Smoke)
    }
}

@Composable
private fun HomeScreen(
    vm: MainViewModel,
    controllerProvider: () -> androidx.media3.session.MediaController?,
    onStationPlayed: (RadioStation) -> Unit,
    onRecordRequested: (RadioStation) -> Unit,
    onSleepRequested: (Int) -> Unit,
    onOpenSystemFm: () -> Unit
) {
    val stations by vm.stations.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    var playing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<RadioStation?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            playing = controllerProvider()?.isPlaying == true
            delay(500)
        }
    }

    val current = stations.firstOrNull { favorites.contains(it.id) && it.internetPlayable }
        ?: stations.firstOrNull { it.internetPlayable }
        ?: stations.firstOrNull()
    val currentFavorite = current?.id?.let { favorites.contains(it) } == true

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("TMFM", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                    Text("VINTAGE RADIO • MODERN SOUND", color = Brass)
                }
                Text("RADIO", color = Smoke, fontWeight = FontWeight.Black)
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Walnut2),
                shape = RoundedCornerShape(30.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("CURRENT STATION", color = Brass, fontWeight = FontWeight.Black)
                            Text(
                                current?.nameArabic ?: "اختر محطة",
                                color = Cream,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(if (playing) "ON AIR" else "READY", color = if (playing) BrassLight else Smoke, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(14.dp))
                    VintageDial(current?.frequencyMhz, Modifier.fillMaxWidth().height(170.dp))

                    Spacer(Modifier.height(12.dp))
                    Text(
                        stationState(current),
                        color = Smoke,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ControlButton(
                            text = if (currentFavorite) "♥" else "♡",
                            action = current?.let { station -> { vm.toggleFavorite(station) } },
                            active = currentFavorite
                        )
                        if (current?.internetPlayable == true) {
                            ControlButton(
                                if (playing) "Ⅱ" else "▶",
                                {
                                    vm.markPlayed(current)
                                    onStationPlayed(current)
                                },
                                true,
                                true
                            )
                        }
                        if (current?.internetPlayable == true) {
                            ControlButton(
                                "●",
                                { onRecordRequested(current) },
                                false
                            )
                        }
                        ControlButton("◴", current?.let { { onSleepRequested(30) } }, false)
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { selected = current },
                        enabled = current != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("تفاصيل المحطة")
                    }
                }
            }
        }

        item { HardwareCard(onOpenSystemFm) }

        item {
            SectionHeader("مصر", "كتالوج ترددات موسّع")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                stations.filter { it.countryCode == "EG" }.take(18).forEach {
                    FilterChip(
                        selected = selected?.id == it.id,
                        onClick = { selected = it },
                        label = { Text(frequencyLabel(it)) }
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("دليل TMFM", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "مصر هي الفئة الأولى. دليل العالم يتوسع عند الاتصال، مع استبعاد المحتوى الإسلامي/القرآني من الكتالوج والبحث والاكتشاف.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    selected?.let {
        StationDialog(
            station = it,
            favorite = favorites.contains(it.id),
            onFavorite = { vm.toggleFavorite(it) },
            onPlay = {
                vm.markPlayed(it)
                onStationPlayed(it)
                selected = null
            },
            onRecord = {
                onRecordRequested(it)
                selected = null
            },
            onSleep = {
                onSleepRequested(it)
                selected = null
            },
            onClose = { selected = null }
        )
    }
}

@Composable
private fun VintageDial(frequency: Double?, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(22.dp)).background(Walnut)) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.62f
            val radius = minOf(size.width * 0.40f, size.height * 0.70f)
            drawCircle(Walnut3, radius, androidx.compose.ui.geometry.Offset(cx, cy))
            drawCircle(
                Brass,
                radius,
                androidx.compose.ui.geometry.Offset(cx, cy),
                style = Stroke(width = 3.dp.toPx())
            )

            for (i in 0..20) {
                val fraction = i / 20f
                val angle = Math.PI * (1.15 + 0.70 * fraction)
                val inner = radius * 0.78f
                val outer = radius * if (i % 5 == 0) 0.94f else 0.88f
                drawLine(
                    BrassLight,
                    androidx.compose.ui.geometry.Offset(
                        cx + (inner * kotlin.math.cos(angle)).toFloat(),
                        cy + (inner * kotlin.math.sin(angle)).toFloat()
                    ),
                    androidx.compose.ui.geometry.Offset(
                        cx + (outer * kotlin.math.cos(angle)).toFloat(),
                        cy + (outer * kotlin.math.sin(angle)).toFloat()
                    ),
                    strokeWidth = if (i % 5 == 0) 3.dp.toPx() else 1.dp.toPx()
                )
            }

            val value = frequency?.coerceIn(87.8, 108.0) ?: 98.0
            val fraction = ((value - 87.8) / (108.0 - 87.8)).coerceIn(0.0, 1.0)
            val angle = Math.PI * (1.15 + 0.70 * fraction)
            drawLine(
                BrassLight,
                androidx.compose.ui.geometry.Offset(cx, cy),
                androidx.compose.ui.geometry.Offset(
                    cx + (radius * 0.74f * kotlin.math.cos(angle)).toFloat(),
                    cy + (radius * 0.74f * kotlin.math.sin(angle)).toFloat()
                ),
                strokeWidth = 4.dp.toPx()
            )
            drawCircle(BrassLight, 7.dp.toPx(), androidx.compose.ui.geometry.Offset(cx, cy))
        }

        Column(
            Modifier.fillMaxWidth().padding(top = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                frequency?.let { String.format(Locale.US, "%.1f", it) } ?: "FM",
                color = BrassLight,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black
            )
            Text("MHz", color = Brass, style = MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable
private fun ControlButton(text: String, action: (() -> Unit)?, active: Boolean, large: Boolean = false) {
    IconButton(
        onClick = { action?.invoke() },
        enabled = action != null,
        modifier = Modifier.size(if (large) 64.dp else 50.dp)
            .background(if (active) Brass else Walnut3, CircleShape)
    ) {
        Text(text, color = if (active) Walnut else Cream, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HardwareCard(onOpenSystemFm: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val status = remember(context) { HardwareRadioProbe.detect(context) }
    val canOpenSystem = status.systemRadioPackage != null

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("الراديو الهوائي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("FM حقيقي فقط • لا محاكاة", color = Brass)
                }
                Text("FM", color = Brass, fontWeight = FontWeight.Black)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                when (status.accessState) {
                    HardwareAccessState.AVAILABLE ->
                        "الوصول مُعلن، لكن جلسة tuner لم تُثبت داخل TMFM."
                    HardwareAccessState.SYSTEM_ONLY ->
                        "يوجد/يُعلن عن FM، لكن التحكم محجوز للنظام أو OEM."
                    HardwareAccessState.NO_TUNER ->
                        "لا يوجد tuner مكشوف لـTMFM على هذا الجهاز."
                    HardwareAccessState.UNKNOWN ->
                        "حالة FM غير معروفة."
                },
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(5.dp))
            Text(status.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    "TUNER",
                    status.featurePresent
                )
                StatusPill(
                    "SESSION",
                    status.tunerSessionVerified
                )
                StatusPill(
                    "SYSTEM FM",
                    canOpenSystem
                )
            }

            if (canOpenSystem) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onOpenSystemFm,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("فتح راديو FM الخاص بالنظام")
                }
                Text(
                    "هذا يفتح تطبيق OEM إن وجده TMFM، ولا يدّعي أن التحكم داخل TMFM متاح.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun StatusPill(title: String, enabled: Boolean) {
    Surface(shape = RoundedCornerShape(50), tonalElevation = 1.dp) {
        Text(
            title + if (enabled) " ✓" else " —",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = if (enabled) Brass else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StationsScreen(
    vm: MainViewModel,
    onStationPlayed: (RadioStation) -> Unit,
    onRecordRequested: (RadioStation) -> Unit,
    onSleepRequested: (Int) -> Unit
) {
    val stations by vm.stations.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val countries by vm.countries.collectAsStateWithLifecycle()
    val selectedCountry by vm.selectedCountry.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var world by remember { mutableStateOf(false) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var recentOnly by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<RadioStation?>(null) }

    LaunchedEffect(world) {
        if (world) vm.loadCountries() else vm.loadCountry("EG")
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(18.dp))
        Text("المحطات", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("مصر أولًا • العالم عند الطلب", color = Brass)

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            query,
            { query = it; vm.search(it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("محطة، تردد، بلد، مدينة، لغة، تصنيف") }
        )

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!world, { world = false }, label = { Text("🇪🇬 مصر") })
            FilterChip(world, { world = true }, label = { Text("العالم") })
            FilterChip(favoritesOnly, { favoritesOnly = !favoritesOnly }, label = { Text("♥ المفضلة") })
            FilterChip(recentOnly, { recentOnly = !recentOnly }, label = { Text("آخر ما استمعت") })
        }

        if (world && countries.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                countries.take(24).forEach {
                    FilterChip(
                        selected = selectedCountry == it.code,
                        onClick = { vm.loadCountry(it.code) },
                        label = { Text(it.name + " " + it.stationCount) }
                    )
                }
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = vm::clearError) { Text("إخفاء") }
                }
            }
        }

        val visible = stations.filter {
            (!favoritesOnly || favorites.contains(it.id)) &&
                (!recentOnly || recents.contains(it.id))
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (world) "محطات " + selectedCountry else "مصر", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(visible.size.toString(), color = Brass)
        }

        Spacer(Modifier.height(8.dp))
        when {
            loading && visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("جاري تحديث الدليل…", color = Smoke)
            }
            visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد محطات مطابقة.", color = Smoke)
            }
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(9.dp),
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
                items(visible, key = { it.id }) {
                    StationRow(it, favorites.contains(it.id), { selected = it }, { vm.toggleFavorite(it) })
                }
            }
        }
    }

    selected?.let {
        StationDialog(
            it,
            favorites.contains(it.id),
            { vm.toggleFavorite(it) },
            {
                vm.markPlayed(it)
                onStationPlayed(it)
                selected = null
            },
            {
                onRecordRequested(it)
                selected = null
            },
            {
                onSleepRequested(it)
                selected = null
            },
            { selected = null }
        )
    }
}

@Composable
private fun StationRow(station: RadioStation, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(15.dp)).background(Walnut2),
                contentAlignment = Alignment.Center
            ) {
                Text(station.frequencyMhz?.let { String.format(Locale.US, "%.1f", it) } ?: "WEB", color = Brass, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(station.nameArabic ?: station.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        station.frequencyMhz?.let { String.format(Locale.US, "%.1f MHz", it) },
                        station.city,
                        station.streamType
                    ).joinToString(" • ").ifBlank { station.countryName },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    classificationLabel(station),,
                    color = Brass,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            TextButton(onClick = onFavorite) { Text(if (favorite) "♥" else "♡", color = Brass) }
        }
    }
}

@Composable
private fun StationDialog(
    station: RadioStation,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onPlay: () -> Unit,
    onRecord: () -> Unit,
    onSleep: (Int) -> Unit,
    onClose: () -> Unit
) {
    var timer by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Column {
                Text(station.nameArabic ?: station.name, fontWeight = FontWeight.Bold)
                Text(station.nameEnglish ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column {
                station.frequencyMhz?.let {
                    Text(String.format(Locale.US, "%.1f MHz", it), color = Brass, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                }
                Text(station.countryName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                station.category?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (station.streamUrl.isNullOrBlank()) "تردد/دليل فقط؛ لا يوجد بث إنترنت موثق داخل TMFM."
                    else "بث إنترنت متاح للتشغيل والتسجيل المحلي.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row {
                if (station.internetPlayable) {
                    Button(onClick = onPlay) { Text("▶ تشغيل") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onRecord) { Text("● تسجيل") }
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onFavorite) { Text(if (favorite) "♥ محفوظة" else "♡ حفظ") }
                TextButton(onClick = { timer = true }) { Text("مؤقت") }
            }
        }
    )
    if (timer) {
        SleepTimerDialog(
            onSelect = { onSleep(it); timer = false },
            onClose = { timer = false }
        )
    }
}

@Composable
private fun SleepTimerDialog(onSelect: (Int) -> Unit, onClose: () -> Unit) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("مؤقت النوم") },
        text = {
            Column {
                listOf(15, 30, 45, 60).forEach {
                    TextButton(onClick = { onSelect(it) }, modifier = Modifier.fillMaxWidth()) {
                        Text(it.toString() + " دقيقة")
                    }
                }
                Spacer(Modifier.height(5.dp))
                OutlinedTextField(
                    custom,
                    { custom = it.filter(Char::isDigit).take(4) },
                    label = { Text("مدة مخصصة بالدقائق") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                custom.toIntOrNull()?.takeIf { it > 0 }?.let(onSelect) ?: onClose()
            }) { Text("تطبيق") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("إلغاء") } }
    )
}

@Composable
private fun RecordingsScreen(vm: MainViewModel, onPlay: (RecordingEntity) -> Unit, onShare: (RecordingEntity) -> Unit) {
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    var renameTarget by remember { mutableStateOf<RecordingEntity?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(18.dp))
        Text("تسجيلاتي", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("تسجيل المصدر الأصلي • لا يتم رفع التسجيل إلى خادم", color = Brass)
        Spacer(Modifier.height(14.dp))

        if (recordings.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد تسجيلات بعد. افتح محطة إنترنت واضغط «● تسجيل».", color = Smoke)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                items(recordings, key = { it.id }) { recording ->
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(recording.title ?: recording.stationName, fontWeight = FontWeight.Bold)
                            Text(
                                listOfNotNull(
                                    recording.frequencyMhz?.let { String.format(Locale.US, "%.1f MHz", it) },
                                    formatDate(recording.createdAtMillis),
                                    DateUtils.formatElapsedTime(recording.durationMillis / 1000L),
                                    formatSize(recording.fileSizeBytes)
                                ).joinToString(" • "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            Row {
                                TextButton(onClick = { onPlay(recording) }) { Text("تشغيل") }
                                TextButton(onClick = { renameTarget = recording }) { Text("إعادة تسمية") }
                                TextButton(onClick = { onShare(recording) }) { Text("مشاركة") }
                                TextButton(onClick = { vm.deleteRecording(recording) }) { Text("حذف") }
                            }
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let {
        varNameDialog(it, { value ->
            vm.renameRecording(it, value)
            renameTarget = null
        }, { renameTarget = null })
    }
}

@Composable
private fun varNameDialog(recording: RecordingEntity, onSave: (String) -> Unit, onClose: () -> Unit) {
    var value by remember { mutableStateOf(recording.title ?: recording.stationName) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("إعادة تسمية التسجيل") },
        text = { OutlinedTextField(value, { value = it }, label = { Text("اسم التسجيل") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onClose) { Text("إلغاء") } }
    )
}

@Composable
private fun SettingsScreen(theme: ThemeChoice, onTheme: (ThemeChoice) -> Unit, vm: MainViewModel) {
    val recents by vm.recents.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(18.dp))
        Text("الإعدادات", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))

        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("المظهر", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(theme == ThemeChoice.SYSTEM, { onTheme(ThemeChoice.SYSTEM) }, label = { Text("النظام") })
                    FilterChip(theme == ThemeChoice.LIGHT, { onTheme(ThemeChoice.LIGHT) }, label = { Text("فاتح") })
                    FilterChip(theme == ThemeChoice.DARK, { onTheme(ThemeChoice.DARK) }, label = { Text("داكن") })
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("هوية TMFM", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "لا يحتاج التطبيق إلى حساب. المفضلة والسجل والتسجيلات محلية. الدليل الخارجي قابل للتوسع، مع فلتر يمنع المحطات الإسلامية/القرآنية من الظهور.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "لا يتم تمثيل ماسح FM أو قوة إشارة وهمية. الراديو الهوائي يحتاج عتادًا مكشوفًا وواجهة tuner مصرحًا بها من النظام/OEM.",
                    color = Brass
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = vm::clearRecent, enabled = recents.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("مسح آخر ما استمعت إليه")
        }
    }
}

private fun frequencyLabel(station: RadioStation): String =
    station.frequencyMhz?.let { String.format(Locale.US, "%.1f", it) } ?: station.name

private fun stationState(station: RadioStation?): String =
    when (station?.classification()) {
        null -> "لا توجد محطة مختارة."
        StationClassification.PLAYABLE_INTERNET ->
            "بث إنترنت مثبت خارجيًا • التشغيل والتسجيل المباشر متاحان."
        StationClassification.VERIFIED_FREQUENCY ->
            "تردد FM موثّق • يحتاج وصول tuner حقيقي من النظام أو OEM."
        StationClassification.DIRECTORY_REFERENCE ->
            "تردد مرجعي • لا يوجد بث إنترنت موثّق."
        StationClassification.OFFLINE ->
            "البث غير متاح حاليًا."
        StationClassification.UNVERIFIED ->
            "غير متاح للمستخدم حتى يكتمل التحقق."
    }

private fun classificationLabel(station: RadioStation): String =
    when (station.classification()) {
        StationClassification.PLAYABLE_INTERNET -> "بث إنترنت متاح"
        StationClassification.VERIFIED_FREQUENCY -> "تردد FM موثّق"
        StationClassification.DIRECTORY_REFERENCE -> "مرجع ترددي"
        StationClassification.OFFLINE -> "غير متاح حاليًا"
        StationClassification.UNVERIFIED -> "غير متاح"
    }

private fun formatDate(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return bytes.toString() + " B"
    if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

@Composable
private fun SectionHeader(title: String, action: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(action, color = Brass, style = MaterialTheme.typography.labelSmall)
    }
}
