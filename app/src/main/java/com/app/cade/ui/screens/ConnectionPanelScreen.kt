package com.app.cade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.cade.ui.AppViewModel
import com.app.cade.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionPanelScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val isAdvertising by viewModel.isAdvertising.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    var acceptUnknown by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Painel de Conexões", fontWeight = FontWeight.SemiBold) },
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
                .padding(24.dp)
        ) {
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(GlassWhite)
                    .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ficar Visível", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Transmite seu sinal PDR/Bluetooth a cada 2min", color = TextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = isAdvertising,
                        onCheckedChange = { viewModel.toggleAdvertising(it) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Permitir pedidos de desconhecidos", color = TextPrimary, fontSize = 16.sp)
                Switch(checked = acceptUnknown, onCheckedChange = { acceptUnknown = it })
            }

            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = { viewModel.toggleScanning(!isScanning) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(BrandGradientHorizontal, RoundedCornerShape(16.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) {
                Text(if(isScanning) "Parar Busca Contínua" else "Iniciar Busca Contínua (Scanner Mode)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
