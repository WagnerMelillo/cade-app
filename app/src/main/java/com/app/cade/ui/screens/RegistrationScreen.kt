package com.app.cade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    val userProfile by viewModel.userProfile.collectAsState()
    val discoveredDevices by viewModel.scannerManager.discoveredDevices.collectAsState()

    var name by remember { mutableStateOf(userProfile?.name ?: "") }
    var phone by remember { mutableStateOf(userProfile?.phone ?: "") }
    var password by remember { mutableStateOf(userProfile?.securityCode ?: "") }
    var visualStatus by remember { mutableStateOf(userProfile?.visualStatus ?: "") }

    var contactName by remember { mutableStateOf("") }
    var contactId by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassWhite, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Text("Seu Perfil (Rastreador)", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Telefone") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = password, onValueChange = { if(it.length <= 4) password = it },
                label = { Text("Senha (4 dígitos)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = visualStatus, onValueChange = { if (it.length <= 15) visualStatus = it },
                label = { Text("Status Visual (ex: Blusa Azul)") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("${visualStatus.length}/15", color = TextSecondary) }
            )

            Button(
                onClick = {
                    viewModel.saveProfile(name, phone, password, visualStatus)
                    onRegistrationComplete()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text("Salvar Perfil Principal")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Contatos
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassWhite, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Text("Adicionar Contato P/ Rastrear", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(value = contactName, onValueChange = { contactName = it }, label = { Text("Nome do Contato") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = contactId, onValueChange = { contactId = it }, label = { Text("ID ou MAC Bluetooth") }, modifier = Modifier.fillMaxWidth())
            
            Button(
                onClick = { 
                    viewModel.saveContact(DiscoveredContact(id = contactId, name = contactName))
                    contactName = ""
                    contactId = ""
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Salvar Novo Contato")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Dispositivos detectados agora:", color = TextSecondary)
            discoveredDevices.forEach { device ->
                Text("${device.name} - ${device.id}", color = Color.White, fontSize = 12.sp)
            }

            TextButton(onClick = { /* TODO: Scanner QR Code real future */ }, modifier = Modifier.padding(top=16.dp)) {
                Icon(Icons.Default.QrCode, contentDescription = "QR", tint = PrimaryCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("QR Code (Troca de ID Rápida)", color = PrimaryCyan)
            }
        }
    }
}
