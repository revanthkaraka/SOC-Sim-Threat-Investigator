package com.example

import android.app.Application
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AlertEntity
import kotlinx.coroutines.launch
import com.example.data.AlertRepository
import com.example.data.AppDatabase
import com.example.ui.AlertViewModel
import com.example.ui.theme.CopilotGradEnd
import com.example.ui.theme.CopilotGradStart
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.CriticalRedBg
import com.example.ui.theme.CriticalRedDark
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyberBg
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberLogBg
import com.example.ui.theme.CyberMuted
import com.example.ui.theme.CyberOnPrimary
import com.example.ui.theme.CyberPrimary
import com.example.ui.theme.CyberSubtext
import com.example.ui.theme.CyberText
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Initialize Room Database & Repository
                val context = LocalContext.current.applicationContext
                val database = remember { AppDatabase.getDatabase(context) }
                val repository = remember { AlertRepository(database.alertDao()) }
                
                // Get the ViewModel via Factory
                val viewModel: AlertViewModel = viewModel(
                    factory = AlertViewModel.Factory(context as Application, repository)
                )

                SocSimApp(viewModel)
            }
        }
    }
}

@Composable
fun SocSimApp(viewModel: AlertViewModel) {
    val alerts by viewModel.alerts.collectAsState()
    val selectedAlertId by viewModel.selectedAlertId.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val gameScore by viewModel.gameScore.collectAsState()
    val avgTriageTime by viewModel.averageTriageTime.collectAsState()
    val context = LocalContext.current

    // Display status messages using Android Toast to avoid cluttered screen toast alerts
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearStatusMessage()
        }
    }

    val selectedAlert = alerts.find { it.id == selectedAlertId }

    // Screen selection for compact views (0 = Alerts List, 1 = Triage Deck)
    var activeTab by remember { mutableStateOf(0) }
    
    // Main mode navigation: 0 = Triage, 1 = Phishing Sandbox, 2 = Leaderboard
    var mainTabSelected by remember { mutableStateOf(0) }

    // If an alert is selected, but we were on the alerts list, we can switch if needed,
    // though we keep it user-driven mostly.
    val selectAndNavigate: (Int) -> Unit = { id ->
        viewModel.selectAlert(id)
        activeTab = 1 // Automatically navigate to the Deck on mobile
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(CyberBg),
        bottomBar = {
            // Adaptive Navigation: Only display bottom navigation on mobile screens when in Triage mode
            BoxWithConstraints {
                if (maxWidth < 600.dp && mainTabSelected == 0) {
                    NavigationBar(
                        containerColor = CyberCard,
                        modifier = Modifier.border(width = (0.5).dp, color = CyberBorder, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    ) {
                        NavigationBarItem(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            icon = { Icon(Icons.Default.List, contentDescription = "Alert Queue") },
                            label = { Text("Alerts Feed", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyberPrimary,
                                unselectedIconColor = CyberMuted,
                                selectedTextColor = CyberPrimary,
                                unselectedTextColor = CyberMuted,
                                indicatorColor = CyberCard
                            )
                        )
                        NavigationBarItem(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            icon = { Icon(Icons.Default.Dashboard, contentDescription = "Triage Deck") },
                            label = { Text("Triage Deck", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyberPrimary,
                                unselectedIconColor = CyberMuted,
                                selectedTextColor = CyberPrimary,
                                unselectedTextColor = CyberMuted,
                                indicatorColor = CyberCard
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(CyberBg)
                .padding(innerPadding)
        ) {
            val isWideScreen = maxWidth >= 600.dp

            Column(modifier = Modifier.fillMaxSize()) {
                // Main Professional Cyber Header with HUD metrics
                HeaderBar(alertCount = alerts.size, gameScore = gameScore, avgTriageTime = avgTriageTime)

                // Modular Sub-Tab Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .background(CyberCard, shape = RoundedCornerShape(12.dp))
                        .border(width = (0.5).dp, color = CyberBorder, shape = RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val unresolvedCount = alerts.count { !it.status.equals("Resolved", ignoreCase = true) }
                    val tabItems = listOf(
                        Triple("INCIDENTS ($unresolvedCount)", Icons.Default.List, 0),
                        Triple("PHISHING DEEP-DIVE", Icons.Default.Mail, 1),
                        Triple("ANALYST STATS", Icons.Default.Leaderboard, 2)
                    )

                    tabItems.forEach { (title, icon, idx) ->
                        val isSelected = mainTabSelected == idx
                        val bg = if (isSelected) CyberPrimary else Color.Transparent
                        val textCol = if (isSelected) CyberOnPrimary else CyberMuted

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(bg, shape = RoundedCornerShape(8.dp))
                                .clickable { mainTabSelected = idx }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(imageVector = icon, contentDescription = title, tint = textCol, modifier = Modifier.size(14.dp))
                                Text(
                                    text = title,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textCol,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Render Active Panel
                when (mainTabSelected) {
                    1 -> {
                        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                            PhishingSandboxView(viewModel = viewModel)
                        }
                    }
                    2 -> {
                        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                            LeaderboardView(viewModel = viewModel)
                        }
                    }
                    else -> {
                        // Main Triage Center
                        if (isWideScreen) {
                            // Responsive Split View: Side-by-side Alerts list & Triage Deck
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Left Column: Alert Queue (35% width)
                                Box(
                                    modifier = Modifier
                                        .weight(0.35f)
                                        .fillMaxHeight()
                                ) {
                                    AlertQueuePane(
                                        alerts = alerts,
                                        selectedId = selectedAlertId,
                                        onSelect = { viewModel.selectAlert(it) }
                                    )
                                }

                                // Right Column: Bento Grid Details Panel (65% width)
                                Box(
                                    modifier = Modifier
                                        .weight(0.65f)
                                        .fillMaxHeight()
                                ) {
                                    if (selectedAlert != null) {
                                        BentoDeckPane(
                                            alert = selectedAlert,
                                            isAnalyzing = isAnalyzing,
                                            viewModel = viewModel
                                        )
                                    } else {
                                        EmptyStateView()
                                    }
                                }
                            }
                        } else {
                            // Mobile Compact view: Tab-driven view
                            Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                if (activeTab == 0) {
                                    AlertQueuePane(
                                        alerts = alerts,
                                        selectedId = selectedAlertId,
                                        onSelect = selectAndNavigate
                                    )
                                } else {
                                    if (selectedAlert != null) {
                                        BentoDeckPane(
                                            alert = selectedAlert,
                                            isAnalyzing = isAnalyzing,
                                            viewModel = viewModel
                                        )
                                    } else {
                                        EmptyStateView()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderBar(alertCount: Int, gameScore: Int, avgTriageTime: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberCard)
            .border(width = (0.5).dp, color = CyberBorder)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(CyberPrimary, shape = RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Shield Logo",
                    tint = CyberOnPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = "SOC-SIM PRO",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberText,
                    letterSpacing = 1.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(Color(0xFF00FF66), shape = CircleShape)
                    )
                    Text(
                        text = "ACTIVE ENGINE",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CyberMuted,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Gamified Score Badge
            Box(
                modifier = Modifier
                    .background(Color(0xFF1E281F), shape = RoundedCornerShape(6.dp))
                    .border(width = 1.dp, color = Color(0xFF00FF88).copy(alpha = 0.3f), shape = RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Score",
                        tint = Color(0xFF00FF88),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "SCORE: $gameScore",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00FF88),
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Time-to-Triage (TTT) Badge
            Box(
                modifier = Modifier
                    .background(Color(0xFF1B2230), shape = RoundedCornerShape(6.dp))
                    .border(width = 1.dp, color = CyanAccent.copy(alpha = 0.3f), shape = RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Avg TTT",
                        tint = CyanAccent,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "AVG TTT: ${avgTriageTime}s",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Active Alert Counter
            Box(
                modifier = Modifier
                    .background(CriticalRedBg, shape = RoundedCornerShape(6.dp))
                    .border(width = 1.dp, color = CriticalRed.copy(alpha = 0.3f), shape = RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "$alertCount INCIDENTS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = CriticalRed,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
fun AlertQueuePane(
    alerts: List<AlertEntity>,
    selectedId: Int?,
    onSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "ALERTS QUEUE",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = CyberSubtext,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )

        if (alerts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CyberCard, shape = RoundedCornerShape(16.dp))
                    .border(width = 1.dp, color = CyberBorder, shape = RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("No incidents in queue.", color = CyberMuted)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                alerts.forEach { alert ->
                    AlertQueueCard(
                        alert = alert,
                        isSelected = alert.id == selectedId,
                        onClick = { onSelect(alert.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun AlertQueueCard(
    alert: AlertEntity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val severityColor = when (alert.severity.uppercase()) {
        "CRITICAL" -> CriticalRed
        "HIGH" -> Color(0xFFFFB300) // Dark Yellow/Orange
        "MEDIUM" -> Color(0xFF4FC3F7) // Soft Cyan
        else -> Color(0xFFAED581) // Soft Green
    }

    val severityBg = when (alert.severity.uppercase()) {
        "CRITICAL" -> CriticalRedBg
        "HIGH" -> Color(0xFF261D03)
        "MEDIUM" -> Color(0xFF0C1D26)
        else -> Color(0xFF102008)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, delayMillis = 100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // Pulsing border for selected or critical items
    val borderModifier = if (isSelected) {
        Modifier.border(
            width = (1.5).dp,
            color = CyberPrimary,
            shape = RoundedCornerShape(16.dp)
        )
    } else if (alert.severity.uppercase() == "CRITICAL") {
        Modifier.border(
            width = 1.dp,
            color = CriticalRed.copy(alpha = borderAlpha),
            shape = RoundedCornerShape(16.dp)
        )
    } else {
        Modifier.border(
            width = 1.dp,
            color = CyberBorder,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
            .testTag("alert_item_${alert.id}")
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CyberCard.copy(alpha = 0.8f) else CyberCard
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Severity indicator circle/pill
            Box(
                modifier = Modifier
                    .size(width = 72.dp, height = 24.dp)
                    .background(severityBg, shape = RoundedCornerShape(12.dp))
                    .border(width = 0.5.dp, color = severityColor.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = alert.severity,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = severityColor,
                    letterSpacing = 0.5.sp
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "IP: ${alert.sourceIp}",
                        fontSize = 11.sp,
                        color = CyberMuted
                    )
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = CyberMuted
                    )
                    Text(
                        text = alert.timestamp.substringAfter(" "),
                        fontSize = 10.sp,
                        color = CyberMuted
                    )
                }
            }

            // Status Badge
            val statusColor = when (alert.status.lowercase()) {
                "resolved" -> Color(0xFF00FF66)
                "investigating" -> CyanAccent
                else -> Color(0xFFFFA500)
            }
            Box(
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = alert.status.uppercase(),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = statusColor
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BentoDeckPane(
    alert: AlertEntity,
    isAnalyzing: Boolean,
    viewModel: AlertViewModel
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Active Incident Title / Quick Metrics (col-span-2, row-span-1)
        BentoHeaderCard(alert = alert)

        // Feature 1: Interactive Network Blast Radius Map
        BentoBlastRadiusCard(alert = alert)

        // Raw logs + Risk score side-by-side or stacked dynamically
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2
        ) {
            // Raw Logs Tile (occupies 1f weight on wide screens, full on narrow)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 280.dp)
            ) {
                BentoLogsCard(alert = alert, viewModel = viewModel)
            }

            // Risk Score Card (occupies 1f weight on wide, full on narrow)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 280.dp)
            ) {
                BentoRiskScoreCard(alert = alert)
            }
        }

        // Feature 2: MITRE ATT&CK Matrix Mapping Card
        BentoMitreMappingCard(alert = alert)

        // Gemini Copilot Threat Intelligence Tile (Bento Gradient block)
        BentoCopilotCard(
            alert = alert,
            isAnalyzing = isAnalyzing,
            viewModel = viewModel
        )

        // Feature 3: One-Click SOAR Automation Remediation Controller
        BentoSOARCard(alert = alert, viewModel = viewModel)

        // Triage Panel & Decision Center (Status, True/False Pos, Custom notes, Report actions)
        BentoTriageActionsCard(alert = alert, viewModel = viewModel)
    }
}

@Composable
fun BentoHeaderCard(alert: AlertEntity) {
    val severityColor = when (alert.severity.uppercase()) {
        "CRITICAL" -> CriticalRed
        "HIGH" -> Color(0xFFFFB300)
        "MEDIUM" -> Color(0xFF4FC3F7)
        else -> Color(0xFFAED581)
    }

    val severityBg = when (alert.severity.uppercase()) {
        "CRITICAL" -> CriticalRedBg
        "HIGH" -> Color(0xFF261D03)
        "MEDIUM" -> Color(0xFF0C1D26)
        else -> Color(0xFF102008)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = CyberBorder, shape = RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberCard),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(severityBg, shape = RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = alert.severity,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = severityColor,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Text(
                        text = alert.timestamp,
                        fontSize = 11.sp,
                        color = CyberMuted
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = alert.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CyberText
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetricText(label = "SRC IP", value = alert.sourceIp)
                    MetricText(label = "DEST PORT", value = alert.destinationPort)
                }
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(severityBg, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (alert.severity.uppercase() == "CRITICAL" || alert.severity.uppercase() == "HIGH") {
                        Icons.Default.Warning
                    } else {
                        Icons.Default.Info
                    },
                    contentDescription = "Alert status icon",
                    tint = severityColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun MetricText(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = CyberMuted,
            letterSpacing = 0.5.sp
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = CyberSubtext
        )
    }
}

@Composable
fun BentoLogsCard(alert: AlertEntity, viewModel: AlertViewModel) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .border(width = 1.dp, color = CyberBorder, shape = RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberCard),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Console",
                        tint = CyberPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "SIEM & FIREWALL RAW LOGS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberPrimary,
                        letterSpacing = 0.5.sp
                    )
                }

                IconButton(
                    onClick = { viewModel.copyToClipboard(context, alert.rawLog, "Raw Logs") },
                    modifier = Modifier.size(28.dp).testTag("copy_logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy log data",
                        tint = CyberMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CyberLogBg, shape = RoundedCornerShape(10.dp))
                    .border(width = 0.5.dp, color = CyberBorder, shape = RoundedCornerShape(10.dp))
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = alert.rawLog,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00FFCC), // Hacker-cyan console text
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
fun BentoRiskScoreCard(alert: AlertEntity) {
    val score = when (alert.severity.uppercase()) {
        "CRITICAL" -> 94
        "HIGH" -> 78
        "MEDIUM" -> 46
        else -> 18
    }

    val riskLabel = when {
        score >= 80 -> "CRITICAL THREAT"
        score >= 40 -> "MITIGATED RISK"
        else -> "LOW SUSPICION"
    }

    val glowColor = when {
        score >= 80 -> CriticalRed
        score >= 40 -> Color(0xFFFFB300)
        else -> Color(0xFFAED581)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .border(width = 1.dp, color = CyberBorder, shape = RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberCard),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Assessment,
                    contentDescription = "Risk Metrics",
                    tint = CyberMuted,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "THREAT SCORE METRIC",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberMuted,
                    letterSpacing = 0.5.sp
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Risk outer dial simulator (Simple circular representation)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(width = 4.dp, color = glowColor.copy(alpha = 0.2f), shape = CircleShape)
                    )
                    // Visual indicator arc simulator
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .border(width = 2.dp, color = glowColor, shape = CircleShape)
                    )
                    Text(
                        text = "$score",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Light,
                        color = CyberText,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = riskLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = glowColor,
                    letterSpacing = 0.5.sp
                )
            }

            Text(
                text = "Derived from security vectors, target asset value, and attacker attempts.",
                fontSize = 9.sp,
                color = CyberMuted,
                lineHeight = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun BentoCopilotCard(
    alert: AlertEntity,
    isAnalyzing: Boolean,
    viewModel: AlertViewModel
) {
    val hasAnalysis = alert.aiExplanation != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (hasAnalysis) CyberPrimary.copy(alpha = 0.3f) else CyberBorder,
                shape = RoundedCornerShape(24.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(CopilotGradStart, CopilotGradEnd)
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(CyberPrimary.copy(alpha = 0.15f), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Copilot Spark",
                                tint = CyberPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "GEMINI THREAT COPILOT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = CyberPrimary,
                            letterSpacing = 1.sp
                        )
                    }

                    if (!hasAnalysis && !isAnalyzing) {
                        Box(
                            modifier = Modifier
                                .background(CyanAccent.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "READY",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent
                            )
                        }
                    }
                }

                if (isAnalyzing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = CyberPrimary,
                            modifier = Modifier.size(32.dp).testTag("ai_loading_indicator")
                        )
                        Text(
                            text = "Analyzing firewall structures and payloads...",
                            fontSize = 11.sp,
                            color = CyberPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else if (hasAnalysis) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Plain english explanation
                        Column {
                            Text(
                                text = "ATTACK VECTOR EXPLANATION",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberPrimary.copy(alpha = 0.8f),
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = alert.aiExplanation ?: "",
                                fontSize = 12.sp,
                                color = CyberText,
                                lineHeight = 16.sp
                            )
                        }

                        HorizontalDivider(color = CyberPrimary.copy(alpha = 0.15f))

                        // Actions Playbook
                        Column {
                            Text(
                                text = "INCIDENT RESPONSE PLAYBOOK",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberPrimary.copy(alpha = 0.8f),
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            // Playbook block with custom styling
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.25f), shape = RoundedCornerShape(10.dp))
                                    .border(width = 0.5.dp, color = CyberPrimary.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = alert.aiPlaybook ?: "",
                                    fontSize = 11.sp,
                                    color = CyberSubtext,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.runAiDiagnostics(alert) },
                                modifier = Modifier.height(36.dp).testTag("reanalyze_button"),
                                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                    brush = Brush.linearGradient(listOf(CyberPrimary, CyanAccent))
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberPrimary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh analysis",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Re-Analyze", fontSize = 10.sp)
                            }
                        }
                    }
                } else {
                    // Empty/Call to action state
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Need intelligent IR guidance on this security threat?",
                            fontSize = 12.sp,
                            color = CyberSubtext,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { viewModel.runAiDiagnostics(alert) },
                            modifier = Modifier.height(44.dp).testTag("run_ai_diagnostics_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberPrimary,
                                contentColor = CyberOnPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Run Diagnostic",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "RUN AI DIAGNOSTICS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BentoTriageActionsCard(alert: AlertEntity, viewModel: AlertViewModel) {
    val context = LocalContext.current
    var notesText by remember(alert.id) { mutableStateOf(alert.userNotes ?: "") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = CyberBorder, shape = RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberCard),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Threat classification actions",
                    tint = CyberMuted,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "INVESTIGATOR DECISION CENTER",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberMuted,
                    letterSpacing = 0.5.sp
                )
            }

            // Triage Classification: True Positive / False Positive (Bento Grid Button layout)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.updateAlertClassification(alert, "True Positive") },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("true_positive_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (alert.classification == "True Positive") Color(0xFF0D533A) else Color(0xFF1E2B24),
                        contentColor = if (alert.classification == "True Positive") Color(0xFF00FF88) else CyberMuted
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = borderAlphaOnSelection(alert.classification == "True Positive", Color(0xFF00FF88))
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "TP",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("True Positive", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { viewModel.updateAlertClassification(alert, "False Positive") },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("false_positive_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (alert.classification == "False Positive") Color(0xFF531E21) else Color(0xFF2C1E20),
                        contentColor = if (alert.classification == "False Positive") CriticalRed else CyberMuted
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = borderAlphaOnSelection(alert.classification == "False Positive", CriticalRed)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = "FP",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("False Positive", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = CyberBorder.copy(alpha = 0.5f))

            // Incident Lifecycle Status Triage (New, Investigating, Resolved)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "INCIDENT LIFECYCLE STATUS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberMuted,
                    letterSpacing = 0.5.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("New", "Investigating", "Resolved").forEach { status ->
                        val active = alert.status.equals(status, ignoreCase = true)
                        val activeColor = when (status.lowercase()) {
                            "resolved" -> Color(0xFF00FF66)
                            "investigating" -> CyanAccent
                            else -> Color(0xFFFFA500)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .background(
                                    if (active) activeColor.copy(alpha = 0.15f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (active) activeColor else CyberBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.updateAlertStatus(alert, status) }
                                .testTag("status_chip_${status.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = status,
                                fontSize = 10.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) activeColor else CyberMuted
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = CyberBorder.copy(alpha = 0.5f))

            // Investigator Dynamic Notes Input
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "INVESTIGATOR NOTES",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberMuted,
                    letterSpacing = 0.5.sp
                )

                OutlinedTextField(
                    value = notesText,
                    onValueChange = {
                        notesText = it
                        viewModel.updateAlertNotes(alert, it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notes_input_field"),
                    placeholder = { Text("Log analysis notes, indicators of compromise (IoC), or custom steps...", fontSize = 11.sp, color = CyberMuted) },
                    textStyle = TextStyle(color = CyberText, fontSize = 12.sp),
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CyberLogBg,
                        unfocusedContainerColor = CyberLogBg,
                        focusedTextColor = CyberText,
                        unfocusedTextColor = CyberText,
                        focusedIndicatorColor = CyberPrimary,
                        unfocusedIndicatorColor = CyberBorder
                    ),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Download & Share Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val report = viewModel.generateIncidentReport(alert)
                        viewModel.copyToClipboard(context, report, "Incident Report")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("copy_report_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberPrimary),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = Brush.linearGradient(listOf(CyberBorder, CyberBorder))
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy report",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Report", fontSize = 11.sp)
                }

                Button(
                    onClick = {
                        val report = viewModel.generateIncidentReport(alert)
                        viewModel.shareIncidentReport(context, report, alert.title)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("share_report_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberPrimary,
                        contentColor = CyberOnPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share report",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share Incident", fontSize = 11.sp)
                }
            }
        }
    }
}

// Inline style utility for input and buttons
private fun borderAlphaOnSelection(isSelected: Boolean, activeColor: Color): androidx.compose.foundation.BorderStroke {
    return androidx.compose.foundation.BorderStroke(
        width = if (isSelected) (1.5).dp else 1.dp,
        color = if (isSelected) activeColor else CyberBorder
    )
}

// Re-usable type safe custom Text style because standard material uses typography which might not match exact font sizes
private fun TextStyle(color: Color, fontSize: androidx.compose.ui.unit.TextUnit) = androidx.compose.ui.text.TextStyle(
    color = color,
    fontSize = fontSize,
    fontFamily = FontFamily.SansSerif
)

@Composable
fun EmptyStateView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberCard, shape = RoundedCornerShape(24.dp))
            .border(width = 1.dp, color = CyberBorder, shape = RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Security Status",
                tint = CyberMuted,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "No Alert Selected",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = CyberSubtext
            )
            Text(
                text = "Select an active security incident from the alerts queue feed to begin triage investigation.",
                fontSize = 11.sp,
                color = CyberMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
fun BentoMitreMappingCard(alert: AlertEntity) {
    val tactic = alert.mitreTactic ?: "Initial Access"
    val technique = alert.mitreTechnique ?: "T1566 (Phishing)"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = CyberBorder, shape = RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberCard),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Assessment,
                    contentDescription = "MITRE ATT&CK",
                    tint = CyberMuted,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "MITRE ATT&CK FRAMEWORK MAPPING",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberMuted,
                    letterSpacing = 0.5.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(CyberLogBg, shape = RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text("TACTIC CATEGORY", fontSize = 8.sp, color = CyberMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(tactic, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberPrimary)
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1.5f)
                        .background(CyberLogBg, shape = RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text("TECHNIQUE ID & NAME", fontSize = 8.sp, color = CyberMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(technique, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB300))
                    }
                }
            }

            // Interactive matrix bar
            val tacticsList = listOf("Recon", "Initial Access", "Execution", "Defense Evasion", "Credential Access", "Discovery", "Exfiltration")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("TACTICAL MATRIX TRACKER", fontSize = 8.sp, color = CyberMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tacticsList.forEach { item ->
                        val isCurrent = tactic.contains(item, ignoreCase = true) || item.contains(tactic, ignoreCase = true)
                        val bg = if (isCurrent) CyberPrimary.copy(alpha = 0.2f) else CyberLogBg.copy(alpha = 0.5f)
                        val borderCol = if (isCurrent) CyberPrimary else CyberBorder.copy(alpha = 0.3f)
                        val textCol = if (isCurrent) CyberPrimary else CyberMuted.copy(alpha = 0.7f)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp)
                                .background(bg, shape = RoundedCornerShape(4.dp))
                                .border(width = 0.5.dp, color = borderCol, shape = RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item,
                                fontSize = 6.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = textCol,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BentoBlastRadiusCard(alert: AlertEntity) {
    // Track clicked node for interactive detail view
    var selectedNodeDetails by remember { mutableStateOf<String?>(null) }
    var selectedNodeTitle by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = CyberBorder, shape = RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberCard),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Blast Radius Map",
                    tint = CyberMuted,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "NETWORK TOPOLOGY & ATTACK BLAST RADIUS MAP",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberMuted,
                    letterSpacing = 0.5.sp
                )
            }

            // Topology Map Visual Layout
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .background(CyberLogBg, shape = RoundedCornerShape(12.dp))
                    .border(width = 0.5.dp, color = CyberBorder, shape = RoundedCornerShape(12.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Node 1: Attacker
                    TopologyNode(
                        title = "ATTACKER",
                        sub = alert.sourceIp ?: "104.22.4.12",
                        color = CriticalRed,
                        onClick = {
                            selectedNodeTitle = "Threat Actor Source IP"
                            selectedNodeDetails = "IP: ${alert.sourceIp ?: "104.22.4.12"}. Initiating vector logs show continuous anomalous payloads targeting local services."
                        }
                    )

                    // Connecting Line 1
                    Text("➔", color = CyberMuted, fontSize = 16.sp)

                    // Node 2: Firewall
                    TopologyNode(
                        title = "FIREWALL",
                        sub = "Edge-ASA",
                        color = Color(0xFFFFA500),
                        onClick = {
                            selectedNodeTitle = "Edge Security Gateway"
                            selectedNodeDetails = "Gateway bypassed. Inbound ACL rule injection pending to drop malicious traffic from source."
                        }
                    )

                    // Connecting Line 2
                    Text("➔", color = CyberMuted, fontSize = 16.sp)

                    // Node 3: Target Server
                    val isDb = alert.destinationPort?.contains("3306") == true || alert.destinationPort?.contains("5432") == true
                    val hostTitle = if (isDb) "INTRA-DB" else "DMZ-WEB"
                    TopologyNode(
                        title = hostTitle,
                        sub = "Port ${alert.destinationPort ?: "80"}",
                        color = CriticalRed,
                        onClick = {
                            selectedNodeTitle = "Target Intranet Node"
                            selectedNodeDetails = "Target Node compromised via Port ${alert.destinationPort ?: "80"}. Status: LIVE THREAT CONTAINMENT MANDATORY."
                        }
                    )
                }
            }

            // Dynamic detail drawer upon clicking any node
            if (selectedNodeDetails != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF140F0F), shape = RoundedCornerShape(8.dp))
                        .border(width = 0.5.dp, color = CriticalRed.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedNodeTitle!!.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = CriticalRed
                            )
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "Close details",
                                tint = CyberMuted,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable {
                                        selectedNodeDetails = null
                                        selectedNodeTitle = null
                                    }
                            )
                        }
                        Text(
                            text = selectedNodeDetails!!,
                            fontSize = 11.sp,
                            color = CyberSubtext
                        )
                    }
                }
            } else {
                Text(
                    text = "💡 Tap on any network node to display real-time isolation status & threat vectors.",
                    fontSize = 10.sp,
                    color = CyberMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun TopologyNode(
    title: String,
    sub: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(color.copy(alpha = 0.15f), shape = CircleShape)
                .border(width = 1.5.dp, color = color, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Computer,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CyberText)
        Text(sub, fontSize = 8.sp, color = CyberMuted)
    }
}

@Composable
fun BentoSOARCard(alert: AlertEntity, viewModel: AlertViewModel) {
    val script = alert.remediationScript
    val context = LocalContext.current
    var isDeploying by remember { mutableStateOf(false) }
    var deploymentLog by remember { mutableStateOf(listOf<String>()) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = CyberBorder, shape = RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberCard),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "SOAR Automation",
                        tint = CyberMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "SOAR AUTOMATED MITIGATION CONTROLS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberMuted,
                        letterSpacing = 0.5.sp
                    )
                }

                if (!script.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF004D20), shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("PYTHON READY", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00FF66))
                    }
                }
            }

            if (script.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberLogBg, shape = RoundedCornerShape(10.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No Mitigation Script Generated",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberMuted
                        )
                        Text(
                            text = "Please tap 'Run AI Diagnostics' in the Threat Intel panel above to generate a Python SOAR response script.",
                            fontSize = 10.sp,
                            color = CyberMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Display python script in terminal
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberLogBg, shape = RoundedCornerShape(12.dp))
                        .border(width = 0.5.dp, color = CyberBorder, shape = RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("mitigate_threat.py", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = CyberMuted)
                            IconButton(
                                onClick = {
                                    viewModel.copyToClipboard(context, script, "Remediation Script")
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy script",
                                    tint = CyberPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        
                        Text(
                            text = script,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF88FF88),
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .height(110.dp)
                        )
                    }
                }

                // Deploy simulation triggers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isDeploying = true
                            deploymentLog = listOf("[+] Opening SSH tunnel to Edge Gateways...", "[+] Authenticating session token...")
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(600)
                                deploymentLog = deploymentLog + "[+] Blocking source attacker IP: ${alert.sourceIp ?: "104.22.4.12"} via ACL rules."
                                kotlinx.coroutines.delay(600)
                                deploymentLog = deploymentLog + "[+] Terminating connections on target port ${alert.destinationPort ?: "80"}."
                                kotlinx.coroutines.delay(600)
                                deploymentLog = deploymentLog + "[✔] SUCCESS: Host isolated. Attack contained successfully!"
                                isDeploying = false
                                viewModel.updateAlertStatus(alert, "Resolved")
                            }
                        },
                        enabled = !isDeploying,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("deploy_script_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0D533A),
                            contentColor = Color(0xFF00FF88)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Deploy", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("DEPLOY AUTOMATION MITIGATION", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // If executing, show terminal stdout
                if (deploymentLog.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black, shape = RoundedCornerShape(8.dp))
                            .border(width = 0.5.dp, color = Color(0xFF00FF66).copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("CONSOLE STDOUT", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            deploymentLog.forEach { logLine ->
                                Text(
                                    text = logLine,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (logLine.contains("✔") || logLine.contains("SUCCESS")) Color(0xFF00FF66) else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PhishingSandboxView(viewModel: AlertViewModel) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var isAnalyzingPhishing by remember { mutableStateOf(false) }
    var analysisResult by remember { mutableStateOf<com.example.api.PhishingAnalysis?>(null) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome and intro card
        Card(
            modifier = Modifier.fillMaxWidth().border(width = 1.dp, color = CyberBorder, shape = RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = CyberCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.Mail, contentDescription = "Phishing Mail", tint = CyberPrimary, modifier = Modifier.size(20.dp))
                    Text("PHISHING HEADER & URL DEEP-DIVE SANDBOX", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberPrimary, letterSpacing = 0.5.sp)
                }
                Text(
                    text = "Forensically analyze suspicious emails and link targets. Paste standard SMTP headers or suspicious URL targets below to extract DNS records, SPF alignment warnings, phishing triggers, and response indicators.",
                    fontSize = 11.sp,
                    color = CyberSubtext
                )
            }
        }

        // Input Card
        Card(
            modifier = Modifier.fillMaxWidth().border(width = 1.dp, color = CyberBorder, shape = RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = CyberCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("SUSPICIOUS TELEMETRY ENVELOPE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberMuted)
                
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.fillMaxWidth().height(120.dp).testTag("phishing_input_field"),
                    placeholder = { Text("Paste raw email headers (e.g. SMTP Received, From, SPF records) or web links (e.g. http://secure-verify-update.com)...", fontSize = 11.sp, color = CyberMuted) },
                    textStyle = TextStyle(color = CyberText, fontSize = 11.sp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CyberLogBg,
                        unfocusedContainerColor = CyberLogBg,
                        focusedTextColor = CyberText,
                        unfocusedTextColor = CyberText,
                        focusedIndicatorColor = CyberPrimary,
                        unfocusedIndicatorColor = CyberBorder
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            if (inputText.isBlank()) {
                                Toast.makeText(context, "Please enter some telemetry or URLs first!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isAnalyzingPhishing = true
                            coroutineScope.launch {
                                try {
                                    val res = com.example.api.GeminiService.analyzePhishingPayload(inputText)
                                    analysisResult = res
                                    Toast.makeText(context, "Scan Complete! +40 score awarded.", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Scan failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isAnalyzingPhishing = false
                                }
                            }
                        },
                        enabled = !isAnalyzingPhishing,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary, contentColor = CyberOnPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("analyze_phishing_button")
                    ) {
                        if (isAnalyzingPhishing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CyberOnPrimary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("RUNNING HEURISTICS...", fontSize = 11.sp)
                        } else {
                            Icon(imageVector = Icons.Default.Terminal, contentDescription = "Scan", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RUN FORENSIC SCAN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Output Result Card
        if (analysisResult != null) {
            val res = analysisResult!!
            val verdictColor = when (res.verdict.uppercase()) {
                "MALICIOUS" -> CriticalRed
                "SUSPICIOUS" -> Color(0xFFFFB300)
                else -> Color(0xFF00FF66)
            }
            val verdictBg = when (res.verdict.uppercase()) {
                "MALICIOUS" -> CriticalRedBg
                "SUSPICIOUS" -> Color(0xFF261D03)
                else -> Color(0xFF102008)
            }

            Card(
                modifier = Modifier.fillMaxWidth().border(width = 1.dp, color = CyberBorder, shape = RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = CyberCard),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Header Row with Verdict
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("FORENSIC ANALYSIS REPORT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberMuted)
                        
                        Box(
                            modifier = Modifier
                                .background(verdictBg, shape = RoundedCornerShape(6.dp))
                                .border(width = 1.dp, color = verdictColor.copy(alpha = 0.5f), shape = RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(res.verdict, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = verdictColor, letterSpacing = 0.5.sp)
                        }
                    }

                    // Sender Domain & SPF/DKIM metrics
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("FORENSIC RECORD ALIGNMENT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CyberMuted)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f).background(CyberLogBg, shape = RoundedCornerShape(8.dp)).padding(10.dp)) {
                                Column {
                                    Text("Sender / Host Domain", fontSize = 8.sp, color = CyberMuted)
                                    Text(res.senderDomain, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Box(modifier = Modifier.weight(1f).background(CyberLogBg, shape = RoundedCornerShape(8.dp)).padding(10.dp)) {
                                Column {
                                    Text("SPF Alignment", fontSize = 8.sp, color = CyberMuted)
                                    Text(res.spfStatus, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (res.spfStatus.contains("FAIL") || res.spfStatus.contains("NONE")) CriticalRed else Color(0xFF00FF66), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Box(modifier = Modifier.weight(1f).background(CyberLogBg, shape = RoundedCornerShape(8.dp)).padding(10.dp)) {
                                Column {
                                    Text("DKIM Alignment", fontSize = 8.sp, color = CyberMuted)
                                    Text(res.dkimStatus, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (res.dkimStatus.contains("FAIL") || res.spfStatus.contains("NONE")) CriticalRed else Color(0xFF00FF66), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }

                    // Phishing Indicators list
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("PHISHING INDICATORS DETECTED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CyberMuted)
                        res.flagsFound.forEach { flag ->
                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color(0xFF241416), shape = RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.size(6.dp).background(CriticalRed, shape = CircleShape))
                                Text(flag, fontSize = 11.sp, color = Color(0xFFFF999A), fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // URL Risk Assessment
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("URL HYPERLINK TELEMETRY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CyberMuted)
                        Text(res.urlAssessment, fontSize = 11.sp, color = CyberSubtext)
                    }

                    // Explanation
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ANALYST FORENSIC COGNITION", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CyberMuted)
                        Text(res.explanation, fontSize = 11.sp, color = CyberText)
                    }

                    // Mitigation Playbook
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("DYNAMIC MITIGATION CONTROLS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CyberMuted)
                        Box(modifier = Modifier.fillMaxWidth().background(CyberLogBg, shape = RoundedCornerShape(10.dp)).padding(12.dp)) {
                            Text(res.dynamicPlaybook, fontSize = 11.sp, color = CyberSubtext, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LeaderboardView(viewModel: AlertViewModel) {
    val gameScore by viewModel.gameScore.collectAsState()
    val avgTriageTime by viewModel.averageTriageTime.collectAsState()
    val alerts by viewModel.alerts.collectAsState()

    val level = gameScore / 200 + 1
    val nextLevelThreshold = level * 200
    val prevLevelThreshold = (level - 1) * 200
    val progress = (gameScore - prevLevelThreshold).toFloat() / 200.0f
    
    val rankTitle = when (level) {
        1 -> "Junior Incident Responder"
        2 -> "Cyber Threat Analyst I"
        3 -> "Security Center Specialist II"
        4 -> "Senior Forensic Investigator"
        else -> "Tier-3 Threat Hunter Elite"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Experience Summary Card
        Card(
            modifier = Modifier.fillMaxWidth().border(width = 1.dp, color = CyberBorder, shape = RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = CyberCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("ANALYST CREDENTIAL PROFILE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("LEVEL $level: $rankTitle", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CyberPrimary)
                    }
                    Box(
                        modifier = Modifier.background(CyberPrimary.copy(alpha = 0.15f), shape = CircleShape).padding(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Leaderboard, contentDescription = "Rank", tint = CyberPrimary, modifier = Modifier.size(24.dp))
                    }
                }

                // Custom experience bar
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Experience score: $gameScore / $nextLevelThreshold XP", fontSize = 9.sp, color = CyberMuted)
                        Text("${(progress * 100).toInt()}%", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CyberPrimary)
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().height(8.dp).background(CyberLogBg, shape = RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().background(CyberPrimary, shape = RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }

        // Critical Stats Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val resolvedCount = alerts.count { it.status.equals("Resolved", ignoreCase = true) }

            Box(
                modifier = Modifier.weight(1f).background(CyberCard, shape = RoundedCornerShape(16.dp)).border(width = 1.dp, color = CyberBorder, shape = RoundedCornerShape(16.dp)).padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("TRIAGED ANOMALIES", fontSize = 9.sp, color = CyberMuted)
                    Text("$resolvedCount CLOSED", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00FF66))
                }
            }

            Box(
                modifier = Modifier.weight(1f).background(CyberCard, shape = RoundedCornerShape(16.dp)).border(width = 1.dp, color = CyberBorder, shape = RoundedCornerShape(16.dp)).padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("TIME TO TRIAGE (AVG)", fontSize = 9.sp, color = CyberMuted)
                    Text("${avgTriageTime}s RESPONSE", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
                }
            }
        }

        // Achievements Badge Showcase
        Card(
            modifier = Modifier.fillMaxWidth().border(width = 1.dp, color = CyberBorder, shape = RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = CyberCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("INVESTIGATOR MILESTONES & ACHIEVEMENTS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberMuted)
                
                val badges = listOf(
                    Triple("First Blood", "Closed your first security alert successfully.", alerts.any { it.status.equals("Resolved", ignoreCase = true) }),
                    Triple("AI-first Responder", "Executed deep Gemini Threat diagnostics on logs.", alerts.any { !it.aiExplanation.isNullOrBlank() }),
                    Triple("Precision Analyst", "Classified an incident as True Positive threat.", alerts.any { it.classification == "True Positive" }),
                    Triple("Grandmaster Investigator", "Reached score multiplier of 400+ points.", gameScore >= 400)
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    badges.forEach { (name, desc, unlocked) ->
                        val badgeColor = if (unlocked) Color(0xFF00FF88) else CyberMuted.copy(alpha = 0.5f)
                        val bg = if (unlocked) Color(0xFF132A1C) else CyberLogBg

                        Row(
                            modifier = Modifier.fillMaxWidth().background(bg, shape = RoundedCornerShape(12.dp)).border(width = 1.dp, color = badgeColor.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp)).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(32.dp).background(badgeColor.copy(alpha = 0.15f), shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.Star, contentDescription = "Badge", tint = badgeColor, modifier = Modifier.size(16.dp))
                            }
                            Column {
                                Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (unlocked) Color(0xFF00FF88) else CyberMuted)
                                Text(desc, fontSize = 9.sp, color = CyberMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}
