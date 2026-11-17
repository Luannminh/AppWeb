package com.twchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twchat.app.ui.AppUiState
import com.twchat.app.ui.AppViewModel
import com.twchat.app.ui.ChatItem
import com.twchat.app.ui.ChatItems
import com.twchat.app.ui.components.ChatHeader
import com.twchat.app.ui.components.Composer
import com.twchat.app.ui.components.DayLabel
import com.twchat.app.ui.components.MessageBubble
import com.twchat.app.util.RelativeTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(state: AppUiState, vm: AppViewModel) {
    val me = state.me ?: return
    val partner = state.partner ?: return

    val chatItems = remember(state.messages) { ChatItems.build(state.messages) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var draft by rememberSaveable { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(chatItems.size) {
        if (chatItems.isNotEmpty()) listState.scrollToItem(chatItems.lastIndex)
    }
    LaunchedEffect(state.messages.size) {
        vm.markRead()
    }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    LaunchedEffect(state.extras.error) {
        state.extras.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            vm.dismissError()
        }
    }
    LaunchedEffect(state.extras.notice) {
        state.extras.notice?.let { message ->
            snackbarHostState.showSnackbar(message)
            vm.dismissNotice()
        }
    }

    val typing = state.live.typingUntil > now
    val online = state.live.partnerOnline
    val statusText = when {
        typing -> "Đang nhập…"
        online -> "Đang hoạt động"
        else -> RelativeTime.lastSeen(state.live.partnerLastSeen)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            ChatHeader(
                partner = partner.copy(online = online),
                statusText = statusText,
                statusHighlighted = typing || online,
                connected = state.live.connected,
                onOpenSettings = vm::openSettings,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (chatItems.isEmpty()) {
                    EmptyConversation(partnerName = partner.displayName)
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val maxBubbleWidth = maxWidth * 0.76f
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(items = chatItems, key = { it.key }) { item ->
                                when (item) {
                                    is ChatItem.DayHeader -> DayLabel(label = item.label)
                                    is ChatItem.Bubble -> {
                                        val message = item.message
                                        val isMine = message.senderId == me.id
                                        MessageBubble(
                                            message = message,
                                            isMine = isMine,
                                            accent = Color(if (isMine) me.color else partner.color),
                                            maxWidth = maxBubbleWidth,
                                            onLongPress = { pressed ->
                                                clipboard.setText(AnnotatedString(pressed.body))
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Đã sao chép tin nhắn")
                                                }
                                            },
                                            onRetry = vm::retry,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Composer(
                draft = draft,
                onDraftChange = { draft = it },
                onSend = {
                    vm.send(draft)
                    draft = ""
                },
                accent = Color(me.color),
                onTyping = vm::sendTyping,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 110.dp),
        )
    }
}

@Composable
private fun EmptyConversation(partnerName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Chưa có tin nhắn nào",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Gửi lời chào đầu tiên cho $partnerName nhé 👋",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
