# FlagFights

Aplicación mobile de trivia competitiva para **dos jugadores** donde cada participante debe identificar banderas correctamente hasta dejar sin vidas al rival.

## Diseño de la app

### Stack elegido

FlagFights está planteada como una app de **Android nativo con Kotlin**. La base actual del repositorio ya sigue esa dirección con:

- **Kotlin** como lenguaje principal.
- **Android nativo** como plataforma objetivo inicial.
- **Jetpack Compose** para construir la interfaz.
- **Navigation Compose** para resolver el flujo entre pantallas.
- **Material 3** para el sistema visual.

Esta decisión favorece una integración directa con el ecosistema Android, tiempos de arranque simples para el MVP y una arquitectura clara para evolucionar la lógica del juego, salas y sincronización en línea.

### Solución de tiempo real elegida

Para el modo multijugador en vivo, la propuesta recomendada es **Firebase Firestore**.

#### ¿Por qué Firestore?

- Permite sincronizar documentos en tiempo real entre ambos jugadores.
- Simplifica el modelado de entidades como `rooms`, `matches`, `players` y `rounds`.
- Encaja bien con un MVP mobile sin necesidad de desplegar y mantener un servidor propio de WebSockets.
- Facilita agregar autenticación, analítica y reglas de seguridad dentro del mismo ecosistema Firebase.

#### Qué se sincroniza en tiempo real

- Estado de la sala: código, jugadores conectados y listo para empezar.
- Estado de la partida: ronda actual, opciones visibles, vidas restantes y estado final.
- Eventos del rival: conexión, abandono, respuesta enviada y resultado de la partida.

> Alternativas válidas para una siguiente etapa serían Firebase Realtime Database o WebSockets, pero para esta versión de diseño se documenta **Firestore** como la opción principal.

## Flujo de usuario completo

### 1. Inicio

Al abrir la app, el usuario llega a la pantalla de inicio y puede:

- **Crear sala**.
- **Unirse a sala** usando un código compartido.

### 2. Creación o ingreso a sala

#### Si crea una sala

- La app genera un código corto.
- Se crea un documento de sala en Firestore.
- El jugador anfitrión queda marcado como conectado y en espera.
- La interfaz muestra el código para compartirlo.

#### Si se une a una sala

- El jugador ingresa el código.
- La app valida que la sala exista y tenga espacio disponible.
- Si la sala está disponible, el jugador se agrega como rival.
- Ambos clientes reciben la actualización en tiempo real.

### 3. Sala de espera

En esta pantalla ambos jugadores pueden ver:

- Código de la sala.
- Estado de conexión de cada participante.
- Confirmación de que hay dos jugadores listos.

Cuando la sala está completa:

- El anfitrión puede iniciar la partida, o
- la app puede hacerlo automáticamente cuando ambos estén listos.

### 4. Inicio de partida

- Se crea el estado inicial del match.
- Cada jugador arranca con **2 vidas**.
- La app publica la **ronda actual** con una bandera objetivo y varias opciones posibles.
- Ambos clientes muestran exactamente la misma ronda.

### 5. Desarrollo de rondas

Durante cada ronda:

- La app muestra el país objetivo o la bandera a identificar.
- El jugador selecciona una respuesta.
- La respuesta se valida en la lógica del juego.
- Si la respuesta es incorrecta, el jugador pierde una vida.
- Luego se genera o publica la siguiente ronda si nadie fue eliminado.

### 6. Eventos especiales en tiempo real

Además de las respuestas, la app debe escuchar:

- Desconexión momentánea del rival.
- Reconexión.
- Abandono explícito de la partida.
- Cierre anticipado por derrota o victoria.

### 7. Resultado final

La partida termina cuando ocurre una de estas condiciones:

- Un jugador pierde todas sus vidas.
- El rival abandona la partida.

Al finalizar:

- Se marca el match como `finished`.
- Se registra el ganador.
- Ambos jugadores navegan a la pantalla de resultado.
- La UI muestra si fue **victoria** o **derrota** y el motivo si se desea exponerlo.

## Reglas del juego

Las reglas funcionales del MVP quedan definidas así:

- La partida es de **2 jugadores**.
- Cada jugador comienza con **2 vidas**.
- El juego avanza por **rondas**.
- En cada ronda, cada jugador debe responder una consigna de banderas.
- **Cada error resta 1 vida**.
- Cuando un jugador llega a **0 vidas**, pierde la partida.
- El otro jugador gana automáticamente cuando su rival se queda sin vidas.
- Si un jugador **abandona** la sala o la partida, el rival gana por abandono.
- Si ambos siguen activos y con vidas disponibles, la app continúa generando nuevas rondas.

## Arquitectura y estructura de carpetas propuesta

La base actual del repositorio ya contiene una capa `domain`, pero para escalar la app conviene evolucionar hacia una estructura modular por responsabilidad.

### Propuesta de estructura

```text
app/
  src/main/java/com/flagfights/
    app/
      navigation/
      theme/
      di/
    data/
      remote/
      repository/
      mapper/
    domain/
      model/
      engine/
      repository/
      usecase/
    feature/
      home/
      room/
      game/
      result/
    core/
      ui/
      common/
      extensions/
      realtime/
```

### Responsabilidad de cada módulo o carpeta

#### `app/`

Responsabilidad: composición global de la aplicación.

- Configuración de navegación principal.
- Tema visual.
- Inyección de dependencias.
- Punto de entrada (`MainActivity`).

#### `data/`

Responsabilidad: acceso a fuentes externas y persistencia.

- **`remote/`**: clientes de Firebase, DTOs y acceso a Firestore.
- **`repository/`**: implementación concreta de repositorios del dominio.
- **`mapper/`**: conversión entre documentos remotos y modelos de dominio.

#### `domain/`

Responsabilidad: reglas de negocio puras del juego.

- **`model/`**: entidades como jugador, ronda, partida y estado de conexión.
- **`engine/`**: lógica central del juego, por ejemplo cálculo de vidas, transición de rondas y victoria.
- **`repository/`**: contratos que usa el dominio para interactuar con datos.
- **`usecase/`**: casos de uso como crear sala, unirse, iniciar partida, responder ronda y abandonar.

#### `feature/`

Responsabilidad: implementación de pantallas por funcionalidad.

- **`home/`**: crear sala y unirse con código.
- **`room/`**: sala de espera, estado de jugadores y botón de inicio.
- **`game/`**: tablero principal, opciones de respuesta, vidas y progreso.
- **`result/`**: resultado final, resumen y acciones para salir o revancha.

#### `core/`

Responsabilidad: componentes y utilidades compartidas.

- **`ui/`**: componentes reutilizables de Compose.
- **`common/`**: constantes, utilidades y helpers transversales.
- **`extensions/`**: extensiones de Kotlin o Android.
- **`realtime/`**: abstracciones compartidas para listeners, presencia y sincronización.

## Modelo funcional sugerido para Firestore

Una forma simple de modelar el backend en tiempo real es:

```text
rooms/{roomId}
  - hostId
  - guestId
  - status
  - createdAt
  - currentMatchId

matches/{matchId}
  - roomId
  - status
  - currentRound
  - players
  - winnerId
  - finishReason

matches/{matchId}/rounds/{roundId}
  - targetCountry
  - options
  - correctAnswer
  - startedAt
```

Con este enfoque:

- `rooms` maneja el ciclo de vida previo al inicio.
- `matches` centraliza el estado compartido de la partida.
- La subcolección `rounds` conserva historial y facilita auditoría o repeticiones.

## Configuración de credenciales y entorno para Firebase

Si se adopta Firebase Firestore, el proyecto debe documentar y preparar la configuración local de credenciales.

### Archivos esperados

#### Android

- `app/google-services.json`: credenciales del proyecto Firebase para Android.
- Variables opcionales en `local.properties` o `gradle.properties` para flags de entorno si se quieren separar `debug` y `release`.

### Pasos de configuración

1. Crear un proyecto en **Firebase Console**.
2. Registrar la app Android con el `applicationId` correcto.
3. Descargar el archivo **`google-services.json`**.
4. Colocarlo en:

```text
app/google-services.json
```

5. Agregar el plugin de Google Services y las dependencias de Firebase necesarias en Gradle.
6. Verificar que Firestore esté habilitado en Firebase Console.
7. Configurar reglas de seguridad para evitar escrituras o lecturas no autorizadas.

### Recomendaciones para el repositorio

- **No subir credenciales reales** al repositorio público.
- Incluir un archivo de referencia como `app/google-services.json.example` o documentación clara en este `README`.
- Explicar en `.gitignore` que `google-services.json` debe mantenerse local.
- Si se usan variables adicionales, documentarlas en un archivo `.env.example` o en `local.properties.example`.

### Variables o parámetros que conviene documentar

Si el proyecto crece y necesita entornos, conviene dejar explícito:

- `FIREBASE_PROJECT_ID`
- `FIREBASE_APP_ID`
- `FIREBASE_API_KEY`
- `FIREBASE_STORAGE_BUCKET`

En Android estas credenciales suelen resolverse desde `google-services.json`, pero documentarlas ayuda a depurar configuraciones y preparar otros clientes en el futuro.

## Relación con la base actual del repositorio

La implementación existente ya refleja partes importantes de este diseño:

- Pantallas de **inicio**, **sala**, **juego** y **resultado**.
- Un motor de reglas en `domain`.
- Estados de jugador, vidas, ronda y resultado.

El siguiente paso natural es conectar estas pantallas y modelos con una capa `data` basada en Firestore para convertir la demo local en una experiencia multijugador real.

## Cómo arrancar

### Opción 1: Android Studio

1. Abrir la carpeta del repositorio en **Android Studio**.
2. Esperar a que Gradle sincronice el proyecto.
3. Si se usa Firebase, agregar `app/google-services.json` antes de compilar.
4. Ejecutar la configuración `app` sobre un emulador o dispositivo Android.

### Opción 2: línea de comandos

Si tienes el SDK de Android y Gradle configurados, puedes compilar con:

```bash
./gradlew assembleDebug
```

> Nota: en esta base se incluyeron los archivos de configuración Gradle del proyecto, pero no el wrapper (`gradlew`). Puedes generarlo con `gradle wrapper` si lo necesitas.
