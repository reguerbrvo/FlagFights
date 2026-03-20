package com.flagfights

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

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
            ScreenTemplate(
                title = "Inicio",
                description = "Pantalla principal para iniciar una partida o revisar el estado general.",
                actionLabel = "Ir a la sala de espera",
                onAction = { navController.navigate(Screen.WaitingRoom.route) }
            )
        }
        composable(Screen.WaitingRoom.route) {
            ScreenTemplate(
                title = "Sala de espera",
                description = "Espacio para reunir jugadores, confirmar equipos y esperar el comienzo.",
                actionLabel = "Comenzar juego",
                onAction = { navController.navigate(Screen.Game.route) }
            )
        }
        composable(Screen.Game.route) {
            ScreenTemplate(
                title = "Juego",
                description = "Vista principal del enfrentamiento con preguntas, banderas y puntaje.",
                actionLabel = "Ver resultado",
                onAction = { navController.navigate(Screen.Result.route) }
            )
        }
        composable(Screen.Result.route) {
            ScreenTemplate(
                title = "Resultado",
                description = "Resumen final con ganador, métricas de la partida y opción para reiniciar.",
                actionLabel = "Volver al inicio",
                onAction = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
    }
}

@Composable
private fun ScreenTemplate(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(onClick = onAction) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}

private sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object WaitingRoom : Screen("waiting_room")
    data object Game : Screen("game")
    data object Result : Screen("result")
}
