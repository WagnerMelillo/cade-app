package com.app.cade.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.cade.ui.AppViewModel
import com.app.cade.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: AppViewModel,
    onFinish: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    val userProfile by viewModel.userProfile.collectAsState()
    var name by remember { mutableStateOf(userProfile?.name ?: "") }
    var phone by remember { mutableStateOf(userProfile?.phone ?: "") }
    var visualStatus by remember { mutableStateOf(userProfile?.visualStatus ?: "") }

    Scaffold(
        containerColor = BackgroundDark,
        bottomBar = {
            Button(
                onClick = {
                    viewModel.saveProfile(name, phone, "1234", visualStatus)
                    viewModel.completeOnboarding()
                    onFinish()
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
            ) {
                Text("Concordar e Começar", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BackgroundDark)
            }
        }
    ) { paddingVals ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Bem-vindo ao CADÊ", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryCyan)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Preencha seus dados rápidos para que as pessoas possam te encontrar pelo seu Radar Bluetooth.",
                fontSize = 16.sp, color = TextSecondary, textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            Column(modifier = Modifier.fillMaxWidth().background(GlassWhite, RoundedCornerShape(24.dp)).padding(24.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Seu Nome") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("Seu Celular") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = visualStatus, onValueChange = { visualStatus = it },
                    label = { Text("O que você está vestindo hoje?") }, 
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Ex: Boné azul e jaqueta") }
                )
            }
        }
    }
}
