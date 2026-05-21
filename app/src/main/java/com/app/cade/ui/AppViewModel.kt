package com.app.cade.ui

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.cade.data.CadeRepository
import com.app.cade.data.DiscoveredContact
import com.app.cade.data.UserProfile
import com.app.cade.scanner.ScannerManager
import com.app.cade.scanner.UwbCapabilityHelper
import com.app.cade.sensors.SensorFusionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Camada que conseguimos usar para apontar o alvo, da melhor para a pior. */
enum class TrackingMode { NONE, HOTCOLD, GPS, UWB }

/** Resultado calculado para o alvo selecionado, consumido pela tela do radar. */
data class TargetState(
    val found: Boolean = false,
    val mode: TrackingMode = TrackingMode.NONE,
    val distanceMeters: Double? = null,
    val arrowAngle: Float? = null, // graus para girar a seta; null = sem direção confiável
    val rssi: Int? = null,
    val hint: String = "Selecione um contato para rastrear"
)

/**
 * Combina POSIÇÃO do alvo + ORIENTAÇÃO do meu aparelho para decidir distância,
 * direção e qual camada está em uso. Função pura -> fácil de raciocinar e testar.
 */
fun computeTargetState(
    target: DiscoveredContact?,
    discovered: List<DiscoveredContact>,
    myLat: Double?,
    myLon: Double?,
    trueHeading: Float
): TargetState {
    if (target == null) {
        return TargetState(hint = "Selecione um contato para rastrear")
    }
    val live = discovered.firstOrNull { it.id == target.id }
        ?: return TargetState(
            found = false,
            mode = TrackingMode.NONE,
            hint = "Sinal de ${target.name} ainda não detectado por perto"
        )

    // Melhor caso disponível agora: ambos com coordenadas -> direção real.
    if (myLat != null && myLon != null && live.lat != null && live.lon != null) {
        val results = FloatArray(2)
        Location.distanceBetween(myLat, myLon, live.lat, live.lon, results)
        val geoDistance = results[0].toDouble()
        val bearingTrueNorth = results[1] // -180..180 a partir do norte verdadeiro
        val arrow = ((bearingTrueNorth - trueHeading) + 360f) % 360f
        return TargetState(
            found = true,
            mode = TrackingMode.GPS,
            distanceMeters = geoDistance,
            arrowAngle = arrow,
            rssi = live.lastRssi,
            hint = "Direção por localização (GPS/WiFi)"
        )
    }

    // Sem coordenadas: só dá para dizer perto/longe pelo sinal (sem direção).
    return TargetState(
        found = true,
        mode = TrackingMode.HOTCOLD,
        distanceMeters = live.distance.takeIf { it > 0 },
        arrowAngle = null,
        rssi = live.lastRssi,
        hint = "Modo aproximação (sem direção precisa neste aparelho)"
    )
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CadeRepository(application)
    val scannerManager = ScannerManager(application)
    val sensorFusion = SensorFusionManager(application)

    /** ID estável deste aparelho no radar (8 hex). Vai no QR e no anúncio BLE. */
    val radarId: String = repository.getOrCreateRadarId()

    /** Detecção REAL de UWB feita uma única vez. */
    val uwbSupported: Boolean = UwbCapabilityHelper.isUwbSupported(application)

    private val _userProfile = MutableStateFlow(repository.getUserProfile())
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _savedContacts = MutableStateFlow(repository.getSavedContacts())
    val savedContacts: StateFlow<List<DiscoveredContact>> = _savedContacts.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising = _isAdvertising.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _isOnboardingComplete = MutableStateFlow(repository.isOnboardingComplete())
    val isOnboardingComplete = _isOnboardingComplete.asStateFlow()

    private val _uwbEnabled = MutableStateFlow(repository.isUwbEnabled())
    val uwbEnabled = _uwbEnabled.asStateFlow()

    private val _soundEnabled = MutableStateFlow(repository.isSoundEnabled())
    val soundEnabled = _soundEnabled.asStateFlow()

    private val _selectedTarget = MutableStateFlow(repository.getSavedContacts().firstOrNull())
    val selectedTarget = _selectedTarget.asStateFlow()

    init {
        sensorFusion.start()
        // Mantém a declinação magnética atualizada conforme a localização chega,
        // para o heading virar NORTE VERDADEIRO (e a seta apontar certo).
        viewModelScope.launch {
            scannerManager.myLocation.collect { loc -> sensorFusion.updateDeclination(loc) }
        }
    }

    fun selectTarget(contact: DiscoveredContact?) {
        _selectedTarget.value = contact
    }

    fun completeOnboarding() {
        repository.setOnboardingComplete(true)
        _isOnboardingComplete.value = true
    }

    fun setUwbEnabled(enabled: Boolean) {
        repository.setUwbEnabled(enabled)
        _uwbEnabled.value = enabled
    }

    fun setSoundEnabled(enabled: Boolean) {
        repository.setSoundEnabled(enabled)
        _soundEnabled.value = enabled
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
                list.add(DiscoveredContact(id = phone.replace(Regex("[^0-9+]"), ""), name = name))
            }
        }
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
        if (_selectedTarget.value == null) {
            _selectedTarget.value = _savedContacts.value.firstOrNull()
        }
    }

    fun removeContact(id: String) {
        repository.removeContact(id)
        _savedContacts.value = repository.getSavedContacts()
        if (_selectedTarget.value?.id == id) {
            _selectedTarget.value = _savedContacts.value.firstOrNull()
        }
    }

    fun toggleAdvertising(enabled: Boolean) {
        _isAdvertising.value = enabled
        if (enabled) {
            scannerManager.startLocationUpdates()
            scannerManager.startAdvertising(radarId)
        } else {
            scannerManager.stopAdvertising()
            maybeStopLocation()
        }
    }

    fun toggleScanning(enabled: Boolean) {
        _isScanning.value = enabled
        if (enabled) {
            scannerManager.startLocationUpdates()
            scannerManager.startScanning()
        } else {
            scannerManager.stopScanning()
            maybeStopLocation()
        }
    }

    private fun maybeStopLocation() {
        if (!_isAdvertising.value && !_isScanning.value) {
            scannerManager.stopLocationUpdates()
        }
    }

    override fun onCleared() {
        super.onCleared()
        scannerManager.stopAdvertising()
        scannerManager.stopScanning()
        scannerManager.stopLocationUpdates()
        sensorFusion.stop()
    }
}
