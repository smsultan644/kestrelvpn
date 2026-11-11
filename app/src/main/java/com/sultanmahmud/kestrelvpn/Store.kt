package com.sultanmahmud.kestrelvpn

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One real, deployed VPN server. Only servers the user imported are ever shown. */
data class ServerProfile(
    val id: String,
    val name: String,
    val countryCode: String,
    val city: String,
    val endpoint: String,        // host:port
    val serverPublicKey: String,
    val address: String,         // this device's tunnel address, e.g. 10.8.0.2/32
    val dns: String,
) {
    val flag: String
        get() = countryCode.uppercase().takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } }
            ?.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }
            ?.joinToString("") ?: "🌐"
}

/**
 * Parses the one-line code printed by server/add-peer.sh:
 * KESTREL1|name|CC|city|host:port|serverPublicKey|clientAddress|dns
 */
fun parseImportCode(code: String): ServerProfile {
    val p = code.trim().split("|")
    require(p.size == 8 && p[0] == "KESTREL1") { "This is not a valid server code." }
    require(p[4].contains(":")) { "The server address is missing its port." }
    Key.fromBase64(p[5]) // throws if the server key is malformed
    return ServerProfile(UUID.randomUUID().toString(), p[1], p[2], p[3], p[4], p[5], p[6], p[7])
}

/** Encrypted on-device storage. The private key never leaves this phone. */
class Store(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "kestrel_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    val privateKey: String
        get() {
            prefs.getString("priv", null)?.let { return it }
            val fresh = KeyPair().privateKey.toBase64()
            prefs.edit().putString("priv", fresh).apply()
            return fresh
        }

    val publicKey: String
        get() = KeyPair(Key.fromBase64(privateKey)).publicKey.toBase64()

    var selectedId: String?
        get() = prefs.getString("selected", null)
        set(v) = prefs.edit().putString("selected", v).apply()

    fun servers(): List<ServerProfile> {
        val arr = JSONArray(prefs.getString("servers", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            ServerProfile(
                o.getString("id"), o.getString("name"), o.getString("cc"), o.getString("city"),
                o.getString("endpoint"), o.getString("pub"), o.getString("addr"), o.getString("dns"),
            )
        }
    }

    fun saveServers(list: List<ServerProfile>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("name", it.name).put("cc", it.countryCode)
                    .put("city", it.city).put("endpoint", it.endpoint).put("pub", it.serverPublicKey)
                    .put("addr", it.address).put("dns", it.dns)
            )
        }
        prefs.edit().putString("servers", arr.toString()).apply()
    }
}
