package com.flagfights.data.network

import com.flagfights.domain.ConnectionStatus
import com.flagfights.domain.CountryFlag
import com.flagfights.domain.MatchStatus
import com.flagfights.domain.PlayerState
import com.flagfights.domain.Round
import com.flagfights.domain.RoundResolution

private const val MAX_PLAYERS = 2

enum class RoomLifecycle {
    WAITING,
    READY,
    PLAYING,
    FINISHED
}

data class RemotePlayer(
    val playerId: String,
    val displayName: String,
    val isHost: Boolean,
    val isReady: Boolean,
    val livesRemaining: Int,
    val connected: Boolean
) {
    fun toMap(): Map<String, Any> = mapOf(
        "playerId" to playerId,
        "displayName" to displayName,
        "isHost" to isHost,
        "isReady" to isReady,
        "livesRemaining" to livesRemaining,
        "connected" to connected
    )

    fun toPlayerState(): PlayerState = PlayerState(
        playerId = playerId,
        livesRemaining = livesRemaining,
        connectionStatus = if (connected) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED
    )

    companion object {
        fun fromMap(value: Map<String, Any?>): RemotePlayer = RemotePlayer(
            playerId = value.string("playerId"),
            displayName = value.string("displayName"),
            isHost = value.boolean("isHost"),
            isReady = value.boolean("isReady"),
            livesRemaining = value.int("livesRemaining", PlayerState.INITIAL_LIVES),
            connected = value.boolean("connected", true)
        )
    }
}

data class RemoteRound(
    val number: Int,
    val targetCountry: String,
    val targetFlagEmoji: String,
    val options: List<CountryFlag>,
    val resolutionByPlayerId: Map<String, String>,
    val answersByPlayerId: Map<String, String>
) {
    fun toMap(): Map<String, Any> = mapOf(
        "number" to number,
        "targetCountry" to targetCountry,
        "targetFlagEmoji" to targetFlagEmoji,
        "options" to options.map {
            mapOf(
                "countryName" to it.countryName,
                "isoCode" to it.isoCode,
                "flagEmoji" to it.flagEmoji
            )
        },
        "resolutionByPlayerId" to resolutionByPlayerId,
        "answersByPlayerId" to answersByPlayerId
    )

    fun toRound(localPlayerId: String? = null): Round {
        val selectedCountryName = localPlayerId?.let(answersByPlayerId::get)
        val selectedCountry = options.firstOrNull { it.countryName == selectedCountryName }
        val resolution = when (localPlayerId?.let(resolutionByPlayerId::get)) {
            RoundResolution.CORRECT.name -> RoundResolution.CORRECT
            RoundResolution.INCORRECT.name -> RoundResolution.INCORRECT
            else -> RoundResolution.PENDING
        }
        val target = options.firstOrNull { it.countryName == targetCountry }
            ?: CountryFlag(
                countryName = targetCountry,
                isoCode = "",
                flagEmoji = targetFlagEmoji
            )
        return Round(
            targetCountry = target,
            flagOptions = options,
            correctAnswer = target,
            resolution = resolution,
            selectedAnswer = selectedCountry
        )
    }

    companion object {
        fun fromMap(value: Map<String, Any?>): RemoteRound = RemoteRound(
            number = value.int("number", 1),
            targetCountry = value.string("targetCountry"),
            targetFlagEmoji = value.string("targetFlagEmoji"),
            options = value.list("options").mapNotNull { option ->
                (option as? Map<*, *>)?.let {
                    CountryFlag(
                        countryName = it["countryName"] as? String ?: return@mapNotNull null,
                        isoCode = it["isoCode"] as? String ?: "",
                        flagEmoji = it["flagEmoji"] as? String ?: return@mapNotNull null
                    )
                }
            },
            resolutionByPlayerId = value.map("resolutionByPlayerId").mapValues { (_, result) -> result as? String ?: RoundResolution.PENDING.name },
            answersByPlayerId = value.map("answersByPlayerId").mapValues { (_, answer) -> answer as? String ?: "" }
        )
    }
}

data class RemoteFinalResult(
    val winnerPlayerId: String? = null,
    val reason: String? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "winnerPlayerId" to winnerPlayerId,
        "reason" to reason
    )

    companion object {
        fun fromMap(value: Map<String, Any?>): RemoteFinalResult = RemoteFinalResult(
            winnerPlayerId = value["winnerPlayerId"] as? String,
            reason = value["reason"] as? String
        )
    }
}

data class RoomSnapshot(
    val roomCode: String,
    val lifecycle: RoomLifecycle,
    val players: List<RemotePlayer>,
    val currentRound: RemoteRound? = null,
    val finalResult: RemoteFinalResult? = null,
    val lastEvent: String? = null
) {
    val canStartMatch: Boolean
        get() = players.size == MAX_PLAYERS && players.all { it.isReady && it.connected }

    val hasCapacity: Boolean
        get() = players.size < MAX_PLAYERS

    fun toMatchStatus(): MatchStatus = when (lifecycle) {
        RoomLifecycle.WAITING, RoomLifecycle.READY -> MatchStatus.WAITING_FOR_PLAYERS
        RoomLifecycle.PLAYING -> MatchStatus.IN_PROGRESS
        RoomLifecycle.FINISHED -> MatchStatus.FINISHED
    }

    fun localPlayerState(localPlayerId: String): PlayerState? =
        players.firstOrNull { it.playerId == localPlayerId }?.toPlayerState()

    companion object {
        fun fromDocument(roomCode: String, value: Map<String, Any?>): RoomSnapshot {
            val players = value.list("players").mapNotNull { player ->
                (player as? Map<*, *>)?.entries
                    ?.associate { (key, rawValue) -> key.toString() to rawValue }
                    ?.let(RemotePlayer::fromMap)
            }
            val lifecycle = value["lifecycle"] as? String ?: RoomLifecycle.WAITING.name
            val currentRound = (value["currentRound"] as? Map<*, *>)
                ?.entries
                ?.associate { (key, rawValue) -> key.toString() to rawValue }
                ?.let(RemoteRound::fromMap)
            val finalResult = (value["finalResult"] as? Map<*, *>)
                ?.entries
                ?.associate { (key, rawValue) -> key.toString() to rawValue }
                ?.let(RemoteFinalResult::fromMap)
            return RoomSnapshot(
                roomCode = roomCode,
                lifecycle = RoomLifecycle.valueOf(lifecycle),
                players = players,
                currentRound = currentRound,
                finalResult = finalResult,
                lastEvent = value["lastEvent"] as? String
            )
        }
    }
}

private fun Map<String, Any?>.string(key: String): String = this[key] as? String ?: ""
private fun Map<String, Any?>.boolean(key: String, default: Boolean = false): Boolean = this[key] as? Boolean ?: default
private fun Map<String, Any?>.int(key: String, default: Int = 0): Int = (this[key] as? Number)?.toInt() ?: default
private fun Map<String, Any?>.list(key: String): List<Any?> = this[key] as? List<Any?> ?: emptyList()
private fun Map<String, Any?>.map(key: String): Map<String, Any?> =
    (this[key] as? Map<*, *>)?.entries?.associate { (nestedKey, nestedValue) -> nestedKey.toString() to nestedValue } ?: emptyMap()
