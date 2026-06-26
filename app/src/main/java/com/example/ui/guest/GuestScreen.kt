package com.example.ui.guest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestScreen(
    onNavigateToActive: (token: String, limitMB: Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: GuestViewModel = hiltViewModel()
) {
    val tokenInput by viewModel.tokenInput.collectAsState()
    val state by viewModel.state.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successDetails by viewModel.joinSuccessDetails.collectAsState()
    val activeSessions by viewModel.activeSessions.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showScanSimDialog by remember { mutableStateOf(false) }
    var simTokenToScan by remember { mutableStateOf("") }

    LaunchedEffect(successDetails) {
        successDetails?.let { (_, limitMB) ->
            onNavigateToActive(tokenInput, limitMB)
            viewModel.clearState()
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect to Bandwidth", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 450.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Enter Session Credentials",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "Please enter the 6-digit alphanumeric token displayed on the host device, or use the scanner below:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { viewModel.onTokenChanged(it) },
                    label = { Text("6-Digit Token") },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    ),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "XK9P2M",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("token_input_field"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { showScanSimDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("scan_qr_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan QR Code", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (state == GuestState.VALIDATING) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else {
                        Button(
                            onClick = { viewModel.connect() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("connect_button")
                        ) {
                            Text("Connect to Peer", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                )

                Text(
                    text = "Live Active Hosts",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start)
                )

                if (activeSessions.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No active hosts online right now",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Ask someone to start a session to share their bandwidth.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        activeSessions.forEach { session ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = session.hostEmail.ifEmpty { "Anonymous Host" },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            SuggestionChip(
                                                onClick = {},
                                                label = { Text(session.token, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
                                            )
                                            Text(
                                                text = "Limit: ${session.bandwidthLimit} MB",
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = { viewModel.connectToSession(session.token) },
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text("Join", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showScanSimDialog) {
            AlertDialog(
                onDismissRequest = { showScanSimDialog = false },
                title = { Text("QR Code Scanner") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Since you are on an emulator or running inside a development environment, you can simulate a QR code scan by typing the 6-digit token here:")
                        OutlinedTextField(
                            value = simTokenToScan,
                            onValueChange = { if (it.length <= 6) simTokenToScan = it.uppercase() },
                            placeholder = { Text("XK9P2M") },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                            modifier = Modifier.fillMaxWidth().testTag("sim_token_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showScanSimDialog = false
                            viewModel.onQRScanned(simTokenToScan)
                            simTokenToScan = ""
                        },
                        modifier = Modifier.testTag("sim_scan_confirm_button")
                    ) {
                        Text("Simulate Scan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showScanSimDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
