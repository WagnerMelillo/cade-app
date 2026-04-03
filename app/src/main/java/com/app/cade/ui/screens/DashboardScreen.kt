package com.app.cade.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: AppViewModel,
    onNavigateToConnections: () -> Unit
) {
    val context = LocalContext.current
    var soundEnabled by remember { mutableStateOf(false) }
    
    // Observers reais do ViewModel
    val isAdvertising by viewModel.isAdvertising.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val discoveredDevices by viewModel.scannerManager.discoveredDevices.collectAsState()
    val distance by viewModel.scannerManager.currentTargetDistance.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val savedContacts by viewModel.savedContacts.collectAsState()

    // 1. Selector
    var expandedDropdown by remember { mutableStateOf(false) }
    var selectedContact by remember { mutableStateOf(savedContacts.firstOrNull()) }

    // Compass Effect
    var azimuth by remember { mutableStateOf(0f) }
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
                    // Converte radianos para graus e simula alinhamento do compasso
                    azimuth = (Math.toDegrees(orientationAngles[0].toDouble()) + 360).toFloat() % 360
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CADÊ - Radar", fontWeight = FontWeight.Bold) },
                actions = {
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            
            // 1. Dropdown do alvo
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Button(onClick = { expandedDropdown = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = if (selectedContact != null) "Rastreando: ${selectedContact?.name}" else "1. Selecionar Contato P/ Rastrear")
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

            // 2. Botão Permitir ser Rastreado
            Button(
                onClick = { viewModel.toggleAdvertising(!isAdvertising) },
                colors = ButtonDefaults.buttonColors(containerColor = if(isAdvertising) Color.Red else PrimaryBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if(isAdvertising) "Parar de compartilhar sinal" else "2. Permitir ser rastreado")
            }

            // 3. Pedir Permissão
            Button(
                onClick = { /* Lógica futura de notificação via Firebase/P2P */ },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("3. Pedir permissão ao contato")
            }

            // Bússola e Radar (Target View)
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(PrimaryBlue.copy(alpha = 0.2f), Color.Transparent),
                            radius = 400f
                        )
                    )
                    .border(2.dp, GlassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(180.dp).clip(CircleShape).border(1.dp, GlassWhite, CircleShape))
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val formattedDistance = if (distance > 0) String.format("%.1fm", distance) else "---"
                    // Gira a seta baseado no Compass (Azimuth)
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Seta",
                        modifier = Modifier
                            .size(70.dp)
                            .rotate(-azimuth), // Simula fixar no alvo
                        tint = PrimaryCyan
                    )
                    Text(
                        text = formattedDistance,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                }
            }
            
            // 4. Tipo de Conexão
            val targetDiscoveredInfo = discoveredDevices.find { it.name == selectedContact?.name }
            val connectionType = if (targetDiscoveredInfo != null) "4. Bluetooth (Visível - RSSI: ${targetDiscoveredInfo.lastRssi})" else "4. Sinal não detectado ou FusedLocation"
            Text(connectionType, color = PrimaryCyan, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(8.dp))

            // 5 e 6
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.toggleScanning(!isScanning) },
                    modifier = Modifier
                        .height(56.dp)
                        .background(BrandGradientHorizontal, RoundedCornerShape(16.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) {
                    Text(if(isScanning) "Parar Radar" else "5. Iniciar Rastreio", color = Color.White)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeUp, "Som", tint = if (soundEnabled) PrimaryCyan else TextSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("6.", color = TextSecondary)
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = { soundEnabled = it }
                    )
                }
            }
        }
    }
}
