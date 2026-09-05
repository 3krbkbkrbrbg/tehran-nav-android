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
import androidx.compose.material3.DrawerValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.ModalNavigationDrawer
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
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hellboy.tehrannav.MainActivity
import com.hellboy.tehrannav.nav.Route
import kotlinx.coroutines.delay

// ============================================================
//  PART 1: root composable + drawer + map + overlays
// ============================================================

@Composable
fun TehranNavApp(activity: MainActivity) {
    val vm: TehranNavViewModel = viewModel()
    remember { vm.host = ActivityHost(activity) }
    val state = vm.state
    // observable night mode from activity
    val night = activity.nightModeState

    // coordinate readout from map taps
    val coords = activity.coordReadout

    MaterialTheme(colorScheme = if (night) darkColorScheme() else lightColorScheme()) {
        ModalNavigationDrawer(
            drawerState = activity.drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    HamburgerMenu(vm, activity)
                }
            }
        ) {
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { activity.createMap() },
                    modifier = Modifier.fillMaxSize()
                )

                // top bar: hamburger + search
                Column(Modifier.fillMaxWidth().imePadding()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { activity.openDrawer() }) {
                            Icon(
                                Icons.Default.Menu, "منو",
                                tint = Color(0xFF222222),
                                modifier = Modifier
                                    .background(Color.White, CircleShape)
                                    .padding(6.dp)
                            )
                        }
                        SearchBar(vm, activity, Modifier.weight(1f))
                    }
                }

                // right side buttons
                IconButton(
                    onClick = { activity.centerOnMyLocation() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 64.dp, end = 12.dp)
                        .size(48.dp)
                        .background(Color.White, CircleShape)
                ) { Icon(Icons.Default.MyLocation, "موقعیت من", tint = Color(0xFF333333)) }
                IconButton(
                    onClick = { activity.toggleNight() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 120.dp, end = 12.dp)
                        .size(48.dp)
                        .background(Color.White, CircleShape)
                ) { Icon(Icons.Default.Nightlight, "حالت شب", tint = Color(0xFF333333)) }

                // tap coordinate banner
                if (coords.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 76.dp, start = 12.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.75f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(coords, color = Color.White, style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { activity.copyReadout() }) {
                                Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

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
                if (state.showOffline) OfflineDialog(vm, activity)
                if (state.showCoords) CoordsDialog(vm, activity)
            }
        }
    }
}// ============================================================
//  PART 2: hamburger menu
// ============================================================

@Composable
private fun HamburgerMenu(vm: TehranNavViewModel, activity: MainActivity) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            "مسیر یاب تهران",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        MenuRow(Icons.Default.Download, "دانلود نقشه آفلاین") { vm.setShowOffline(true); activity.closeDrawer() }
        MenuRow(Icons.Default.Place, "مختصات موقعیت") { vm.setShowCoords(true); activity.closeDrawer() }
        MenuRow(Icons.Default.Navigation, "مرکز روی GPS") { activity.centerOnMyLocation(); activity.closeDrawer() }
        MenuRow(Icons.Default.Nightlight, "حالت شب") { activity.toggleNight(); activity.closeDrawer() }
        MenuRow(Icons.Default.Settings, "تنظیمات AI") { vm.setShowSettings(true); activity.closeDrawer() }
        if (vm.settings.geminiApiKey.isNullOrBlank()) {
            Text(
                "💡 بدون هوش مصنوعی هم می‌توانید مسیریابی کنید — فقط نام مقصد را بنویسید.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 12.dp)
            )
        } else {
            Text(
                "🤖 هوش مصنوعی متصل است",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF2E7D32),
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(label, fontWeight = FontWeight.Medium)
    }
}

// ============================================================
//  PART 3: search bar
// ============================================================

@Composable
private fun SearchBar(vm: TehranNavViewModel, activity: MainActivity, modifier: Modifier = Modifier) {
    val state = vm.state
    Surface(
        modifier = modifier.fillMaxWidth().padding(end = 8.dp),
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
                placeholder = { Text("کجا می‌روید؟...") },
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
                        Icon(Icons.Default.Place, null, tint = Color(0xFFFF6F00))
                        Spacer(Modifier.width(10.dp))
                        Text(place.name, fontWeight = FontWeight.Medium, color = Color(0xFF222222))
                    }
                }
            }
        }
    }
}

// ============================================================
//  PART 4: AI bar + dest card + nav card
// ============================================================

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
                placeholder = { Text("از AI بپرسید: «برو میدان آزادی»...") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )
            IconButton(onClick = { vm.sendAiCommand(state.aiQuery) }) {
                Icon(Icons.Default.Navigation, "ارسال", tint = Color(0xFF7B1FA2))
            }
            Text(
                if (vm.settings.geminiApiKey.isNullOrBlank()) "AI: خاموش" else "AI: متصل",
                style = MaterialTheme.typography.labelSmall,
                color = if (vm.settings.geminiApiKey.isNullOrBlank()) Color.Gray else Color(0xFF2E7D32)
            )
        }
    }
}// ============================================================
//  PART 5: DestInfoCard + NavCard + dialogs
// ============================================================

@Composable
private fun DestInfoCard(vm: TehranNavViewModel, state: UiState, activity: MainActivity, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("مقصد: ${state.destName}", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (state.distance.isNotEmpty() && state.time.isNotEmpty()) {
                Text(
                    "${state.distance} • ${state.time}",
                    color = Color(0xFF1565C0),
                    fontWeight = FontWeight.Medium
                )
            }
            if (state.guidance.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(state.guidance, style = MaterialTheme.typography.bodySmall, color = Color(0xFF333333))
            }
            Spacer(Modifier.height(10.dp))
            Row {
                Button(
                    onClick = { vm.beginNav() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                ) { Icon(Icons.Default.Navigation, null); Spacer(Modifier.width(6.dp)); Text("شروع ناوبری") }
                Spacer(Modifier.width(8.dp))
                OutlinedButtonGm(activity, state)
            }
        }
    }
}

@Composable
private fun OutlinedButtonGm(activity: MainActivity, state: UiState) {
    androidx.compose.material3.OutlinedButton(onClick = {
        if (state.destLat != 0.0) {
            activity.openInGoogleMaps(state.destLat, state.destLon, state.destName)
        }
    }) {
        Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(6.dp)); Text("گوگل مپ")
    }
}

@Composable
private fun NavCard(vm: TehranNavViewModel, activity: MainActivity, modifier: Modifier = Modifier) {
    val distToDest = activity.distToDest
    LaunchedEffect(activity.navActive) {
        while (activity.navActive) {
            activity.navTick()
            delay(2000)
        }
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF263238))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                activity.navGuidance.ifEmpty { "به سمت مقصد حرکت کنید" },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "باقی‌مانده: ${if (distToDest > 0) "%.1f کیلومتر".format(distToDest / 1000) else "—"} • ${activity.uiDestName}",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { activity.speakNext() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                ) { Text("🔊 دوباره بگو") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { activity.stopNav() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(4.dp)); Text("توقف") }
            }
        }
    }
}

// ============================================================
//  PART 6: settings / offline / coords dialogs
// ============================================================

@Composable
private fun SettingsDialog(vm: TehranNavViewModel, activity: MainActivity) {
    val key = remember { mutableStateOf(vm.settings.geminiApiKey) }
    AlertDialog(
        onDismissRequest = { vm.setShowSettings(false) },
        title = { Text("تنظیمات هوش مصنوعی") },
        text = {
            Column {
                Text("کلید API جمینی (از aistudio.google.com/app/apikey بگیرید):")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = key.value,
                    onValueChange = { key.value = it },
                    singleLine = true,
                    placeholder = { Text("AIza...") }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "بدون کلید هم مسیریابی بدون AI کار می‌کند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.saveApiKey(key.value)
                vm.setShowSettings(false)
            }) { Text("ذخیره") }
        },
        dismissButton = {
            TextButton(onClick = { vm.setShowSettings(false) }) { Text("بستن") }
        }
    )
}

@Composable
private fun OfflineDialog(vm: TehranNavViewModel, activity: MainActivity) {
    AlertDialog(
        onDismissRequest = { vm.setShowOffline(false) },
        title = { Text("دانلود نقشه آفلاین") },
        text = {
            Column {
                Text("محدوده مورد نظر را انتخاب کنید:")
                Spacer(Modifier.height(10.dp))
                OfflineRow("همه ایران", "کل کشور (پیش‌فرض همه مناطق)", vm, activity)
                OfflineRow("تهران و حومه", "محدوده شهر تهران", vm, activity)
                if (activity.downloadProgress.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(activity.downloadProgress, color = Color(0xFF1565C0))
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.setShowOffline(false) }) { Text("بستن") } }
    )
}

@Composable
private fun OfflineRow(label: String, sub: String, vm: TehranNavViewModel, activity: MainActivity) {
    Row(
        Modifier.fillMaxWidth().clickable {
            activity.downloadOfflineFor(
                when (label) {
                    "همه ایران" -> "iran"
                    else -> "tehran"
                },
                object : com.hellboy.tehrannav.OfflineMap.Listener {
                    override fun onProgress(done: Int, total: Int) {
                        activity.downloadProgress = if (total > 0) "$done از $total تایل..." else "شروع دانلود..."
                    }
                    override fun onFinished(success: Boolean, count: Int) {
                        activity.downloadProgress =
                            if (success) "دانلود کامل شد ✓ ($count تایل)" else "دانلود ناقص شد"
                    }
                }
            )
        }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, fontWeight = FontWeight.Medium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
private fun CoordsDialog(vm: TehranNavViewModel, activity: MainActivity) {
    val loc = remember { mutableStateOf(activity.lastLocation?.let { "%.6f".format(it.latitude) + " ، " + "%.6f".format(it.longitude) } ?: "موقعیت هنوز مشخص نیست") }
    AlertDialog(
        onDismissRequest = { vm.setShowCoords(false) },
        title = { Text("مختصات موقعیت من") },
        text = {
            Column {
                Text(loc.value)
                Spacer(Modifier.height(8.dp))
                Text(
                    "برای مختصات هر نقطه، روی نقشه ضربه بزنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                activity.copyLocation()
                vm.setShowCoords(false)
            }) { Text("کپی") }
        },
        dismissButton = { TextButton(onClick = { vm.setShowCoords(false) }) { Text("بستن") } }
    )
}
private class ActivityHost(private val activity: MainActivity) : TehranNavViewModel.MapHost {
    override fun currentLocation() = activity.currentLocation()
    override fun drawRoute(route: Route) = activity.drawRoute(route)
    override fun zoomToRoute(route: Route) = activity.zoomToRoute(route)
    override fun placeDestMarker(lat: Double, lon: Double, name: String) =
        activity.placeDestMarker(lat, lon, name)
    override fun clearRoute() = activity.clearRoute()
    override fun goTo(lat: Double, lon: Double) = activity.goTo(lat, lon)
    override fun startNav() = activity.startNav()
    override fun stopNav() = activity.stopNav()
    override fun speak(text: String) = activity.speak(text)
    override fun hasLocation() = activity.hasLocation()
    override fun ensurePermissions() = activity.ensurePermissions()
}
