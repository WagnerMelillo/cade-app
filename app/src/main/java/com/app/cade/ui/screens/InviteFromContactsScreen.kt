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

    val permission = rememberPermissionState(Manifest.permission.READ_CONTACTS)
    var query by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf<List<DiscoveredContact>?>(null) }
    var loading by remember { mutableStateOf(false) }

    // Carrega a agenda FORA da thread principal (evita o ANR).
    LaunchedEffect(permission.status.isGranted) {
        if (permission.status.isGranted && contacts == null) {
            loading = true
            contacts = withContext(Dispatchers.IO) { viewModel.fetchPhoneContacts() }
            loading = false
        }
    }

    val filtered = remember(contacts, query) {
        val list = contacts ?: emptyList()
        if (query.isBlank()) list
        else list.filter {
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Toque numa pessoa para enviar o convite do CADÊ por SMS.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = PrimaryCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Carregando contatos...", color = TextSecondary)
                    }
                } else {
                    LazyColumn {
                        items(filtered, key = { it.id }) { c ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        val msg = "Oi! Estou usando o app CADE para a gente se encontrar facil em lugares cheios. Baixe o app e me adicione pelo meu QR Code. Ass: $myName (ID de radar: $myRadarId)"
                                        val sms = Intent(Intent.ACTION_VIEW).apply {
                                            data = Uri.parse("sms:" + c.id)
                                            putExtra("sms_body", msg)
                                        }
                                        context.startActivity(sms)
                                    },
                                colors = CardDefaults.cardColors(containerColor = GlassWhite)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(c.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text(c.id, fontSize = 12.sp, color = TextSecondary)
                                    }
                                    Icon(Icons.Default.Sms, contentDescription = "Convidar", tint = PrimaryCyan)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
