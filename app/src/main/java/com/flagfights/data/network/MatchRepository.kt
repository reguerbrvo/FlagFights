package com.flagfights.data.network

import kotlinx.coroutines.flow.Flow

interface MatchRepository {
    fun observeRoom(roomCode: String): Flow<RoomSnapshot>
    suspend fun createRoom(hostPlayerName: String, hostDeviceId: String): RoomSnapshot
    suspend fun joinRoom(roomCode: String, playerName: String, deviceId: String)
    suspend fun setPlayerReady(roomCode: String, playerId: String, ready: Boolean)
    suspend fun startMatch(roomCode: String, playerId: String)
    suspend fun submitAnswer(roomCode: String, playerId: String, answerCountry: String)
    suspend fun advanceRound(roomCode: String, playerId: String)
    suspend fun updateConnection(roomCode: String, playerId: String, connected: Boolean)
}
