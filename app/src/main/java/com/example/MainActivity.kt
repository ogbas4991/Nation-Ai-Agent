package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.network.model.ConnectionStatus
import com.example.ui.components.AddMcpDialog
import com.example.ui.components.ChatInputBar
import com.example.ui.components.ChatMessageItem
import com.example.ui.components.ClearThreadConfirmationDialog
import com.example.ui.components.McpDiagnosticDialog
import com.example.ui.components.RawRpcInspectorDialog
import com.example.ui.components.SessionSwitcherDialog
import com.example.ui.components.SettingsDrawerContent
import com.example.ui.components.TopBar
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.OpaTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        enableEdgeToEdge()
        setContent {
            OpaTheme {
                OpaAiAgentApp(
                    onSpeak = { text -> speakText(text) },
                    onStopSpeaking = { stopSpeaking() }
                )
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isTtsInitialized = true
        }
    }

    private fun speakText(text: String) {
        if (isTtsInitialized && tts != null) {
            val cleanText = text.replace(Regex("```[\\s\\S]*?```"), "Code block omitted.")
                .replace(Regex("[#*`_]"), "")
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "opa_tts")
        } else {
            Toast.makeText(this, "Text-to-Speech initializing...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopSpeaking() {
        tts?.stop()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun OpaAiAgentApp(
    viewModel: ChatViewModel = viewModel(),
    onSpeak: (String) -> Unit = {},
    onStopSpeaking: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Launcher for selecting an image for Vision reasoning
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val base64 = encodeUriToBase64(context, uri)
            if (base64 != null) {
                viewModel.setAttachedImage(uri.toString(), base64)
                Toast.makeText(context, "Image attached for Gemini Vision analysis", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to decode selected image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Launcher for Speech-to-Text Recognition
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spokenMatches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognizedText = spokenMatches?.firstOrNull()
            if (!recognizedText.isNullOrBlank()) {
                val currentText = uiState.inputText
                val newText = if (currentText.isBlank()) recognizedText else "$currentText $recognizedText"
                viewModel.onInputTextChanged(newText)
                Toast.makeText(context, "Dictated: $recognizedText", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Auto-scroll to bottom on new message or stream tokens
    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.content?.length) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Handle Toast
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToastMessage()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SettingsDrawerContent(
                config = uiState.config,
                mcpServers = uiState.mcpServers,
                onSaveConfig = { url, token, provider, model, apiUrl, webSearch, codeRunner, fileMgr, bashTerm, policy ->
                    viewModel.saveConfig(
                        serverUrl = url,
                        authToken = token,
                        llmProvider = provider,
                        primaryModel = model,
                        baseApiUrl = apiUrl,
                        webSearchEnabled = webSearch,
                        codeRunnerEnabled = codeRunner,
                        fileManagerEnabled = fileMgr,
                        bashTerminalEnabled = bashTerm,
                        executionPolicy = policy
                    )
                },
                onToggleMcpServer = { id, enabled ->
                    viewModel.toggleMcpServer(id, enabled)
                },
                onDeleteMcpServer = { id ->
                    viewModel.deleteMcpServer(id)
                },
                onOpenAddMcpDialog = {
                    viewModel.setShowAddMcpDialog(true)
                },
                onCloseDrawer = {
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground),
            containerColor = DarkBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopBar(
                    connectionStatus = uiState.connectionStatus,
                    config = uiState.config,
                    mcpServers = uiState.mcpServers,
                    estimatedTokens = uiState.totalEstimatedTokens,
                    onOpenSettings = {
                        scope.launch { drawerState.open() }
                    },
                    onOpenSessionSwitcher = {
                        viewModel.setShowSessionSwitcher(true)
                    },
                    onOpenMcpDiagnostic = {
                        viewModel.setShowMcpDiagnostic(true)
                    },
                    onOpenRawRpcDialog = {
                        viewModel.setShowRawRpcDialog(true)
                    },
                    onExportConversation = {
                        val exported = viewModel.getConversationMarkdown()
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, exported)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Export Conversation")
                        context.startActivity(shareIntent)
                    },
                    onReconnect = {
                        viewModel.reconnectGateway()
                    },
                    onClearThreadRequested = {
                        viewModel.setShowClearDialog(true)
                    }
                )
            },
            bottomBar = {
                ChatInputBar(
                    inputText = uiState.inputText,
                    onInputTextChanged = { viewModel.onInputTextChanged(it) },
                    onSendMessage = { viewModel.sendMessage() },
                    isSending = uiState.isSending,
                    attachedImageUri = uiState.attachedImageUri,
                    onRemoveAttachment = { viewModel.clearAttachedImage() },
                    onVoiceInputRequested = {
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your prompt to OPA AI Agent...")
                            }
                            speechRecognizerLauncher.launch(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Speech recognition not available on this device", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onAttachRequested = {
                        imagePickerLauncher.launch("image/*")
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(DarkBackground)
            ) {
                if (uiState.messages.isEmpty()) {
                    WelcomeHeroView(
                        config = uiState.config,
                        connectionStatus = uiState.connectionStatus,
                        onPromptClick = { prompt ->
                            viewModel.onInputTextChanged(prompt)
                        },
                        onOpenSettings = {
                            scope.launch { drawerState.open() }
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        items(
                            items = uiState.messages,
                            key = { it.id }
                        ) { message ->
                            val isSpeaking = uiState.speakingMessageId == message.id
                            ChatMessageItem(
                                message = message,
                                isSpeakingThisMessage = isSpeaking,
                                onSpeak = { text ->
                                    viewModel.setSpeakingMessageId(message.id)
                                    onSpeak(text)
                                },
                                onStopSpeak = {
                                    viewModel.setSpeakingMessageId(null)
                                    onStopSpeaking()
                                }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }

    // Session Switcher Dialog
    if (uiState.showSessionSwitcher) {
        SessionSwitcherDialog(
            sessions = uiState.sessions,
            activeSessionId = uiState.config.activeSessionId,
            onSelectSession = { sid ->
                viewModel.switchSession(sid)
            },
            onCreateNewSession = {
                viewModel.createNewSession()
            },
            onDeleteSession = { sid ->
                viewModel.deleteSession(sid)
            },
            onDismiss = {
                viewModel.setShowSessionSwitcher(false)
            }
        )
    }

    // Live JSON-RPC Frame Inspector Dialog
    if (uiState.showRawRpcDialog) {
        RawRpcInspectorDialog(
            logs = uiState.rawRpcLogs,
            onClearLogs = { viewModel.clearRawRpcLogs() },
            onDismiss = { viewModel.setShowRawRpcDialog(false) }
        )
    }

    // MCP Diagnostic Tester Dialog
    if (uiState.showMcpDiagnostic) {
        McpDiagnosticDialog(
            mcpServers = uiState.mcpServers,
            onTestPing = { url ->
                viewModel.testMcpServer(url)
            },
            diagnosticResult = uiState.mcpDiagnosticResult,
            isTesting = uiState.isTestingMcp,
            onDismiss = {
                viewModel.setShowMcpDiagnostic(false)
            }
        )
    }

    // Add MCP Server Dialog
    if (uiState.showAddMcpDialog) {
        AddMcpDialog(
            initialServer = uiState.editingMcpServer,
            onSave = { name, transportType, urlOrCommand, headersJson ->
                val editing = uiState.editingMcpServer
                if (editing == null) {
                    viewModel.addMcpServer(name, transportType, urlOrCommand, headersJson)
                } else {
                    viewModel.updateMcpServer(editing.id, name, transportType, urlOrCommand, headersJson)
                }
            },
            onDismiss = {
                viewModel.setShowAddMcpDialog(false)
            }
        )
    }

    // Clear Thread Dialog
    if (uiState.showClearDialog) {
        ClearThreadConfirmationDialog(
            onConfirm = {
                viewModel.clearChat()
            },
            onDismiss = {
                viewModel.setShowClearDialog(false)
            }
        )
    }
}

@Composable
private fun WelcomeHeroView(
    config: com.example.data.local.entity.GatewayConfigEntity,
    connectionStatus: ConnectionStatus,
    onPromptClick: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing Hero Icon
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(CyanPrimary, CyanContainer, Color.Transparent)
                    )
                )
                .border(2.dp, CyanPrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ElectricBolt,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "OPA AI Agent",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Text(
            text = "Autonomous Gateway & Real-Time MCP Runtime",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = CyanPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Active Model & Server Info Pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurfaceElevated)
                .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(20.dp))
                .clickable { onOpenSettings() }
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lan,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "${config.llmProvider}: ${config.primaryModel.substringAfter("/")}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Feature Highlights Grid
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HeroFeatureCard(
                icon = Icons.Default.Terminal,
                title = "Live Streaming & Auto-Recovery",
                subtitle = "Sub-millisecond token deltas with resilient reconnection"
            )
            HeroFeatureCard(
                icon = Icons.Default.Visibility,
                title = "Multimodal Vision & Audio",
                subtitle = "Gemini 3.5 vision attachments, native speech dictation & TTS"
            )
            HeroFeatureCard(
                icon = Icons.Default.DataObject,
                title = "Live JSON-RPC Inspector",
                subtitle = "Real-time socket frame telemetry and protocol debugging"
            )
            HeroFeatureCard(
                icon = Icons.Default.Code,
                title = "Autonomous Tool Execution",
                subtitle = "Sandboxed code runner, live web search, HTTP client & workspace"
            )
        }
    }
}

@Composable
private fun HeroFeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(CyanContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CyanPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextMuted,
                lineHeight = 15.sp
            )
        }
    }
}

/**
 * Decodes and downsamples a content Uri to base64 JPEG format for Gemini Vision
 */
private fun encodeUriToBase64(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (bitmap == null) return null

        val maxDim = 1024
        val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        Base64.encodeToString(byteArray, Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }
}
