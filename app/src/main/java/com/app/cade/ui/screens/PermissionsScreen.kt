@file:OptIn(ExperimentalPermissionsApi::class)
package com.app.cade.ui.screens

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.cade.ui.theme.AccentTeal
import com.app.cade.ui.theme.PrimaryBlue
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@Composable
fun PermissionsScreen(onPermissionsGranted: () -> Unit) {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = "Security",
            modifier = Modifier.size(80.dp),
            tint = AccentTeal
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Precisamos de sua Permissão",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "O CADÊ usa Localização Precisa, Bluetooth (BLE) e UWB para conectar você com seus contatos em locais movimentados. Sem eles, o app não funciona.",
            fontSize = 16.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = {
                if (permissionsState.allPermissionsGranted) {
                    onPermissionsGranted()
                } else {
                    permissionsState.launchMultiplePermissionRequest()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (permissionsState.allPermissionsGranted) "Continuar" else "Conceder Permissões",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
        
        if (permissionsState.allPermissionsGranted) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Permissões concedidas com sucesso!",
                color = AccentTeal,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
