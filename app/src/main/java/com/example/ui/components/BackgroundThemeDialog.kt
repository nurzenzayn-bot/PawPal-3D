package com.example.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BackgroundTheme
import com.example.model.TimeOfDay

import androidx.compose.material3.Surface

@Composable
fun BackgroundThemeDialog(
    currentTheme: BackgroundTheme,
    currentTimeOfDay: TimeOfDay,
    coins: Int = 1500,
    diamonds: Int = 240,
    unlockedThemes: Set<String> = setOf("living_room", "bedroom", "kitchen"),
    shopFeedbackMessage: String = "",
    onSelectTheme: (BackgroundTheme) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(text = "🏠 ", fontSize = 22.sp)
                    Text(
                        text = "Pilih Ruangan",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0x28F59E0B)) {
                        Text(
                            text = "⭐ $coins",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706)
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0x2838BDF8)) {
                        Text(
                            text = "💎 $diamonds",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0284C7)
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column {
                Text(
                    text = "Pilih ruangan rumah 3D atau buka tema spesial menggunakan Koin & Berlianmu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (shopFeedbackMessage.isNotBlank()) {
                    val isSuccess = shopFeedbackMessage.contains("berhasil", ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSuccess) Color(0x2210B981) else Color(0x22EF4444),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = shopFeedbackMessage,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSuccess) Color(0xFF047857) else Color(0xFFB91C1C)
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    items(BackgroundTheme.values()) { theme ->
                        val isSelected = theme == currentTheme
                        val isFree = theme.coinPrice == 0 && theme.diamondPrice == 0
                        val isUnlocked = isFree || unlockedThemes.contains(theme.id)

                        val itemBrush = if (isSelected) {
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                )
                            )
                        } else {
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0x351F2A44),
                                    Color(0x201F2A44)
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(itemBrush)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    brush = if (isSelected) Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)) else LiquidGlassDefaults.SpecularBorderBrush,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { onSelectTheme(theme) }
                                .testTag("theme_option_${theme.id}")
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Theme preview color disc
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(listOf(theme.topColor, theme.bottomColor))
                                        )
                                        .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Text(text = theme.icon, fontSize = 20.sp)
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = theme.displayName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        if (theme.isOrnate) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "HD",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color(0xFFFFB703),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                    if (!isUnlocked) {
                                        val priceLabel = if (theme.diamondPrice > 0) {
                                            "🔒 Buka dengan 💎 ${theme.diamondPrice}"
                                        } else {
                                            "🔒 Buka dengan ⭐ ${theme.coinPrice}"
                                        }
                                        Text(
                                            text = priceLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color(0xFFF59E0B),
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    } else if (!isFree) {
                                        Text(
                                            text = "✓ Sudah Terbuka",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color(0xFF10B981),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                    } else if (theme == BackgroundTheme.AUTO_REALTIME) {
                                        Text(
                                            text = "Aktif sekarang: ${currentTimeOfDay.displayName} ${currentTimeOfDay.icon}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Tema terpilih",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("confirm_theme_dialog_button")
            ) {
                Text("Selesai")
            }
        }
    )
}
