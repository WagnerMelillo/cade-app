package com.app.cade.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class UserProfile(
    val name: String,
    val phone: String,
    val securityCode: String,
    val visualStatus: String
)

data class DiscoveredContact(
    val id: String,
    val name: String,
    val lastRssi: Int = 0,
    val distance: Double = 0.0,
    val lat: Double? = null,
    val lon: Double? = null,
    val lastSeen: Long = 0L,
    val visualStatus: String? = null,
    val securityCode: String? = null,
    val uwbAddress: ByteArray? = null,
    val uwbChannel: Int? = null,
    val uwbPreamble: Int? = null,
    val targetId: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DiscoveredContact

        if (id != other.id) return false
        if (name != other.name) return false
        if (lastRssi != other.lastRssi) return false
        if (distance != other.distance) return false
        if (lat != other.lat) return false
        if (lon != other.lon) return false
        if (lastSeen != other.lastSeen) return false
        if (visualStatus != other.visualStatus) return false
        if (securityCode != other.securityCode) return false
        if (uwbAddress != null) {
            if (other.uwbAddress == null) return false
            if (!uwbAddress.contentEquals(other.uwbAddress)) return false
        } else if (other.uwbAddress != null) return false
        if (uwbChannel != other.uwbChannel) return false
        if (uwbPreamble != other.uwbPreamble) return false
        if (targetId != other.targetId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + lastRssi
        result = 31 * result + distance.hashCode()
        result = 31 * result + (lat?.hashCode() ?: 0)
        result = 31 * result + (lon?.hashCode() ?: 0)
        result = 31 * result + lastSeen.hashCode()
        result = 31 * result + (visualStatus?.hashCode() ?: 0)
        result = 31 * result + (securityCode?.hashCode() ?: 0)
        result = 31 * result + (uwbAddress?.contentHashCode() ?: 0)
        result = 31 * result + (uwbChannel ?: 0)
        result = 31 * result + (uwbPreamble ?: 0)
        result = 31 * result + (targetId?.hashCode() ?: 0)
        return result
    }
}

class CadeRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("cade_prefs", Context.MODE_PRIVATE)

    fun isOnboardingComplete(): Boolean = prefs.getBoolean("ONBOARDING_COMPLETE", false)

    fun setOnboardingComplete(complete: Boolean) {
        prefs.edit().putBoolean("ONBOARDING_COMPLETE", complete).apply()
    }

    /**
     * ID estável e curto deste aparelho no radar (8 caracteres hex).
     * Gerado uma única vez e reutilizado. Vai no QR Code e no anúncio BLE.
     */
    fun getOrCreateRadarId(): String {
        var id = prefs.getString("RADAR_ID", null)
        if (id == null) {
            id = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
            prefs.edit().putString("RADAR_ID", id).apply()
        }
        return id
    }

    // ----- Preferência de UWB (ver UWB.docx) -----
    fun isUwbEnabled(): Boolean = prefs.getBoolean("UWB_ENABLED", false)

    fun setUwbEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("UWB_ENABLED", enabled).apply()
    }

    // ----- Preferência de aceitar desconhecidos -----
    fun isAcceptUnknownEnabled(): Boolean = prefs.getBoolean("ACCEPT_UNKNOWN", true)

    fun setAcceptUnknownEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("ACCEPT_UNKNOWN", enabled).apply()
    }

    // ----- Preferência de som de aproximação -----
    fun isSoundEnabled(): Boolean = prefs.getBoolean("SOUND_ENABLED", false)

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("SOUND_ENABLED", enabled).apply()
    }

    fun saveUserProfile(profile: UserProfile) {
        prefs.edit()
            .putString("USER_NAME", profile.name)
            .putString("USER_PHONE", profile.phone)
            .putString("USER_CODE", profile.securityCode)
            .putString("USER_VISUAL", profile.visualStatus)
            .apply()
    }

    fun getUserProfile(): UserProfile? {
        val name = prefs.getString("USER_NAME", null) ?: return null
        return UserProfile(
            name = name,
            phone = prefs.getString("USER_PHONE", "") ?: "",
            securityCode = prefs.getString("USER_CODE", "") ?: "",
            visualStatus = prefs.getString("USER_VISUAL", "") ?: ""
        )
    }

    fun saveContact(contact: DiscoveredContact) {
        val contacts = getSavedContacts().toMutableList()
        contacts.removeAll { it.id == contact.id }
        contacts.add(contact)

        val jsonArray = JSONArray()
        contacts.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("name", it.name)
            if (it.securityCode != null) {
                obj.put("securityCode", it.securityCode)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("SAVED_CONTACTS", jsonArray.toString()).apply()
    }

    fun removeContact(id: String) {
        val contacts = getSavedContacts().toMutableList()
        contacts.removeAll { it.id == id }
        val jsonArray = JSONArray()
        contacts.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("name", it.name)
            if (it.securityCode != null) {
                obj.put("securityCode", it.securityCode)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("SAVED_CONTACTS", jsonArray.toString()).apply()
    }

    /** Apaga toda a lista de rastreados (útil para limpar de uma vez). */
    fun clearAllContacts() {
        prefs.edit().putString("SAVED_CONTACTS", "[]").apply()
    }

    fun getSavedContacts(): List<DiscoveredContact> {
        val jsonStr = prefs.getString("SAVED_CONTACTS", "[]") ?: "[]"
        val list = mutableListOf<DiscoveredContact>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    DiscoveredContact(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        securityCode = if (obj.has("securityCode")) obj.getString("securityCode") else null
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Remove duplicados (mesmo id) e ordena por nome, para a lista nunca
        // aparecer repetida ou bagunçada.
        return list.distinctBy { it.id }.sortedBy { it.name.lowercase() }
    }
}
