package com.twchat.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twchat.app.data.Message
import com.twchat.app.data.MessageKind
import com.twchat.app.ui.theme.readableOn
import com.twchat.app.util.MediaFormat
import com.twchat.app.util.TimeFormat

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isMine: Boolean,
    accent: Color,
    maxWidth: Dp,
    playing: Boolean,
    playProgress: Float,
    loadImage: suspend (String) -> Bitmap?,
    onCopy: (Message) -> Unit,
    onEdit: (Message) -> Unit,
    onDelete: (Message) -> Unit,
    onRetry: (Message) -> Unit,
    onPlayVoice: (Message) -> Unit,
    onOpenFile: (Message) -> Unit,
    onOpenImage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val canEdit = isMine && !message.isDeleted && message.kind == MessageKind.TEXT &&
        message.serverId != null && !message.isPending
    val canDelete = isMine && !message.isDeleted && message.serverId != null && !message.isPending

    val bubbleColor = when {
        message.isDeleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        !isMine -> MaterialTheme.colorScheme.surfaceVariant
        message.isFailed -> MaterialTheme.colorScheme.errorContainer
        else -> accent
    }
    val textColor = if (isMine && !message.isFailed && !message.isDeleted) {
        accent.readableOn()
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val isSticker = message.kind == MessageKind.STICKER

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        Box {
            Surface(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .alpha(if (message.isPending) 0.7f else 1f)
                    .clip(bubbleShape(isMine))
                    .combinedClickable(
                        onClick = {
                            if (isSticker) onCopy(message)
                        },
                        onLongClick = { menuOpen = true },
                    ),
                color = if (isSticker) Color.Transparent else bubbleColor,
                shape = bubbleShape(isMine),
                shadowElevation = if (isMine || isSticker) 0.dp else 1.dp,
            ) {
                when {
                    message.isDeleted -> DeletedContent(textColor)
                    isSticker -> Text(
                        text = message.sticker ?: message.body,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 46.sp,
                    )

                    message.kind == MessageKind.IMAGE -> ImageContent(
                        path = message.attachmentPath,
                        loadImage = loadImage,
                        onOpen = onOpenImage,
                    )

                    message.kind == MessageKind.FILE ->
                        FileContent(message, textColor, onClick = { onOpenFile(message) })

                    message.kind == MessageKind.VOICE -> VoiceContent(
                        message = message,
                        textColor = textColor,
                        playing = playing,
                        progress = playProgress,
                        onPlay = onPlayVoice,
                    )

                    else -> Text(
                        text = message.body,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                    )
                }
            }

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Sao chép") },
                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onCopy(message)
                    },
                )
                if (canEdit) {
                    DropdownMenuItem(
                        text = { Text("Sửa") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onEdit(message)
                        },
                    )
                }
                if (canDelete) {
                    DropdownMenuItem(
                        text = { Text("Thu hồi") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete(message)
                        },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isMine && message.isFailed) {
                Text(
                    text = "Gửi lại",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onRetry(message) }
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    textDecoration = TextDecoration.Underline,
                )
            }
            Text(
                text = TimeFormat.time(message.sentAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            if (message.isEdited && !message.isDeleted) {
                Text(
                    text = "đã sửa",
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            if (isMine) {
                when {
                    message.isPending -> Text(
                        text = "Đang gửi…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )

                    message.isRead -> Text(
                        text = "Đã xem",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    message.isFailed -> Unit

                    else -> Text(
                        text = "Đã gửi",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeletedContent(textColor: Color) {
    Text(
        text = "⛔ Tin nhắn đã được thu hồi",
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodyMedium,
        fontStyle = FontStyle.Italic,
        color = textColor.copy(alpha = 0.8f),
    )
}

@Composable
private fun ImageContent(
    path: String?,
    loadImage: suspend (String) -> Bitmap?,
    onOpen: (String) -> Unit,
) {
    if (path.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Image, contentDescription = null, tint = Color.Gray)
        }
        return
    }
    RemoteImage(
        path = path,
        loadImage = loadImage,
        modifier = Modifier
            .padding(4.dp)
            .size(210.dp)
            .clip(RoundedCornerShape(16.dp)),
        onClick = { onOpen(path) },
    )
}

@Composable
private fun FileContent(message: Message, textColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.InsertDriveFile,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = message.attachmentName ?: "Tệp đính kèm",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
            )
            Text(
                text = MediaFormat.size(message.attachmentSize),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun VoiceContent(
    message: Message,
    textColor: Color,
    playing: Boolean,
    progress: Float,
    onPlay: (Message) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .clickable { onPlay(message) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(textColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Tạm dừng" else "Phát",
                tint = textColor,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .size(width = 140.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(textColor.copy(alpha = 0.25f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = (140 * (1f - progress.coerceIn(0f, 1f))).dp)
                        .background(textColor),
                )
            }
            Spacer(Modifier.size(4.dp))
            Text(
                text = MediaFormat.duration(message.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.8f),
            )
        }
    }
}

/** Loads an image attachment from Supabase Storage (cached in the VM). */
@Composable
fun RemoteImage(
    path: String,
    loadImage: suspend (String) -> Bitmap?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(path) { mutableStateOf(true) }

    LaunchedEffect(path) {
        bitmap = runCatching { loadImage(path) }.getOrNull()
        loading = false
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        when {
            current != null -> Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "Ảnh",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            loading -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )

            else -> Icon(
                imageVector = Icons.Rounded.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun bubbleShape(isMine: Boolean): RoundedCornerShape = if (isMine) {
    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
} else {
    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
}

/** Small pill that separates days in the list. */
@Composable
fun DayLabel(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shadowElevation = 1.dp,
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
