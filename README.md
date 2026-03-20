# FlagFights

Aplicación base de **Android nativo con Kotlin** para iniciar el desarrollo de FlagFights.

## Arquitectura elegida

Se creó un proyecto Gradle de un solo módulo `app/` usando:

- **Kotlin + Android nativo**.
- **Jetpack Compose** para la UI.
- **Navigation Compose** para la navegación entre pantallas.
- **Material 3** como tema base de la aplicación.

## Estructura inicial

La app incluye cuatro pantallas base para evolucionar la experiencia del juego:

1. **Inicio**: punto de entrada de la aplicación.
2. **Sala de espera**: preparación de jugadores y estado previo a la partida.
3. **Juego**: flujo principal de la partida.
4. **Resultado**: resumen final del enfrentamiento.

## Archivos principales

- `settings.gradle.kts`: configuración del proyecto y módulos.
- `build.gradle.kts`: plugins raíz del proyecto.
- `app/build.gradle.kts`: configuración Android, Compose y dependencias.
- `app/src/main/AndroidManifest.xml`: declaración de la app y `MainActivity`.
- `app/src/main/java/com/flagfights/MainActivity.kt`: navegación base y pantallas iniciales.
- `app/src/main/res/values/themes.xml`: tema base de Material 3.

## Cómo arrancar

### Opción 1: Android Studio

1. Abrir la carpeta del repositorio en **Android Studio**.
2. Esperar a que Gradle sincronice el proyecto.
3. Ejecutar la configuración `app` sobre un emulador o dispositivo Android.

### Opción 2: línea de comandos

Si tienes el SDK de Android y Gradle configurados, puedes compilar con:

```bash
./gradlew assembleDebug
```

> Nota: en esta base se incluyeron los archivos de configuración Gradle del proyecto, pero no el wrapper (`gradlew`). Puedes generarlo con `gradle wrapper` si lo necesitas.

## Próximos pasos sugeridos

- Añadir estado real para salas y partidas.
- Incorporar ViewModels y capa de dominio/datos.
- Crear componentes reutilizables para tablero, puntajes y banderas.
- Agregar pruebas UI y unitarias.


## Capa de red 1v1 con Firestore

Se añadió una base para multijugador 1 contra 1 usando **Firebase Firestore** con una colección `rooms/{roomCode}`.

### Esquema remoto propuesto

- `roomCode`: código compartible de 6 caracteres.
- `lifecycle`: `WAITING`, `READY`, `PLAYING` o `FINISHED`.
- `maxPlayers`: límite fijo en `2`.
- `players[]`: lista de jugadores con `playerId`, `displayName`, `isHost`, `isReady`, `livesRemaining` y `connected`.
- `currentRound`: ronda activa con país objetivo, opciones, respuestas por jugador y resolución por jugador.
- `finalResult`: ganador final y motivo de cierre.
- `lastEvent`: último evento útil para depuración/UI.

### Clases añadidas

- `RoomCodeGenerator`: genera códigos únicos y compartibles.
- `MatchRepository`: contrato para crear sala, unirse, escuchar cambios, marcar listo, iniciar, avanzar ronda y enviar respuestas.
- `FirestoreMatchRepository`: implementación con transacciones y listeners en tiempo real mediante `addSnapshotListener`.
- `RoomViewModel`: ejemplo de capa de presentación que expone el estado remoto como `StateFlow` para Compose.

### Flujo recomendado en UI

1. Crear o unirse a una sala usando `createRoom` / `joinRoom`.
2. Vincular la pantalla a `observeRoom(roomCode)` o a `RoomViewModel.roomState`.
3. Reflejar en la UI los cambios de jugadores conectados, listos, inicio, respuestas, vidas y resultado final.
4. Invocar `startMatch` solo cuando haya exactamente 2 jugadores conectados y listos.
