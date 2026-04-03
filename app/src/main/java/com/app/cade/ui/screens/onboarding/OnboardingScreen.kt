package com.app.cade.ui.screens.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.cade.data.DiscoveredContact
import com.app.cade.ui.AppViewModel
import com.app.cade.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: AppViewModel,
    onFinish: () -> Unit
) {
    var currentStep by remember { mutableStateOf(1) }
    
    // Perfil States
    val userProfile by viewModel.userProfile.collectAsState()
    var name by remember { mutableStateOf(userProfile?.name ?: "") }
    var phone by remember { mutableStateOf(userProfile?.phone ?: "") }
    // Status State
    var visualStatus by remember { mutableStateOf(userProfile?.visualStatus ?: "") }
    // Resources State
    var useBluetooth by remember { mutableStateOf(true) }
    var useSound by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = BackgroundDark,
        bottomBar = {
            if (currentStep < 5) {
                Button(
                    onClick = { currentStep++ },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Próximo Passo", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                Button(
                    onClick = {
                        viewModel.saveProfile(name, phone, "1234", visualStatus)
                        viewModel.completeOnboarding()
                        onFinish()
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                ) {
                    Text("Concluir e Ir para o Radar", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BackgroundDark)
                }
            }
        }
    ) { paddingVals ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (currentStep) {
                1 -> WelcomeStep()
                2 -> ProfileStep(name, phone, { name = it }, { phone = it })
                3 -> StatusStep(visualStatus) { visualStatus = it }
                4 -> ResourcesStep(useBluetooth, useSound, { useBluetooth = it }, { useSound = it })
                5 -> ContactsStep(viewModel, name)
            }
        }
    }
}

@Composable
fun WelcomeStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Bem-vindo ao CADÊ", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryCyan)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Antes de começar a encontrar pessoas, vamos configurar o seu próprio perfil e definir suas preferências. É rápido e seguro!",
            fontSize = 18.sp, color = TextSecondary, textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ProfileStep(name: String, phone: String, onNameChange: (String) -> Unit, onPhoneChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(GlassWhite, RoundedCornerShape(24.dp)).padding(24.dp)) {
        Text("Passo 1: Meu Perfil", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Preencha seus dados para personalizar sua experiência.", color = TextSecondary)
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(value = name, onValueChange = onNameChange, label = { Text("Seu Nome") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = phone, onValueChange = onPhoneChange, label = { Text("Seu Telefone") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    }
}

@Composable
fun StatusStep(status: String, onStatusChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(GlassWhite, RoundedCornerShape(24.dp)).padding(24.dp)) {
        Text("Passo 2: Meu Status Visual", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text("O status ajuda a outra pessoa a te reconhecer visualmente na multidão. Você poderá mudar isso a qualquer momento no app.", color = TextSecondary)
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = status, onValueChange = onStatusChange, label = { Text("O que você está vestindo hoje?") }, 
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Ex: Jaqueta preta e boné verde") }
        )
    }
}

@Composable
fun ResourcesStep(useBle: Boolean, useSound: Boolean, onBleChange: (Boolean) -> Unit, onSoundChange: (Boolean) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(GlassWhite, RoundedCornerShape(24.dp)).padding(24.dp)) {
        Text("Passo 3: Recursos App", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Escolha os recursos que deseja utilizar para encontrar os outros.", color = TextSecondary)
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Radar Direcional (Bluetooth)", color = TextPrimary)
            Switch(checked = useBle, onCheckedChange = onBleChange)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Sonoplastia (Bipe de Radar)", color = TextPrimary)
            Switch(checked = useSound, onCheckedChange = onSoundChange)
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ContactsStep(viewModel: AppViewModel, myName: String) {
    val context = LocalContext.current
    val contactsPermission = rememberPermissionState(Manifest.permission.READ_CONTACTS)
    var phoneContacts by remember { mutableStateOf<List<DiscoveredContact>?>(null) }
    
    Column(modifier = Modifier.fillMaxWidth().background(GlassWhite, RoundedCornerShape(24.dp)).padding(24.dp)) {
        Text("Passo Final: Interlocutores", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Agora você pode adicionar ou convidar conhecidos. O radar necessitará parear com o aparelho de destino para apontar a direção correta.", color = TextSecondary)
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                if (contactsPermission.status.isGranted) {
                    phoneContacts = viewModel.fetchPhoneContacts()
                } else {
                    contactsPermission.launchPermissionRequest()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Escolher da Agenda Telefônica")
        }

        phoneContacts?.let { list ->
            Spacer(modifier = Modifier.height(8.dp))
            Text("Encontrados ${list.size} contatos. Eles serão importados na seção Contatos do app.", color = PrimaryCyan, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                val p = "Baixe o CADÊ e adicione meu convite secreto para nos encontrarmos no radar! Ass: $myName"
                val sendIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("sms:")
                    putExtra("sms_body", p)
                }
                context.startActivity(sendIntent)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
        ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Convidar por SMS")
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { /* Navegar pro QR depois */ },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Conectar por QR Code Pessoal")
        }
    }
}
