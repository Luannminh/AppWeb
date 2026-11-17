package com.twchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twchat.app.ui.components.ColorSwatchRow
import com.twchat.app.ui.theme.AccentPalette
import com.twchat.app.ui.theme.readableOn

@Composable
fun OnboardingScreen(
    onDone: (meName: String, partnerName: String, meColor: Long, partnerColor: Long) -> Unit,
) {
    var meName by rememberSaveable { mutableStateOf("") }
    var partnerName by rememberSaveable { mutableStateOf("") }
    var meColor by rememberSaveable { mutableStateOf(AccentPalette.first().argb) }
    var partnerColor by rememberSaveable { mutableStateOf(AccentPalette[3].argb) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.readableOn(),
                modifier = Modifier.size(44.dp),
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = "TwoChat",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Cuộc trò chuyện riêng của hai người,\nchỉ nằm trên chiếc máy này.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = meName,
            onValueChange = { meName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Bạn tên là gì?") },
            placeholder = { Text("Ví dụ: Minh") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.height(10.dp))
        ColorSwatchRow(
            selected = meColor,
            onSelect = { meColor = it },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(22.dp))

        OutlinedTextField(
            value = partnerName,
            onValueChange = { partnerName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Người ấy tên gì?") },
            placeholder = { Text("Ví dụ: An") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.height(10.dp))
        ColorSwatchRow(
            selected = partnerColor,
            onSelect = { partnerColor = it },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "Không tài khoản, không máy chủ, không quảng cáo.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            )
        }

        Spacer(Modifier.height(22.dp))

        Button(
            onClick = { onDone(meName, partnerName, meColor, partnerColor) },
            enabled = meName.isNotBlank() && partnerName.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("Bắt đầu trò chuyện", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(12.dp))
    }
}
