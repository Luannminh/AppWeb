package com.twchat.app.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twchat.app.data.Message
import com.twchat.app.ui.AppUiState
import com.twchat.app.ui.AppViewModel
import com.twchat.app.ui.ChatItem
import com.twchat.app.ui.ChatItems
import com.twchat.app.ui.components.AttachmentSheet
import com.twchat.app.ui.components.ChatHeader
import com.twchat.app.ui.components.Composer
import com.twchat.app.ui.components.DayLabel
import com.twchat.app.ui.components.MessageBubble
import com.twchat.app.ui.components.StickerSheet
import com.twchat.app.util.FileOpener
import com.twchat.app.util.RelativeTime
import com.twchat.app.util.VoiceRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(state: AppUiState, vm: AppViewModel) {
    val me = state.me ?: return
    val partner = state.partner ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    val recorder = remember { VoiceRecorder(context) }

    var draft by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var showAttach by remember { mutableStateOf(false) }
    var showStickers by remember { mutableStateOf(false) }
    var previewPath by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf(false) }
    var recordMillis by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val searching = state.extras.searchQuery.isNotBlank()
    val source = if (searching) state.searchResults else state.messages
    val chatItems = remember(source) { ChatItems.build(source) }
    val listState = rememberLazyListState()
    val editing = state.extras.editing

    fun notify(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    fun startRecording() {
        if (recorder.start()) {
            recording = true
            recordMillis = 0L
        } else {
            notify("Không mở được micro để ghi âm")
        }
    }

    fun finishRecording(send: Boolean) {
        recording = false
        if (!send) {
            recorder.cancel()
            return
        }
        val result = recorder.stop()
        if (result != null && result.second >= 400) {
            vm.sendVoice(result.first, result.second)
        } else {
            result?.first?.delete()
            notify("Bản ghi quá ngắn")
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.sendImage(it) }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.sendFile(it) }
    }
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startRecording() else notify("Cần quyền micro để gửi tin nhắn thoại")
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(recording) {
        while (recording) {
            recordMillis = recorder.elapsedMillis()
            delay(200)
        }
    }
    LaunchedEffect(editing?.clientId) {
        if (editing != null) draft = editing.body
    }
    LaunchedEffect(searchOpen) {
        if (!searchOpen) vm.setSearchQuery("")
    }
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

    val typing = state.live.isTyping(now)
    val online = state.live.partnerOnline(now)
    val lastSeen = maxOf(state.live.partnerLastSeen, partner.lastSeen)
    val statusText = when {
        typing -> "Đang nhập…"
        online -> "Đang hoạt động"
        else -> RelativeTime.lastSeen(lastSeen)
    }

    fun copyMessage(message: Message) {
        clipboard.setText(AnnotatedString(message.body.ifBlank { message.preview }))
        notify("Đã sao chép tin nhắn")
    }

    fun openFile(message: Message) {
        val path = message.attachmentPath ?: return
        scope.launch {
            val file = vm.fileFor(path)
            if (file == null) {
                notify("Không tải được tệp")
            } else {
                FileOpener.open(context, file, message.attachmentMime.orEmpty())
            }
        }
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
                onOpenSearch = { searchOpen = true },
            )

            if (searchOpen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.extras.searchQuery,
                        onValueChange = vm::setSearchQuery,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Tìm trong cuộc trò chuyện…") },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                        },
                    )
                    IconButton(onClick = { searchOpen = false }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Đóng tìm kiếm")
                    }
                }
                if (searching) {
                    Text(
                        text = if (state.searchResults.isEmpty()) {
                            "Không tìm thấy tin nhắn nào"
                        } else {
                            "${state.searchResults.size} tin nhắn khớp"
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when {
                    searching && state.searchResults.isEmpty() -> SearchEmpty()
                    chatItems.isEmpty() -> EmptyConversation(partnerName = partner.displayName)
                    else -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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
                                            playing = state.extras.playingClientId == message.clientId,
                                            playProgress = state.extras.playProgress,
                                            loadImage = vm::loadImage,
                                            onCopy = ::copyMessage,
                                            onEdit = vm::beginEdit,
                                            onDelete = { target -> vm.deleteMessage(target) },
                                            onRetry = vm::retry,
                                            onPlayVoice = vm::toggleVoice,
                                            onOpenFile = ::openFile,
                                            onOpenImage = { path -> previewPath = path },
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
                    val text = draft
                    draft = ""
                    if (editing != null) vm.saveEdit(text) else vm.send(text)
                },
                accent = Color(me.color),
                onTyping = vm::sendTyping,
                onAttach = { showAttach = true },
                onMic = {
                    val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                },
                recording = recording,
                recordMillis = recordMillis,
                onStopRecording = { finishRecording(true) },
                onCancelRecording = { finishRecording(false) },
                uploading = state.extras.uploading,
                editing = editing != null,
                onCancelEdit = {
                    draft = ""
                    vm.cancelEdit()
                },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 110.dp),
        )
    }

    if (showAttach) {
        AttachmentSheet(
            onDismiss = { showAttach = false },
            onPickImage = {
                showAttach = false
                imagePicker.launch("image/*")
            },
            onPickFile = {
                showAttach = false
                filePicker.launch(arrayOf("*/*"))
            },
            onRecordVoice = {
                showAttach = false
                val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                if (granted) startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            },
            onPickSticker = {
                showAttach = false
                showStickers = true
            },
        )
    }

    if (showStickers) {
        StickerSheet(
            onDismiss = { showStickers = false },
            onSticker = { sticker ->
                showStickers = false
                vm.sendSticker(sticker)
            },
        )
    }

    previewPath?.let { path ->
        ImagePreviewDialog(path = path, loadImage = vm::loadImage, onDismiss = { previewPath = null })
    }
}

@Composable
private fun ImagePreviewDialog(
    path: String,
    loadImage: suspend (String) -> Bitmap?,
    onDismiss: () -> Unit,
) {
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = runCatching { loadImage(path) }.getOrNull()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Đóng") }
        },
        text = {
            val current = bitmap
            if (current != null) {
                androidx.compose.foundation.Image(
                    bitmap = current.asImageBitmap(),
                    contentDescription = "Ảnh",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text("Đang tải ảnh…")
            }
        },
    )
}

@Composable
private fun SearchEmpty() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Không tìm thấy tin nhắn nào",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyConversation(partnerName: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "👋",
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                text = "Hai bạn đã ghép đôi.\nGửi lời chào tới $partnerName nhé!",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
