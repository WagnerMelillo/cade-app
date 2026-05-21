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

/**
 * Representa um contato descoberto pelo radar OU salvo na lista.
 * - id: o "radarId" estável do dispositivo (8 hex). É o que casa o anúncio BLE
 *   com o contato salvo.
 * - lat/lon: última posição conhecida recebida pelo ar (pode ser null quando
 *   o aparelho do alvo não tem fix de localização).
 */
data class DiscoveredContact(
    val id: String,
    val name: String,
    val lastRssi: Int = 0,
    val distance: Double = 0.0,
    val lat: Double? = null,
    val lon: Double? = null,
    val lastSeen: Long = 0L
)

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
            jsonArray.put(obj)
        }
        prefs.edit().putString("SAVED_CONTACTS", jsonArray.toString()).apply()
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
                        name = obj.getString("name")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
