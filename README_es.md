# Tarati — Un Juego de Tablero por George Spencer-Brown

<div align="center">

<img src="screenshots/logo.png" alt="Logo" style="display: block; margin: 0 auto;">

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-purple.svg)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.11.1-blue.svg)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Room](https://img.shields.io/badge/Room-2.8.4-red.svg)](https://developer.android.com/jetpack/androidx/releases/room)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://www.android.com)
[![Desktop](https://img.shields.io/badge/Desktop-Windows%20%7C%20macOS%20%7C%20Linux-orange.svg)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Web](https://img.shields.io/badge/Web-tarati.tech-blue.svg)](https://tarati.tech)
[![itch.io](https://img.shields.io/badge/itch.io-Tarati-FA5C5C.svg?logo=itchdotio&logoColor=white)](https://6e61646965.itch.io/tarati)
[![en](https://flagcdn.com/w20/gb.png)](README.md)

**Implementación multiplataforma (Android · Desktop · Web) del juego de estrategia Tarati**

[Jugar Online](https://tarati.tech) · [Google Play](#descargar) · [Reglas](#reglas) · [Multijugador](#modo-multijugador--tarati-six-26-jugadores) · [Tecnologías](#tecnologías) · [Descargar Desktop](#desktop) · [Documentación](docs/README.md)

</div>

---

## Plataformas Disponibles

### Web

Jugá directamente en **[tarati.tech](https://tarati.tech)** — sin instalación. Matchmaking online, partidas
clasificatorias y funciones sociales.

### Android

**Versión 1.0.0** — Disponible en Google Play  
Requisitos: Android 8.0+ (API 26)

### Desktop

**Versión 1.0.0** — Windows · macOS · Linux  
Requisitos: Windows 10+ · macOS 11+ · Linux (Ubuntu 20.04+)

### iOS

En desarrollo

---

## El Juego

**Tarati** es un juego de mesa estratégico creado por George Spencer-Brown, autor de *Laws of Form*, que aplica su
cálculo de distinciones al juego. La estructura es minimalista pero las consecuencias son profundas: los jugadores
mueven piezas, voltean piezas enemigas y promueven las propias a través de un tablero de 23 vértices organizados en
zonas concéntricas.

<img src="screenshots/board.png" alt="Board" style="display: block; margin: 0 auto;">

### Origen

Diseñado como aplicación práctica del cálculo de distinciones de Brown, Tarati encarna los principios matemáticos y
filosóficos de *Laws of Form* — obra fundamental que explora la lógica a través del concepto de distinción.

---

## Reglas

### Objetivo

Capturar la última pieza enemiga en un solo movimiento (Mit) o dejar al oponente sin movimientos legales (Stalemit).

### Estructura del Tablero

El tablero tiene 23 vértices distribuidos en cuatro zonas (no completamente disjuntas):

- **Centro absoluto**: 1 vértice (A1)
- **Puente**: 6 vértices que conectan el centro con la circunferencia
- **Circunferencia**: 12 vértices que forman el anillo exterior
- **Bases domésticas**: 4 vértices por jugador (8 en total); 2 son vértices D exclusivos y 2 son vértices C compartidos
  con la circunferencia

Cada jugador comienza con 4 Cobs colocados en su base doméstica.

### Piezas

**Cob** — la pieza básica. Se mueve solo hacia adelante, por aristas en dirección a la base enemiga. No puede retroceder
salvo que sea promovida.

**Rok** — un Cob promovido. Se mueve libremente en cualquier dirección a lo largo de cualquier arista. Un Cob se
convierte en Rok al entrar en cualquier vértice de la base doméstica del oponente. Un Rok capturado conserva su estado
de Rok: cambia de color, pero sigue siendo Rok.

### Movimiento

Una pieza se mueve a un vértice adyacente libre. Al llegar, voltea todas las piezas enemigas elegibles conectadas
directamente a ese vértice, convirtiéndolas al color del jugador activo.

Excepción: un Cob en su propia base doméstica puede moverse en cualquier dirección, pero solo si ese movimiento produce
al menos una captura.

### Regla de Pre-adyacencia

Solo se puede capturar una pieza enemiga si la pieza que se mueve **no era adyacente a ella antes del movimiento**. Es
decir, únicamente las piezas enemigas que son nuevas vecinas del destino — y no lo eran del origen — quedan volteadas.
Las piezas que ya eran adyacentes al origen quedan protegidas.

Esta es la regla táctica más importante del juego: hay que aproximarse desde afuera.

### Piezas Muertas y Promoción Forzada

Un Cob puede quedar **muerto** — atrapado sin posibilidad de avanzar. La patente define dos situaciones en las que esto
ocurre:

**Muerte primaria.** Un Cob que es capturado y volteado sobre uno de los dos vértices más exteriores de la base enemiga
queda muerto de inmediato. Desde esos vértices no existe ningún camino hacia adelante: la pieza no puede moverse por sus
propios medios.

**Muerte por cadena.** Un Cob también queda muerto si todos sus vértices adyacentes hacia adelante están ocupados por
Cobs muertos del mismo color. Esta condición se propaga: una pieza puede morir porque la bloquea otra que murió por la
misma razón, y así sucesivamente. La cadena siempre termina en un vértice de muerte primaria.

**Qué no produce muerte.** Una pieza no queda muerta por estar bloqueada por una pieza enemiga, por un Cob vivo del
mismo color, ni por un Rok de cualquier color — porque cualquiera de esos bloqueadores puede moverse y liberar el
camino. Solo los Cobs muertos del mismo color bloquean de forma permanente. Los Roks nunca están muertos.

**Cuándo se puede promover una pieza muerta.** La promoción de un Cob muerto a Rok no es automática. Solo ocurre cuando
el jugador no puede realizar ningún movimiento normal. En ese caso, puede promover uno de sus Cobs muertos — pero
únicamente si el Rok resultante tendría al menos un movimiento disponible. Si la promoción no resuelve la inmovilidad,
esa pieza no puede promoverse.

**Caso especial — última pieza.** Si un Cob es la única pieza del jugador que queda en el tablero, debe promoverse a Rok
de forma obligatoria, sin importar en qué vértice se encuentre y aunque el jugador todavía pueda mover otras piezas.

### Condiciones de Fin de Partida

La partida termina cuando:

- **Mit**: un jugador captura todas las piezas enemigas en un solo movimiento.
- **Stalemit**: el jugador activo no tiene movimientos normales ni promociones forzadas disponibles. Gana el oponente.
- **Triple repetición**: la misma posición de tablero aparece tres veces con el mismo jugador a mover. Pierde el jugador
  que provocó la tercera repetición.
- **Regla de 50 movimientos**: si transcurren 100 semimovimientos consecutivos sin un movimiento de Cob ni ninguna
  promoción, el jugador activo puede reclamar tablas. Un jugador con un movimiento ganador disponible no puede reclamar.
- **Tiempo agotado**: en partidas con control de tiempo, el jugador que supera su límite pierde.

---

## Modo Multijugador — Tarati Six (2–6 jugadores)

Además del juego clásico de dos jugadores, Tarati incluye un **modo multijugador** propio basado en la variante *cúbica*
de la patente de Spencer-Brown: un tablero ampliado y piezas con forma de cubo para **2 a 6 jugadores**. Es un motor de
juego aparte — no toca el Tarati de dos jugadores ya publicado.

### Tablero y piezas

- Tablero ampliado de **49 vértices** con **6 bases equidistantes** (el centro A–B–C del tablero clásico queda embebido
  intacto, más dos anillos nuevos).
- **Piezas cúbicas** de hasta 6 colores — un color por jugador (cada cara del cubo es un jugador). Cada jugador arranca
  con 4 piezas en su base.
- Con menos de 6 jugadores, las bases se ubican lo más separadas posible (2 → opuestas, 3 → alternadas, …).

### Reglas (distintas del Tarati clásico)

- Los jugadores mueven por orden de asiento; una pieza se desliza por una línea hasta un punto de parada adyacente en
  **cualquier dirección**.
- **Salida de base**: una pieza deja su base solo por sus dos aristas radiales — nunca por el anillo exterior en su
  primer movimiento.
- **Captura por conversión**: terminar adyacente a piezas de otros jugadores las voltea a tu color (cambian de dueño, no
  se eliminan). Aplica la **regla de pre-adyacencia** (hay que aproximarse desde afuera), como en el Tarati clásico.
- Un jugador que no puede mover, o al que le capturan todas las piezas, **se retira**. La partida termina cuando quedan
  **dos jugadores**: gana el de más piezas en el tablero; si empatan, es **victoria compartida**. Los estancamientos se
  resuelven por mayoría de piezas (triple repetición · límite sin capturas · temporizador por turno).
- Un único tipo de pieza (el cubo): sin promoción, sin roks, sin piezas muertas.

### Formas de jugar

- **Local** — hot-seat (2–6 humanos) o contra bots greedy, en cualquier plataforma. Deshacer/rehacer, pre-movimientos y
  rotación del tablero.
- **Mesas online** — creá o unite a una mesa pública (2–6 asientos), rellená asientos vacíos con bots y jugá cuando el
  anfitrión inicia. Modo espectador, temporizador por turno, reconexión con gracia. Casual (sin rating) en el MVP
  actual.

---

## Logros

¡Tarati tiene logros! (Android)

**Empezando**

<img src="screenshots/achievements_1.png" alt="Board" style="display: block; margin: 0 auto;">

- Bienvenido a Tarati — Termina el tutorial
- Primera captura — Voltea tu primera pieza
- Primera promoción — Sube un Cob a Rok
- Primera victoria — Gana a la IA

**El farmeo**

<img src="screenshots/achievements_2.png" alt="Board" style="display: block; margin: 0 auto;">

- 10 partidas jugadas — Lo que dice
- El volteador — 50 capturas total
- Maestro Rok — 25 promociones en todas las partidas
- Imparable — 10 victorias vs IA
- Campeón — Gana en dificultad máxima

Y hay **logros secretos** escondidos. Descúbrelos tú mismo.

---

## Capturas de Pantalla

### Android

| <img src="/screenshots/screenshot8.png" width="300"/> | <img src="/screenshots/screenshot10.png" width="300"/> | <img src="/screenshots/screenshot4.png" width="300"/> |
|-------------------------------------------------------|--------------------------------------------------------|-------------------------------------------------------|
| <img src="/screenshots/screenshot3.png" width="300"/> | <img src="/screenshots/screenshot2.png" width="300"/>  | <img src="/screenshots/screenshot9.png" width="300"/> |

### Desktop (Windows)

| <img src="/screenshots/screenshot11.png" width="500"/> | <img src="/screenshots/screenshot13.png" width="300"/> |
|--------------------------------------------------------|--------------------------------------------------------|
| <img src="/screenshots/screenshot12.png" width="500"/> | <img src="/screenshots/screenshot14.png" width="300"/> |

### Multiplayer (Android)

| <img src="/screenshots/screenshot15.png" width="300"/> | <img src="/screenshots/screenshot16.png" width="300"/> | <img src="/screenshots/screenshot17.png" width="300"/> |
|--------------------------------------------------------|--------------------------------------------------------|--------------------------------------------------------|

Interfaz intuitiva construida con Jetpack Compose / Compose Multiplatform.

---

## Descargar

### Android

[<img src="https://play.google.com/intl/es/badges/static/images/badges/es_badge_web_generic.png" alt="Disponible en Google Play" height="70">](https://play.google.com/store/apps/details?id=com.agustin.tarati)

[![Descargar APK](https://img.shields.io/badge/Descargar_APK-v1.0.0-success?style=for-the-badge&logo=android)](https://github.com/AgustinGomila/tarati-kmp/releases)

**Requisitos:**

- Android 8.0 (API 26) o superior
- 5–10 MB de espacio libre
- Pantalla táctil

### Desktop

[![Descargar Windows](https://img.shields.io/badge/Windows-v1.0.0-0078D6?style=for-the-badge&logo=windows)](https://github.com/AgustinGomila/tarati-kmp/releases)
[![Descargar macOS](https://img.shields.io/badge/macOS-v1.0.0-000000?style=for-the-badge&logo=apple)](https://github.com/AgustinGomila/tarati-kmp/releases)
[![Descargar Linux](https://img.shields.io/badge/Linux-v1.0.0-FCC624?style=for-the-badge&logo=linux&logoColor=black)](https://github.com/AgustinGomila/tarati-kmp/releases)

**Requisitos:**

- Windows 10+, macOS 11+, o Linux (Ubuntu 20.04+)
- Autocontenido — no requiere runtime de Java
- 50–100 MB de espacio libre

### Firma de código

Los instaladores de Windows están firmados digitalmente de forma gratuita por
[SignPath.io](https://signpath.io), con un certificado provisto por el programa
[SignPath Foundation](https://signpath.io/open-source) para proyectos de código abierto.

### itch.io

Todos los instaladores de escritorio y el APK de Android también están publicados en itch.io:

[![Descargar en itch.io](https://img.shields.io/badge/itch.io-Descargar-FA5C5C?style=for-the-badge&logo=itchdotio&logoColor=white)](https://6e61646965.itch.io/tarati)

---

## Características

- **Motor IA con cuatro niveles de dificultad** — Minimax con poda Alpha-Beta, profundización iterativa y tabla de
  transposición
- **Control de tiempo** — Unlimited, Sudden Death, Fischer, Bronstein y Byoyomi con formatos adaptativos
- **Pre-movimientos** — selección anticipada durante el turno de la IA
- **Paletas de colores intercambiables** — incluyendo paletas de temporada y temas especiales
- **Tipos de pieza personalizables** — selector visual con animación
- **Biblioteca de partidas** — historial completo con navegación por movimientos
- **Análisis de partidas (estilo ajedrez)** — barra de evaluación en vivo, probabilidad de victoria con desglose
  por término y gráfico post-partida con clasificación de jugadas (mejor, imprecisión, error, blunder). En todos
  los modos —vs IA, local u online— y plataformas (incluida la web), sin necesidad de cuenta
- **Tutorial interactivo** — burbujas de guía superpuestas al tablero
- **Logros** — cross-platform, sincronizados al servidor; integración Google Play Games en Android
- **Soporte bilingüe** — español e inglés con selector in-app
- **Juego online (clásico 2 jugadores)** — matchmaking, rating Glicko-2, clasificación, perfiles públicos, seguidos,
  desafíos, modo espectador, revanchas, reconexión y torneos (Round Robin, Swiss, Arena y eliminación directa)
  en [tarati.tech](https://tarati.tech)
- **Modo multijugador (Tarati Six)** — un segundo juego en un tablero ampliado de 49 vértices con piezas cúbicas para
  2–6 jugadores: local hot-seat, vs bots, o mesas públicas online con espectadores
  (ver [Modo Multijugador](#modo-multijugador--tarati-six-26-jugadores))
- **Acceso como invitado** — jugá online sin registrarte
- **Panel lateral adaptativo** — en pantallas anchas, lobby, configuración y biblioteca coexisten junto al tablero
- **Multiplataforma** — mismo código compartido entre Android, Desktop y Web

---

## Tecnologías

| Componente                | Librería / Versión                                                            |
|---------------------------|-------------------------------------------------------------------------------|
| Lenguaje                  | Kotlin 2.4.10                                                                 |
| Arquitectura              | Kotlin Multiplatform (KMP)                                                    |
| UI                        | Compose Multiplatform 1.11.1, Material Design 3                               |
| Inyección de dependencias | Koin 4.2.2                                                                    |
| Almacenamiento local      | Room 2.8.4 (Android/Desktop), DataStore 1.2.1 (Android)                       |
| Tests                     | JUnit 4.13.2, MockK 1.14.11, Coroutines Test 1.11.0                           |
| Concurrencia              | Kotlin Coroutines 1.11.0                                                      |
| Networking                | Ktor 3.5.1 (cliente + servidor, online en tarati.tech)                        |
| Cliente Redis             | Kreds 0.9.1 (Kotlin-nativo, coroutines-first)                                 |
| Base de datos (servidor)  | PostgreSQL 16, Exposed ORM 1.3.1                                              |
| Auth (servidor)           | Auth0 java-jwt 4.6.0, jBCrypt 0.4                                             |
| Serialización             | kotlinx-serialization 1.11.0                                                  |
| Tiempo                    | kotlinx-datetime 0.8.0                                                        |
| IA                        | Minimax con poda Alpha-Beta, profundización iterativa, tabla de transposición |

### Arquitectura Multiplatforma

```
Tarati/
├── shared/              # Kotlin Multiplatform (~90% código compartido)
│   ├── commonMain/     # Código común (UI, lógica, IA, ViewModels)
│   ├── androidMain/    # Android-specific (APIs gráficas)
│   ├── jvmMain/        # Desktop-specific (Skia, java.util)
│   └── wasmJsMain/     # Web (Compose WASM)
├── androidApp/         # Aplicación Android (MainActivity, Services)
├── desktopApp/         # Aplicación Desktop (Main.kt, window)
└── webApp/             # Aplicación Web (Compose for Web / Kotlin WASM)
```

### Estructura del Proyecto

```
shared/commonMain/
├── core/
│   ├── data/
│   │   ├── database/          # Base de datos Room y DAOs
│   │   └── repositories/      # Implementaciones de repositorios
│   ├── domain/
│   │   ├── ai/                # Motor, evaluador, estrategia minimax
│   │   ├── game/              # Tablero, estado de juego, lógica de movimientos (clásico 2 jugadores)
│   │   │   └── time/          # Control de tiempo (modos, estado del reloj)
│   │   ├── game6/             # Motor multijugador — Board25 (49 vértices), piezas cúbicas, reglas MP, bot greedy, notación MP
│   │   ├── repository/        # Interfaces de repositorio
│   │   └── tutorial/          # Definición de pasos del tutorial
│   └── utils/
│       ├── helpers/            # Auxiliares de fecha/hora
│       └── logging/            # Sistema de logging
├── di/
│   └── SharedModule.kt        # Koin modules compartidos
├── features/
│   ├── achievements/          # Pantalla de logros, insignias, ViewModel
│   ├── game/                  # Pantalla de juego principal y ViewModels (clásico 2 jugadores)
│   ├── game6/                 # UI del modo multijugador — renderer Board25, scaffold adaptativo, pantallas local y online, lobby de mesas
│   ├── detail/                # Pantalla de detalle de partida
│   ├── library/               # Biblioteca de partidas guardadas
│   ├── online/                # Multijugador online
│   │   ├── auth/              # Flujo de auth, JWT, gestión de sesión
│   │   ├── connection/        # ConnectionViewModel, ciclo de vida WebSocket
│   │   ├── game/              # OnlineGameViewModel, OnlineGameClient
│   │   ├── lobby/             # OnlineLobbyScreen, UI de matchmaking
│   │   ├── social/            # Clasificación, perfiles, seguidos, feed
│   │   ├── supporter/         # Pantalla Supporter (checkout Polar / Play)
│   │   ├── tournament/        # TournamentViewModel, TournamentDetailScreen
│   │   └── ui/                # OnlineGameBar, UIMessageBus
│   ├── seasonal/              # Eventos de temporada
│   ├── settings/              # Pantalla de configuración
│   └── store/                 # Vitrina de tienda (preview en vivo)
├── network/
│   ├── client/                # TaratiWebSocketClient, cliente HTTP
│   ├── models/                # DTOs compartidos con el servidor
│   └── protocol/              # ClientMessage / ServerMessage (sealed classes, incl. protocolo MP anidado)
├── services/
│   ├── achievements/          # Logros cross-platform, sincronización al servidor
│   ├── ai/                    # Servicio de IA y ViewModel
│   ├── billing/               # Interfaz de billing (expect/actual)
│   ├── clipboard/             # Exportación de partidas por portapapeles
│   ├── clock/                 # Reloj de partida y lógica de control de tiempo
│   ├── dialogs/               # Sistema de diálogos
│   ├── localization/          # Gestión de idiomas
│   ├── notifications/         # UIMessageBus (Toast + Alert)
│   ├── pwa/                   # Instalación PWA (expect/actual)
│   ├── sound/                 # Interfaz de sonido (expect/actual)
│   └── url/                   # Resolvedor de URLs (dev/prod)
└── ui/
    ├── components/
    │   ├── game/              # Tablero, highlights, animaciones, pre-movimientos
    │   ├── bottombar/         # Barra inferior de juego
    │   ├── carditem/          # Ítem de tarjeta de partida
    │   ├── editor/            # Editor de tablero
    │   ├── library/           # Renderizador estático de tablero
    │   ├── movelist/          # Lista de movimientos
    │   ├── navigation/        # Grafo de navegación
    │   ├── sidebar/           # Panel lateral
    │   ├── topbar/            # Barra superior
    │   ├── turnIndicator/     # Indicador de turno
    │   └── tutorial/          # Componentes de UI del tutorial
    ├── layout/                # Layout adaptativo (panel lateral)
    ├── splash/                # Pantalla de inicio
    └── theme/                 # Sistema de diseño, temas y paletas

androidApp/
├── features/
│   ├── online/
│   │   └── auth/              # Auth repository Android (SharedPreferences)
│   ├── seasonal/              # Eventos de temporada (Android)
│   └── settings/              # Settings Android-specific
└── services/
    ├── achievements/          # Google Play Games
    ├── billing/               # Google Play Billing
    ├── clipboard/             # ClipboardManager Android
    ├── localization/          # Proveedor de locale Android
    ├── sound/                 # MediaPlayer Android
    └── url/                   # Manejo de intents de URL

desktopApp/
├── desktop/
│   ├── data/                  # Desktop database builder
│   ├── di/                    # Desktop Koin modules
│   └── services/              # Servicios de plataforma Desktop
├── features/
│   ├── online/
│   │   └── auth/              # Auth repository Desktop (java.util.prefs)
│   └── settings/              # Desktop SettingsViewModel
├── services/
│   ├── clipboard/             # ClipboardManager Desktop
│   ├── sound/                 # Servicio de sonido Desktop
│   └── url/                   # Apertura de URLs Desktop
└── Main.kt                    # Entry point Desktop

webApp/
└── web/
    └── di/                    # Módulos Koin web, implementaciones de plataforma

server/
├── Application.kt             # Entry point, plugins Ktor, setup de módulos
├── ConnectionManager.kt       # Sesiones WebSocket, presencia (fachada)
├── ChallengeManager.kt        # Desafíos directos (extraído de ConnectionManager)
├── auth/                      # Configuración JWT, auth WebSocket
├── billing/                   # Clientes de checkout Stripe y Polar, webhooks
├── bots/                      # BotService, BotManager, BotAgent, BotPlayer
├── config/                    # ServerConfig, AuthRateLimiter
├── database/
│   ├── dao/                   # UserDao, GameDao, SessionDao, FollowDao, TournamentDao, AchievementDao, EntitlementDao
│   └── tables/                # Definiciones de tablas Exposed (PostgreSQL) — incl. mp_games (resultados multijugador)
├── entitlements/              # EntitlementService, GooglePlayValidator
├── game/                      # GameSessionManager (fachada), ClockManager, PostGameProcessor, SessionRecovery, RematchCoordinator, SessionStore, SessionBroadcaster
│   └── mp/                    # Sesiones y mesas multijugador — MpGameSessionManager, MpTableManager, MpGameRecord
├── matchmaking/               # MatchmakingEngine (cola Glicko-2)
├── metrics/                   # TaratiMetrics (Prometheus)
├── models/                    # Role, User, AuthResponse
├── rating/                    # Glicko-2 RatingCalculator, RatingService
├── redis/                     # TaratiRedisClient (Kreds)
├── routes/                    # Auth/Admin/Tournament + Profile/Social/Game/Achievement/Billing/Lobby + MP mesas/en-vivo Routes
├── services/                  # AuthService, EmailService, GuestCleanupJob
└── tournament/                # TournamentEngine (Round Robin, Swiss, Arena y Eliminación), TournamentManager (fachada), TournamentNotifier, ArenaCoordinator, EliminationCoordinator
```

Más de 600 tests (cliente · servidor) y previews de Compose — incluyendo el motor multijugador game6 (Board25, reglas,
notación, cortes/estancamiento, bot greedy) y el servidor multijugador (mesas, sesiones, persistencia).

---

## Documentación

La documentación técnica — arquitectura del sistema online, contexto de integración del servidor, la bitácora de
migración a AGP 9 y los textos de tiendas — vive en [`docs/`](docs/README.md).

---

## Créditos

- **Concepto original**: George Spencer-Brown — *Laws of Form*
- **Referencia de implementación React**: [Adam Blvck](https://github.com/adamblvck/tarati-react)
- **Efectos de sonido**: [MattRuthSound](https://freesound.org/people/MattRuthSound)

### Para Profundizar

Tarati está fundamentado en *Laws of Form* de George Spencer-Brown, que introduce un cálculo matemáticamente completo
construido íntegramente sobre el concepto de distinción.

- [Louis Kauffman — Laws of Form (video)](https://youtu.be/UqMl_Wb04nU)
- [LoF24: Divyamaan Sahoo — Cómo jugar Tarati](https://www.youtube.com/watch?v=HUCVEcbehaM)
- [Mini Curso LoF por Leon Conrad](https://www.youtube.com/playlist?list=PLoK3NtWr5NbqEOdjQrWaq1sDweF7NJ5NB)

---

<div style="text-align: center;">

_"Enseñar el orgullo en el conocimiento es poner una barrera efectiva contra cualquier avance sobre lo que ya se
conoce."_
— George Spencer-Brown

</div>

---

*El código fuente se publica bajo la [Licencia MIT](LICENSE). Tarati es una implementación educativa; el concepto
original del juego y "Laws of Form" pertenecen a George Spencer-Brown.*