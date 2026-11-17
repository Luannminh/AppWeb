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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PersonRemove
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twchat.app.ui.AppUiState
import com.twchat.app.ui.AppViewModel
import com.twchat.app.ui.components.Avatar
import com.twchat.app.ui.components.ColorSwatchRow
import com.twchat.app.util.Transcript
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(state: AppUiState, vm: AppViewModel) {
    val me = state.me ?: return
    val partner = state.partner
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by rememberSaveable(me.displayName) { mutableStateOf(me.displayName) }
    var color by rememberSaveable(me.color) { mutableStateOf(me.color) }
    var confirmUnpair by remember { mutableStateOf(false) }

    val hasChanges = name.trim() != me.displayName || color != me.color

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
            SectionCard(title = "Hồ sơ của bạn") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(name = me.displayName, color = Color(me.color), size = 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = me.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "@${me.username}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tên hiển thị") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                ColorSwatchRow(selected = color, onSelect = { color = it })
                Button(
                    onClick = { vm.updateProfile(name, color) },
                    enabled = hasChanges && name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Lưu hồ sơ")
                }
            }

            partner?.let { mate ->
                SectionCard(title = "Người ấy") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(name = mate.displayName, color = Color(mate.color), size = 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = mate.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (state.live.partnerOnline) "Đang hoạt động" else "Ngoại tuyến",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { confirmUnpair = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PersonRemove,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Huỷ ghép đôi", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            SectionCard(title = "Kết nối") {
                InfoRow(label = "Máy chủ", value = state.session.serverUrl)
                InfoRow(
                    label = "Trạng thái",
                    value = when {
                        !state.live.connected -> "Mất kết nối — đang thử lại…"
                        state.live.partnerOnline -> "Đã kết nối · người ấy đang online"
                        else -> "Đã kết nối"
                    },
                )
                OutlinedButton(
                    onClick = { vm.logout() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Đăng xuất")
                }
                Text(
                    text = "Muốn đổi máy chủ thì đăng xuất trước, rồi nhập địa chỉ mới ở màn hình đăng nhập.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }

            SectionCard(title = "Dữ liệu") {
                Text(
                    text = "${state.messages.size} tin nhắn trong bộ nhớ đệm của máy này",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        val transcript = Transcript.build(state.messages, me, partner)
                        shareText(context, transcript)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Rounded.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Xuất / chia sẻ cuộc trò chuyện")
                }
                Text(
                    text = "Tin nhắn được lưu trên máy chủ của bạn và giữ một bản đệm trên máy để mở " +
                        "nhanh, xem được cả khi mất mạng.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }

            SectionCard(title = "Về TwoChat") {
                Text(
                    text = "Phiên bản 1.1 · Kotlin + Jetpack Compose\n" +
                        "Nhắn tin thời gian thực qua WebSocket, dữ liệu nằm trên máy chủ bạn tự host. " +
                        "Không quảng cáo, không theo dõi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Huỷ ghép đôi?") },
            text = {
                Text(
                    "Toàn bộ tin nhắn giữa hai người sẽ bị xoá khỏi máy chủ và khỏi máy này. " +
                        "Bạn vẫn có thể ghép đôi lại sau.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmUnpair = false
                        vm.unpair()
                        Toast.makeText(context, "Đã huỷ ghép đôi", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("Huỷ ghép đôi", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnpair = false }) { Text("Giữ nguyên") }
            },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
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
private fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
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
