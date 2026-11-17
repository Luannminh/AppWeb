package com.twchat.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.twchat.app.data.ChatDatabase
import com.twchat.app.data.ChatRepository
import com.twchat.app.data.Message
import com.twchat.app.data.Profile
import com.twchat.app.data.ProfileStore
import com.twchat.app.data.Sender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Screen { Chat, Settings }

data class AppUiState(
    val profile: Profile = Profile(),
    val messages: List<Message> = emptyList(),
    val query: String = "",
    val isSearching: Boolean = false,
    val sendAsPartner: Boolean = false,
    val screen: Screen = Screen.Chat,
    val ready: Boolean = false,
) {
    /** Name and colour of whoever the next message will be sent as. */
    val activeName: String get() = if (sendAsPartner) profile.partnerName else profile.meName
    val activeColor: Long get() = if (sendAsPartner) profile.partnerColor else profile.meColor

    val partnerName: String get() = profile.partnerName
    val partnerColor: Long get() = profile.partnerColor
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(ChatDatabase.getInstance(application).messageDao())
    private val profileStore = ProfileStore(application)

    private val query = MutableStateFlow("")
    private val isSearching = MutableStateFlow(false)
    private val sendAsPartner = MutableStateFlow(false)
    private val screen = MutableStateFlow(Screen.Chat)

    private val profile = profileStore.profile
        .stateIn(viewModelScope, SharingStarted.Eagerly, Profile())

    private val allMessages = repository.observeMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val controls: Flow<Controls> =
        combine(query, isSearching, sendAsPartner, screen) { q, searching, asPartner, current ->
            Controls(q, searching, asPartner, current)
        }

    val uiState: StateFlow<AppUiState> =
        combine(profile, allMessages, controls) { profile, messages, controls ->
            AppUiState(
                profile = profile,
                messages = if (controls.query.isBlank()) {
                    messages
                } else {
                    messages.filter { it.body.contains(controls.query, ignoreCase = true) }
                },
                query = controls.query,
                isSearching = controls.isSearching,
                sendAsPartner = controls.sendAsPartner,
                screen = controls.screen,
                ready = true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    // ---------------------------------------------------------------- composing

    fun send(text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        val sender = if (sendAsPartner.value) Sender.PARTNER else Sender.ME
        viewModelScope.launch { repository.send(sender, body) }
    }

    fun toggleSendAs() {
        sendAsPartner.value = !sendAsPartner.value
    }

    // -------------------------------------------------------------- message ops

    fun toggleReaction(message: Message, emoji: String) {
        val next = if (message.reaction == emoji) null else emoji
        viewModelScope.launch { repository.setReaction(message.id, next) }
    }

    fun deleteMessage(message: Message) {
        viewModelScope.launch { repository.delete(message.id) }
    }

    fun clearConversation() {
        viewModelScope.launch { repository.clear() }
    }

    suspend fun transcriptForSharing(): String =
        com.twchat.app.util.Transcript.build(
            messages = repository.snapshot(),
            profile = profile.value,
        )

    // ------------------------------------------------------------ search & nav

    fun setQuery(value: String) {
        query.value = value
    }

    fun toggleSearch() {
        val next = !isSearching.value
        isSearching.value = next
        if (!next) query.value = ""
    }

    fun openSettings() {
        screen.value = Screen.Settings
    }

    fun closeSettings() {
        screen.value = Screen.Chat
    }

    // ---------------------------------------------------------------- profile

    fun completeOnboarding(meName: String, partnerName: String, meColor: Long, partnerColor: Long) {
        saveProfile(meName, partnerName, meColor, partnerColor, onboarded = true)
    }

    fun saveProfile(
        meName: String,
        partnerName: String,
        meColor: Long,
        partnerColor: Long,
        onboarded: Boolean = true,
    ) {
        viewModelScope.launch {
            profileStore.save(
                Profile(
                    meName = meName.trim().ifEmpty { Profile.DEFAULT_ME_NAME },
                    partnerName = partnerName.trim().ifEmpty { Profile.DEFAULT_PARTNER_NAME },
                    meColor = meColor,
                    partnerColor = partnerColor,
                    onboarded = onboarded,
                ),
            )
        }
    }

    private data class Controls(
        val query: String,
        val isSearching: Boolean,
        val sendAsPartner: Boolean,
        val screen: Screen,
    )
}
