package com.app.cade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.cade.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(onRegistrationComplete: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visualStatus by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassWhite, RoundedCornerShape(24.dp))
                .padding(32.dp)
        ) {
            Text(
                text = "Cadê?",
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = PrimaryCyan
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Crie seu Perfil",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(32.dp))

            val textFieldColors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = PrimaryCyan,
                unfocusedBorderColor = GlassBorder,
                focusedLabelColor = PrimaryCyan,
                unfocusedLabelColor = TextSecondary,
                textColor = TextPrimary,
                cursorColor = PrimaryCyan
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome completo") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Telefone") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { if (it.length <= 4) password = it },
                label = { Text("Senha secreta (4 dígitos)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = visualStatus,
                onValueChange = { if (it.length <= 15) visualStatus = it },
                label = { Text("Status Visual (ex: Blusa Azul)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors,
                supportingText = { Text("${visualStatus.length}/15", color = TextSecondary) }
            )
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onRegistrationComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(BrandGradientHorizontal, RoundedCornerShape(16.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                Text("Entrar no App", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            TextButton(onClick = { /* TODO: Scanner QR Code */ }) {
                Icon(Icons.Default.QrCode, contentDescription = "QR Code", tint = PrimaryCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Adicionar via QR Code", color = PrimaryCyan, fontWeight = FontWeight.Medium)
            }
        }
    }
}
