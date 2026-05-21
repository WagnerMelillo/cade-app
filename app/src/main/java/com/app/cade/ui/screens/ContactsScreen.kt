package com.app.cade.ui.screens

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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
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
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onNavigateToInvite: () -> Unit
) {
    val context = LocalContext.current
    val savedContacts by viewModel.savedContacts.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val myName = userProfile?.name ?: ""

    var query by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }

    // Lista filtrada pela busca (lupa). Não toca em disco; só filtra o que já está em memória.
    val filtered = remember(savedContacts, query) {
        if (query.isBlank()) savedContacts
        else savedContacts.filter {
            it.name.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true)
        }
    }

    // Leitor de QR Code (Zxing). Formato: "CADE:Nome:Telefone:radarId".
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val qrData = result.contents
            val parts = qrData.split(":")
            if (parts.size >= 2 && parts[0] == "CADE") {
                val scannedName = parts.getOrNull(1)?.ifBlank { "Contato" } ?: "Contato"
                val scannedId = parts.getOrNull(3) ?: parts.getOrNull(2) ?: qrData.take(8)
                viewModel.saveContact(DiscoveredContact(id = scannedId, name = scannedName))
            } else {
                viewModel.saveContact(DiscoveredContact(id = qrData.take(8), name = "Contato QR"))
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Limpar lista?") },
            text = { Text("Isso remove todos os contatos rastreados. Você pode adicioná-los de novo pelo QR Code.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllContacts()
                    showClearDialog = false
                }) { Text("Limpar tudo", color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancelar") }
            }
        )
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

            // ---------- Adicionar novo contato ----------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassWhite, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Text("Adicionar Novo Contato", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val options = ScanOptions()
                        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        options.setPrompt("Aponte para o QR Code do seu amigo")
                        options.setCameraId(0)
                        options.setBeepEnabled(true)
                        options.setBarcodeImageEnabled(true)
                        scanLauncher.launch(options)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Escanear QR Code do amigo")
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { onNavigateToInvite() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Convidar da minha agenda")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ---------- Busca (lupa) ----------
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Buscar rastreado") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar", tint = PrimaryCyan) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ---------- Cabeçalho da lista + limpar ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Lista de Rastreados (${filtered.size})",
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold
                )
                if (savedContacts.isNotEmpty()) {
                    TextButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Limpar tudo", color = DangerRed, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (savedContacts.isEmpty()) {
                Text(
                    "Nenhum rastreado ainda. Use 'Escanear QR Code do amigo' para adicionar.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            LazyColumn {
                items(filtered, key = { it.id }) { contact ->
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
