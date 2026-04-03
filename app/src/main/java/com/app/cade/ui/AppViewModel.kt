package com.app.cade.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.cade.data.CadeRepository
import com.app.cade.scanner.ScannerManager
import com.app.cade.data.DiscoveredContact
import com.app.cade.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.provider.ContactsContract
import android.annotation.SuppressLint

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CadeRepository(application)
    val scannerManager = ScannerManager(application)

    private val _userProfile = MutableStateFlow<UserProfile?>(repository.getUserProfile())
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _savedContacts = MutableStateFlow<List<DiscoveredContact>>(repository.getSavedContacts())
    val savedContacts: StateFlow<List<DiscoveredContact>> = _savedContacts.asStateFlow()

    // Controla se a pessoa está transmitindo seu sinal
    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising = _isAdvertising.asStateFlow()

    // Controla se a pessoa está procurando
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _isOnboardingComplete = MutableStateFlow(repository.isOnboardingComplete())
    val isOnboardingComplete = _isOnboardingComplete.asStateFlow()

    fun completeOnboarding() {
        repository.setOnboardingComplete(true)
        _isOnboardingComplete.value = true
    }

    @SuppressLint("Range")
    fun fetchPhoneContacts(): List<DiscoveredContact> {
        val list = mutableListOf<DiscoveredContact>()
        val resolver = getApplication<Application>().contentResolver
        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null, null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                val phone = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                // Retornando DiscoveredContact com id sendo o phone como provisório
                list.add(DiscoveredContact(id = phone.replace(Regex("[^0-9+]"), ""), name = name))
            }
        }
        // Deduplicate
        return list.distinctBy { it.id }
    }

    fun saveProfile(name: String, phone: String, code: String, visual: String) {
        val profile = UserProfile(name, phone, code, visual)
        repository.saveUserProfile(profile)
        _userProfile.value = profile
    }

    fun saveContact(contact: DiscoveredContact) {
        repository.saveContact(contact)
        _savedContacts.value = repository.getSavedContacts()
    }

    // Ações de Bluetooth via Dashboard e Connections

    fun toggleAdvertising(enabled: Boolean) {
        _isAdvertising.value = enabled
        if (enabled) {
            val me = _userProfile.value?.name ?: "Anon" 
            scannerManager.startAdvertising(me)
        } else {
            scannerManager.stopAdvertising()
        }
    }

    fun toggleScanning(enabled: Boolean) {
        _isScanning.value = enabled
        if (enabled) {
            scannerManager.startScanning()
        } else {
            scannerManager.stopScanning()
        }
    }

    override fun onCleared() {
        super.onCleared()
        scannerManager.stopAdvertising()
        scannerManager.stopScanning()
    }
}
