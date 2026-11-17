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
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.twchat.app.data.Message
import com.twchat.app.data.Sender
import com.twchat.app.ui.AppUiState
import com.twchat.app.ui.AppViewModel
import com.twchat.app.ui.ChatItem
import com.twchat.app.ui.ChatItems
import com.twchat.app.ui.components.ChatHeader
import com.twchat.app.ui.components.Composer
import com.twchat.app.ui.components.DayLabel
import com.twchat.app.ui.components.MessageActionsSheet
import com.twchat.app.ui.components.MessageBubble
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: AppUiState,
    vm: AppViewModel,
) {
    val chatItems = remember(state.messages) { ChatItems.build(state.messages) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var draft by rememberSaveable { mutableStateOf("") }
    var actionTarget by remember { mutableStateOf<Message?>(null) }

    LaunchedEffect(chatItems.size) {
        if (chatItems.isNotEmpty()) listState.scrollToItem(chatItems.lastIndex)
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
                profile = state.profile,
                isSearching = state.isSearching,
                query = state.query,
                onQueryChange = vm::setQuery,
                onToggleSearch = vm::toggleSearch,
                onOpenSettings = vm::openSettings,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (chatItems.isEmpty()) {
                    EmptyConversation(isSearching = state.isSearching)
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
                                        val isMine = item.message.sender == Sender.ME
                                        MessageBubble(
                                            message = item.message,
                                            isMine = isMine,
                                            accent = Color(
                                                if (isMine) {
                                                    state.profile.meColor
                                                } else {
                                                    state.profile.partnerColor
                                                },
                                            ),
                                            maxWidth = maxBubbleWidth,
                                            onLongPress = { actionTarget = it },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Composer(
                profile = state.profile,
                sendAsPartner = state.sendAsPartner,
                draft = draft,
                onDraftChange = { draft = it },
                onToggleSendAs = vm::toggleSendAs,
                onSend = {
                    vm.send(draft)
                    draft = ""
                },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 150.dp),
        )
    }

    actionTarget?.let { message ->
        ModalBottomSheet(
            onDismissRequest = { actionTarget = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            MessageActionsSheet(
                message = message,
                onReact = { emoji ->
                    vm.toggleReaction(message, emoji)
                    actionTarget = null
                },
                onCopy = {
                    clipboard.setText(AnnotatedString(message.body))
                    actionTarget = null
                    scope.launch { snackbarHostState.showSnackbar("Đã sao chép tin nhắn") }
                },
                onDelete = {
                    vm.deleteMessage(message)
                    actionTarget = null
                    scope.launch { snackbarHostState.showSnackbar("Đã xoá tin nhắn") }
                },
            )
        }
    }
}

@Composable
private fun EmptyConversation(isSearching: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (isSearching) Icons.Rounded.SearchOff else Icons.Rounded.Favorite,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (isSearching) "Không tìm thấy tin nhắn nào" else "Chưa có tin nhắn nào",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (isSearching) {
                "Thử một từ khoá khác xem sao."
            } else {
                "Nhấn vào dòng \"Gửi với tên…\" ở dưới để đổi người gửi, rồi bắt đầu cuộc trò chuyện."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
