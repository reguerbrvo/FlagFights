package com.flagfights.domain

class GameEngine(
    private val countries: List<CountryFlag> = defaultCountries,
    private val recentCountryWindow: Int = 2,
    private val initialLives: Int = PlayerState.INITIAL_LIVES
) {
    init {
        require(countries.size >= ROUND_OPTIONS_COUNT) {
            "At least $ROUND_OPTIONS_COUNT countries are required to generate a round."
        }
    }

    fun createMatch(playerIds: List<String>): MatchState {
        require(playerIds.isNotEmpty()) { "A match requires at least one player." }

        val players = playerIds.map { playerId ->
            PlayerState(playerId = playerId, livesRemaining = initialLives)
        }

        return MatchState(
            players = players,
            currentRound = createRound(),
            recentCountries = emptyList()
        )
    }

    fun createRound(recentCountries: List<String> = emptyList()): Round {
        val availableTargets = countries.filterNot { it.countryName in recentCountries.takeLast(recentCountryWindow) }
        val targetPool = if (availableTargets.isNotEmpty()) availableTargets else countries
        val targetCountry = targetPool.random()
        val distractors = countries
            .asSequence()
            .filterNot { it.countryName == targetCountry.countryName }
            .shuffled()
            .take(ROUND_OPTIONS_COUNT - 1)
            .toList()

        val options = (distractors + targetCountry).shuffled()

        return Round(
            targetCountry = targetCountry,
            flagOptions = options,
            correctAnswer = targetCountry
        )
    }

    fun submitAnswer(matchState: MatchState, playerId: String, selectedCountry: String): MatchState {
        if (matchState.status == MatchStatus.FINISHED) {
            return matchState
        }

        val selectedOption = matchState.currentRound.flagOptions.firstOrNull {
            it.countryName == selectedCountry
        } ?: return matchState

        val answeredCorrectly = selectedOption.countryName == matchState.currentRound.correctAnswer.countryName
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
                recentCountries = updateRecentCountries(matchState, matchState.currentRound.targetCountry.countryName)
            )
        }

        return matchState.copy(
            players = updatedPlayers,
            currentRound = resolvedRound,
            recentCountries = updateRecentCountries(matchState, matchState.currentRound.targetCountry.countryName)
        )
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

    private fun updateRecentCountries(matchState: MatchState, countryName: String): List<String> {
        return (matchState.recentCountries + countryName).takeLast(recentCountryWindow)
    }

    companion object {
        const val ROUND_OPTIONS_COUNT = 4

        val defaultCountries = listOf(
            CountryFlag("Argentina", "🇦🇷"),
            CountryFlag("Brasil", "🇧🇷"),
            CountryFlag("Canadá", "🇨🇦"),
            CountryFlag("Francia", "🇫🇷"),
            CountryFlag("Alemania", "🇩🇪"),
            CountryFlag("Italia", "🇮🇹"),
            CountryFlag("Japón", "🇯🇵"),
            CountryFlag("México", "🇲🇽"),
            CountryFlag("España", "🇪🇸"),
            CountryFlag("Corea del Sur", "🇰🇷")
        )
    }
}
