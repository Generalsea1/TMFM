package com.generalsea1.debaradio

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.Locale
import java.util.concurrent.TimeUnit

private val DarkBg = Color(0xFF0B0D12)
private val Panel = Color(0xFF141821)
private val TextPrimary = Color(0xFFF5F7FA)
private val Muted = Color(0xFF9DA5B4)
private val Accent = Color(0xFFF4B73F)

private val DebaColors = darkColorScheme(
    primary = Accent,
    secondary = Accent,
    background = DarkBg,
    surface = Panel,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener(
            {
                controller = runCatching { controllerFuture?.get() }.getOrNull()
            },
            ContextCompat.getMainExecutor(this)
        )

        setContent {
            MaterialTheme(colorScheme = DebaColors) {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                    DebaRadioApp(
                        controllerProvider = { controller },
                        onStationPlayed = ::play
                    )
                }
            }
        }
    }

    private fun play(station: RadioStation) {
        val c = controller ?: return
        val url = station.streamUrl ?: return

        val item = MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setArtist(station.countryName)
                    .build()
            )
            .build()

        c.setMediaItem(item)
        c.prepare()
        c.play()
    }

    override fun onDestroy() {
        controller?.release()
        controller = null
        controllerFuture = null
        super.onDestroy()
    }
}

@Composable
private fun DebaRadioApp(
    controllerProvider: () -> MediaController?,
    onStationPlayed: (RadioStation) -> Unit,
    vm: MainViewModel = viewModel()
) {
    val stations by vm.stations.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    var search by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf("الكل") }
    var selectedStation by remember { mutableStateOf<RadioStation?>(null) }
    var showFavorites by remember { mutableStateOf(false) }
    var sleepMinutes by remember { mutableIntStateOf(0) }

    val countries = remember(stations) {
        listOf("الكل") + stations.map { it.countryName }.distinct()
    }

    val filtered = stations.filter { station ->
        val haystack = listOf(
            station.name,
            station.countryName,
            station.city.orEmpty(),
            station.frequencyMhz?.toString().orEmpty(),
            station.language.orEmpty(),
            station.category.orEmpty()
        ).joinToString(" ").lowercase(Locale.getDefault())

        val query = search.trim().lowercase(Locale.getDefault())
        val matchesSearch = query.isBlank() || haystack.contains(query)
        val matchesCountry = selectedCountry == "الكل" || station.countryName == selectedCountry
        val matchesFavorite = !showFavorites || favorites.contains(station.id)

        matchesSearch && matchesCountry && matchesFavorite
    }

    LaunchedEffect(sleepMinutes) {
        if (sleepMinutes > 0) {
            kotlinx.coroutines.delay(TimeUnit.MINUTES.toMillis(sleepMinutes.toLong()))
            controllerProvider()?.stop()
            sleepMinutes = 0
        }
    }

    selectedStation?.let { station ->
        StationPlayerDialog(
            station = station,
            favorite = favorites.contains(station.id),
            onFavorite = { vm.toggleFavorite(station) },
            onPlay = {
                vm.saveLastStation(station)
                onStationPlayed(station)
                selectedStation = null
            },
            onClose = { selectedStation = null },
            onSleep = { minutes -> sleepMinutes = minutes }
        )
    }

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
                Text("DEBA RADIO", style = MaterialTheme.typography.headlineSmall)
                Text("راديو مصر والعالم", color = Muted)
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(Accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("FM", color = DarkBg, style = MaterialTheme.typography.labelLarge)
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
            label = { Text("بحث: محطة، بلد، تردد، نوع") }
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
            if (!status.accessible) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "سيعمل DEBA Radio كراديو إنترنت؛ لا يتم عرض ماسح FM وهمي.",
                    color = Muted
                )
            } else {
                Text(
                    "FM: " + if (status.fm) "نعم" else "لا" +
                        " • AM: " + if (status.am) "نعم" else "لا",
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
                Text(
                    station.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val meta = buildList {
                    station.frequencyMhz?.let {
                        add(String.format(Locale.US, "%.2f FM", it))
                    }
                    station.city?.let { add(it) }
                    station.category?.let { add(it) }
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
                Spacer(Modifier.height(8.dp))
                station.frequencyMhz?.let {
                    Text(
                        String.format(Locale.US, "FM %.2f", it),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                station.category?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(8.dp))
                Text(
                    "المحطة ظاهرة لأنها اجتازت حالة التحقق الحالية في دليل DEBA.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onPlay, enabled = station.streamUrl != null) {
                Text("▶ تشغيل")
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
                                onClose()
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
