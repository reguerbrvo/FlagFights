package com.flagfights.data.network

import com.flagfights.domain.ConnectionStatus
import com.flagfights.domain.MatchStatus
import com.flagfights.domain.RoundResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteMatchModelsTest {

    @Test
    fun toPlayerState_preservesPlayerIdAndConnectionStatus() {
        val remotePlayer = RemotePlayer(
            playerId = "player-1",
            displayName = "Alice",
            isHost = true,
            isReady = true,
            livesRemaining = 1,
            connected = false
        )

        val playerState = remotePlayer.toPlayerState()

        assertEquals("player-1", playerState.playerId)
        assertEquals(1, playerState.livesRemaining)
        assertEquals(ConnectionStatus.DISCONNECTED, playerState.connectionStatus)
    }

    @Test
    fun remoteRound_serializationRoundTrip_keepsIsoCodesAndPlayerResolution() {
        val round = RemoteRound(
            number = 2,
            targetCountry = "Brasil",
            targetFlagEmoji = "🇧🇷",
            options = listOf(
                com.flagfights.domain.CountryFlag("Argentina", "AR", "🇦🇷"),
                com.flagfights.domain.CountryFlag("Brasil", "BR", "🇧🇷"),
                com.flagfights.domain.CountryFlag("Canadá", "CA", "🇨🇦"),
                com.flagfights.domain.CountryFlag("Chile", "CL", "🇨🇱")
            ),
            resolutionByPlayerId = mapOf("player-1" to RoundResolution.CORRECT.name),
            answersByPlayerId = mapOf("player-1" to "Brasil")
        )

        val parsed = RemoteRound.fromMap(round.toMap())
        val localRound = parsed.toRound(localPlayerId = "player-1")

        assertEquals(listOf("AR", "BR", "CA", "CL"), parsed.options.map { it.isoCode })
        assertEquals("BR", localRound.targetCountry.isoCode)
        assertEquals(RoundResolution.CORRECT, localRound.resolution)
        assertNotNull(localRound.selectedAnswer)
        assertEquals("Brasil", localRound.selectedAnswer?.countryName)
    }

    @Test
    fun roomSnapshot_localPlayerState_returnsNullWhenPlayerDoesNotExist() {
        val snapshot = RoomSnapshot(
            roomCode = "ABCD",
            lifecycle = RoomLifecycle.WAITING,
            players = listOf(
                RemotePlayer("player-1", "Alice", true, false, 2, true)
            )
        )

        assertNull(snapshot.localPlayerState("missing"))
        assertEquals(MatchStatus.WAITING_FOR_PLAYERS, snapshot.toMatchStatus())
        assertTrue(snapshot.hasCapacity)
    }
}
