package com.app.cade.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.cade.ui.theme.AccentTeal

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
                title = { Text("Painel de Conexões") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Ficar Visível", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("A cada 2 min altera o pareamento", color = Color.Gray, fontSize = 14.sp)
                    }
                    Switch(
                        checked = isVisible,
                        onCheckedChange = { isVisible = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentTeal)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Gestão de Hardware", color = AccentTeal, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            HardwareToggleRow("UWB (Ultra Wideband)", useUWB) { useUWB = it }
            HardwareToggleRow("Bluetooth BLE", useBLE) { useBLE = it }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Privacidade", color = AccentTeal, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            HardwareToggleRow("Aceitar pedidos de não-cadastrados", acceptUnknown) { acceptUnknown = it }

            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { /* Force Scan */ },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
            ) {
                Text("Busca Manual de Dispositivos", fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun HardwareToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 16.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
