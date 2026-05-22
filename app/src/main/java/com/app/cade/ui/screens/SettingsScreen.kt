package com.app.cade.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.cade.ui.AppViewModel
import com.app.cade.ui.theme.*
import com.app.cade.utils.QRCodeGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val uwbEnabled by viewModel.uwbEnabled.collectAsState()
    val radarId = viewModel.radarId
    val uwbSupported = viewModel.uwbSupported

    var name by remember { mutableStateOf(userProfile?.name ?: "") }
    var phone by remember { mutableStateOf(userProfile?.phone ?: "") }
    var password by remember { mutableStateOf(userProfile?.securityCode ?: "") }

    // O QR Code agora carrega também o radarId e a senha secreta/chave de segurança.
    val qrCodeBitmap = remember(name, phone, radarId, password) {
        QRCodeGenerator.generateQRCode("CADE:$name:$phone:$radarId:$password")
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações do Perfil", fontWeight = FontWeight.SemiBold) },
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
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ---------- Perfil ----------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassWhite, RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Text("Meus Dados Básicos", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Meu ID de radar: $radarId", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Seu Nome") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("Seu Telefone") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password, onValueChange = { if (it.length <= 4) password = it },
                    label = { Text("Senha Secreta (4 Dígitos)") }, modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val visual = userProfile?.visualStatus ?: ""
                        viewModel.saveProfile(name, phone, password, visual)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Text("Atualizar Dados")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ---------- Conexão UWB (ver UWB.docx) ----------
            UwbSection(
                supported = uwbSupported,
                enabled = uwbEnabled,
                onToggle = { viewModel.setUwbEnabled(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ---------- QR Code ----------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PrimaryBlue.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Meu QR Code de Contato", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Mostre este QR code para a pessoa que vai te rastrear parear rapidamente.",
                    fontSize = 14.sp, color = TextSecondary, textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                qrCodeBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp,
                        contentDescription = "QR Code para pareamento rápido",
                        modifier = Modifier.size(200.dp),
                        contentScale = ContentScale.Fit
                    )
                } ?: run {
                    Box(modifier = Modifier.size(200.dp).background(Color.Gray)) {
                        Text("Preencha os dados acima", modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
}

/**
 * Seção de UWB. Aparece como toggle ativável quando o aparelho é compatível
 * (detecção REAL via UwbCapabilityHelper) e como aviso desabilitado quando não.
 */
@Composable
private fun UwbSection(
    supported: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassWhite, RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bolt, contentDescription = null, tint = PrimaryCyan)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Conexão UWB", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (supported) {
            Text(
                "Seu dispositivo é compatível com UWB. Ative esta opção caso deseje utilizar esse recurso para localização de alta precisão.",
                fontSize = 14.sp, color = TextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Habilitar conexão UWB", fontSize = 16.sp, color = TextPrimary)
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
        } else {
            Text(
                "Este dispositivo não oferece suporte à tecnologia UWB.",
                fontSize = 14.sp, color = TextSecondary
            )
        }
    }
}
