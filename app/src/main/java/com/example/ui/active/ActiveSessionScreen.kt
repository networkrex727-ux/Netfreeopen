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
