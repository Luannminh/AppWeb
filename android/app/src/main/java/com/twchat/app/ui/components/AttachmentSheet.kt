package com.twchat.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val stickerPack = listOf(
    "❤️", "😍", "😂", "🥰", "😘", "😎",
    "🤗", "😴", "👍", "🙏", "🎉", "🌹",
    "☕", "🍜", "🐱", "🐶", "🌙", "⭐",
    "💌", "🔥", "✨", "🥲", "😭", "🤔",
)

/** Bottom sheet with the four attachment actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentSheet(
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onRecordVoice: () -> Unit,
    onPickSticker: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            AttachmentAction("Ảnh", Icons.Rounded.Image, onPickImage)
            AttachmentAction("Tệp", Icons.Rounded.InsertDriveFile, onPickFile)
            AttachmentAction("Ghi âm", Icons.Rounded.Mic, onRecordVoice)
            AttachmentAction("Nhãn dán", Icons.Rounded.EmojiEmotions, onPickSticker)
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun AttachmentAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

/** Emoji sticker picker; tapping a sticker sends it right away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerSheet(
    onDismiss: () -> Unit,
    onSticker: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = "Nhãn dán",
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleSmall,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(horizontal = 12.dp),
        ) {
            items(stickerPack) { sticker ->
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSticker(sticker) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = sticker, fontSize = 30.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
