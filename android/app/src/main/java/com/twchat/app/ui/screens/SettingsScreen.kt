package com.twchat.app.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twchat.app.ui.AppUiState
import com.twchat.app.ui.AppViewModel
import com.twchat.app.ui.components.Avatar
import com.twchat.app.ui.components.ColorSwatchRow
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    state: AppUiState,
    vm: AppViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile = state.profile

    var meName by rememberSaveable(profile.meName) { mutableStateOf(profile.meName) }
    var partnerName by rememberSaveable(profile.partnerName) { mutableStateOf(profile.partnerName) }
    var meColor by rememberSaveable(profile.meColor) { mutableStateOf(profile.meColor) }
    var partnerColor by rememberSaveable(profile.partnerColor) { mutableStateOf(profile.partnerColor) }
    var confirmClear by remember { mutableStateOf(false) }

    val hasChanges = meName.trim() != profile.meName ||
        partnerName.trim() != profile.partnerName ||
        meColor != profile.meColor ||
        partnerColor != profile.partnerColor

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = vm::closeSettings) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Quay lại")
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Cài đặt",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = "Hai người") {
                PersonEditor(
                    name = meName,
                    onNameChange = { meName = it },
                    color = meColor,
                    onColorChange = { meColor = it },
                    label = "Tên của bạn",
                    placeholder = "Ví dụ: Minh",
                )
                PersonEditor(
                    name = partnerName,
                    onNameChange = { partnerName = it },
                    color = partnerColor,
                    onColorChange = { partnerColor = it },
                    label = "Tên người ấy",
                    placeholder = "Ví dụ: An",
                )
                Button(
                    onClick = {
                        vm.saveProfile(meName, partnerName, meColor, partnerColor)
                        Toast.makeText(context, "Đã lưu", Toast.LENGTH_SHORT).show()
                    },
                    enabled = hasChanges && meName.isNotBlank() && partnerName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Lưu thay đổi")
                }
            }

            SectionCard(title = "Dữ liệu") {
                Text(
                    text = "${state.messages.size} tin nhắn · nằm trong bộ nhớ máy này",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            shareText(context, vm.transcriptForSharing())
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Rounded.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Xuất / chia sẻ cuộc trò chuyện")
                }
                OutlinedButton(
                    onClick = { confirmClear = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Xoá toàn bộ tin nhắn", color = MaterialTheme.colorScheme.error)
                }
            }

            SectionCard(title = "Về TwoChat") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(name = profile.meName, color = Color(profile.meColor), size = 36.dp)
                    Spacer(Modifier.width(8.dp))
                    Avatar(
                        name = profile.partnerName,
                        color = Color(profile.partnerColor),
                        size = 36.dp,
                    )
                }
                Text(
                    text = "Phiên bản 1.0 · Kotlin + Jetpack Compose\n" +
                        "Không có tài khoản, không có máy chủ, không thu thập dữ liệu. " +
                        "Gỡ ứng dụng là mất cuộc trò chuyện, nên hãy xuất bản sao nếu cần.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Xoá toàn bộ tin nhắn?") },
            text = { Text("Tất cả tin nhắn sẽ bị xoá khỏi máy này và không thể khôi phục.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearConversation()
                        confirmClear = false
                        Toast.makeText(context, "Đã xoá cuộc trò chuyện", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("Xoá", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Huỷ") }
            },
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun PersonEditor(
    name: String,
    onNameChange: (String) -> Unit,
    color: Long,
    onColorChange: (Long) -> Unit,
    label: String,
    placeholder: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Avatar(name = name.ifBlank { "?" }, color = Color(color), size = 28.dp) },
        )
        ColorSwatchRow(selected = color, onSelect = onColorChange)
    }
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Cuộc trò chuyện TwoChat")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Chia sẻ cuộc trò chuyện"))
}
