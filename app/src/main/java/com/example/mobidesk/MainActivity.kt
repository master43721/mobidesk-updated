package com.example.mobidesk

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.graphicsLayer
import com.example.mobidesk.data.*
import com.example.mobidesk.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupabaseClientProvider.init(applicationContext)
        setContent {
            MobideskTheme {
                MobideskAppOrchestrator()
            }
        }
    }
}

enum class Screen {
    LOGIN, DASHBOARD, ADMIN_PANEL, VM_LOGIN, MOBILE_VIEW, PI_HANDOFF
}

@Composable
fun MobideskAppOrchestrator() {
    val supabase = SupabaseClientProvider.client
    val scope = rememberCoroutineScope()
    var currentScreen by rememberSaveable { mutableStateOf(Screen.LOGIN) }
    var authData by remember { mutableStateOf<VmDetails?>(null) }
    var isCheckingSession by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val session = supabase.auth.currentSessionOrNull()
        if (session != null) {
            try {
                val userUuid = session.user?.id ?: throw Exception("Invalid session")
                
                val student = supabase.postgrest.from("students")
                    .select { filter { eq("id", userUuid) } }
                    .decodeSingle<StudentProfile>()

                val vm = supabase.postgrest.from("vm_profiles")
                    .select { filter { eq("vm_username", student.vm_username) } }
                    .decodeSingle<VmProfile>()

                authData = VmDetails(
                    vm_username = student.vm_username,
                    current_ip = vm.current_ip ?: "135.119.92.61",
                    name = student.name,
                    assigned_vm = vm.assigned_vm,
                    rdp_port = vm.rdp_port
                )
                currentScreen = Screen.DASHBOARD
            } catch (e: Exception) {
                currentScreen = Screen.LOGIN
            }
        } else {
            currentScreen = Screen.LOGIN
        }
        isCheckingSession = false
    }

    if (isCheckingSession) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = RVRJC_Navy)
        }
    } else {
        Surface(modifier = Modifier.fillMaxSize(), color = BrightBackground) {
            when (currentScreen) {
                Screen.LOGIN -> {
                    RVRJC_LoginGateway(onLoginSuccess = { details ->
                        authData = details
                        currentScreen = Screen.DASHBOARD
                    })
                }

                Screen.DASHBOARD -> {
                    authData?.let { details ->
                        StudentResourceDashboard(
                            auth = details,
                            onModeSelected = { mode ->
                                currentScreen = if (mode == "mobile") Screen.VM_LOGIN else Screen.PI_HANDOFF
                            },
                            onLogout = { 
                                scope.launch {
                                    AuthRepository().logout()
                                    currentScreen = Screen.LOGIN 
                                }
                            }
                        )
                    }
                }

                Screen.VM_LOGIN -> {
                    VMLoginScreen(
                        vmIp = authData?.current_ip ?: "135.119.92.61",
                        vmUsername = authData?.vm_username ?: "user1",
                        rdpPort = authData?.rdp_port ?: "3389",
                        onConnectionInitiated = {
                            currentScreen = Screen.MOBILE_VIEW
                        },
                        onBack = { currentScreen = Screen.DASHBOARD }
                    )
                }

                Screen.ADMIN_PANEL -> {
                    AdminManagementPanel(
                        adminName = authData?.name ?: "Admin",
                        onLogout = { 
                            scope.launch {
                                AuthRepository().logout()
                                currentScreen = Screen.LOGIN 
                            }
                        }
                    )
                }

                Screen.MOBILE_VIEW -> {
                    BackHandler { currentScreen = Screen.DASHBOARD }
                    authData?.let { details ->
                        MobileRemoteView(
                            vmIp = details.current_ip,
                            onExit = { currentScreen = Screen.DASHBOARD }
                        )
                    }
                }

                Screen.PI_HANDOFF -> {
                    BackHandler { currentScreen = Screen.DASHBOARD }
                    authData?.let { details ->
                        PiTokenDeploymentCenter(
                            vmIp = details.current_ip,
                            onBack = { currentScreen = Screen.DASHBOARD }
                        )
                    }
                }
            }
        }
    }
}

// --- SCREEN 1: RVRJC LOGIN GATEWAY ---
@Composable
fun RVRJC_LoginGateway(onLoginSuccess: (VmDetails) -> Unit) {
    var userId by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // Blurred Background Image Simulation
                Box(modifier = Modifier.fillMaxSize()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(RVRJC_Navy.copy(alpha = 0.05f))
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(24.dp)
                        .border(BorderStroke(1.dp, BorderLight), RoundedCornerShape(24.dp))
                        .background(SurfaceWhite.copy(alpha = 0.95f), RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape,
                            color = RVRJC_Navy.copy(alpha = 0.1f)
                        ) {
                            Icon(
                                Icons.Default.School,
                                null,
                                modifier = Modifier.padding(16.dp),
                                tint = RVRJC_Navy
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "RVR & JC",
                                style = MaterialTheme.typography.titleLarge,
                                color = RVRJC_Navy,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.sp
                            )
                            Text(
                                "CLOUD HUB ACCESS",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = userId,
                            onValueChange = { userId = it },
                            label = { Text("Roll Number") },
                            placeholder = { Text("e.g. Y20IT001") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            leadingIcon = { Icon(Icons.Default.Badge, null, tint = RVRJC_Navy) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RVRJC_Navy,
                                unfocusedBorderColor = BorderLight,
                                focusedLabelColor = RVRJC_Navy
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = pin,
                            onValueChange = { pin = it },
                            label = { Text("Personal Access PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = RVRJC_Navy) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RVRJC_Navy,
                                unfocusedBorderColor = BorderLight,
                                focusedLabelColor = RVRJC_Navy
                            ),
                            singleLine = true
                        )

                        if (error != null) {
                            Surface(
                                color = ErrorRed.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    error!!,
                                    color = ErrorRed,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.02f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scale"
                        )

                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    error = null
                                    
                                    val repo = AuthRepository()
                                    val result = repo.authenticateStudent(userId, pin)
                                    
                                    isLoading = false
                                    
                                    result.onSuccess { details ->
                                        onLoginSuccess(details)
                                    }.onFailure { exception ->
                                        error = exception.message ?: "Authentication failed"
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .graphicsLayer(scaleX = scale, scaleY = scale),
                            colors = ButtonDefaults.buttonColors(containerColor = RVRJC_Navy),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("SECURE LOGIN", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            }
                        }
                    }
                }
    }
}

// --- SCREEN 1.5: VM LOGIN SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VMLoginScreen(vmIp: String, vmUsername: String, rdpPort: String, onConnectionInitiated: () -> Unit, onBack: () -> Unit) {
    var redirectCamera by remember { mutableStateOf(false) }
    var redirectMic by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            redirectCamera = true
        } else {
            redirectCamera = false
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            redirectMic = true
        } else {
            redirectMic = false
            Toast.makeText(context, "Microphone permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).background(BrightBackground),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().border(BorderStroke(1.dp, BorderLight), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
        ) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Dns, contentDescription = null, tint = RVRJC_Navy, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("VM INSTANCE LOGIN", style = MaterialTheme.typography.titleMedium, color = TextDark, fontWeight = FontWeight.Bold)
                Text("IP: $vmIp", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = "USERNAME: $vmUsername",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = RVRJC_Navy,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                Divider(color = BorderLight, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "HARDWARE REDIRECTION",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Redirect Camera", style = MaterialTheme.typography.bodyMedium, color = TextDark)
                    Switch(
                        checked = redirectCamera,
                        onCheckedChange = { 
                            if (it) {
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            } else {
                                redirectCamera = false
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = RVRJC_Navy, checkedTrackColor = RVRJC_Navy.copy(alpha = 0.5f))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Redirect Microphone", style = MaterialTheme.typography.bodyMedium, color = TextDark)
                    Switch(
                        checked = redirectMic,
                        onCheckedChange = { 
                            if (it) {
                                micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            } else {
                                redirectMic = false
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = RVRJC_Navy, checkedTrackColor = RVRJC_Navy.copy(alpha = 0.5f))
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { showBottomSheet = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RVRJC_Navy),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("OPEN RDP SESSION", fontWeight = FontWeight.Bold)
                }
                
                TextButton(onClick = onBack) {
                    Text("CANCEL", color = RVRJC_Maroon)
                }
            }
        }
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = SurfaceWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = RVRJC_Gold,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "The official Microsoft Windows App is required to open this session.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextDark,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = {
                        val rdpPackageId = "com.microsoft.rdc.androidx"
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$rdpPackageId")))
                        } catch (e: Exception) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$rdpPackageId")))
                        }
                        showBottomSheet = false
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RVRJC_Navy),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("GET WINDOWS APP", fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedButton(
                    onClick = {
                        // Construct the dynamic Microsoft RDP deep link
                        val rdpUrl = "rdp://full%20address=s:${vmIp}:${rdpPort}&username=s:.\\${vmUsername}"
                        if (redirectMic) { /* handle hardware flags if needed by the Microsoft app */ }
                        
                        val rdpIntent = Intent(Intent.ACTION_VIEW, Uri.parse(rdpUrl))
                        rdpIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

                        try {
                            context.startActivity(rdpIntent)
                            onConnectionInitiated()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Please install Microsoft Remote Desktop", Toast.LENGTH_LONG).show()
                            val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.microsoft.rdc.androidx"))
                            context.startActivity(playStoreIntent)
                        }
                        showBottomSheet = false
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    border = BorderStroke(1.dp, RVRJC_Navy),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("CONNECT RDP", color = RVRJC_Navy, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// --- SCREEN 2: STUDENT RESOURCE DASHBOARD ---
@Composable
fun StudentResourceDashboard(auth: VmDetails, onModeSelected: (String) -> Unit, onLogout: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("RVR & JC CLOUD HUB", style = MaterialTheme.typography.labelSmall, color = RVRJC_Navy, fontWeight = FontWeight.Bold)
                Text(auth.name, style = MaterialTheme.typography.headlineSmall, color = TextDark, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(SuccessGreen, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "ONLINE",
                    color = SuccessGreen,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
        Text("SELECT INFRASTRUCTURE ENDPOINT", style = MaterialTheme.typography.titleMedium, color = TextDark, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        ResourceCard(
            title = "MOBILE REMOTE WORKSPACE",
            desc = "Render cloud session on this mobile device",
            accent = RVRJC_Navy,
            icon = Icons.Default.Smartphone,
            isLoading = false,
            onClick = { onModeSelected("mobile") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        ResourceCard(
            title = "RASPBERRY PI BRIDGE",
            desc = "Handoff session to local campus workstation",
            accent = RVRJC_Maroon,
            icon = Icons.Default.Router,
            isLoading = false,
            onClick = { onModeSelected("pi") }
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        TextButton(
            onClick = onLogout,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("SECURELY LOGOUT")
        }
    }
}

@Composable
fun ResourceCard(title: String, desc: String, accent: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, isLoading: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, BorderLight), RoundedCornerShape(12.dp))
            .clickable(enabled = !isLoading) { onClick() },
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(accent.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent)
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = accent, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = accent, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BorderLight)
            }
        }
    }
}

// --- SCREEN 3: MOBILE REMOTE VIEW (LANDSCAPE) ---
@Composable
fun MobileRemoteView(vmIp: String, onExit: () -> Unit) {
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFE2E8F0))) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp).background(Color.White).border(1.dp, BorderLight)) {
            val gridStep = 50.dp.toPx()
            for (x in 0..(size.width / gridStep).toInt()) {
                drawLine(Color(0xFFF1F5F9), Offset(x * gridStep, 0f), Offset(x * gridStep, size.height), 1f)
            }
            for (y in 0..(size.height / gridStep).toInt()) {
                drawLine(Color(0xFFF1F5F9), Offset(0f, y * gridStep), Offset(size.width, y * gridStep), 1f)
            }
        }

        Row(modifier = Modifier.padding(32.dp).background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(8.dp)).padding(12.dp)) {
            Icon(Icons.Default.CloudQueue, contentDescription = null, tint = RVRJC_Navy)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("RVRJC REMOTE INSTANCE: $vmIp", color = TextDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("STATUS: CONNECTED", color = SuccessGreen, fontSize = 10.sp)
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)) {
            if (menuExpanded) {
                Column(
                    modifier = Modifier
                        .padding(bottom = 64.dp)
                        .background(SurfaceWhite, RoundedCornerShape(12.dp))
                        .border(1.dp, BorderLight, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = {}) { Icon(Icons.Default.Keyboard, null, tint = RVRJC_Navy) }
                    IconButton(onClick = {}) { Icon(Icons.Default.Settings, null, tint = RVRJC_Navy) }
                    IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = ErrorRed) }
                }
            }
            FloatingActionButton(
                onClick = { menuExpanded = !menuExpanded },
                containerColor = RVRJC_Navy,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(if (menuExpanded) Icons.Default.Close else Icons.Default.Menu, null)
            }
        }
    }
}

// --- SCREEN 4: PI TOKEN DEPLOYMENT CENTER (QR SCANNER) ---
@Composable
fun PiTokenDeploymentCenter(vmIp: String, onBack: () -> Unit) {
    var isScanned by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val scanLineOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 200.dp.value,
        animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "scanLine"
    )

    Column(
        modifier = Modifier.fillMaxSize().background(BrightBackground).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!isScanned) {
            Text(
                "SCAN PI QR CODE",
                style = MaterialTheme.typography.headlineSmall,
                color = RVRJC_Navy,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Align the QR code on the monitor within the frame",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            Box(
                modifier = Modifier
                    .size(280.dp)
                    .background(Color.Black, RoundedCornerShape(12.dp))
                    .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.3f)), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Scanning Frame
                Canvas(modifier = Modifier.size(200.dp)) {
                    val strokeWidth = 4.dp.toPx()
                    val cornerLength = 40.dp.toPx()
                    
                    // Top Left
                    drawLine(Color.White, Offset(0f, 0f), Offset(cornerLength, 0f), strokeWidth)
                    drawLine(Color.White, Offset(0f, 0f), Offset(0f, cornerLength), strokeWidth)
                    
                    // Top Right
                    drawLine(Color.White, Offset(size.width, 0f), Offset(size.width - cornerLength, 0f), strokeWidth)
                    drawLine(Color.White, Offset(size.width, 0f), Offset(size.width, cornerLength), strokeWidth)
                    
                    // Bottom Left
                    drawLine(Color.White, Offset(0f, size.height), Offset(cornerLength, size.height), strokeWidth)
                    drawLine(Color.White, Offset(0f, size.height), Offset(0f, size.height - cornerLength), strokeWidth)
                    
                    // Bottom Right
                    drawLine(Color.White, Offset(size.width, size.height), Offset(size.width - cornerLength, size.height), strokeWidth)
                    drawLine(Color.White, Offset(size.width, size.height), Offset(size.width, size.height - cornerLength), strokeWidth)

                    // Animated Scan Line
                    drawLine(
                        color = SuccessGreen,
                        start = Offset(0f, scanLineOffset * density),
                        end = Offset(size.width, scanLineOffset * density),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                
                Icon(
                    Icons.Default.QrCodeScanner,
                    null,
                    tint = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(100.dp)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = { isScanned = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RVRJC_Navy),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.CameraAlt, null)
                Spacer(modifier = Modifier.width(12.dp))
                Text("MOCK SCAN QR CODE", fontWeight = FontWeight.Bold)
            }
        } else {
            // Success State
            Icon(
                Icons.Default.CheckCircle,
                null,
                tint = SuccessGreen,
                modifier = Modifier.size(100.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "SESSION HANDOFF SUCCESSFUL",
                style = MaterialTheme.typography.titleLarge,
                color = TextDark,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Your VM session is now active on the classroom monitor for instance $vmIp. You can now use the physical keyboard and mouse.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Surface(
                color = RVRJC_Gold.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, RVRJC_Gold)
            ) {
                Text(
                    "REMOTE IP: $vmIp",
                    modifier = Modifier.padding(16.dp),
                    color = TextDark,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(64.dp))
        
        TextButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("RETURN TO DASHBOARD", color = RVRJC_Maroon)
        }
    }
}

// --- SCREEN 5: ADMIN INFRASTRUCTURE PANEL ---
@Composable
fun AdminManagementPanel(adminName: String, onLogout: () -> Unit) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("CAMPUS ADMINISTRATOR", style = MaterialTheme.typography.labelSmall, color = RVRJC_Navy, fontWeight = FontWeight.Bold)
        Text(adminName, style = MaterialTheme.typography.headlineSmall, color = TextDark, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Surface(color = RVRJC_Gold.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
            Text(
                "PRIVILEGES: FULL LAB MANAGEMENT",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = RVRJC_Maroon,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
        Text("INFRASTRUCTURE REAL-TIME MONITOR", style = MaterialTheme.typography.titleSmall, color = TextDark, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        AdminStatRow("ACTIVE VM POOL", "16 / 16 Available", SuccessGreen)
        AdminStatRow("STUDENT SESSIONS", "5 Active Connections", RVRJC_Navy)
        
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = BorderStroke(1.dp, BorderLight)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("TOTAL CLUSTER LOAD", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(progress = { 0.24f }, modifier = Modifier.weight(1f).height(8.dp), color = RVRJC_Maroon, trackColor = BorderLight, strokeCap = StrokeCap.Round)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("24%", color = TextDark, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { Toast.makeText(context, "Bridges Flushed Successfully", Toast.LENGTH_SHORT).show() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RVRJC_Maroon),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.FlashOn, null)
            Spacer(modifier = Modifier.width(12.dp))
            Text("FLUSH ACTIVE CONNECTIONS")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, BorderLight),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("TERMINATE ADMIN SESSION", color = TextMuted)
        }
    }
}

@Composable
fun AdminStatRow(label: String, value: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Row(modifier = Modifier.padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(value, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun VMLoginScreenPreview() {
    MobideskTheme {
        VMLoginScreen(vmIp = "135.119.92.61", vmUsername = "user1", rdpPort = "3389", onConnectionInitiated = {}, onBack = {})
    }
}

@Preview(showBackground = true)
@Composable
fun PiScannerPreview() {
    MobideskTheme {
        PiTokenDeploymentCenter(
            vmIp = "135.119.92.61",
            onBack = {}
        )
    }
}
