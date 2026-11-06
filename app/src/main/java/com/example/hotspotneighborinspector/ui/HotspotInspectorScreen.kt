package com.example.hotspotneighborinspector.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hotspotneighborinspector.model.AutoRefreshInterval
import com.example.hotspotneighborinspector.model.ConnectedDevice
import com.example.hotspotneighborinspector.model.HotspotNetworkInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BgDark = Color(0xFF0F141C)
private val CardBg = Color(0xFF18202C)
private val CardBorder = Color(0xFF263244)
private val AccentBlue = Color(0xFF38BDF8)
private val AccentGreen = Color(0xFF22C55E)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecondary = Color(0xFF94A3B8)

@Composable
fun HotspotInspectorScreen(
    viewModel: HotspotInspectorViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var copiedIp by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(copiedIp) {
        if (copiedIp != null) {
            delay(1500L)
            copiedIp = null
        }
    }

    fun copyText(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
        copiedIp = value
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar("Copied $value")
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        containerColor = BgDark,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Hotspot Devices",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                StatusBadge(
                    isActive = uiState.networkInfo.isActive,
                    interfaceName = uiState.networkInfo.interfaceName
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Network Summary Card (clean horizontal rows so text never truncates)
            item {
                NetworkSummaryCard(
                    networkInfo = uiState.networkInfo,
                    connectedCount = uiState.allDevices.size,
                    onSelectInterface = { viewModel.selectInterface(it) },
                    onCopyGateway = {
                        if (uiState.networkInfo.localIp.contains(".")) {
                            copyText("Gateway IP", uiState.networkInfo.localIp)
                        }
                    }
                )
            }

            // Scan Button, Progress & Auto-Refresh Controls
            item {
                ScanControlsSection(
                    isScanning = uiState.isScanning,
                    scanProgress = uiState.scanProgress,
                    autoRefreshInterval = uiState.autoRefreshInterval,
                    searchQuery = uiState.searchQuery,
                    showSearch = uiState.allDevices.size > 1 || uiState.searchQuery.isNotEmpty(),
                    onScanClick = { viewModel.scanDevices() },
                    onAutoRefreshChange = { viewModel.setAutoRefreshInterval(it) },
                    onSearchChange = { viewModel.updateSearchQuery(it) }
                )
            }

            // Section Title
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Connected IP Addresses",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Text(
                        text = "${uiState.filteredDevices.size} found",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            // Device List or Empty State
            if (uiState.filteredDevices.isEmpty() && !uiState.isScanning) {
                item {
                    EmptyDeviceState()
                }
            } else {
                items(
                    items = uiState.filteredDevices,
                    key = { it.ipAddress }
                ) { device ->
                    ConnectedDeviceRow(
                        device = device,
                        isCopied = copiedIp == device.ipAddress,
                        onCopyIp = { copyText("IP Address", device.ipAddress) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StatusBadge(
    isActive: Boolean,
    interfaceName: String
) {
    val dotColor = if (isActive) AccentGreen else AccentBlue
    val label = if (isActive) {
        "Hotspot ($interfaceName)"
    } else if (interfaceName != "None") {
        "Network ($interfaceName)"
    } else {
        "Offline"
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun NetworkSummaryCard(
    networkInfo: HotspotNetworkInfo,
    connectedCount: Int,
    onSelectInterface: (String) -> Unit,
    onCopyGateway: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // If multiple network interfaces are active (e.g. Hotspot + Wi-Fi), allow switching
            if (networkInfo.availableInterfaces.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    networkInfo.availableInterfaces.forEach { iface ->
                        val selected = iface.interfaceName == networkInfo.interfaceName
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) AccentBlue.copy(alpha = 0.18f) else BgDark,
                            border = BorderStroke(
                                1.dp,
                                if (selected) AccentBlue else CardBorder
                            ),
                            modifier = Modifier.clickable { onSelectInterface(iface.interfaceName) }
                        ) {
                            Text(
                                text = if (iface.isHotspotInterface) {
                                    "${iface.interfaceName} (Hotspot)"
                                } else {
                                    iface.interfaceName
                                },
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) AccentBlue else TextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(color = CardBorder)
            }

            InfoRow(
                label = "Connected Devices",
                value = connectedCount.toString(),
                valueColor = AccentGreen
            )
            HorizontalDivider(color = CardBorder.copy(alpha = 0.6f))
            InfoRow(
                label = "Your / Gateway IP",
                value = networkInfo.localIp,
                valueColor = TextPrimary,
                onClick = onCopyGateway
            )
            HorizontalDivider(color = CardBorder.copy(alpha = 0.6f))
            InfoRow(
                label = "Subnet Mask",
                value = networkInfo.subnetMask,
                valueColor = TextSecondary
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = TextSecondary
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
private fun ScanControlsSection(
    isScanning: Boolean,
    scanProgress: Float,
    autoRefreshInterval: AutoRefreshInterval,
    searchQuery: String,
    showSearch: Boolean,
    onScanClick: () -> Unit,
    onAutoRefreshChange: (AutoRefreshInterval) -> Unit,
    onSearchChange: (String) -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = scanProgress,
        animationSpec = tween(200),
        label = "scan_progress"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onScanClick,
                enabled = !isScanning,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue,
                    contentColor = BgDark,
                    disabledContainerColor = CardBg,
                    disabledContentColor = TextSecondary
                ),
                contentPadding = PaddingValues(vertical = 14.dp, horizontal = 16.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = AccentBlue
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Scanning... ${(animatedProgress * 100).toInt()}%",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Scan Devices",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Simple Auto-refresh selector
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CardBg,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AutoRefreshInterval.entries.forEach { interval ->
                        val selected = autoRefreshInterval == interval
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) AccentBlue.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { onAutoRefreshChange(interval) }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = interval.label,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) AccentBlue else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = isScanning) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AccentBlue,
                trackColor = CardBg,
                strokeCap = StrokeCap.Round
            )
        }

        if (showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = {
                    Text(
                        text = "Search IP or device name...",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = TextSecondary
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardBg,
                    unfocusedContainerColor = CardBg,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ConnectedDeviceRow(
    device: ConnectedDevice,
    isCopied: Boolean,
    onCopyIp: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        border = BorderStroke(
            1.dp,
            if (isCopied) AccentGreen.copy(alpha = 0.7f) else CardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCopyIp() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Prominent IP Address
                Text(
                    text = device.ipAddress,
                    fontSize = 19.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // Device Name & Latency
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = device.deviceName,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (device.latencyMs != null) {
                        Text(
                            text = "• ${device.latencyMs} ms",
                            fontSize = 13.sp,
                            color = AccentGreen
                        )
                    }
                }

                // Only show MAC address if the system actually provided one
                if (!device.macAddress.isNullOrBlank()) {
                    Text(
                        text = "MAC: ${device.macAddress}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Clean Copy IP Button
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isCopied) AccentGreen.copy(alpha = 0.2f) else BgDark,
                border = BorderStroke(
                    1.dp,
                    if (isCopied) AccentGreen else CardBorder
                ),
                modifier = Modifier.clickable { onCopyIp() }
            ) {
                Text(
                    text = if (isCopied) "Copied" else "Copy IP",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isCopied) AccentGreen else AccentBlue,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyDeviceState() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "No Connected Devices Found",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = "Make sure your Mobile Hotspot is turned on and at least one device is connected, then tap Scan Devices.",
                fontSize = 13.sp,
                color = TextSecondary
            )
        }
    }
}
