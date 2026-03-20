package com.flagfights.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flagfights.data.network.MatchRepository
import com.flagfights.data.network.RoomSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoomViewModel(
    private val repository: MatchRepository
) : ViewModel() {
    private val activeRoomCode = MutableStateFlow<String?>(null)

    val roomState: StateFlow<RoomSnapshot?> = activeRoomCode
        .filterNotNull()
        .flatMapLatest(repository::observeRoom)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun bindRoom(roomCode: String) {
        activeRoomCode.value = roomCode
    }

    fun createRoom(playerName: String, deviceId: String) {
        viewModelScope.launch {
            val room = repository.createRoom(playerName, deviceId)
            activeRoomCode.value = room.roomCode
        }
    }

    fun joinRoom(roomCode: String, playerName: String, deviceId: String) {
        viewModelScope.launch {
            repository.joinRoom(roomCode, playerName, deviceId)
            activeRoomCode.value = roomCode
        }
    }

    fun setReady(playerId: String, ready: Boolean) {
        val roomCode = activeRoomCode.value ?: return
        viewModelScope.launch {
            repository.setPlayerReady(roomCode, playerId, ready)
        }
    }

    fun startMatch(playerId: String) {
        val roomCode = activeRoomCode.value ?: return
        viewModelScope.launch {
            repository.startMatch(roomCode, playerId)
        }
    }

    fun submitAnswer(playerId: String, answerCountry: String) {
        val roomCode = activeRoomCode.value ?: return
        viewModelScope.launch {
            repository.submitAnswer(roomCode, playerId, answerCountry)
        }
    }

    fun advanceRound(playerId: String) {
        val roomCode = activeRoomCode.value ?: return
        viewModelScope.launch {
            repository.advanceRound(roomCode, playerId)
        }
    }

    fun updateConnection(playerId: String, connected: Boolean) {
        val roomCode = activeRoomCode.value ?: return
        viewModelScope.launch {
            repository.updateConnection(roomCode, playerId, connected)
        }
    }
}
