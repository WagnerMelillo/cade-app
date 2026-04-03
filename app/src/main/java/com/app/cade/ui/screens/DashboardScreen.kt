package com.app.cade.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.material.icons.filled.ArrowUpward
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.cade.ui.AppViewModel
import com.app.cade.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: AppViewModel,
    onNavigateToConnections: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    var soundEnabled by remember { mutableStateOf(false) }
    
    val isAdvertising by viewModel.isAdvertising.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val discoveredDevices by viewModel.scannerManager.discoveredDevices.collectAsState()
    val distance by viewModel.scannerManager.currentTargetDistance.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val savedContacts by viewModel.savedContacts.collectAsState()

    var expandedDropdown by remember { mutableStateOf(false) }
    var selectedContact by remember { mutableStateOf(savedContacts.firstOrNull()) }

    var myVisualStatus by remember { mutableStateOf(userProfile?.visualStatus ?: "") }

    var azimuth by remember { mutableStateOf(0f) }
    var lastDistance by remember { mutableStateOf(distance) }
    var isGettingCloser by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    // Bússola
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientationAngles = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    azimuth = (Math.toDegrees(orientationAngles[0].toDouble()) + 360).toFloat() % 360
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    // Engine Hot/Cold (Seta e Som)
    LaunchedEffect(distance) {
        if (distance > 0) {
            isGettingCloser = distance < lastDistance
            lastDistance = distance
        }
    }

    // Engine de Som de Radar
    LaunchedEffect(distance, soundEnabled) {
        val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        while (soundEnabled && distance > 0.0) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            // Quão menor a distância, mais rápido apita (min 200ms, max 2000ms)
            val delayTime = (distance * 100).toLong().coerceIn(200L, 2000L)
            delay(delayTime)
        }
        toneGenerator.release()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CADÊ - Radar", fontWeight = FontWeight.Bold) },
                actions = {
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
                        // Atualiza no banco rápido
                        viewModel.saveProfile(userProfile?.name ?: "", userProfile?.phone ?: "", userProfile?.securityCode ?: "", it)
                    }
                },
                label = { Text("O que você está vestindo hoje?") }, 
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("Dica visual rápida pra quem te procura", color = TextSecondary) }
            )

            Button(
                onClick = { viewModel.toggleAdvertising(!isAdvertising) },
                colors = ButtonDefaults.buttonColors(containerColor = if(isAdvertising) Color.Red else PrimaryBlue),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(if(isAdvertising) "Parar de ser rastreável" else "1. Permitir ser rastreado")
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { expandedDropdown = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(text = if (selectedContact != null) "Alvo: ${selectedContact?.name}" else "2. Selecionar Contato P/ Rastrear")
                }
                DropdownMenu(expanded = expandedDropdown, onDismissRequest = { expandedDropdown = false }) {
                    savedContacts.forEach { contact ->
                        DropdownMenuItem(
                            text = { Text(contact.name, color = Color.Black) },
                            onClick = {
                                selectedContact = contact
                                expandedDropdown = false
                            }
                        )
                    }
                }
            }

            // Radar e Bússola Quente/Frio
            val compassColor = if (distance <= 0) PrimaryCyan else if (isGettingCloser) Color.Green else Color.Red
            val compassGlow = if (distance <= 0) PrimaryBlue else if (isGettingCloser) Color(0xFF00FF00) else Color(0xFFFF0000)
            
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(colors = listOf(compassGlow.copy(alpha = 0.3f), Color.Transparent), radius = 400f))
                    .border(2.dp, GlassBorder, CircleShape)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(180.dp).clip(CircleShape).border(1.dp, compassGlow.copy(alpha=0.5f), CircleShape))
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val formattedDistance = if (distance > 0) String.format("%.1fm", distance) else "---"
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Seta",
                        modifier = Modifier.size(70.dp).rotate(-azimuth), 
                        tint = compassColor
                    )
                    Text(
                        text = formattedDistance,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                }
            }
            
            // Log target Info
            val targetDiscoveredInfo = discoveredDevices.find { it.name == selectedContact?.name }
            val connectionType = if (targetDiscoveredInfo != null) "Status: Sinal Encontrado (RSSI: ${targetDiscoveredInfo.lastRssi})" else "Sinal não localizado próximo."
            Text(connectionType, color = compassColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

            Spacer(modifier = Modifier.height(4.dp))

            // Switch Search / Sonar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.toggleScanning(!isScanning) },
                    modifier = Modifier.weight(1f).height(56.dp).background(BrandGradientHorizontal, RoundedCornerShape(16.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) {
                    Text(if(isScanning) "Desligar Radar" else "3. Iniciar Radar", color = Color.White)
                }
                
                Spacer(modifier = Modifier.width(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeUp, "Som Radar", tint = if (soundEnabled) PrimaryCyan else TextSecondary)
                    Switch(checked = soundEnabled, onCheckedChange = { soundEnabled = it })
                }
            }
            
            Spacer(modifier=Modifier.height(16.dp))
        }
    }
}
