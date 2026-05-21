package com.app.cade.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.cade.data.DiscoveredContact
import com.app.cade.ui.AppViewModel
import com.app.cade.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val savedContacts by viewModel.savedContacts.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val myName = userProfile?.name ?: ""

    // Permissões e States
    val contactsPermission = rememberPermissionState(Manifest.permission.READ_CONTACTS)
    var phoneContacts by remember { mutableStateOf<List<DiscoveredContact>?>(null) }

    // Launcher do Laser da Câmera (Zxing)
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val qrData = result.contents
            // Novo formato: "CADE:Nome:Telefone:radarId" | Antigo: "CADE:Nome:Id"
            val parts = qrData.split(":")
            if (parts.size >= 2 && parts[0] == "CADE") {
                val scannedName = parts.getOrNull(1)?.ifBlank { "Contato" } ?: "Contato"
                // O radarId (parts[3]) é o que o radar BLE casa de fato.
                val scannedId = parts.getOrNull(3) ?: parts.getOrNull(2) ?: qrData.take(8)
                viewModel.saveContact(DiscoveredContact(id = scannedId, name = scannedName))
            } else {
                viewModel.saveContact(DiscoveredContact(id = qrData.take(8), name = "Contato QR"))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meus Rastreados", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = PrimaryCyan
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
        ) {
            
            // Botões de Ação de Adicionar
            Column(modifier = Modifier.fillMaxWidth().background(GlassWhite, RoundedCornerShape(24.dp)).padding(20.dp)) {
                Text("Adicionar Novo Contato", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val options = ScanOptions()
                        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        options.setPrompt("Aponte para o QR Code do seu amigo")
                        options.setCameraId(0) // Câmera traseira
                        options.setBeepEnabled(true)
                        options.setBarcodeImageEnabled(true)
                        scanLauncher.launch(options)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Escanear via Câmera")
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (contactsPermission.status.isGranted) {
                            phoneContacts = viewModel.fetchPhoneContacts()
                            phoneContacts?.forEach { viewModel.saveContact(it) } // Sincroniza simples
                        } else {
                            contactsPermission.launchPermissionRequest()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (phoneContacts != null) "Sincronizado na nuvem local!" else "Sincronizar Agenda")
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val p = "Baixe o CADÊ e adicione meu QR Code para nos encontrarmos no radar! Ass: $myName"
                        val sendIntent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("sms:")
                            putExtra("sms_body", p)
                        }
                        context.startActivity(sendIntent)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Convidar Amigo por SMS")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Lista de Rastreados (${savedContacts.size})", color = TextSecondary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            // Lista de Contatos Importados
            LazyColumn {
                items(savedContacts) { contact ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = GlassWhite)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(contact.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("ID Radar: ${contact.id}", fontSize = 12.sp, color = TextSecondary)
                            }
                            IconButton(onClick = { viewModel.removeContact(contact.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remover", tint = DangerRed)
                            }
                        }
                    }
                }
            }
        }
    }
}
