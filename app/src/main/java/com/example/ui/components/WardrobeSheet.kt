package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AccessoryStyle
import com.example.model.AvatarCustomization
import com.example.model.BackgroundTheme
import com.example.model.CostumeStyle
import com.example.model.CustomizationCatalog
import com.example.model.EarStyle
import com.example.model.EyeStyle

enum class WardrobeTab(val title: String, val icon: String) {
    COSTUME("Kostum", "🧥"),
    EYES("Mata & Ekspresi", "👀"),
    EARS("Telinga", "👂"),
    FUR("Warna Bulu", "🎨"),
    ACCESSORIES("Aksesoris", "✨"),
    ROOM("Ruangan", "🏠")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WardrobeSheet(
    sheetState: SheetState,
    customization: AvatarCustomization,
    currentBackgroundTheme: BackgroundTheme = BackgroundTheme.LIVING_ROOM,
    coins: Int = 1500,
    diamonds: Int = 240,
    unlockedCostumes: Set<String> = setOf("none"),
    unlockedAccessories: Set<String> = setOf("none", "bell_collar"),
    unlockedThemes: Set<String> = setOf("living_room", "bedroom", "kitchen"),
    shopFeedbackMessage: String = "",
    onCustomizationChanged: (AvatarCustomization) -> Unit,
    onSelectBackgroundTheme: (BackgroundTheme) -> Unit = {},
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(WardrobeTab.COSTUME) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("wardrobe_modal_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Header + Economy Balance Pills
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kustomisasi & Toko",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text(
                        text = "Kumpulkan koin & berlian dari chat, tugas, atau mini-game",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0x28F59E0B)
                    ) {
                        Text(
                            text = "⭐ $coins",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309)
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0x2838BDF8)
                    ) {
                        Text(
                            text = "💎 $diamonds",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0369A1)
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_wardrobe_button")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                    }
                }
            }

            if (shopFeedbackMessage.isNotBlank()) {
                val isSuccess = shopFeedbackMessage.contains("berhasil", ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSuccess) Color(0x2210B981) else Color(0x22EF4444),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSuccess) Color(0xFF10B981) else Color(0xFFEF4444)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = shopFeedbackMessage,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSuccess) Color(0xFF047857) else Color(0xFFB91C1C)
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            // Tab chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                items(WardrobeTab.entries) { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text("${tab.icon} ${tab.title}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("wardrobe_tab_${tab.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tab Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(260.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (selectedTab) {
                    WardrobeTab.COSTUME -> {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CostumeStyle.entries.forEach { costume ->
                                val isSelected = customization.costume == costume
                                val isFree = costume.coinPrice == 0 && costume.diamondPrice == 0
                                val isUnlocked = isFree || unlockedCostumes.contains(costume.id)
                                val badgeText = when {
                                    isUnlocked && !isFree -> "✓ Milikmu"
                                    !isUnlocked && costume.diamondPrice > 0 -> "🔒 💎 ${costume.diamondPrice}"
                                    !isUnlocked && costume.coinPrice > 0 -> "🔒 ⭐ ${costume.coinPrice}"
                                    else -> null
                                }
                                SelectionCard(
                                    title = costume.displayName,
                                    icon = costume.icon,
                                    isSelected = isSelected,
                                    badgeText = badgeText,
                                    isLocked = !isUnlocked,
                                    onClick = {
                                        onCustomizationChanged(customization.copy(costume = costume))
                                    },
                                    modifier = Modifier.testTag("costume_item_${costume.id}")
                                )
                            }
                        }
                    }

                    WardrobeTab.EYES -> {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            EyeStyle.entries.forEach { eye ->
                                val isSelected = customization.eyeStyle == eye
                                SelectionCard(
                                    title = eye.displayName,
                                    icon = eye.icon,
                                    isSelected = isSelected,
                                    onClick = {
                                        onCustomizationChanged(customization.copy(eyeStyle = eye))
                                    },
                                    modifier = Modifier.testTag("eye_style_${eye.id}")
                                )
                            }
                        }
                    }

                    WardrobeTab.EARS -> {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            EarStyle.entries.forEach { ear ->
                                val isSelected = customization.earStyle == ear
                                SelectionCard(
                                    title = ear.displayName,
                                    icon = ear.icon,
                                    isSelected = isSelected,
                                    onClick = {
                                        onCustomizationChanged(customization.copy(earStyle = ear))
                                    },
                                    modifier = Modifier.testTag("ear_style_${ear.id}")
                                )
                            }
                        }
                    }

                    WardrobeTab.FUR -> {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CustomizationCatalog.furColors.forEach { opt ->
                                val isSelected = customization.furColorId == opt.id
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            onCustomizationChanged(
                                                customization.copy(furColor = opt.color, furColorId = opt.id)
                                            )
                                        }
                                        .padding(8.dp)
                                        .testTag("fur_color_${opt.id}")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(opt.color)
                                            .border(
                                                width = if (isSelected) 3.5.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                                shape = CircleShape
                                            )
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Dipilih",
                                                tint = if (opt.color == Color(0xFFFBFBFA) || opt.color == Color(0xFFF7EBE1)) Color.Black else Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = opt.name,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    WardrobeTab.ACCESSORIES -> {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AccessoryStyle.entries.forEach { acc ->
                                val isSelected = customization.accessory == acc
                                val isFree = acc.coinPrice == 0 && acc.diamondPrice == 0
                                val isUnlocked = isFree || unlockedAccessories.contains(acc.id)
                                val badgeText = when {
                                    isUnlocked && !isFree -> "✓ Milikmu"
                                    !isUnlocked && acc.diamondPrice > 0 -> "🔒 💎 ${acc.diamondPrice}"
                                    !isUnlocked && acc.coinPrice > 0 -> "🔒 ⭐ ${acc.coinPrice}"
                                    else -> null
                                }
                                SelectionCard(
                                    title = acc.displayName,
                                    icon = acc.icon,
                                    isSelected = isSelected,
                                    badgeText = badgeText,
                                    isLocked = !isUnlocked,
                                    onClick = {
                                        onCustomizationChanged(customization.copy(accessory = acc))
                                    },
                                    modifier = Modifier.testTag("accessory_${acc.id}")
                                )
                            }
                        }
                    }

                    WardrobeTab.ROOM -> {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BackgroundTheme.entries.forEach { bgTheme ->
                                val isSelected = currentBackgroundTheme == bgTheme
                                val isFree = bgTheme.coinPrice == 0 && bgTheme.diamondPrice == 0
                                val isUnlocked = isFree || unlockedThemes.contains(bgTheme.id)
                                val badgeText = when {
                                    isUnlocked && !isFree -> "✓ Milikmu"
                                    !isUnlocked && bgTheme.diamondPrice > 0 -> "🔒 💎 ${bgTheme.diamondPrice}"
                                    !isUnlocked && bgTheme.coinPrice > 0 -> "🔒 ⭐ ${bgTheme.coinPrice}"
                                    else -> null
                                }
                                SelectionCard(
                                    title = bgTheme.displayName,
                                    icon = bgTheme.icon,
                                    isSelected = isSelected,
                                    badgeText = badgeText,
                                    isLocked = !isUnlocked,
                                    onClick = { onSelectBackgroundTheme(bgTheme) },
                                    modifier = Modifier.testTag("room_theme_${bgTheme.id}")
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
fun SelectionCard(
    title: String,
    icon: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    isLocked: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            isLocked -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        },
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else if (isLocked) {
            androidx.compose.foundation.BorderStroke(1.dp, Color(0x55F59E0B))
        } else {
            null
        },
        modifier = modifier
            .width(152.dp)
            .height(78.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(text = icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 2
                )
                if (!badgeText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLocked) Color(0xFFD97706) else Color(0xFF059669)
                        )
                    )
                }
            }
        }
    }
}
