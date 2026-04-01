package com.app.cade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.cade.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionPanelScreen(onBack: () -> Unit) {
    var isVisible by remember { mutableStateOf(false) }
    var useUWB by remember { mutableStateOf(true) }
    var useBLE by remember { mutableStateOf(true) }
    var acceptUnknown by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Painel de Conexões", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
        ) {
            
            // Cartão de Visibilidade com Glassmorfismo
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
                        Text("Muda o código periodicamente", color = TextSecondary, fontSize = 14.sp)
                    }
                    Switch(
                        checked = isVisible,
                        onCheckedChange = { isVisible = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryCyan,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = SurfaceDark
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Text("HARDWARE", color = PrimaryCyan, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(16.dp))
            
            HardwareToggleRow("UWB (Ultra Wideband)", useUWB) { useUWB = it }
            HardwareToggleRow("Bluetooth BLE", useBLE) { useBLE = it }
            
            Spacer(modifier = Modifier.height(32.dp))
            Text("PRIVACIDADE", color = PrimaryCyan, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(16.dp))
            
            HardwareToggleRow("Aceitar conexões desconhecidas", acceptUnknown) { acceptUnknown = it }

            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = { /* Force Scan */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(BrandGradientHorizontal, RoundedCornerShape(16.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) {
                Text("Busca Manual de Dispositivos", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun HardwareToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryCyan,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SurfaceDark
            )
        )
    }
}
