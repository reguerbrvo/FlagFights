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
