package com.flagfights

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.flagfights.domain.ConnectionStatus
import com.flagfights.domain.CountryFlag
import com.flagfights.domain.GameEngine
import com.flagfights.domain.MatchEndReason
import com.flagfights.domain.MatchStatus
import com.flagfights.domain.PlayerState
import com.flagfights.domain.RoundResolution

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FlagFightsApp()
        }
    }
}

@Composable
fun FlagFightsApp() {
    MaterialTheme {
        val navController = rememberNavController()
        Scaffold { padding ->
            FlagFightsNavGraph(navController = navController, padding = padding)
        }
    }
}

@Composable
private fun FlagFightsNavGraph(navController: NavHostController, padding: PaddingValues) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = Modifier.padding(padding)
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onCreateRoom = { navController.navigate(Screen.WaitingRoom.createRoute("ABCD")) },
                onJoinRoom = { roomCode ->
                    val normalizedCode = roomCode.ifBlank { "ABCD" }.uppercase()
                    navController.navigate(Screen.WaitingRoom.createRoute(normalizedCode))
                }
            )
        }
        composable(
            route = Screen.WaitingRoom.route,
            arguments = listOf(navArgument(Screen.WaitingRoom.roomCodeArg) { type = NavType.StringType })
        ) { backStackEntry ->
            val roomCode = backStackEntry.arguments?.getString(Screen.WaitingRoom.roomCodeArg).orEmpty()
            RoomScreen(
                roomCode = roomCode,
                onStartGame = { navController.navigate(Screen.Game.route) },
                onBackHome = { navController.popBackStack(Screen.Home.route, false) }
            )
        }
        composable(Screen.Game.route) {
            GameScreen(
                onFinish = { playerWon, endReason ->
                    navController.navigate(Screen.Result.createRoute(playerWon, endReason))
                }
            )
        }
        composable(
            route = Screen.Result.route,
            arguments = listOf(
                navArgument(Screen.Result.playerWonArg) { type = NavType.BoolType },
                navArgument(Screen.Result.endReasonArg) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val playerWon = backStackEntry.arguments?.getBoolean(Screen.Result.playerWonArg) == true
            val endReason = backStackEntry.arguments
                ?.getString(Screen.Result.endReasonArg)
                ?.takeUnless { it == "NONE" }
                ?.let(MatchEndReason::valueOf)
            ResultScreen(
                playerWon = playerWon,
                endReason = endReason,
                onBackHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    onCreateRoom: () -> Unit,
    onJoinRoom: (String) -> Unit
) {
    var roomCode by rememberSaveable { mutableStateOf("") }

    ScreenContainer(title = "FlagFights", subtitle = "Prepara una partida rápida entre dos jugadores.") {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Inicio",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onCreateRoom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Crear sala")
                }
                OutlinedTextField(
                    value = roomCode,
                    onValueChange = { roomCode = it.take(6).uppercase() },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Código de sala") },
                    placeholder = { Text("Ej. ABCD") }
                )
                OutlinedButton(
                    onClick = { onJoinRoom(roomCode) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Unirse a sala")
                }
            }
        }
    }
}

@Composable
private fun RoomScreen(
    roomCode: String,
    onStartGame: () -> Unit,
    onBackHome: () -> Unit
) {
    val players = listOf(
        PlayerState(playerId = "Tú", connectionStatus = ConnectionStatus.CONNECTED),
        PlayerState(playerId = "Rival", connectionStatus = ConnectionStatus.DISCONNECTED)
    )

    ScreenContainer(title = "Sala de espera", subtitle = "Comparte el código y confirma el estado de ambos jugadores.") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoCard(title = "Código de sala") {
                Text(
                    text = roomCode,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            InfoCard(title = "Jugadores") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    players.forEach { player ->
                        PlayerStatusRow(player = player)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onBackHome,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Volver")
                }
                Button(
                    onClick = onStartGame,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Iniciar partida")
                }
            }
        }
    }
}

@Composable
private fun GameScreen(onFinish: (Boolean, MatchEndReason?) -> Unit) {
    val engine = remember { GameEngine() }
    val localPlayerId = "Tú"
    val rivalPlayerId = "Rival"
    val heartbeatIntervalMillis = 1_500L
    val abandonmentThresholdMillis = 5_000L
    val lifecycleOwner = LocalLifecycleOwner.current
    var matchState by remember { mutableStateOf(engine.createMatch(listOf(localPlayerId, rivalPlayerId))) }
    val currentRound = matchState.currentRound
    val playerState = matchState.players.first { it.playerId == localPlayerId }
    val opponentState = matchState.players.first { it.playerId == rivalPlayerId }

    LaunchedEffect(matchState.status) {
        while (matchState.status != MatchStatus.FINISHED) {
            val now = System.currentTimeMillis()
            matchState = engine.updateHeartbeat(matchState, localPlayerId, now)
            matchState = engine.resolveAbandonmentByTimeout(matchState, abandonmentThresholdMillis, now)
            delay(heartbeatIntervalMillis)
        }
    }

    DisposableEffect(lifecycleOwner, matchState.status) {
        val observer = LifecycleEventObserver { _, event ->
            val now = System.currentTimeMillis()
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    matchState = engine.updateHeartbeat(matchState, localPlayerId, now)
                }
                Lifecycle.Event.ON_STOP -> {
                    matchState = engine.markPlayerDisconnected(matchState, localPlayerId, now)
                }
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    ScreenContainer(title = "Partida", subtitle = "Selecciona la bandera correcta antes de quedarte sin vidas.") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoCard(title = "País objetivo") {
                Text(
                    text = currentRound.targetCountry.countryName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LivesCard(name = playerState.playerId, lives = playerState.livesRemaining, modifier = Modifier.weight(1f))
                LivesCard(name = opponentState.playerId, lives = opponentState.livesRemaining, modifier = Modifier.weight(1f))
            }
            InfoCard(title = "Presencia") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlayerStatusRow(player = playerState)
                    PlayerStatusRow(player = opponentState)
                    Text(
                        text = if (matchState.endReason == MatchEndReason.OPPONENT_DISCONNECTED) {
                            "La partida terminó por desconexión confirmada del rival."
                        } else {
                            "Se mantiene un heartbeat periódico y se declara abandono tras ${abandonmentThresholdMillis / 1000} segundos sin actividad."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = {
                        matchState = engine.markPlayerDisconnected(matchState, rivalPlayerId)
                    }) {
                        Text("Simular abandono del rival")
                    }
                }
            }
            Text(
                text = when (currentRound.resolution) {
                    RoundResolution.PENDING -> "Elige una bandera"
                    RoundResolution.CORRECT -> "¡Respuesta correcta!"
                    RoundResolution.INCORRECT -> "Respuesta incorrecta, perdiste una vida."
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                currentRound.flagOptions.chunked(2).forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowOptions.forEach { option ->
                            FlagOptionCard(
                                option = option,
                                isSelected = currentRound.selectedAnswer?.countryName == option.countryName,
                                modifier = Modifier.weight(1f),
                                enabled = currentRound.resolution == RoundResolution.PENDING,
                                onClick = {
                                    matchState = engine.submitAnswer(matchState, playerState.playerId, option.countryName)
                                }
                            )
                        }
                        repeat(2 - rowOptions.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Button(
                onClick = {
                    if (matchState.status == MatchStatus.FINISHED) {
                        onFinish(matchState.winner == playerState.playerId, matchState.endReason)
                    } else {
                        matchState = engine.advanceRound(matchState)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = matchState.status == MatchStatus.FINISHED || currentRound.resolution != RoundResolution.PENDING
            ) {
                Text(if (matchState.status == MatchStatus.FINISHED) "Ver resultado" else "Siguiente ronda")
            }
        }
    }
}

@Composable
private fun ResultScreen(
    playerWon: Boolean,
    endReason: MatchEndReason?,
    onBackHome: () -> Unit
) {
    val title = if (playerWon) "¡Ganaste!" else "Has perdido"
    val description = when {
        endReason == MatchEndReason.OPPONENT_DISCONNECTED && playerWon -> {
            "Victoria por desconexión del rival. Se detectó abandono y se te marcó como ganador automáticamente."
        }
        endReason == MatchEndReason.OPPONENT_DISCONNECTED && !playerWon -> {
            "Derrota por desconexión propia. Tu rival fue marcado como ganador automático."
        }
        playerWon -> {
            "Sobreviviste a la partida acertando más banderas que tu rival."
        }
        else -> {
            "Te quedaste sin vidas. Vuelve al inicio para crear otra partida."
        }
    }

    ScreenContainer(title = "Resultado", subtitle = "Resumen final de la partida.") {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (playerWon) Color(0xFFDFF7E2) else Color(0xFFFFE1E1)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onBackHome) {
                    Text("Volver al inicio")
                }
            }
        }
    }
}

@Composable
private fun ScreenContainer(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        content()
    }
}

@Composable
private fun InfoCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}

@Composable
private fun PlayerStatusRow(player: PlayerState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = player.playerId, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusChip(
                label = if (player.connectionStatus == ConnectionStatus.CONNECTED) "Conectado" else "Desconectado",
                active = player.connectionStatus == ConnectionStatus.CONNECTED
            )
            StatusChip(
                label = "${player.livesRemaining} vidas",
                active = player.livesRemaining > 0
            )
        }
    }
}

@Composable
private fun StatusChip(label: String, active: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (active) Color(0xFF16A34A) else Color(0xFFDC2626))
            )
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun LivesCard(name: String, lives: Int, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(PlayerState.INITIAL_LIVES) { index ->
                    Text(
                        text = if (index < lives) "❤️" else "🖤",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun FlagOptionCard(
    option: CountryFlag,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .border(
                width = 2.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = option.flagEmoji, style = MaterialTheme.typography.displaySmall)
            Text(text = option.countryName, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private sealed class Screen(val route: String) {
    data object Home : Screen("home")

    data object WaitingRoom : Screen("room/{roomCode}") {
        const val roomCodeArg = "roomCode"
        fun createRoute(roomCode: String) = "room/$roomCode"
    }

    data object Game : Screen("game")

    data object Result : Screen("result/{playerWon}/{endReason}") {
        const val playerWonArg = "playerWon"
        const val endReasonArg = "endReason"
        fun createRoute(playerWon: Boolean, endReason: MatchEndReason?) = "result/$playerWon/${endReason?.name ?: "NONE"}"
    }
}
