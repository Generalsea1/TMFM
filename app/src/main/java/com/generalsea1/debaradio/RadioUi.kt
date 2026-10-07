package com.generalsea1.debaradio

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val DarkBg = Color(0xFF0B0D12)
private val Panel = Color(0xFF141821)
private val TextPrimary = Color(0xFFF5F7FA)
private val Muted = Color(0xFF9DA5B4)
private val Accent = Color(0xFFF4B73F)

val TmfmColors = darkColorScheme(
    primary = Accent,
    secondary = Accent,
    background = DarkBg,
    surface = Panel,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun TmfmRadioScreen(
    vm: MainViewModel,
    controllerProvider: () -> MediaController?,
    onStationPlayed: (RadioStation) -> Unit,
    onRecordRequested: (RadioStation) -> Unit,
    onPlayRecording: (RecordingEntity) -> Unit,
    onShareRecording: (RecordingEntity) -> Unit
) {
    val stations by vm.stations.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val recordings by vm.recordings.collectAsStateWithLifecycle()

    var search by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf("الكل") }
    var selectedStation by remember { mutableStateOf<RadioStation?>(null) }
    var showFavorites by remember { mutableStateOf(false) }
    var showRecordings by remember { mutableStateOf(false) }
    var sleepMinutes by remember { mutableIntStateOf(0) }

    LaunchedEffect(search) {
        if (search.isBlank()) {
            vm.refresh()
        } else {
            vm.search(search)
        }
    }

    LaunchedEffect(sleepMinutes) {
        if (sleepMinutes > 0) {
            kotlinx.coroutines.delay(TimeUnit.MINUTES.toMillis(sleepMinutes.toLong()))
            controllerProvider()?.stop()
            sleepMinutes = 0
        }
    }

    val countries = remember(stations) {
        listOf("الكل") + stations
            .map { it.countryName }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    val filtered = stations.filter { station ->
        val matchesCountry =
            selectedCountry == "الكل" || station.countryName == selectedCountry
        val matchesFavorite =
            !showFavorites || favorites.contains(station.id)
        matchesCountry && matchesFavorite
    }

    selectedStation?.let { station ->
        StationPlayerDialog(
            station = station,
            favorite = favorites.contains(station.id),
            onFavorite = { vm.toggleFavorite(station) },
            onPlay = {
                vm.saveLastStation(station)
                vm.markPlayed(station)
                onStationPlayed(station)
                selectedStation = null
            },
            onRecord = {
                onRecordRequested(station)
                selectedStation = null
            },
            onClose = { selectedStation = null },
            onSleep = { minutes ->
                sleepMinutes = minutes
                selectedStation = null
            }
        )
    }

    if (showRecordings) {
        RecordingsDialog(
            recordings = recordings,
            onPlay = onPlayRecording,
            onShare = onShareRecording,
            onDelete = vm::deleteRecording,
            onClose = { showRecordings = false }
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp)
        ) {
            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("TMFM RADIO", style = MaterialTheme.typography.headlineSmall)
                    Text("راديو مصر والعالم", color = Muted)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { showRecordings = true }) {
                        Text("التسجيلات")
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(Accent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("FM", color = DarkBg, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HardwareCard()
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("بحث: محطة، بلد، تردد، لغة، تصنيف") }
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                countries.forEach { country ->
                    FilterChip(
                        selected = selectedCountry == country && !showFavorites,
                        onClick = {
                            showFavorites = false
                            selectedCountry = country
                        },
                        label = { Text(country) }
                    )
                }

                FilterChip(
                    selected = showFavorites,
                    onClick = { showFavorites = !showFavorites },
                    label = { Text("♥ المفضلة") }
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (showFavorites) "المفضلة" else "المحطات المتاحة",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(filtered.size.toString(), color = Muted)
            }

            Spacer(Modifier.height(6.dp))

            when {
                loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                error != null -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(error ?: "خطأ", color = TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = vm::refresh) { Text("إعادة المحاولة") }
                }

                filtered.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("لا توجد محطات مطابقة حاليًا.", color = Muted)
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { station ->
                        StationRow(
                            station = station,
                            favorite = favorites.contains(station.id),
                            onClick = { selectedStation = station },
                            onFavorite = { vm.toggleFavorite(station) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HardwareCard() {
    val context = LocalContext.current
    val status = remember(context) { HardwareRadioProbe.detect(context) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("الراديو الهوائي", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (status.accessible) "متاح" else "غير متاح",
                    color = if (status.accessible) Accent else Muted
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(status.detail, color = Muted)

            Spacer(Modifier.height(6.dp))
            Text(
                "FM: " + if (status.fm) "نعم" else "لا" +
                    " • AM: " + if (status.am) "نعم" else "لا",
                color = Muted
            )

            if (!status.accessible) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "سيعمل TMFM كراديو إنترنت؛ لا يتم عرض ماسح FM/AM أو قوة إشارة وهمية.",
                    color = Muted
                )
            }
        }
    }
}

@Composable
private fun StationRow(
    station: RadioStation,
    favorite: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFF202633), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = Accent)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        station.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    if (station.isVerified) {
                        Text("✓ موثقة", color = Accent, style = MaterialTheme.typography.labelSmall)
                    }
                }

                val meta = buildList {
                    station.frequencyMhz?.let {
                        add(String.format(Locale.US, "%.2f FM", it))
                    }
                    station.city?.let { add(it) }
                    station.streamType?.let { add(it) }
                }.joinToString(" • ")

                Text(
                    meta.ifBlank { station.countryName },
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            TextButton(onClick = onFavorite) {
                Text(if (favorite) "♥" else "♡", color = Accent)
            }
        }
    }
}

@Composable
private fun StationPlayerDialog(
    station: RadioStation,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onPlay: () -> Unit,
    onRecord: () -> Unit,
    onClose: () -> Unit,
    onSleep: (Int) -> Unit
) {
    var showTimerOptions by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(station.name) },
        text = {
            Column {
                Text(station.countryName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                station.frequencyMhz?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        String.format(Locale.US, "FM %.2f", it),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                station.streamType?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("البث: " + it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (station.isVerified) {
                        "محطة موثقة داخل كتالوج TMFM."
                    } else {
                        "المحطة مكتشفة عبر Radio Browser وتبقى غير موثقة من TMFM."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row {
                Button(onClick = onPlay, enabled = !station.streamUrl.isNullOrBlank()) {
                    Text("▶ تشغيل")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onRecord,
                    enabled = Build.VERSION.SDK_INT >= 29 && !station.streamUrl.isNullOrBlank()
                ) {
                    Text("● تسجيل")
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onFavorite) {
                    Text(if (favorite) "♥ محفوظة" else "♡ حفظ")
                }
                TextButton(onClick = { showTimerOptions = true }) {
                    Text("مؤقت")
                }
            }
        }
    )

    if (showTimerOptions) {
        AlertDialog(
            onDismissRequest = { showTimerOptions = false },
            title = { Text("مؤقت النوم") },
            text = {
                Column {
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                onSleep(minutes)
                                showTimerOptions = false
                            }
                        ) {
                            Text(minutes.toString() + " دقيقة")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimerOptions = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun RecordingsDialog(
    recordings: List<RecordingEntity>,
    onPlay: (RecordingEntity) -> Unit,
    onShare: (RecordingEntity) -> Unit,
    onDelete: (RecordingEntity) -> Unit,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("مكتبة التسجيلات", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onClose) { Text("إغلاق") }
                }

                if (recordings.isEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "لا توجد تسجيلات بعد. افتح محطة ثم اضغط «تسجيل».",
                        color = Muted
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recordings, key = { it.id }) { recording ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF202633)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        recording.stationName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        formatRecordingDate(recording.createdAtMillis) +
                                            " • " +
                                            formatDuration(recording.durationMillis) +
                                            " • " +
                                            formatSize(recording.fileSizeBytes),
                                        color = Muted,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row {
                                        TextButton(onClick = { onPlay(recording) }) {
                                            Text("تشغيل")
                                        }
                                        TextButton(onClick = { onShare(recording) }) {
                                            Text("مشاركة")
                                        }
                                        TextButton(onClick = { onDelete(recording) }) {
                                            Text("حذف")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatRecordingDate(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))

private fun formatDuration(millis: Long): String {
    val seconds = millis.coerceAtLeast(0L) / 1_000L
    val minutes = seconds / 60
    val remaining = seconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, remaining)
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return bytes.toString() + " B"
    if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}
