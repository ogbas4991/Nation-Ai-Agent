package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.MessageRole
import com.example.data.local.entity.MessageStatus
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.PurpleContainer
import com.example.ui.theme.StatusDisconnected
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatMessageItem(
    message: ChatMessageEntity,
    isSpeakingThisMessage: Boolean = false,
    onSpeak: (String) -> Unit = {},
    onStopSpeak: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    when (message.role) {
        MessageRole.USER.name -> UserMessageBubble(message, modifier)
        MessageRole.ASSISTANT.name -> AssistantMessageBubble(
            message = message,
            isSpeaking = isSpeakingThisMessage,
            onSpeak = onSpeak,
            onStopSpeak = onStopSpeak,
            modifier = modifier
        )
        MessageRole.TOOL.name -> ToolExecutionView(
            toolName = message.toolName ?: "tool",
            inputJson = message.toolInput,
            output = message.toolOutput,
            status = message.status,
            modifier = modifier.padding(vertical = 4.dp)
        )
        MessageRole.SYSTEM.name -> SystemAlertBanner(message, modifier)
        else -> AssistantMessageBubble(
            message = message,
            isSpeaking = isSpeakingThisMessage,
            onSpeak = onSpeak,
            onStopSpeak = onStopSpeak,
            modifier = modifier
        )
    }
}

@Composable
private fun UserMessageBubble(
    message: ChatMessageEntity,
    modifier: Modifier = Modifier
) {
    val timeString = formatTime(message.timestamp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                CyanContainer,
                                DarkSurfaceElevated
                            )
                        )
                    )
                    .border(
                        1.dp,
                        CyanPrimary.copy(alpha = 0.5f),
                        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("user_message_bubble")
            ) {
                Column {
                    // If user message has an image attached or embedded
                    if (message.content.startsWith("[Image Attached]") || message.content.contains("data:image/")) {
                        Text(
                            text = "📷 Image Attached for Vision Analysis",
                            color = CyanPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Text(
                        text = message.content.removePrefix("[Image Attached]\n").trim(),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = timeString,
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(PurpleContainer)
                .border(1.dp, PurpleAccent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "User",
                tint = PurpleAccent,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun AssistantMessageBubble(
    message: ChatMessageEntity,
    isSpeaking: Boolean,
    onSpeak: (String) -> Unit,
    onStopSpeak: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isStreaming = message.status == MessageStatus.STREAMING.name
    val isError = message.status == MessageStatus.ERROR.name
    val timeString = formatTime(message.timestamp)

    val infiniteTransition = rememberInfiniteTransition(label = "cursor_blink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(CyanContainer)
                .border(1.dp, CyanPrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "OPA Agent",
                tint = CyanPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "OPA AI Agent",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = CyanPrimary
                    )
                    Text(
                        text = timeString,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted
                    )
                    if (isStreaming) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(CyanGlow)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "STREAMING",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = CyanPrimary
                            )
                        }
                    }
                }

                // Action icons: TTS & Copy
                if (!isStreaming && message.content.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // TTS Audio Playback
                        IconButton(
                            onClick = {
                                if (isSpeaking) {
                                    onStopSpeak()
                                } else {
                                    onSpeak(message.content)
                                }
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("tts_speak_button")
                        ) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                                contentDescription = if (isSpeaking) "Stop Speaking" else "Read Aloud",
                                tint = if (isSpeaking) CyanPrimary else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Copy entire message
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("agent_reply", message.content))
                                Toast.makeText(context, "Message copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("copy_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy message",
                                tint = TextMuted,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(DarkSurfaceElevated)
                    .border(
                        1.dp,
                        if (isError) StatusDisconnected.copy(alpha = 0.5f) else DarkSurfaceBorder,
                        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                    )
                    .padding(12.dp)
                    .testTag("assistant_message_bubble")
            ) {
                if (message.content.isEmpty() && isStreaming) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(CyanPrimary)
                                .alpha(cursorAlpha)
                        )
                        Text(
                            text = "Processing response...",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                } else {
                    MarkdownContentRenderer(
                        content = message.content,
                        isStreaming = isStreaming,
                        cursorAlpha = cursorAlpha
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownContentRenderer(
    content: String,
    isStreaming: Boolean,
    cursorAlpha: Float
) {
    val blocks = parseMarkdownBlocks(content)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is ContentBlock.Text -> {
                    val isLast = index == blocks.size - 1
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = block.text,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isStreaming && isLast) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 4.dp, bottom = 3.dp)
                                    .size(width = 4.dp, height = 14.dp)
                                    .background(CyanPrimary)
                                    .alpha(cursorAlpha)
                            )
                        }
                    }
                }
                is ContentBlock.Code -> {
                    CodeBlockView(
                        code = block.code,
                        language = block.language
                    )
                }
            }
        }
    }
}

sealed class ContentBlock {
    data class Text(val text: String) : ContentBlock()
    data class Code(val code: String, val language: String) : ContentBlock()
}

private fun parseMarkdownBlocks(content: String): List<ContentBlock> {
    val blocks = mutableListOf<ContentBlock>()
    val codeBlockRegex = Regex("```([a-zA-Z0-9_-]*)\\n?([\\s\\S]*?)```")

    var lastIndex = 0
    val matches = codeBlockRegex.findAll(content)

    for (match in matches) {
        val start = match.range.first
        val end = match.range.last + 1

        if (start > lastIndex) {
            val textPart = content.substring(lastIndex, start).trim()
            if (textPart.isNotEmpty()) {
                blocks.add(ContentBlock.Text(textPart))
            }
        }

        val lang = match.groupValues[1]
        val code = match.groupValues[2].trimEnd()
        blocks.add(ContentBlock.Code(code = code, language = lang))

        lastIndex = end
    }

    if (lastIndex < content.length) {
        val remaining = content.substring(lastIndex).trim()
        if (remaining.isNotEmpty()) {
            blocks.add(ContentBlock.Text(remaining))
        }
    }

    if (blocks.isEmpty() && content.isNotEmpty()) {
        blocks.add(ContentBlock.Text(content))
    }

    return blocks
}

@Composable
private fun SystemAlertBanner(
    message: ChatMessageEntity,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "System Alert",
            tint = CyanPrimary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = message.content,
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 15.sp
        )
    }
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
