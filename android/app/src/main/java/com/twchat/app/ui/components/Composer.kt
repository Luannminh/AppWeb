package com.twchat.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.twchat.app.data.Profile
import com.twchat.app.ui.theme.readableOn

@Composable
fun Composer(
    profile: Profile,
    sendAsPartner: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    onToggleSendAs: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(if (sendAsPartner) profile.partnerColor else profile.meColor)
    val activeName = if (sendAsPartner) profile.partnerName else profile.meName

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
        ) {
            // Who is typing right now. Tap to hand the phone to the other person.
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onToggleSendAs)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(name = activeName, color = accent, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Gửi với tên $activeName",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.SwapHoriz,
                    contentDescription = "Đổi người gửi",
                    tint = accent,
                    modifier = Modifier.size(14.dp),
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Nhắn gì đó…") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                )
                FilledIconButton(
                    onClick = onSend,
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = accent,
                        contentColor = accent.readableOn(),
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Send,
                        contentDescription = "Gửi",
                    )
                }
            }
        }
    }
}
