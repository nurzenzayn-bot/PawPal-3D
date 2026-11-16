package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.window.Dialog
import com.example.model.AppLanguage

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.data.GoogleAuthHelper
import com.example.notifications.ConversationReminderScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsDialog(
    selectedLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    userNickname: String = "",
    onSaveNickname: (String) -> Unit = {},
    memoryCount: Int = 0,
    onOpenMemories: () -> Unit = {},
    signedInEmail: String? = null,
    signedInDisplayName: String? = null,
    isCloudSyncing: Boolean = false,
    cloudSyncStatus: String = "",
    onGoogleSignedIn: (String?, String?) -> Unit = { _, _ -> },
    onSyncNow: () -> Unit = {},
    onSignedOut: () -> Unit = {},
    dailyRemindersEnabled: Boolean = false,
    onToggleDailyReminders: (Boolean) -> Unit = {},
    onTestReminderNotification: () -> Unit = {},
    conversationRemindersEnabled: Boolean = false,
    onToggleConversationReminders: (Boolean) -> Unit = {},
    conversationReminders: List<ConversationReminderScheduler.Reminder> = emptyList(),
    onRemoveConversationReminder: (String) -> Unit = {},
    isHandsFreeWakeEnabled: Boolean = true,
    onToggleHandsFreeWake: (Boolean) -> Unit = {},
    standbyRequireWakeWord: Boolean = true,
    onStandbyRequireWakeWordChanged: (Boolean) -> Unit = {},
    micSensitivityHigh: Boolean = false,
    onMicSensitivityHighChanged: (Boolean) -> Unit = {},
    allowBargeInWhileAiSpeaking: Boolean = true,
    onAllowBargeInChanged: (Boolean) -> Unit = {},
    onTestVoice: () -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nicknameInput by remember(userNickname) { mutableStateOf(userNickname) }
    var nicknameSaved by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }
    var authFeedback by remember { mutableStateOf("") }
    var pendingNotificationAction by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingNotificationAction
        pendingNotificationAction = null
        if (granted) {
            when (action) {
                "test" -> onTestReminderNotification()
                "toggle" -> onToggleDailyReminders(true)
                "conversation_toggle" -> onToggleConversationReminders(true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color(0xF50F172A),
            border = BorderStroke(1.5.dp, Color(0x55818CF8)),
            shadowElevation = 20.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚙️ Pengaturan",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF))
                            .testTag("close_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1. User Nickname & Memory Profile Section
                Text(
                    text = "Profil & Panggilan Kamu",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF93C5FD)
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = nicknameInput,
                        onValueChange = {
                            nicknameInput = it
                            nicknameSaved = false
                        },
                        placeholder = {
                            Text(
                                text = "Nama panggilanmu...",
                                color = Color(0xFF64748B),
                                fontSize = 13.sp
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF818CF8),
                            unfocusedBorderColor = Color(0x35FFFFFF)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSaveNickname(nicknameInput.trim())
                            nicknameSaved = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Text("Simpan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (nicknameSaved) {
                    Text(
                        text = "✓ Nama panggilan berhasil disimpan!",
                        color = Color(0xFF34D399),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Button to open Long-Term Character Memories
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x286366F1),
                    border = BorderStroke(1.dp, Color(0x55818CF8)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onOpenMemories()
                        }
                        .testTag("settings_open_memories_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🧠", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Ingatan Jangka Panjang Sahabat",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "$memoryCount hal penting tentangmu tersimpan",
                                    color = Color(0xFFA5B4FC),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Text(
                            text = "Lihat >",
                            color = Color(0xFF93C5FD),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 2. Optional Google Cloud Sync (Firestore)
                Text(
                    text = "☁️ Google Cloud Sync (Opsional)",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF93C5FD)
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x241E293B),
                    border = BorderStroke(1.dp, Color(0x4538BDF8)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        val isSignedIn = !signedInEmail.isNullOrBlank() || !signedInDisplayName.isNullOrBlank()
                        if (isSignedIn) {
                            Text(
                                text = "Terhubung: ${signedInDisplayName?.takeIf { it.isNotBlank() } ?: signedInEmail}",
                                color = Color(0xFF6EE7B7),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (!signedInEmail.isNullOrBlank()) {
                                Text(
                                    text = signedInEmail,
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp
                                )
                            }
                            if (cloudSyncStatus.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = cloudSyncStatus,
                                    color = Color(0xFF93C5FD),
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = onSyncNow,
                                    enabled = !isCloudSyncing,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0284C7),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("sync_now_button")
                                ) {
                                    if (isCloudSyncing) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text("Sinkronkan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        GoogleAuthHelper.signOut(context, scope) {
                                            authFeedback = ""
                                            onSignedOut()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0x33F43F5E),
                                        contentColor = Color(0xFFFDA4AF)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("google_sign_out_button")
                                ) {
                                    Text("Keluar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Text(
                                text = "Cadangkan koin, berlian, kostum, ingatan, dan jurnal mood ke akun Google Anda.",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                            if (authFeedback.isNotBlank() || cloudSyncStatus.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = authFeedback.ifBlank { cloudSyncStatus },
                                    color = Color(0xFFFDE68A),
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    isSigningIn = true
                                    authFeedback = ""
                                    GoogleAuthHelper.signInWithGoogleInteractive(
                                        context = context,
                                        scope = scope,
                                        onAuthSuccess = { _, email, displayName ->
                                            isSigningIn = false
                                            onGoogleSignedIn(email, displayName)
                                        },
                                        onAuthCancelled = {
                                            isSigningIn = false
                                            authFeedback = "Login Google dibatalkan."
                                        },
                                        onAuthError = { err ->
                                            isSigningIn = false
                                            authFeedback = err
                                        }
                                    )
                                },
                                enabled = !isSigningIn,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4F46E5),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("google_sign_in_button")
                            ) {
                                if (isSigningIn) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Menghubungkan...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("🔐 Masuk dengan Google (Cloud Sync)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 3. Mood-Aware Scheduled Daily Reminders
                Text(
                    text = "🔔 Pengingat Harian (Pagi & Malam)",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF93C5FD)
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x241E293B),
                    border = BorderStroke(1.dp, Color(0x4510B981)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sapaan Otomatis 07:30 & 20:15",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Menyesuaikan mood terakhirmu di Jurnal Mood",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = dailyRemindersEnabled,
                                onCheckedChange = { wantEnable ->
                                    if (wantEnable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (!granted) {
                                            pendingNotificationAction = "toggle"
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            return@Switch
                                        }
                                    }
                                    onToggleDailyReminders(wantEnable)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF10B981)
                                ),
                                modifier = Modifier.testTag("daily_reminders_switch")
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!granted) {
                                        pendingNotificationAction = "test"
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        return@Button
                                    }
                                }
                                onTestReminderNotification()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0x3310B981),
                                contentColor = Color(0xFF6EE7B7)
                            ),
                            border = BorderStroke(1.dp, Color(0x5510B981)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("test_reminder_notification_button")
                        ) {
                            Text("🔔 Tes Notifikasi Sapaan Sekarang", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x243B82F6),
                    border = BorderStroke(1.dp, Color(0x554F8EF7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🧠 Ingat Agenda dari Obrolan", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(3.dp))
                            Text("Contoh: ‘Besok ada acara nikahan keluarga’. PawPal akan menjadwalkan notifikasi meski aplikasi ditutup.", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                        }
                        Switch(
                            checked = conversationRemindersEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    pendingNotificationAction = "conversation_toggle"
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else onToggleConversationReminders(enabled)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF3B82F6)),
                            modifier = Modifier.testTag("conversation_reminders_switch")
                        )
                    }
                }

                if (conversationReminders.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Agenda yang diingat PawPal", color = Color(0xFFBFDBFE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    conversationReminders.take(6).forEach { reminder ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(reminder.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    SimpleDateFormat("EEEE, d MMM yyyy • HH:mm", Locale("id", "ID")).format(Date(reminder.at)),
                                    color = Color(0xFF9CAFC4), fontSize = 10.sp
                                )
                            }
                            IconButton(onClick = { onRemoveConversationReminder(reminder.id) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = "Hapus pengingat", tint = Color(0xFFFCA5A5))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 3.5 Speech-to-Wake ("Petto") & Microphone Settings
                Text(
                    text = "🎙️ Speech-to-Wake Petto & Mikrofon",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF93C5FD)
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x241E293B),
                    border = BorderStroke(1.dp, Color(0x4538BDF8)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Panggil \"Petto\" Tanpa Sentuh",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Mikrofon siaga di ruangan dengan Smart Barge-In",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = isHandsFreeWakeEnabled,
                                onCheckedChange = onToggleHandsFreeWake,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF38BDF8)
                                ),
                                modifier = Modifier.testTag("hands_free_wake_switch")
                            )
                        }

                        if (isHandsFreeWakeEnabled) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Aturan Pemicu Saat Standby:",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            val wakeOptions = listOf(
                                Triple(
                                    true,
                                    "Wajib panggil \"Petto\" / nama dulu",
                                    "Aman dari suara TV atau obrolan orang sekitar"
                                ),
                                Triple(
                                    false,
                                    "Langsung bicara tanpa panggil nama",
                                    "Semua ucapan di ruangan langsung dijawab AI"
                                )
                            )
                            wakeOptions.forEach { (reqWake, title, desc) ->
                                val selected = standbyRequireWakeWord == reqWake
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) Color(0x3838BDF8) else Color(0x14FFFFFF),
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) Color(0xFF38BDF8) else Color(0x25FFFFFF)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clickable { onStandbyRequireWakeWordChanged(reqWake) }
                                        .testTag(if (reqWake) "standby_mode_wake_word" else "standby_mode_direct")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = { onStandbyRequireWakeWordChanged(reqWake) },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = Color(0xFF38BDF8),
                                                unselectedColor = Color(0xFF94A3B8)
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = title,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            )
                                            Text(
                                                text = desc,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Sensitivitas Mikrofon Tinggi",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Membantu menangkap suara pelan atau jarak agak jauh",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp
                                    )
                                }
                                Switch(
                                    checked = micSensitivityHigh,
                                    onCheckedChange = onMicSensitivityHighChanged,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF10B981)
                                    ),
                                    modifier = Modifier.testTag("mic_sensitivity_high_switch")
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Potong Ucapan Karakter (Barge-In)",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Jika mati, mik otomatis jeda hingga karakter selesai bicara",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp
                                    )
                                }
                                Switch(
                                    checked = allowBargeInWhileAiSpeaking,
                                    onCheckedChange = onAllowBargeInChanged,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF38BDF8)
                                    ),
                                    modifier = Modifier.testTag("allow_barge_in_switch")
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 4. Language Selection
                Text(
                    text = "Bahasa Percakapan",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF93C5FD)
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))

                AppLanguage.entries.forEach { lang ->
                    val isSelected = lang == selectedLanguage
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) Color(0x386366F1) else Color(0x18FFFFFF),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF818CF8) else Color(0x25FFFFFF)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { onLanguageSelected(lang) }
                            .testTag("language_option_${lang.code}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = lang.flag, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = lang.displayName,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            RadioButton(
                                selected = isSelected,
                                onClick = { onLanguageSelected(lang) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF818CF8),
                                    unselectedColor = Color(0xFF94A3B8)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5. Quick Actions: Test Voice & Clear Chat History
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onTestVoice,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color(0x4593C5FD)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_voice_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Tes Suara",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tes Suara", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onClearHistory,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x33F43F5E),
                            contentColor = Color(0xFFFDA4AF)
                        ),
                        border = BorderStroke(1.dp, Color(0x55F43F5E)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("clear_chat_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Hapus Chat",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Hapus Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
