package com.sultanmahmud.kestrelvpn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URL
import javax.net.ssl.HttpsURLConnection

enum class Status { Disconnected, Connecting, Connected }

data class UiState(
    val servers: List<ServerProfile> = emptyList(),
    val selectedId: String? = null,
    val status: Status = Status.Disconnected,
    val connectedAtMs: Long = 0,
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val rxSpeed: Long = 0,
    val txSpeed: Long = 0,
    val publicIp: String? = null,   // address websites currently see
    val realIp: String? = null,     // address seen before the VPN was turned on
    val publicIpV6: String? = null,
    val message: String? = null,
)

class VpnViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val backend = GoBackend(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    private var statsJob: Job? = null

    private val tunnel = object : Tunnel {
        override fun getName() = "kestrel"
        override fun onStateChange(newState: Tunnel.State) {
            // If the system turns the tunnel off (e.g. the user revokes VPN access), reflect it.
            if (newState == Tunnel.State.DOWN && _state.value.status == Status.Connected) {
                statsJob?.cancel()
                _state.update { it.copy(status = Status.Disconnected) }
            }
        }
    }

    val devicePublicKey: String get() = store.publicKey

    init {
        val list = store.servers()
        val sel = store.selectedId?.takeIf { id -> list.any { it.id == id } } ?: list.firstOrNull()?.id
        _state.update { it.copy(servers = list, selectedId = sel) }
        refreshIp()
    }

    fun addServerFromCode(code: String) {
        try {
            val s = parseImportCode(code)
            val list = _state.value.servers + s
            store.saveServers(list)
            store.selectedId = s.id
            _state.update { it.copy(servers = list, selectedId = s.id, message = "Server added: ${s.name}") }
        } catch (e: Exception) {
            _state.update { it.copy(message = e.message ?: "Could not read that server code.") }
        }
    }

    fun removeServer(id: String) {
        val list = _state.value.servers.filterNot { it.id == id }
        store.saveServers(list)
        val sel = if (_state.value.selectedId == id) list.firstOrNull()?.id else _state.value.selectedId
        store.selectedId = sel
        _state.update { it.copy(servers = list, selectedId = sel) }
    }

    fun select(id: String) {
        store.selectedId = id
        _state.update { it.copy(selectedId = id) }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun connect() {
        val server = _state.value.servers.firstOrNull { it.id == _state.value.selectedId }
        if (server == null) {
            _state.update { it.copy(message = "Add a server first (Servers tab).") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(status = Status.Connecting, message = null) }
            try {
                val iface = Interface.Builder()
                    .parseAddresses(server.address)
                    .parseDnsServers(server.dns)
                    .setKeyPair(KeyPair(Key.fromBase64(store.privateKey)))
                    .build()
                // ::/0 is routed into the tunnel too, so IPv6 traffic cannot bypass the VPN.
                val peer = Peer.Builder()
                    .parsePublicKey(server.serverPublicKey)
                    .parseEndpoint(server.endpoint)
                    .parseAllowedIPs("0.0.0.0/0, ::/0")
                    .setPersistentKeepalive(25)
                    .build()
                val config = Config.Builder().setInterface(iface).addPeer(peer).build()
                backend.setState(tunnel, Tunnel.State.UP, config)
                _state.update { it.copy(status = Status.Connected, connectedAtMs = System.currentTimeMillis()) }
                startStats()
                delay(1500)
                refreshIp()
            } catch (e: Exception) {
                _state.update { it.copy(status = Status.Disconnected, message = "Could not connect: ${e.message}") }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                backend.setState(tunnel, Tunnel.State.DOWN, null)
            } catch (e: Exception) {
                _state.update { it.copy(message = "Disconnect problem: ${e.message}") }
            }
            statsJob?.cancel()
            _state.update { it.copy(status = Status.Disconnected, rxSpeed = 0, txSpeed = 0, rxBytes = 0, txBytes = 0) }
            delay(800)
            refreshIp()
        }
    }

    private fun startStats() {
        statsJob?.cancel()
        statsJob = viewModelScope.launch(Dispatchers.IO) {
            var lastRx = 0L
            var lastTx = 0L
            while (isActive) {
                delay(1000)
                try {
                    val st = backend.getStatistics(tunnel)
                    val rx = st.totalRx()
                    val tx = st.totalTx()
                    _state.update { it.copy(rxBytes = rx, txBytes = tx, rxSpeed = (rx - lastRx).coerceAtLeast(0), txSpeed = (tx - lastTx).coerceAtLeast(0)) }
                    lastRx = rx
                    lastTx = tx
                } catch (_: Exception) {
                }
            }
        }
    }

    /** Asks a third-party service (api.ipify.org) which public address websites see. */
    fun refreshIp() {
        viewModelScope.launch(Dispatchers.IO) {
            val v4 = fetch("https://api.ipify.org")
            val v6 = fetch("https://api64.ipify.org")?.takeIf { it.contains(":") }
            _state.update {
                it.copy(
                    publicIp = v4,
                    publicIpV6 = v6,
                    realIp = if (it.status == Status.Disconnected) v4 else it.realIp,
                )
            }
        }
    }

    private fun fetch(url: String): String? = try {
        val c = URL(url).openConnection() as HttpsURLConnection
        c.connectTimeout = 5000
        c.readTimeout = 5000
        c.inputStream.bufferedReader().use { it.readText().trim() }.takeIf { it.length < 64 }
    } catch (_: Exception) {
        null
    }
}
