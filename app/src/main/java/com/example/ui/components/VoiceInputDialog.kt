package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VoiceInputDialog(
    isListening: Boolean,
    liveTranscript: String,
    rmsLevel: Float,
    characterName: String,
    isUserSpeakingNow: Boolean = false,
    isLoadingAi: Boolean = false,
    isServiceUnavailable: Boolean = false,
    onLaunchSystemSpeech: () -> Unit = {},
    onSendTranscript: (String) -> Unit,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "geminiLivePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isUserSpeakingNow) 1.45f else 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isUserSpeakingNow) 420 else 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val auraColors = when {
        isLoadingAi -> listOf(Color(0xFFA855F7), Color(0xFF6366F1))
        isUserSpeakingNow -> listOf(Color(0xFF10B981), Color(0xFF06B6D4))
        isListening -> listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6))
        else -> listOf(Color(0xFF475569), Color(0xFF334155))
    }

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Color(0xFF0F172A),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "✨ Gemini Live Voice",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Text(
                        text = "Bicara ke $characterName",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    )
                }
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Batal",
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                // Google Gemini Live Glowing Orb
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(124.dp)
                ) {
                    // Outer Halo Ripple
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .scale(if (isListening || isUserSpeakingNow || isLoadingAi) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        auraColors.first().copy(alpha = 0.38f),
                                        auraColors.last().copy(alpha = 0.05f)
                                    )
                                )
                            )
                    )

                    // Inner Gemini Orb
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(auraColors))
                            .border(2.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                            .clickable(enabled = !isLoadingAi) {
                                onSendTranscript(liveTranscript)
                            }
                            .testTag("gemini_live_orb_button")
                    ) {
                        if (isLoadingAi) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(32.dp)
                            )
                        } else {
                            Icon(
                                imageVector = if (isUserSpeakingNow) Icons.Default.GraphicEq else Icons.Default.Mic,
                                contentDescription = "Mikrofon Gemini Live",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 7-Bar Live Audio Wave Visualizer reacting to sound
                LiveAudioWaveVisualizer(
                    rmsLevel = rmsLevel,
                    isListening = isListening || isUserSpeakingNow,
                    accentColor = if (isUserSpeakingNow) Color(0xFF34D399) else Color(0xFF60A5FA)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Dynamic Status Label
                val statusText = when {
                    isLoadingAi -> "✨ Gemini AI sedang memahami & merespons suaramu..."
                    isUserSpeakingNow -> "🎙️ Mendeteksi suaramu... (Otomatis kirim saat berhenti bicara)"
                    isListening -> "Silakan langsung bicara! Otomatis dijawab saat kamu berhenti bicara."
                    else -> "Memproses suara..."
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = when {
                            isLoadingAi -> Color(0xFFC084FC)
                            isUserSpeakingNow -> Color(0xFF34D399)
                            else -> Color(0xFF93C5FD)
                        },
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Real-time Gemini Multimodal Status Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(
                        1.dp,
                        if (isUserSpeakingNow) Color(0xFF10B981).copy(alpha = 0.6f)
                        else Color.White.copy(alpha = 0.14f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = if (liveTranscript.isBlank()) {
                                "Ucapkan pertanyaan, curhatan, atau obrolan apa saja. Begitu kamu berhenti bicara sejenak, $characterName akan langsung menjawab dengan suaranya!"
                            } else {
                                liveTranscript
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.5.sp,
                                color = Color(0xFFE2E8F0),
                                lineHeight = 19.sp
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (isServiceUnavailable) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onLaunchSystemSpeech,
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("launch_system_speech_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gunakan Dikte Suara Sistem",
                            fontSize = 12.sp,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSendTranscript(liveTranscript)
                },
                enabled = !isLoadingAi,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981),
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("send_voice_transcript_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isLoadingAi) "Memproses..." else "Selesai & Kirim",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                modifier = Modifier.testTag("cancel_voice_input_button")
            ) {
                Text("Tutup", color = Color.White)
            }
        }
    )
}

@Composable
fun LiveAudioWaveVisualizer(
    rmsLevel: Float,
    isListening: Boolean,
    accentColor: Color = Color(0xFF38BDF8)
) {
    val normalizedRms = ((rmsLevel + 1.5f) / 10f).coerceIn(0.18f, 1f)
    val infiniteTransition = rememberInfiniteTransition(label = "waveAnim")
    val animFactor by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(320, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "animFactor"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.height(34.dp)
    ) {
        val multipliers = listOf(0.45f, 0.75f, 1.05f, 1.3f, 1.0f, 0.8f, 0.5f)
        multipliers.forEach { mult ->
            val h = if (isListening) {
                (7f + normalizedRms * 22f * mult * animFactor).coerceIn(7f, 34f).dp
            } else {
                6.dp
            }
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(h)
                    .clip(CircleShape)
                    .background(accentColor)
            )
        }
    }
}
