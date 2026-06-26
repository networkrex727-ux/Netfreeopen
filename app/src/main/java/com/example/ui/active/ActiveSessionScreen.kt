package com.example.ui.active

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.webrtc.WebRTCConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(
    token: String,
    isHost: Boolean,
    limitMB: Long,
    onSessionEnded: () -> Unit,
    viewModel: ActiveSessionViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val speedMbps by viewModel.speedMbps.collectAsState()
    val usedMB by viewModel.usedMB.collectAsState()
    val remainingMB by viewModel.remainingMB.collectAsState()
    val progressPercent by viewModel.progressPercent.collectAsState()
    val elapsedTimeStr by viewModel.elapsedTimeStr.collectAsState()

    val simulatedResponse by viewModel.simulatedResponse.collectAsState()
    val isSimulating by viewModel.isSimulating.collectAsState()
    val publicIpInfo by viewModel.publicIpInfo.collectAsState()
    val isFetchingIp by viewModel.isFetchingIp.collectAsState()

    var testUrlInput by remember { mutableStateOf("https://api.github.com/zen") }
    var showExitConfirmation by remember { mutableStateOf(false) }

    BackHandler(enabled = connectionState == WebRTCConnectionState.CONNECTED) {
        showExitConfirmation = true
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("End Active Session?") },
            text = { Text("Navigating back will stop sharing/using bandwidth. If you want to keep sharing in the background, press your device's Home button instead.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmation = false
                        viewModel.disconnect()
                    }
                ) {
                    Text("End Session", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) {
                    Text("Keep Active")
                }
            }
        )
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val vpnPrepareIntent = remember { android.net.VpnService.prepare(context) }
    val vpnLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.addLog("✅ VPN permission granted by user.")
        } else {
            viewModel.addLog("❌ VPN permission denied. VPN tunnel won't start automatically.")
        }
    }

    LaunchedEffect(vpnPrepareIntent) {
        if (!isHost && vpnPrepareIntent != null) {
            viewModel.addLog("🔐 Requesting VPN authorization...")
            vpnLauncher.launch(vpnPrepareIntent)
        }
    }

    LaunchedEffect(token, isHost, limitMB) {
        viewModel.initSession(token, isHost, limitMB)
    }

    LaunchedEffect(connectionState) {
        if (connectionState == WebRTCConnectionState.DISCONNECTED) {
            onSessionEnded()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active P2P Session", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .background(
                        color = when (connectionState) {
                            WebRTCConnectionState.CONNECTED -> Color(0xFFE8F5E9)
                            WebRTCConnectionState.CONNECTING -> Color(0xFFFFFDE7)
                            else -> Color(0xFFFFEBEE)
                        },
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("connection_status_badge")
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            color = when (connectionState) {
                                WebRTCConnectionState.CONNECTED -> Color(0xFF4CAF50)
                                WebRTCConnectionState.CONNECTING -> Color(0xFFFFEB3B)
                                else -> Color(0xFFF44336)
                            }
                        )
                )

                Text(
                    text = when (connectionState) {
                        WebRTCConnectionState.CONNECTED -> "Connected ✓"
                        WebRTCConnectionState.CONNECTING -> "Connecting..."
                        else -> "Disconnected"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (connectionState) {
                        WebRTCConnectionState.CONNECTED -> Color(0xFF2E7D32)
                        WebRTCConnectionState.CONNECTING -> Color(0xFFF57F17)
                        else -> Color(0xFFC62828)
                    }
                )
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = if (isHost) {
                            "You are currently SHARING bandwidth. Tap your device's Home button to share in the background. The active speed and bandwidth statistics will show in your notification bar."
                        } else {
                            "You are currently USING shared bandwidth. All requests typed below route through the peer's connection. You can monitor progress in your notification bar."
                        },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            val connectionLogs by viewModel.connectionLogs.collectAsState()

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1E24)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF00FF00))
                            )
                            Text(
                                text = "P2P Handshake & Signal Logs",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "Live Stream",
                            fontSize = 11.sp,
                            color = Color(0xFF00FF00).copy(alpha = 0.8f),
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(Color.Black, shape = RoundedCornerShape(10.dp))
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (connectionLogs.isEmpty()) {
                            Text(
                                text = "Initializing signalling logs...",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            connectionLogs.forEach { log ->
                                Text(
                                    text = log,
                                    color = if (log.contains("❌") || log.contains("failed", ignoreCase = true) || log.contains("error", ignoreCase = true)) {
                                        Color(0xFFFF5252)
                                    } else if (log.contains("✅") || log.contains("connected", ignoreCase = true) || log.contains("success", ignoreCase = true)) {
                                        Color(0xFF69F0AE)
                                    } else if (log.contains("⚡") || log.contains("🚀")) {
                                        Color(0xFF40C4FF)
                                    } else if (log.contains("❄️") || log.contains("ICE")) {
                                        Color(0xFFFFD740)
                                    } else {
                                        Color(0xFFE0E0E0)
                                    },
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Elapsed Time",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = elapsedTimeStr,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("elapsed_time_text")
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Current Speed",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = String.format("%.2f Mbps", speedMbps) + if (isHost) " ↑" else " ↓",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("current_speed_text")
                            )
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bandwidth Transferred",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$usedMB MB / $limitMB MB",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("bandwidth_usage_text")
                            )
                        }

                        LinearProgressIndicator(
                            progress = progressPercent / 100f,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(50))
                                .testTag("bandwidth_progress_bar")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$progressPercent% Consumed",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "$remainingMB MB Remaining",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Security Gateway",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Tunnel IP Verification",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        IconButton(
                            onClick = { viewModel.fetchPublicIpAddress() },
                            enabled = !isFetchingIp && connectionState == WebRTCConnectionState.CONNECTED
                        ) {
                            if (isFetchingIp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Gateway",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Text(
                        text = if (isHost) {
                            "Below is your device's active public IP gateway. The guest's outgoing traffic will exit onto the public web from this location."
                        } else {
                            "Verify routing success below. When tunnel is active, this must reflect your Host's public IP address (rather than your SIM/WiFi IP)."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            publicIpInfo?.split("\n")?.forEach { line ->
                                Text(
                                    text = line,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (line.startsWith("IP:")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            if (!isHost) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "P2P Web Proxy Test Sandbox",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "Enter any HTTP endpoint to fetch URL content securely over the P2P direct WebRTC proxy tunnel:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = testUrlInput,
                                onValueChange = { testUrlInput = it },
                                placeholder = { Text("https://example.com") },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 14.sp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_url_input"),
                                shape = RoundedCornerShape(10.dp)
                            )

                            IconButton(
                                onClick = { viewModel.simulateFetchUrl(testUrlInput) },
                                enabled = !isSimulating && connectionState == WebRTCConnectionState.CONNECTED,
                                modifier = Modifier
                                    .background(
                                        color = if (connectionState == WebRTCConnectionState.CONNECTED) MaterialTheme.colorScheme.primary else Color.Gray,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .testTag("fetch_button")
                            ) {
                                if (isSimulating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Fetch",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }

                        simulatedResponse?.let { responseText ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .background(Color.Black, shape = RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = responseText,
                                    color = Color(0xFF00FF00),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.testTag("simulated_response_terminal")
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { viewModel.disconnect() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("disconnect_button")
            ) {
                Text(
                    text = if (isHost) "Stop Sharing" else "Disconnect Session",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
