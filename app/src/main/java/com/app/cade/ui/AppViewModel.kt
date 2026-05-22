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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    trueHeading: Float,
    uwbRangingData: ScannerManager.UwbRangingData?
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

    // 1. Caso de UWB disponível (melhor precisão)
    if (uwbRangingData != null) {
        return TargetState(
            found = true,
            mode = TrackingMode.UWB,
            distanceMeters = uwbRangingData.distance.toDouble(),
            arrowAngle = uwbRangingData.azimuth,
            rssi = live.lastRssi,
            hint = "Rastreamento UWB • Alta Precisão"
        )
    }

    // 2. Caso de GPS disponível
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

    // 3. Sem coordenadas: só dá para dizer perto/longe pelo sinal (sem direção).
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
        
        // Sincroniza o status visual do perfil do usuário com o scannerManager
        val profile = repository.getUserProfile()
        if (profile != null) {
            scannerManager.localVisualStatus = profile.visualStatus
        }

        // Mantém a declinação magnética atualizada conforme a localização chega,
        // para o heading virar NORTE VERDADEIRO (e a seta apontar certo).
        viewModelScope.launch {
            scannerManager.myLocation.collect { loc -> sensorFusion.updateDeclination(loc) }
        }

        // Fluxo reativo para gerenciar a sessão UWB
        viewModelScope.launch {
            combine(
                _isScanning,
                _isAdvertising,
                _uwbEnabled,
                _selectedTarget,
                scannerManager.discoveredDevices
            ) { isScan, isAdv, isUwb, target, discovered ->
                if (!isUwb) {
                    scannerManager.stopUwbRanging()
                    return@combine
                }

                var sessionStarted = false

                // 1. Caso Seeker (Controller): se estamos escaneando e temos um alvo selecionado
                if (isScan && target != null) {
                    val liveTarget = discovered.firstOrNull { it.id == target.id }
                    if (liveTarget != null) {
                        val addr = liveTarget.uwbAddress
                        val chan = liveTarget.uwbChannel
                        val prem = liveTarget.uwbPreamble
                        if (addr != null && addr.size == 2 && !(addr[0] == 0.toByte() && addr[1] == 0.toByte())) {
                            scannerManager.startUwbRanging(
                                peerId = target.id,
                                role = ScannerManager.ROLE_CONTROLLER,
                                peerAddressBytes = addr,
                                channel = chan ?: 9,
                                preamble = prem ?: 11
                            )
                            sessionStarted = true
                        }
                    }
                }

                // 2. Caso Target (Controlee): se estamos anunciando e alguém está nos rastreando
                if (!sessionStarted && isAdv) {
                    val seekingPeer = discovered.firstOrNull { it.targetId == radarId }
                    if (seekingPeer != null) {
                        // Verifica permissão/privacidade: se acceptUnknown for falso, o seeker precisa estar nos contatos salvos
                        val savedPeer = repository.getSavedContacts().firstOrNull { it.id == seekingPeer.id }
                        val isAuthorized = repository.isAcceptUnknownEnabled() || (savedPeer != null)

                        if (isAuthorized) {
                            val addr = seekingPeer.uwbAddress
                            val chan = seekingPeer.uwbChannel
                            val prem = seekingPeer.uwbPreamble
                            if (addr != null && addr.size == 2 && !(addr[0] == 0.toByte() && addr[1] == 0.toByte())) {
                                scannerManager.startUwbRanging(
                                    peerId = seekingPeer.id,
                                    role = ScannerManager.ROLE_CONTROLEE,
                                    peerAddressBytes = addr,
                                    channel = chan ?: 9,
                                    preamble = prem ?: 11
                                )
                                sessionStarted = true
                            }
                        }
                    }
                }

                if (!sessionStarted) {
                    scannerManager.stopUwbRanging()
                }
            }.collect {}
        }
    }

    // Estado de contatos da agenda sincronizados e pendentes
    private val _phoneCadeContacts = MutableStateFlow<List<DiscoveredContact>>(emptyList())
    val phoneCadeContacts: StateFlow<List<DiscoveredContact>> = _phoneCadeContacts.asStateFlow()

    private val _phonePendingContacts = MutableStateFlow<List<DiscoveredContact>>(emptyList())
    val phonePendingContacts: StateFlow<List<DiscoveredContact>> = _phonePendingContacts.asStateFlow()

    private val _isSyncingContacts = MutableStateFlow(false)
    val isSyncingContacts = _isSyncingContacts.asStateFlow()

    private val _acceptUnknown = MutableStateFlow(repository.isAcceptUnknownEnabled())
    val acceptUnknown = _acceptUnknown.asStateFlow()

    fun setAcceptUnknown(enabled: Boolean) {
        repository.setAcceptUnknownEnabled(enabled)
        _acceptUnknown.value = enabled
    }

    fun selectTarget(contact: DiscoveredContact?) {
        _selectedTarget.value = contact
        scannerManager.currentTargetId = contact?.id ?: "00000000"
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

    @SuppressLint("Range")
    fun fetchPhoneContactsWithCadeTags(): Pair<List<DiscoveredContact>, List<DiscoveredContact>> {
        val registered = mutableListOf<DiscoveredContact>()
        val pending = mutableListOf<DiscoveredContact>()
        val resolver = getApplication<Application>().contentResolver

        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.CommonDataKinds.Note.NOTE,
            ContactsContract.CommonDataKinds.Email.ADDRESS
        )
        
        var cursor: android.database.Cursor? = null
        try {
            cursor = resolver.query(uri, projection, null, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        class TempContact(
            val id: String,
            var name: String = "",
            var phone: String = "",
            var note: String = "",
            var email: String = ""
        )
        val tempMap = mutableMapOf<String, TempContact>()
        
        cursor?.use {
            val idIdx = it.getColumnIndex(ContactsContract.Data.CONTACT_ID)
            val nameIdx = it.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
            val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val mimeIdx = it.getColumnIndex(ContactsContract.Data.MIMETYPE)
            val noteIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Note.NOTE)
            val emailIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            
            while (it.moveToNext()) {
                val cId = it.getString(idIdx) ?: continue
                val tc = tempMap.getOrPut(cId) { TempContact(cId) }
                if (nameIdx != -1) tc.name = it.getString(nameIdx) ?: tc.name
                
                val mime = if (mimeIdx != -1) it.getString(mimeIdx) else null
                if (mime == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE) {
                    if (numIdx != -1) tc.phone = it.getString(numIdx) ?: tc.phone
                } else if (mime == ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE) {
                    if (noteIdx != -1) tc.note = it.getString(noteIdx) ?: tc.note
                } else if (mime == ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE) {
                    if (emailIdx != -1) tc.email = it.getString(emailIdx) ?: tc.email
                }
            }
        }
        
        for (tc in tempMap.values) {
            if (tc.name.isBlank()) continue
            
            var radarId: String? = null
            var securityCode: String? = null
            
            val textToSearch = "${tc.note}\n${tc.email}"
            val regex = Regex("CADE:([A-Za-z0-9]+)(?::([A-Za-z0-9]+))?")
            val match = regex.find(textToSearch)
            if (match != null) {
                radarId = match.groupValues.getOrNull(1)
                securityCode = match.groupValues.getOrNull(2)
            }
            
            if (radarId != null && radarId.isNotBlank()) {
                registered.add(
                    DiscoveredContact(
                        id = radarId.uppercase().trim(),
                        name = tc.name,
                        securityCode = securityCode?.ifBlank { null }
                    )
                )
            } else if (tc.phone.isNotBlank()) {
                pending.add(
                    DiscoveredContact(
                        id = tc.phone.replace(Regex("[^0-9+]"), ""),
                        name = tc.name
                    )
                )
            }
        }
        
        return Pair(
            registered.distinctBy { it.id }.sortedBy { it.name.lowercase() },
            pending.distinctBy { it.id }.sortedBy { it.name.lowercase() }
        )
    }

    fun syncPhoneContacts() {
        viewModelScope.launch {
            _isSyncingContacts.value = true
            try {
                val (cadeContacts, pendingContacts) = withContext(Dispatchers.IO) {
                    fetchPhoneContactsWithCadeTags()
                }
                _phoneCadeContacts.value = cadeContacts
                _phonePendingContacts.value = pendingContacts

                // Auto-sync: salva no CADÊ contatos da agenda que contêm a tag
                var importedCount = 0
                for (contact in cadeContacts) {
                    val alreadySaved = repository.getSavedContacts().any { it.id == contact.id }
                    if (!alreadySaved) {
                        repository.saveContact(contact)
                        importedCount++
                    }
                }
                if (importedCount > 0) {
                    _savedContacts.value = repository.getSavedContacts()
                    if (_selectedTarget.value == null) {
                        _selectedTarget.value = _savedContacts.value.firstOrNull()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSyncingContacts.value = false
            }
        }
    }

    fun saveProfile(name: String, phone: String, code: String, visual: String) {
        val profile = UserProfile(name, phone, code, visual)
        repository.saveUserProfile(profile)
        _userProfile.value = profile
        scannerManager.localVisualStatus = visual
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

    fun clearAllContacts() {
        repository.clearAllContacts()
        _savedContacts.value = repository.getSavedContacts()
        _selectedTarget.value = null
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
