package com.example.mobilab

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import com.example.mobilab.data.*
import com.example.mobilab.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MobiLabTheme {
                MobiLabAppOrchestrator()
            }
        }
    }
}

enum class Screen {
    LOGIN, DASHBOARD, ADMIN_PANEL, VM_LOGIN, MOBILE_VIEW, PI_HANDOFF
}

@Composable
fun MobiLabAppOrchestrator() {
    var currentScreen by rememberSaveable { mutableStateOf(Screen.LOGIN) }
    var authData by remember { mutableStateOf<AuthResponse?>(null) }
    var sessionData by remember { mutableStateOf<SessionResponse?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = BrightBackground) {
        when (currentScreen) {
            Screen.LOGIN -> {
                RVRJC_LoginGateway(onLoginSuccess = { response ->
                    authData = response
                    currentScreen = if (response.role == UserRole.ADMIN) Screen.ADMIN_PANEL else Screen.DASHBOARD
                })
            }

            Screen.DASHBOARD -> {
                authData?.let { auth ->
                    StudentResourceDashboard(
                        auth = auth,
                        onModeSelected = { session, mode ->
                            sessionData = session
                            currentScreen = if (mode == "mobile") Screen.VM_LOGIN else Screen.PI_HANDOFF
                        },
                        onLogout = { currentScreen = Screen.LOGIN }
                    )
                }
            }

            Screen.VM_LOGIN -> {
                VMLoginScreen(
                    vmIp = sessionData?.vmIp ?: "135.119.92.61",
                    onConnectionInitiated = {
                        currentScreen = Screen.MOBILE_VIEW
                    },
                    onBack = { currentScreen = Screen.DASHBOARD }
                )
            }

            Screen.ADMIN_PANEL -> {
                AdminManagementPanel(
                    adminName = authData?.studentName ?: "Admin",
                    onLogout = { currentScreen = Screen.LOGIN }
                )
            }

            Screen.MOBILE_VIEW -> {
                BackHandler { currentScreen = Screen.DASHBOARD }
                sessionData?.let { session ->
                    MobileRemoteView(
                        session = session,
                        onExit = { currentScreen = Screen.DASHBOARD }
                    )
                }
            }

            Screen.PI_HANDOFF -> {
                BackHandler { currentScreen = Screen.DASHBOARD }
                sessionData?.let { session ->
                    PiTokenDeploymentCenter(
                        session = session,
                        onBack = { currentScreen = Screen.DASHBOARD }
                    )
                }
            }
        }
    }
}

// --- SCREEN 1: RVRJC LOGIN GATEWAY ---
@Composable
fun RVRJC_LoginGateway(onLoginSuccess: (AuthResponse) -> Unit) {
    var userId by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .border(BorderStroke(1.dp, BorderLight), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(RVRJC_Navy)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                )
                
                Text(
                    "RVR & JC COLLEGE OF ENGINEERING",
                    style = MaterialTheme.typography.titleSmall,
                    color = RVRJC_Navy,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "SECURE ACCESS PORTAL",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = userId,
                    onValueChange = { userId = it },
                    label = { Text("STUDENT / ADMIN ID") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RVRJC_Navy,
                        unfocusedBorderColor = BorderLight,
                        focusedTextColor = TextDark,
                        unfocusedTextColor = TextDark
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("ACCESS PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RVRJC_Navy,
                        unfocusedBorderColor = BorderLight,
                        focusedTextColor = TextDark,
                        unfocusedTextColor = TextDark
                    ),
                    singleLine = true
                )

                if (error != null) {
                    Text(error!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            error = null
                            val res = MockApiService.authenticateUser(userId, pin)
                            isLoading = false
                            if (res.success) onLoginSuccess(res) else error = res.errorMessage
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RVRJC_Navy, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("LOGIN TO CLOUD HUB", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- SCREEN 1.5: VM LOGIN SCREEN ---
@Composable
fun VMLoginScreen(vmIp: String, onConnectionInitiated: () -> Unit, onBack: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
                    text = "USERNAME: user1",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = RVRJC_Navy,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("VM PASSWORD") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RVRJC_Navy,
                        unfocusedBorderColor = BorderLight
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        isConnecting = true
                        val rdpUriString = "rdp://full%20address=s:$vmIp&username=s:user1&password=s:$password"
                        val rdpIntent = Intent(Intent.ACTION_VIEW, Uri.parse(rdpUriString))
                        
                        val packageManager = context.packageManager
                        val activities = packageManager.queryIntentActivities(rdpIntent, 0)
                        val isRdpClientInstalled = activities.isNotEmpty()

                        if (isRdpClientInstalled) {
                            context.startActivity(rdpIntent)
                            onConnectionInitiated()
                        } else {
                            Toast.makeText(context, "Microsoft Remote Desktop not found. Redirecting to Play Store...", Toast.LENGTH_LONG).show()
                            try {
                                val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.microsoft.rdc.android"))
                                context.startActivity(playStoreIntent)
                            } catch (e: Exception) {
                                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.microsoft.rdc.android"))
                                context.startActivity(webIntent)
                            }
                            isConnecting = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RVRJC_Navy),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text("OPEN RDP SESSION", fontWeight = FontWeight.Bold)
                    }
                }
                
                TextButton(onClick = onBack) {
                    Text("CANCEL", color = RVRJC_Maroon)
                }
            }
        }
    }
}

// --- SCREEN 2: STUDENT RESOURCE DASHBOARD ---
@Composable
fun StudentResourceDashboard(auth: AuthResponse, onModeSelected: (SessionResponse, String) -> Unit, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loadingMode by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("RVR & JC CLOUD HUB", style = MaterialTheme.typography.labelSmall, color = RVRJC_Navy, fontWeight = FontWeight.Bold)
                Text(auth.studentName ?: "Student", style = MaterialTheme.typography.headlineSmall, color = TextDark, fontWeight = FontWeight.Bold)
            }
            Surface(
                color = RVRJC_Gold.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, RVRJC_Gold)
            ) {
                Text(
                    "VM STATUS: ONLINE",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = RVRJC_Gold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
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
            isLoading = loadingMode == "mobile",
            onClick = {
                scope.launch {
                    loadingMode = "mobile"
                    val session = MockApiService.requestSession(auth.assignedVmId ?: "")
                    onModeSelected(session, "mobile")
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        ResourceCard(
            title = "RASPBERRY PI BRIDGE",
            desc = "Handoff session to local campus workstation",
            accent = RVRJC_Maroon,
            icon = Icons.Default.Router,
            isLoading = loadingMode == "pi",
            onClick = {
                scope.launch {
                    loadingMode = "pi"
                    val session = MockApiService.requestSession(auth.assignedVmId ?: "")
                    onModeSelected(session, "pi")
                }
            }
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
fun MobileRemoteView(session: SessionResponse, onExit: () -> Unit) {
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
                Text("RVRJC REMOTE INSTANCE: ${session.vmIp}", color = TextDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("STATUS: CONNECTED via ${session.protocol.uppercase()}", color = SuccessGreen, fontSize = 10.sp)
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

// --- SCREEN 4: PI TOKEN DEPLOYMENT CENTER ---
@Composable
fun PiTokenDeploymentCenter(session: SessionResponse, onBack: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "broadcast")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse),
        label = "alpha"
    )

    Column(
        modifier = Modifier.fillMaxSize().background(BrightBackground).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.size(280.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            border = BorderStroke(2.dp, RVRJC_Maroon)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.size(200.dp)) {
                    drawRect(RVRJC_Maroon, style = Stroke(width = 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(20f, 10f))))
                }
                Icon(Icons.Default.QrCodeScanner, null, tint = RVRJC_Maroon, modifier = Modifier.size(80.dp))
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
        Text(
            "STREAMING HANDOFF CREDENTIALS\nTO CAMPUS NETWORK WORKSTATION...",
            color = RVRJC_Maroon.copy(alpha = alpha),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Surface(color = RVRJC_Gold.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
            Text(
                "SECURE TOKEN: ${session.token.take(8).uppercase()}",
                modifier = Modifier.padding(8.dp),
                color = TextDark,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(64.dp))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = RVRJC_Maroon),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
            Spacer(modifier = Modifier.width(12.dp))
            Text("CANCEL DEPLOYMENT")
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
