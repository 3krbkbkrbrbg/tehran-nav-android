package com.hellboy.tehrannav.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hellboy.tehrannav.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TehranNavApp(activity: MainActivity) {
    val vm: TehranNavViewModel = viewModel()
    remember { vm.host = ActivityHost(activity) }
    val state = vm.state
    val night = activity.nightOn()

    MaterialTheme(colorScheme = if (night) darkColorScheme() else lightColorScheme()) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { activity.createMap() },
                modifier = Modifier.fillMaxSize()
            )

            // top search bar
            Column(Modifier.fillMaxWidth().imePadding()) {
                SearchBar(vm, activity)
            }

            // right side buttons
            IconButton(
                onClick = { activity.centerOnMyLocation() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 130.dp, end = 12.dp)
                    .size(48.dp)
                    .background(Color.White, CircleShape)
            ) { Icon(Icons.Default.MyLocation, "موقعیت من", tint = Color(0xFF333333)) }
            IconButton(
                onClick = { activity.toggleNight() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 186.dp, end = 12.dp)
                    .size(48.dp)
                    .background(Color.White, CircleShape)
            ) { Icon(Icons.Default.Nightlight, "حالت شب", tint = Color(0xFF333333)) }

            // AI input bar (bottom when not navigating)
            if (!activity.navActive) {
                AiBar(
                    vm = vm,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(10.dp)
                )
            } else {
                NavCard(
                    vm, activity,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                )
            }

            // destination info card above AI bar
            if (state.destName.isNotEmpty() && !activity.navActive) {
                DestInfoCard(
                    vm, state,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 92.dp, start = 12.dp, end = 12.dp)
                )
            }

            // loading indicator
            if (state.searching || state.aiLoading) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.92f),
                    shadowElevation = 4.dp
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (state.aiLoading) "در حال اتصال به هوش مصنوعی..." else "در حال جستجو...",
                            color = Color(0xFF333333)
                        )
                    }
                }
            }

            if (state.showSettings) SettingsDialog(vm, activity)
        }
    }
}

@Composable
private fun SearchBar(vm: TehranNavViewModel, activity: MainActivity) {
    val state = vm.state
    Surface(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 6.dp,
        color = Color.White
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = Color.Gray)
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { vm.onSearchType(it) },
                placeholder = { Text("کجا می‌روید؟ جستجو کنید...") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )
            IconButton(onClick = { vm.search(state.searchQuery) }) {
                Icon(Icons.Default.Place, "جستجو", tint = Color(0xFFFF6F00))
            }
            IconButton(onClick = { vm.setShowSettings(true) }) {
                Icon(Icons.Default.Settings, "تنظیمات", tint = Color.Gray)
            }
        }
    }

    if (state.searchResults.isNotEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(300.dp),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp,
            color = Color.White
        ) {
            LazyColumn {
                items(state.searchResults) { place ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { vm.pickDestination(place.name, place.lat, place.lon) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, null, tint = Color(0xFFFF6F00))
                        Spacer(Modifier.width(10.dp))
                        Text(place.name, fontWeight = FontWeight.Medium, color = Color(0xFF222222))
                    }
                }
            }
        }
    }
}

@Composable
private fun AiBar(vm: TehranNavViewModel, modifier: Modifier = Modifier) {
    val state = vm.state
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 8.dp,
        color = Color.White
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.SmartToy, null, tint = Color(0xFF7B1FA2))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = state.aiQuery,
                onValueChange = { vm.onAiType(it) },
                placeholder = { Text("از AI بپرسید: «برو میدان آزادی بدون ترافیک»...") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )
            IconButton(onClick = { vm.sendAiCommand(state.aiQuery) }) {
                Icon(
                    Icons.Default.Navigation,
                    "ارسال به AI",
                    tint = Color(0xFF7B1FA2)
                )
            }
            if (!vm.settings.geminiApiKey.isNullOrBlank()) {
                Text("AI متصل", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
            } else {
                Text("AI: خاموش", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun DestInfoCard(vm: TehranNavViewModel, state: UiState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Place, null, tint = Color(0xFFFF6F00))
                Spacer(Modifier.width(6.dp))
                Text(
                    "مقصد: ${state.destName}",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF222222)
                )
                IconButton(onClick = { vm.clearRouteAndDest() }) {
                    Icon(Icons.Default.Close, "پاک کردن", tint = Color.Gray)
                }
            }
            if (state.aiLoading) {
                Text("در حال دریافت راهنمای هوش مصنوعی...", style = MaterialTheme.typography.bodySmall, color = Color(0xFF7B1FA2))
            }
            if (state.distance.isNotEmpty()) {
                Text(
                    "فاصله: ${state.distance} • ${state.time}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2979FF)
                )
            }
            if (state.aiSummary.isNotEmpty()) {
                Text(
                    "🤖 ${state.aiSummary}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF222222),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (state.aiInstructions.isNotEmpty()) {
                Column(Modifier.padding(top = 4.dp)) {
                    state.aiInstructions.take(5).forEach { ins ->
                        Text("• $ins", style = MaterialTheme.typography.bodySmall, color = Color(0xFF555555))
                    }
                }
            }
            if (state.guidance.isNotEmpty() && state.searching.not()) {
                Text(
                    state.guidance,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF777777),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Button(
                onClick = { vm.beginNav() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2979FF)),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Icon(Icons.Default.Navigation, null)
                Spacer(Modifier.width(6.dp))
                Text("شروع ناوبری")
            }
        }
    }
}

@Composable
private fun NavCard(vm: TehranNavViewModel, activity: MainActivity, modifier: Modifier = Modifier) {
    val state = vm.state
    val night = activity.nightOn()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (night) Color(0xFF1E1E1E) else Color.White
        ),
        elevation = CardDefaults.cardElevation(10.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🧭 ناوبری فعال",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    color = if (night) Color.White else Color.Black
                )
                IconButton(onClick = { vm.endNav(); activity.clearRoute() }) {
                    Icon(Icons.Default.Stop, "توقف ناوبری", tint = Color.Red)
                }
            }
            Text(
                state.guidance,
                style = MaterialTheme.typography.titleMedium,
                color = if (night) Color.White else Color.Black,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "باقی‌مانده: ${state.distance}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF2979FF)
            )
        }
    }
    // scroll auto-updates guidance via ticker in host
    LaunchedEffect(activity.navActive) {
        if (activity.navActive) {
            while (true) {
                activity.navTick()
                kotlinx.coroutines.delay(2000)
            }
        }
    }
}

@Composable
private fun SettingsDialog(vm: TehranNavViewModel, activity: MainActivity) {
    val s = vm.settings
    var modelMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { vm.setShowSettings(false) },
        title = { Text("تنظیمات ⚙️") },
        text = {
            Column {
                Text("اتصال به هوش مصنوعی Gemini", fontWeight = FontWeight.Bold)
                Text(
                    "با API Key شخصی. از https://aistudio.google.com/apikey دریافت کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                OutlinedTextField(
                    value = s.geminiApiKey,
                    onValueChange = { s.geminiApiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("AIza...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Spacer(Modifier.height(12.dp))
                Box {
                    OutlinedTextField(
                        value = s.aiModel,
                        onValueChange = {},
                        label = { Text("مدل AI") },
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().clickable { modelMenu = true },
                        trailingIcon = {
                            IconButton(onClick = { modelMenu = true }) {
                                Icon(Icons.Default.Search, null)
                            }
                        }
                    )
                    DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                        listOf("gemini-2.0-flash", "gemini-2.5-flash-preview", "gemini-1.5-flash", "gemini-2.0-flash-thinking").forEach { m ->
                            DropdownMenuItem(text = { Text(m) }, onClick = { s.aiModel = m; modelMenu = false })
                        }
                    }
                }
                if (activity.hasLocation()) {
                    Text(
                        "GPS: متصل ✓",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    TextButton(onClick = { activity.ensurePermissions() }) {
                        Text("مجوز موقعیت مکانی بدهید")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.setShowSettings(false) }) { Text("ذخیره و بستن") }
        }
    )
}

/** Bridges UI events to MainActivity map/engine operations. */
private class ActivityHost(private val activity: MainActivity) : TehranNavViewModel.MapHost {
    override fun currentLocation() = activity.currentLocation()
    override fun drawRoute(route: com.hellboy.tehrannav.nav.Route) = activity.drawRoute(route)
    override fun zoomToRoute(route: com.hellboy.tehrannav.nav.Route) = activity.zoomToRoute(route)
    override fun placeDestMarker(lat: Double, lon: Double, name: String) = activity.placeDestMarker(lat, lon, name)
    override fun clearRoute() = activity.clearRoute()
    override fun goTo(lat: Double, lon: Double) = activity.goTo(lat, lon)
    override fun startNav() = activity.startNav()
    override fun stopNav() = activity.stopNav()
    override fun speak(text: String) = activity.speak(text)
    override fun hasLocation() = activity.hasLocation()
    override fun ensurePermissions() = activity.ensurePermissions()
}