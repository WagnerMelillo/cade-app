package com.app.cade.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class UserProfile(
    val name: String,
    val phone: String,
    val securityCode: String,
    val visualStatus: String
)

data class DiscoveredContact(
    val id: String, // MAC Address ou UUID
    val name: String,
    val lastRssi: Int = 0,
    val distance: Double = 0.0
)

class CadeRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cade_prefs", Context.MODE_PRIVATE)

    fun isOnboardingComplete(): Boolean {
        return prefs.getBoolean("ONBOARDING_COMPLETE", false)
    }

    fun setOnboardingComplete(complete: Boolean) {
        prefs.edit().putBoolean("ONBOARDING_COMPLETE", complete).apply()
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
        // Remover se já existir para atualizar
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
