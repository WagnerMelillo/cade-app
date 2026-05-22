package com.app.cade.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.app.cade.data.DiscoveredContact
import com.app.cade.ui.AppViewModel
import com.app.cade.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Busca na agenda do telefone (com lupa) para CONVIDAR pessoas para o CADÊ.
 *
 * Pontos importantes:
 *  - A leitura da agenda roda em Dispatchers.IO (fora da thread principal),
 *    o que evita o travamento/ANR que existia antes.
 *  - Tocar numa pessoa abre o SMS de convite já preenchido com o nome e o
 *    ID de radar do usuário. A pessoa só vira "rastreável" quando instala o
 *    CADÊ e vocês pareiam pelo QR (regra: lista de rastreados = só usuários).
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InviteFromContactsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()
    val myName = userProfile?.name ?: ""
    val myRadarId = viewModel.radarId
    val savedContacts by viewModel.savedContacts.collectAsState()

    val permission = rememberPermissionState(Manifest.permission.READ_CONTACTS)
    var query by remember { mutableStateOf("") }

    val isSyncing by viewModel.isSyncingContacts.collectAsState()
    val phoneCadeContacts by viewModel.phoneCadeContacts.collectAsState()
    val phonePendingContacts by viewModel.phonePendingContacts.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Adesão (Convidar), 1 = Já usam o CADÊ (Importar)

    // Sincroniza a agenda ao obter permissão
    LaunchedEffect(permission.status.isGranted) {
        if (permission.status.isGranted) {
            viewModel.syncPhoneContacts()
        }
    }

    // Filtra os contatos da aba selecionada conforme a busca (query)
    val filteredCade = remember(phoneCadeContacts, query) {
        if (query.isBlank()) phoneCadeContacts
        else phoneCadeContacts.filter {
            it.name.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true)
        }
    }

    val filteredPending = remember(phonePendingContacts, query) {
        if (query.isBlank()) phonePendingContacts
        else phonePendingContacts.filter {
            it.name.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Convidar da agenda", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar") }
                },
                actions = {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 8.dp),
                            color = PrimaryCyan,
                            strokeWidth = 2.dp
                        )
                    } else if (permission.status.isGranted) {
                        IconButton(onClick = { viewModel.syncPhoneContacts() }) {
                            Icon(Icons.Default.Sync, contentDescription = "Sincronizar", tint = PrimaryCyan)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = PrimaryCyan
                )
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (!permission.status.isGranted) {
                Text(
                    "Para listar seus contatos, o app precisa da sua permissão de acesso à agenda.",
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { permission.launchPermissionRequest() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Permitir acesso aos contatos")
                }
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar na agenda") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar", tint = PrimaryCyan) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = PrimaryCyan,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Convidar (${filteredPending.size})", fontSize = 14.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Importar (${filteredCade.size})", fontSize = 14.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isSyncing && phonePendingContacts.isEmpty() && phoneCadeContacts.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = PrimaryCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sincronizando agenda...", color = TextSecondary)
                    }
                } else {
                    val currentList = if (selectedTab == 0) filteredPending else filteredCade

                    if (currentList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (selectedTab == 0) "Nenhum contato pendente encontrado." else "Nenhum contato com tag CADÊ na agenda.",
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(currentList, key = { it.id }) { c ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = GlassWhite)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(c.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                                            Text(c.id, fontSize = 12.sp, color = TextSecondary)
                                            if (selectedTab == 1 && c.securityCode != null) {
                                                Text("🔑 Chave: ${c.securityCode}", fontSize = 11.sp, color = PrimaryCyan)
                                            }
                                        }
                                        if (selectedTab == 0) {
                                            IconButton(onClick = {
                                                val msg = "Oi! Estou usando o app CADE para a gente se encontrar facil em lugares cheios. Baixe o app e me adicione pelo meu QR Code. Ass: $myName (ID de radar: $myRadarId)"
                                                val sms = Intent(Intent.ACTION_VIEW).apply {
                                                    data = Uri.parse("sms:" + c.id)
                                                    putExtra("sms_body", msg)
                                                }
                                                context.startActivity(sms)
                                            }) {
                                                Icon(Icons.Default.Sms, contentDescription = "Convidar", tint = PrimaryCyan)
                                            }
                                        } else {
                                            val alreadySaved = savedContacts.any { it.id == c.id }
                                            if (alreadySaved) {
                                                Text(
                                                    "Já salvo",
                                                    color = SuccessGreen,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(end = 8.dp)
                                                )
                                            } else {
                                                Button(
                                                    onClick = { viewModel.saveContact(c) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("Importar", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
