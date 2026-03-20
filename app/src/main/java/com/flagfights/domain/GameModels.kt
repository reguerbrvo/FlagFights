package com.flagfights.domain

enum class ConnectionStatus {
    CONNECTED,
    DISCONNECTED
}

data class PlayerState(
    val playerId: String,
    val livesRemaining: Int = INITIAL_LIVES,
    val connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTED
) {
    companion object {
        const val INITIAL_LIVES = 2
    }
}

data class CountryFlag(
    val countryName: String,
    val isoCode: String,
    val flagEmoji: String
)

enum class RoundResolution {
    PENDING,
    CORRECT,
    INCORRECT
}

data class Round(
    val targetCountry: CountryFlag,
    val flagOptions: List<CountryFlag>,
    val correctAnswer: CountryFlag,
    val resolution: RoundResolution = RoundResolution.PENDING,
    val selectedAnswer: CountryFlag? = null
)

enum class MatchStatus {
    WAITING_FOR_PLAYERS,
    IN_PROGRESS,
    FINISHED
}

data class MatchState(
    val players: List<PlayerState>,
    val currentRound: Round,
    val winner: String? = null,
    val status: MatchStatus = MatchStatus.IN_PROGRESS,
    val recentCountries: List<String> = emptyList()
)
