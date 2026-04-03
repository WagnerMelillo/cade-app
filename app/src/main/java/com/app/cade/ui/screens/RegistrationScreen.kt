package com.app.cade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.cade.data.DiscoveredContact
import com.app.cade.ui.AppViewModel
import com.app.cade.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    viewModel: AppViewModel,
    onRegistrationComplete: () -> Unit
) {
    val discoveredDevices by viewModel.scannerManager.discoveredDevices.collectAsState()

    var contactName by remember { mutableStateOf("") }
    var contactId by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassWhite, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Text("Adicionar Contato P/ Rastrear", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(value = contactName, onValueChange = { contactName = it }, label = { Text("Nome do Contato") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = contactId, onValueChange = { contactId = it }, label = { Text("MAC BLE ou UUID Manual") }, modifier = Modifier.fillMaxWidth())
            
            Button(
                onClick = { 
                    viewModel.saveContact(DiscoveredContact(id = contactId, name = contactName))
                    contactName = ""
                    contactId = ""
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Salvar Contato Manual")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Dispositivos detectados agora (Perto de Você):", color = TextSecondary, fontSize = 12.sp)
            discoveredDevices.forEach { device ->
                Text("${device.name} - ${device.id}", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }

            Button(
                onClick = { onRegistrationComplete() },
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Ir para Radar (Dashboard)")
            }

            TextButton(onClick = { /* Lógica futura abri camera */ }, modifier = Modifier.padding(top=16.dp).align(Alignment.CenterHorizontally)) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scanner", tint = PrimaryCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Escanear QR Code de Alguém", color = PrimaryCyan)
            }
        }
    }
}
