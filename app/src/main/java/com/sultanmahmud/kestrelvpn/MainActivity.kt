package com.sultanmahmud.kestrelvpn

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: VpnViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KestrelTheme { App(vm) } }
    }
}

@Composable
fun App(vm: VpnViewModel) {
    val ui by vm.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Home" to Icons.Default.Home, "Servers" to Icons.Default.Place, "Settings" to Icons.Default.Settings)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen(vm, ui) { tab = 1 }
                1 -> ServersScreen(vm, ui)
                else -> SettingsScreen()
            }
        }
    }
}

@Composable
fun HomeScreen(vm: VpnViewModel, ui: UiState, goServers: () -> Unit) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) vm.connect()
    }
    fun toggle() {
        when (ui.status) {
            Status.Connected -> vm.disconnect()
            Status.Connecting -> Unit
            Status.Disconnected -> {
                val intent = VpnService.prepare(ctx)
                if (intent != null) launcher.launch(intent) else vm.connect()
            }
        }
    }

    val selected = ui.servers.firstOrNull { it.id == ui.selectedId }
    val now by produceState(System.currentTimeMillis(), ui.connectedAtMs) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val scheme = MaterialTheme.colorScheme
    val ring by animateColorAsState(
        when (ui.status) {
            Status.Connected -> scheme.primary
            Status.Connecting -> Color(0xFFF5B942)
            Status.Disconnected -> scheme.surfaceVariant
        },
        label = "ring",
    )
    val iconTint = if (ui.status == Status.Disconnected) scheme.onSurfaceVariant else ring

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Kestrel VPN", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = scheme.onBackground)
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier.size(190.dp).clip(CircleShape)
                .background(ring.copy(alpha = 0.18f))
                .border(4.dp, ring, CircleShape)
                .clickable { toggle() },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = iconTint, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    when (ui.status) {
                        Status.Disconnected -> "Tap to connect"
                        Status.Connecting -> "Connecting…"
                        Status.Connected -> "Tap to disconnect"
                    },
                    color = scheme.onBackground,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            when (ui.status) {
                Status.Disconnected -> "Not connected"
                Status.Connecting -> "Connecting…"
                Status.Connected -> "Connected"
            },
            fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = scheme.onBackground,
        )
        ui.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = scheme.error, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(20.dp))

        InfoCard(Modifier.clickable { goServers() }) {
            if (selected == null) {
                Text("No server yet. Tap here to add one.", color = scheme.onSurface)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selected.flag, fontSize = 30.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(selected.name, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                        Text("${selected.city}, ${selected.countryCode.uppercase()}", color = scheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (ui.status == Status.Connected) {
            Spacer(Modifier.height(12.dp))
            InfoCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Time", formatDuration(now - ui.connectedAtMs))
                    Stat("Down", formatSpeed(ui.rxSpeed))
                    Stat("Up", formatSpeed(ui.txSpeed))
                }
                Spacer(Modifier.height(10.dp))
                Text("Data used: ${formatBytes(ui.rxBytes + ui.txBytes)}", color = scheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(12.dp))
        InfoCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Public IP seen by websites", color = scheme.onSurfaceVariant)
                    Text(ui.publicIp ?: "checking…", fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                }
                IconButton(onClick = { vm.refreshIp() }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh IP") }
            }
            if (ui.status == Status.Connected && ui.realIp != null && ui.publicIp != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (ui.realIp != ui.publicIp) "Changed from ${ui.realIp}" else "Same as before the VPN. Something may be wrong.",
                    color = if (ui.realIp != ui.publicIp) scheme.primary else scheme.error,
                )
            }
            ui.publicIpV6?.let {
                Spacer(Modifier.height(6.dp))
                Text("IPv6 address visible: $it", color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ServersScreen(vm: VpnViewModel, ui: UiState) {
    val ctx = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    val scheme = MaterialTheme.colorScheme

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(20.dp))
            Text("Servers", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = scheme.onBackground)
        }
        item {
            InfoCard {
                Text("This phone's public key", fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(vm.devicePublicKey, color = scheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Kestrel public key", vm.devicePublicKey))
                }) { Text("Copy key") }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Give this key to your server (see docs). It is safe to share. The secret half never leaves this phone.",
                    color = scheme.onSurfaceVariant, fontSize = 13.sp,
                )
            }
        }
        item {
            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add server")
            }
        }
        ui.message?.let { msg ->
            item { Text(msg, color = scheme.primary) }
        }
        if (ui.servers.isEmpty()) {
            item { Text("No servers yet. Only real servers you add appear here.", color = scheme.onSurfaceVariant) }
        }
        items(ui.servers, key = { it.id }) { s ->
            InfoCard(Modifier.clickable { vm.select(s.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = s.id == ui.selectedId, onClick = { vm.select(s.id) })
                    Text(s.flag, fontSize = 28.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                        Text("${s.city}, ${s.countryCode.uppercase()}", color = scheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.removeServer(s.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove server")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add server") },
            text = {
                Column {
                    Text("Paste the line your server printed (it starts with KESTREL1).")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = code, onValueChange = { code = it }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.addServerFromCode(code); code = ""; showAdd = false }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = scheme.onBackground)
        SettingBlock("Protocol", "WireGuard (the only protocol in this version).")
        SettingBlock(
            "Kill switch",
            "Android can block all internet if the VPN drops. Open your phone's Settings, find VPN, tap the gear next to Kestrel VPN, then turn on \"Always-on VPN\" and \"Block connections without VPN\". Menu names vary by phone.",
        )
        SettingBlock("DNS", "The DNS server comes from the server code you added. DNS requests go through the tunnel.")
        SettingBlock(
            "Privacy",
            "No ads and no analytics. Your device key and server list are stored encrypted on this phone. The app contacts api.ipify.org (a third party) to show your public IP when you open it or tap refresh. A full privacy policy will be added before any public release.",
        )
        SettingBlock("Terms of service", "Draft. To be written before any public release.")
        SettingBlock("Support", "Personal build. No public support channel yet.")
        SettingBlock("App version", version)
    }
}

@Composable
private fun SettingBlock(title: String, body: String) {
    val scheme = MaterialTheme.colorScheme
    InfoCard {
        Text(title, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(body, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

private fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}

private fun formatSpeed(bytesPerSec: Long): String = formatBytes(bytesPerSec) + "/s"
