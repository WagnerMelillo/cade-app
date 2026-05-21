package com.app.cade.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.cade.ui.AppViewModel
import com.app.cade.ui.TrackingMode
import com.app.cade.ui.computeTargetState
import com.app.cade.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: AppViewModel,
    onNavigateToConnections: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToContacts: () -> Unit
) {
    val isAdvertising by viewModel.isAdvertising.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val discoveredDevices by viewModel.scannerManager.discoveredDevices.collectAsState()
    val myLocation by viewModel.scannerManager.myLocation.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val savedContacts by viewModel.savedContacts.collectAsState()
    val selectedTarget by viewModel.selectedTarget.collectAsState()
    val heading by viewModel.sensorFusion.trueHeading.collectAsState()
    val compassAccuracy by viewModel.sensorFusion.accuracy.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()

    var expandedDropdown by remember { mutableStateOf(false) }
    var myVisualStatus by remember { mutableStateOf(userProfile?.visualStatus ?: "") }

    // Estado calculado da direção/distância em camadas (GPS -> quente/frio).
    val target = computeTargetState(
        target = selectedTarget,
        discovered = discoveredDevices,
        myLat = myLocation?.latitude,
        myLon = myLocation?.longitude,
        trueHeading = heading
    )

    // Detecta se estamos esquentando (chegando perto) com uma pequena histerese.
    var lastDistance by remember { mutableStateOf<Double?>(null) }
    var gettingCloser by remember { mutableStateOf(false) }
    LaunchedEffect(target.distanceMeters) {
        val d = target.distanceMeters
        val prev = lastDistance
        if (d != null && prev != null) {
            if (d < prev - 0.2) gettingCloser = true
            else if (d > prev + 0.2) gettingCloser = false
        }
        if (d != null) lastDistance = d
    }

    // Som de aproximação: apita mais rápido quanto mais perto.
    LaunchedEffect(soundEnabled, target.distanceMeters) {
        if (soundEnabled) {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            try {
                while (soundEnabled) {
                    val d = target.distanceMeters
                    if (d == null || d <= 0) break
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                    val delayTime = (d * 100).toLong().coerceIn(200L, 2000L)
                    delay(delayTime)
                }
            } finally {
                tone.release()
            }
        }
    }

    val scrollState = rememberScrollState()

    val accent = when {
        !target.found -> PrimaryCyan
        gettingCloser -> SuccessGreen
        else -> DangerRed
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CADÊ - Radar", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToContacts) {
                        Icon(Icons.Default.People, contentDescription = "Contatos")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurações")
                    }
                    IconButton(onClick = onNavigateToConnections) {
                        Icon(Icons.Default.SettingsInputAntenna, contentDescription = "Conexões")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = TextPrimary,
                    actionIconContentColor = PrimaryCyan
                )
            )
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            OutlinedTextField(
                value = myVisualStatus,
                onValueChange = {
                    if (it.length <= 20) {
                        myVisualStatus = it
                        viewModel.saveProfile(
                            userProfile?.name ?: "",
                            userProfile?.phone ?: "",
                            userProfile?.securityCode ?: "",
                            it
                        )
                    }
                },
                label = { Text("O que você está vestindo hoje?") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("Dica visual rápida pra quem te procura", color = TextSecondary) }
            )

            Button(
                onClick = { viewModel.toggleAdvertising(!isAdvertising) },
                colors = ButtonDefaults.buttonColors(containerColor = if (isAdvertising) DangerRed else PrimaryBlue),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(if (isAdvertising) "Parar de ser rastreável" else "1. Permitir ser rastreado")
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { expandedDropdown = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(
                        text = if (selectedTarget != null) "Alvo: ${selectedTarget?.name}"
                        else "2. Selecionar Contato P/ Rastrear"
                    )
                }
                DropdownMenu(expanded = expandedDropdown, onDismissRequest = { expandedDropdown = false }) {
                    if (savedContacts.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Nenhum contato. Adicione em Contatos.", color = Color.Black) },
                            onClick = { expandedDropdown = false }
                        )
                    }
                    savedContacts.forEach { contact ->
                        DropdownMenuItem(
                            text = { Text(contact.name, color = Color.Black) },
                            onClick = {
                                viewModel.selectTarget(contact)
                                expandedDropdown = false
                            }
                        )
                    }
                }
            }

            // ---------- Radar / Bússola ----------
            val glow = accent
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(glow.copy(alpha = 0.30f), Color.Transparent),
                            radius = 400f
                        )
                    )
                    .border(2.dp, GlassBorder, CircleShape)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .border(1.dp, glow.copy(alpha = 0.5f), CircleShape)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (target.mode == TrackingMode.GPS && target.arrowAngle != null) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Direção do alvo",
                            modifier = Modifier.size(70.dp).rotate(target.arrowAngle),
                            tint = accent
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Adjust,
                            contentDescription = "Aproximação",
                            modifier = Modifier.size(70.dp),
                            tint = accent
                        )
                    }
                    val distanceText = target.distanceMeters?.let { String.format("%.1f m", it) } ?: "---"
                    Text(distanceText, fontSize = 32.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                }
            }

            // ---------- Linha de status ----------
            val modeLabel = when (target.mode) {
                TrackingMode.UWB -> "UWB • alta precisão"
                TrackingMode.GPS -> "GPS/WiFi • direção"
                TrackingMode.HOTCOLD -> "Bluetooth • quente/frio"
                TrackingMode.NONE -> "Procurando…"
            }
            Text(
                text = if (target.found) "$modeLabel  ·  RSSI ${target.rssi ?: "—"}" else target.hint,
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(target.hint, color = TextSecondary, fontSize = 11.sp)

            if (compassAccuracy <= 1) {
                Text(
                    "Bússola precisa calibrar: faça um ∞ no ar com o celular.",
                    color = DangerRed,
                    fontSize = 11.sp
                )
            }

            // ---------- Controles inferiores ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.toggleScanning(!isScanning) },
                    modifier = Modifier.weight(1f).height(56.dp)
                        .background(BrandGradientHorizontal, RoundedCornerShape(16.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) {
                    Text(if (isScanning) "Desligar Radar" else "3. Iniciar Radar", color = Color.White)
                }

                Spacer(modifier = Modifier.width(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeUp, "Som", tint = if (soundEnabled) PrimaryCyan else TextSecondary)
                    Switch(checked = soundEnabled, onCheckedChange = { viewModel.setSoundEnabled(it) })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
