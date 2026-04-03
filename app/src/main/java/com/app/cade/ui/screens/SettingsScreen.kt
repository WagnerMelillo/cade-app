package com.app.cade.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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

    var name by remember { mutableStateOf(userProfile?.name ?: "") }
    var phone by remember { mutableStateOf(userProfile?.phone ?: "") }
    var password by remember { mutableStateOf(userProfile?.securityCode ?: "") }

    val qrCodeBitmap = remember(name, phone) {
        QRCodeGenerator.generateQRCode("CADE:$name:$phone")
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
            
            // Perfil Edit
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassWhite, RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Text("Meus Dados Básicos", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
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
                    value = password, onValueChange = { if(it.length <= 4) password = it },
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

            // QR Code Panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PrimaryBlue.copy(alpha=0.1f), RoundedCornerShape(24.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Meu QR Code de Contato", 
                    fontSize = 18.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Mostre este QR code para a pessoa que vai te rastrear parear rapidamente.", 
                    fontSize = 14.sp, 
                    color = TextSecondary,
                    textAlign = TextAlign.Center
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
                    Box(modifier=Modifier.size(200.dp).background(Color.Gray)) {
                        Text("Preencha os dados acima", modifier=Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
}
