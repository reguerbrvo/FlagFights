package com.flagfights.data.network

import com.flagfights.domain.GameEngine
import com.flagfights.domain.PlayerState
import com.flagfights.domain.RoundResolution
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreMatchRepository(
    private val firestore: FirebaseFirestore,
    private val gameEngine: GameEngine = GameEngine(),
    private val roomCodeGenerator: RoomCodeGenerator = RoomCodeGenerator()
) : MatchRepository {

    override fun observeRoom(roomCode: String): Flow<RoomSnapshot> = callbackFlow {
        val registration = roomDocument(roomCode).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val data = snapshot?.data ?: return@addSnapshotListener
            trySend(RoomSnapshot.fromDocument(roomCode, data))
        }
        awaitClose { registration.remove() }
    }

    override suspend fun createRoom(hostPlayerName: String, hostDeviceId: String): RoomSnapshot {
        repeat(MAX_CODE_ATTEMPTS) {
            val roomCode = roomCodeGenerator.generate()
            val roomRef = roomDocument(roomCode)
            val host = RemotePlayer(
                playerId = hostDeviceId,
                displayName = hostPlayerName,
                isHost = true,
                isReady = false,
                livesRemaining = PlayerState.INITIAL_LIVES,
                connected = true
            )
            val payload = mapOf(
                "roomCode" to roomCode,
                "lifecycle" to RoomLifecycle.WAITING.name,
                "maxPlayers" to MAX_PLAYERS,
                "players" to listOf(host.toMap()),
                "lastEvent" to "room_created"
            )
            try {
                firestore.runTransaction { transaction ->
                    if (transaction.get(roomRef).exists()) {
                        throw RoomCodeCollisionException(roomCode)
                    }
                    transaction.set(roomRef, payload)
                }.await()
                return RoomSnapshot.fromDocument(roomCode, payload)
            } catch (_: RoomCodeCollisionException) {
                // retry with another generated code
            }
        }
        error("Unable to allocate a unique room code after $MAX_CODE_ATTEMPTS attempts.")
    }

    override suspend fun joinRoom(roomCode: String, playerName: String, deviceId: String) {
        val roomRef = roomDocument(roomCode)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(roomRef)
            check(snapshot.exists()) { "La sala $roomCode no existe." }
            val room = RoomSnapshot.fromDocument(roomCode, snapshot.data.orEmpty())
            require(room.players.none { it.playerId == deviceId }) { "Este jugador ya está dentro de la sala." }
            require(room.hasCapacity) { "La sala ya tiene 2 jugadores." }

            val joinedPlayer = RemotePlayer(
                playerId = deviceId,
                displayName = playerName,
                isHost = false,
                isReady = false,
                livesRemaining = PlayerState.INITIAL_LIVES,
                connected = true
            )
            val players = room.players + joinedPlayer
            transaction.update(roomRef, mapOf(
                "players" to players.map(RemotePlayer::toMap),
                "lifecycle" to if (players.size == MAX_PLAYERS) RoomLifecycle.READY.name else RoomLifecycle.WAITING.name,
                "lastEvent" to "player_joined"
            ))
        }.await()
    }

    override suspend fun setPlayerReady(roomCode: String, playerId: String, ready: Boolean) {
        mutatePlayers(roomCode) { room ->
            val players = room.players.map { player ->
                if (player.playerId == playerId) player.copy(isReady = ready, connected = true) else player
            }
            mapOf(
                "players" to players.map(RemotePlayer::toMap),
                "lifecycle" to if (players.size == MAX_PLAYERS && players.all { it.isReady }) RoomLifecycle.READY.name else RoomLifecycle.WAITING.name,
                "lastEvent" to if (ready) "player_ready" else "player_unready"
            )
        }
    }

    override suspend fun startMatch(roomCode: String, playerId: String) {
        val roomRef = roomDocument(roomCode)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(roomRef)
            val room = RoomSnapshot.fromDocument(roomCode, snapshot.data.orEmpty())
            require(room.players.size == MAX_PLAYERS) { "Se requieren 2 jugadores para iniciar." }
            require(room.players.firstOrNull { it.playerId == playerId }?.isHost == true) { "Solo el host puede iniciar la partida." }
            require(room.players.all { it.isReady && it.connected }) { "Todos los jugadores deben estar listos y conectados." }

            val match = gameEngine.createMatch(room.players.map(RemotePlayer::playerId))
            val round = match.currentRound
            val remoteRound = RemoteRound(
                number = 1,
                targetCountry = round.targetCountry.countryName,
                targetFlagEmoji = round.targetCountry.flagEmoji,
                options = round.flagOptions,
                resolutionByPlayerId = emptyMap(),
                answersByPlayerId = emptyMap()
            )
            transaction.set(roomRef, mapOf(
                "lifecycle" to RoomLifecycle.PLAYING.name,
                "players" to room.players.map { it.copy(livesRemaining = PlayerState.INITIAL_LIVES).toMap() },
                "currentRound" to remoteRound.toMap(),
                "finalResult" to null,
                "lastEvent" to "match_started"
            ), SetOptions.merge())
        }.await()
    }

    override suspend fun submitAnswer(roomCode: String, playerId: String, answerCountry: String) {
        val roomRef = roomDocument(roomCode)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(roomRef)
            val room = RoomSnapshot.fromDocument(roomCode, snapshot.data.orEmpty())
            require(room.lifecycle == RoomLifecycle.PLAYING) { "La partida no está en curso." }
            val currentRound = requireNotNull(room.currentRound) { "No existe ronda activa." }
            val isCorrect = currentRound.targetCountry == answerCountry

            val updatedPlayers = room.players.map { player ->
                if (player.playerId == playerId || isCorrect) {
                    if (player.playerId == playerId) player.copy(connected = true) else player
                } else {
                    player
                }
            }.map { player ->
                if (player.playerId == playerId && !isCorrect) {
                    player.copy(livesRemaining = (player.livesRemaining - 1).coerceAtLeast(0))
                } else {
                    player
                }
            }

            val updatedRound = currentRound.copy(
                answersByPlayerId = currentRound.answersByPlayerId + (playerId to answerCountry),
                resolutionByPlayerId = currentRound.resolutionByPlayerId + (playerId to if (isCorrect) RoundResolution.CORRECT.name else RoundResolution.INCORRECT.name)
            )

            val loser = updatedPlayers.firstOrNull { it.livesRemaining == 0 }
            val winnerId = loser?.let { defeated -> updatedPlayers.firstOrNull { it.playerId != defeated.playerId }?.playerId }
            val payload = mutableMapOf<String, Any?>(
                "players" to updatedPlayers.map(RemotePlayer::toMap),
                "currentRound" to updatedRound.toMap(),
                "lastEvent" to "answer_submitted"
            )
            if (winnerId != null) {
                payload["lifecycle"] = RoomLifecycle.FINISHED.name
                payload["finalResult"] = RemoteFinalResult(
                    winnerPlayerId = winnerId,
                    reason = "opponent_out_of_lives"
                ).toMap()
                payload["lastEvent"] = "match_finished"
            }
            transaction.set(roomRef, payload, SetOptions.merge())
        }.await()
    }


    override suspend fun advanceRound(roomCode: String, playerId: String) {
        val roomRef = roomDocument(roomCode)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(roomRef)
            val room = RoomSnapshot.fromDocument(roomCode, snapshot.data.orEmpty())
            require(room.lifecycle == RoomLifecycle.PLAYING) { "La partida no está en curso." }
            require(room.players.any { it.playerId == playerId }) { "El jugador no pertenece a la sala." }
            val currentRound = requireNotNull(room.currentRound) { "No existe ronda activa." }
            val recentCountries = buildList {
                add(currentRound.targetCountry)
            }
            val nextRound = gameEngine.createRound(recentCountries)
            val remoteRound = RemoteRound(
                number = currentRound.number + 1,
                targetCountry = nextRound.targetCountry.countryName,
                targetFlagEmoji = nextRound.targetCountry.flagEmoji,
                options = nextRound.flagOptions,
                resolutionByPlayerId = emptyMap(),
                answersByPlayerId = emptyMap()
            )
            transaction.set(roomRef, mapOf(
                "currentRound" to remoteRound.toMap(),
                "lastEvent" to "round_advanced"
            ), SetOptions.merge())
        }.await()
    }

    override suspend fun updateConnection(roomCode: String, playerId: String, connected: Boolean) {
        mutatePlayers(roomCode) { room ->
            val players = room.players.map { player ->
                if (player.playerId == playerId) player.copy(connected = connected) else player
            }
            mapOf(
                "players" to players.map(RemotePlayer::toMap),
                "lastEvent" to if (connected) "player_reconnected" else "player_disconnected"
            )
        }
    }

    private suspend fun mutatePlayers(roomCode: String, transform: (RoomSnapshot) -> Map<String, Any?>) {
        val roomRef = roomDocument(roomCode)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(roomRef)
            check(snapshot.exists()) { "La sala $roomCode no existe." }
            val room = RoomSnapshot.fromDocument(roomCode, snapshot.data.orEmpty())
            transaction.set(roomRef, transform(room), SetOptions.merge())
        }.await()
    }

    private fun roomDocument(roomCode: String): DocumentReference =
        firestore.collection(ROOMS_COLLECTION).document(roomCode.uppercase())

    private class RoomCodeCollisionException(roomCode: String) : IllegalStateException(roomCode)

    companion object {
        private const val ROOMS_COLLECTION = "rooms"
        private const val MAX_CODE_ATTEMPTS = 10
        private const val MAX_PLAYERS = 2
    }
}
