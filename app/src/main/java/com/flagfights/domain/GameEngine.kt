package com.flagfights.domain

import com.flagfights.data.CountryRepository

class GameEngine(
    private val countryRepository: CountryRepository = CountryRepository(defaultCountries),
    private val recentCountryWindow: Int = 2,
    private val initialLives: Int = PlayerState.INITIAL_LIVES
) {
    init {
        require(countryRepository.getAllCountries().size >= ROUND_OPTIONS_COUNT) {
            "At least $ROUND_OPTIONS_COUNT countries are required to generate a round."
        }
    }

    fun createMatch(playerIds: List<String>, createdAtMillis: Long = System.currentTimeMillis()): MatchState {
        require(playerIds.isNotEmpty()) { "A match requires at least one player." }

        val players = playerIds.map { playerId ->
            PlayerState(
                playerId = playerId,
                livesRemaining = initialLives,
                connectionStatus = ConnectionStatus.CONNECTED,
                lastSeenAtMillis = createdAtMillis
            )
        }

        return MatchState(
            players = players,
            currentRound = createRound(),
            recentCountries = emptyList()
        )
    }

    fun createRound(recentCountries: List<String> = emptyList()): Round {
        val question = countryRepository.getRoundCandidates(
            recentCountryCodes = recentCountries,
            recentWindow = recentCountryWindow
        )

        return Round(
            targetCountry = question.targetCountry,
            flagOptions = question.options,
            correctAnswer = question.correctAnswer
        )
    }

    fun submitAnswer(matchState: MatchState, playerId: String, selectedCountry: String): MatchState {
        if (matchState.status == MatchStatus.FINISHED) {
            return matchState
        }

        val selectedOption = matchState.currentRound.flagOptions.firstOrNull {
            it.countryName == selectedCountry
        } ?: return matchState

        val answeredCorrectly = selectedOption.isoCode == matchState.currentRound.correctAnswer.isoCode
        val resolvedRound = matchState.currentRound.copy(
            resolution = if (answeredCorrectly) RoundResolution.CORRECT else RoundResolution.INCORRECT,
            selectedAnswer = selectedOption
        )

        val updatedPlayers = matchState.players.map { player ->
            if (player.playerId != playerId || answeredCorrectly) {
                player
            } else {
                player.copy(livesRemaining = (player.livesRemaining - 1).coerceAtLeast(0))
            }
        }

        val eliminatedPlayer = updatedPlayers.firstOrNull { it.livesRemaining == 0 }
        if (eliminatedPlayer != null) {
            val winner = updatedPlayers.firstOrNull { it.playerId != eliminatedPlayer.playerId }?.playerId
            return matchState.copy(
                players = updatedPlayers,
                currentRound = resolvedRound,
                winner = winner,
                status = MatchStatus.FINISHED,
                recentCountries = updateRecentCountries(matchState, matchState.currentRound.targetCountry.isoCode)
            )
        }

        return matchState.copy(
            players = updatedPlayers,
            currentRound = resolvedRound,
            recentCountries = updateRecentCountries(matchState, matchState.currentRound.targetCountry.isoCode)
        )
    }

    fun updateHeartbeat(matchState: MatchState, playerId: String, heartbeatAtMillis: Long = System.currentTimeMillis()): MatchState {
        if (matchState.status == MatchStatus.FINISHED) {
            return matchState
        }

        return matchState.copy(
            players = matchState.players.map { player ->
                if (player.playerId == playerId) {
                    player.copy(
                        connectionStatus = ConnectionStatus.CONNECTED,
                        lastSeenAtMillis = heartbeatAtMillis
                    )
                } else {
                    player
                }
            }
        )
    }

    fun markPlayerDisconnected(
        matchState: MatchState,
        playerId: String,
        disconnectedAtMillis: Long = System.currentTimeMillis()
    ): MatchState {
        if (matchState.status == MatchStatus.FINISHED) {
            return matchState
        }

        val updatedPlayers = matchState.players.map { player ->
            if (player.playerId == playerId) {
                player.copy(
                    connectionStatus = ConnectionStatus.DISCONNECTED,
                    lastSeenAtMillis = disconnectedAtMillis
                )
            } else {
                player
            }
        }

        val disconnectedPlayers = updatedPlayers.filter { it.connectionStatus == ConnectionStatus.DISCONNECTED }
        val connectedPlayers = updatedPlayers.filter { it.connectionStatus == ConnectionStatus.CONNECTED }

        val endReason = if (connectedPlayers.isEmpty()) {
            MatchEndReason.ALL_PLAYERS_DISCONNECTED
        } else {
            MatchEndReason.OPPONENT_DISCONNECTED
        }

        return matchState.copy(
            players = updatedPlayers,
            winner = connectedPlayers.singleOrNull()?.playerId,
            status = MatchStatus.FINISHED,
            endReason = endReason
        )
    }

    fun resolveAbandonmentByTimeout(
        matchState: MatchState,
        timeoutThresholdMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): MatchState {
        if (matchState.status == MatchStatus.FINISHED) {
            return matchState
        }

        val timedOutPlayers = matchState.players.filter { player ->
            nowMillis - player.lastSeenAtMillis >= timeoutThresholdMillis
        }

        if (timedOutPlayers.size != 1) {
            return matchState
        }

        return markPlayerDisconnected(matchState, timedOutPlayers.single().playerId, nowMillis)
    }

    fun advanceRound(matchState: MatchState): MatchState {
        if (matchState.status == MatchStatus.FINISHED) {
            return matchState
        }

        return matchState.copy(
            currentRound = createRound(matchState.recentCountries),
            status = MatchStatus.IN_PROGRESS
        )
    }

    private fun updateRecentCountries(matchState: MatchState, countryCode: String): List<String> {
        return (matchState.recentCountries + countryCode).takeLast(recentCountryWindow)
    }

    companion object {
        const val ROUND_OPTIONS_COUNT = 4

        val defaultCountries = listOf(
            CountryFlag("Argentina", "AR", "🇦🇷"),
            CountryFlag("Brasil", "BR", "🇧🇷"),
            CountryFlag("Canadá", "CA", "🇨🇦"),
            CountryFlag("Francia", "FR", "🇫🇷"),
            CountryFlag("Alemania", "DE", "🇩🇪"),
            CountryFlag("Italia", "IT", "🇮🇹"),
            CountryFlag("Japón", "JP", "🇯🇵"),
            CountryFlag("México", "MX", "🇲🇽"),
            CountryFlag("España", "ES", "🇪🇸"),
            CountryFlag("Corea del Sur", "KR", "🇰🇷")
        )
    }
}
