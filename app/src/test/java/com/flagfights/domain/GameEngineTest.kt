package com.flagfights.domain

import com.flagfights.data.CountryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    private val engine = GameEngine()

    @Test
    fun createMatch_startsPlayersWithTwoLives() {
        val match = engine.createMatch(listOf("player-1", "player-2"), createdAtMillis = 1_000L)

        assertEquals(listOf(2, 2), match.players.map { it.livesRemaining })
        assertEquals(MatchStatus.IN_PROGRESS, match.status)
        assertEquals(listOf(1_000L, 1_000L), match.players.map { it.lastSeenAtMillis })
    }

    @Test
    fun createRound_generatesFourUniqueOptionsIncludingCorrectAnswer() {
        val round = engine.createRound()

        assertEquals(4, round.flagOptions.size)
        assertEquals(4, round.flagOptions.map { it.isoCode }.toSet().size)
        assertTrue(round.flagOptions.contains(round.correctAnswer))
        assertEquals(round.targetCountry, round.correctAnswer)
    }

    @Test
    fun submitAnswer_removesOneLifeOnWrongAnswer() {
        val match = engine.createMatch(listOf("player-1", "player-2"))
        val wrongAnswer = match.currentRound.flagOptions.first {
            it.isoCode != match.currentRound.correctAnswer.isoCode
        }

        val updatedMatch = engine.submitAnswer(match, "player-1", wrongAnswer.countryName)

        assertEquals(1, updatedMatch.players.first { it.playerId == "player-1" }.livesRemaining)
        assertEquals(RoundResolution.INCORRECT, updatedMatch.currentRound.resolution)
        assertEquals(MatchStatus.IN_PROGRESS, updatedMatch.status)
    }

    @Test
    fun submitAnswer_finishesMatchWhenPlayerRunsOutOfLives() {
        val baseMatch = engine.createMatch(listOf("player-1", "player-2"))
        val weakenedMatch = baseMatch.copy(
            players = baseMatch.players.map {
                if (it.playerId == "player-1") it.copy(livesRemaining = 1) else it
            }
        )
        val wrongAnswer = weakenedMatch.currentRound.flagOptions.first {
            it.isoCode != weakenedMatch.currentRound.correctAnswer.isoCode
        }

        val updatedMatch = engine.submitAnswer(weakenedMatch, "player-1", wrongAnswer.countryName)

        assertEquals(0, updatedMatch.players.first { it.playerId == "player-1" }.livesRemaining)
        assertEquals("player-2", updatedMatch.winner)
        assertEquals(MatchStatus.FINISHED, updatedMatch.status)
        assertEquals(MatchEndReason.ELIMINATION, updatedMatch.endReason)
    }


    @Test
    fun submitAnswer_clearsPreviousEndReasonWhenMatchContinues() {
        val match = engine.createMatch(listOf("player-1", "player-2")).copy(endReason = MatchEndReason.OPPONENT_DISCONNECTED)
        val wrongAnswer = match.currentRound.flagOptions.first {
            it.isoCode != match.currentRound.correctAnswer.isoCode
        }

        val updatedMatch = engine.submitAnswer(match, "player-1", wrongAnswer.countryName)

        assertEquals(MatchStatus.IN_PROGRESS, updatedMatch.status)
        assertEquals(null, updatedMatch.endReason)
    }

    @Test
    fun updateHeartbeat_marksPlayerConnectedAndRefreshesTimestamp() {
        val baseMatch = engine.createMatch(listOf("player-1", "player-2"), createdAtMillis = 1_000L)
        val disconnectedMatch = baseMatch.copy(
            players = baseMatch.players.map {
                if (it.playerId == "player-1") it.copy(connectionStatus = ConnectionStatus.DISCONNECTED) else it
            }
        )

        val updatedMatch = engine.updateHeartbeat(disconnectedMatch, "player-1", heartbeatAtMillis = 4_000L)

        val updatedPlayer = updatedMatch.players.first { it.playerId == "player-1" }
        assertEquals(ConnectionStatus.CONNECTED, updatedPlayer.connectionStatus)
        assertEquals(4_000L, updatedPlayer.lastSeenAtMillis)
    }

    @Test
    fun markPlayerDisconnected_finishesMatchAndAwardsWinToOpponent() {
        val match = engine.createMatch(listOf("player-1", "player-2"), createdAtMillis = 1_000L)

        val updatedMatch = engine.markPlayerDisconnected(match, "player-1", disconnectedAtMillis = 6_000L)

        val disconnectedPlayer = updatedMatch.players.first { it.playerId == "player-1" }
        assertEquals(ConnectionStatus.DISCONNECTED, disconnectedPlayer.connectionStatus)
        assertEquals(6_000L, disconnectedPlayer.lastSeenAtMillis)
        assertEquals("player-2", updatedMatch.winner)
        assertEquals(MatchStatus.FINISHED, updatedMatch.status)
        assertEquals(MatchEndReason.OPPONENT_DISCONNECTED, updatedMatch.endReason)
    }

    @Test
    fun resolveAbandonmentByTimeout_finishesMatchWhenSinglePlayerStopsSendingHeartbeat() {
        val match = engine.createMatch(listOf("player-1", "player-2"), createdAtMillis = 1_000L)
        val refreshedMatch = engine.updateHeartbeat(match, "player-2", heartbeatAtMillis = 4_000L)

        val updatedMatch = engine.resolveAbandonmentByTimeout(
            refreshedMatch,
            timeoutThresholdMillis = 2_000L,
            nowMillis = 4_500L
        )

        assertEquals("player-2", updatedMatch.winner)
        assertEquals(MatchStatus.FINISHED, updatedMatch.status)
        assertEquals(MatchEndReason.OPPONENT_DISCONNECTED, updatedMatch.endReason)
        assertEquals(
            ConnectionStatus.DISCONNECTED,
            updatedMatch.players.first { it.playerId == "player-1" }.connectionStatus
        )
    }

    @Test
    fun advanceRound_avoidsRecentCountryWhenPossible() {
        val match = engine.createMatch(listOf("player-1", "player-2"))
        val recentCountry = match.currentRound.targetCountry.isoCode
        val progressed = engine.advanceRound(match.copy(recentCountries = listOf(recentCountry)))

        assertNotEquals(recentCountry, progressed.currentRound.targetCountry.isoCode)
        assertFalse(progressed.currentRound.flagOptions.isEmpty())
    }

    @Test
    fun countryRepository_returnsUniqueOptionsAndIncludesCorrectAnswer() {
        val repository = CountryRepository.fromJson(
            """
            [
              {"name":"Argentina","isoCode":"AR","flagEmoji":"🇦🇷"},
              {"name":"Brasil","isoCode":"BR","flagEmoji":"🇧🇷"},
              {"name":"Canadá","isoCode":"CA","flagEmoji":"🇨🇦"},
              {"name":"Chile","isoCode":"CL","flagEmoji":"🇨🇱"},
              {"name":"España","isoCode":"ES","flagEmoji":"🇪🇸"}
            ]
            """.trimIndent()
        )

        val question = repository.getRoundCandidates(recentCountryCodes = listOf("AR"), recentWindow = 1)

        assertEquals(4, question.options.size)
        assertEquals(4, question.options.map { it.isoCode }.toSet().size)
        assertTrue(question.options.any { it.isoCode == question.correctAnswer.isoCode })
        assertNotEquals("AR", question.targetCountry.isoCode)
    }
}
