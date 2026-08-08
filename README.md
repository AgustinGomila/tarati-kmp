# Tarati — A Board Game by George Spencer-Brown

<div align="center">

<img src="screenshots/logo.png" alt="Logo" style="display: block; margin: 0 auto;">

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-purple.svg)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.11.1-blue.svg)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Room](https://img.shields.io/badge/Room-2.8.4-red.svg)](https://developer.android.com/jetpack/androidx/releases/room)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://www.android.com)
[![Desktop](https://img.shields.io/badge/Desktop-Windows%20%7C%20macOS%20%7C%20Linux-orange.svg)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Web](https://img.shields.io/badge/Web-tarati.tech-blue.svg)](https://tarati.tech)
[![itch.io](https://img.shields.io/badge/itch.io-Tarati-FA5C5C.svg?logo=itchdotio&logoColor=white)](https://6e61646965.itch.io/tarati)
[![es](https://flagcdn.com/w20/es.png)](README_es.md)

**Multiplatform implementation (Android · Desktop · Web) of the strategy game Tarati**

[Play Online](https://tarati.tech) · [Google Play](#download) · [Core Rules](#core-rules) · [Multiplayer](#multiplayer-mode--tarati-six-26-players) · [Technologies](#technologies) · [Download Desktop](#desktop) · [Documentation](docs/README.md)

</div>

---

## Available Platforms

### Web

Play directly at **[tarati.tech](https://tarati.tech)** — no installation required. Online matchmaking, ranked games and
social features.

### Android

**Version 1.0.0** — Available on Google Play  
Requirements: Android 8.0+ (API 26)

### Desktop

**Version 1.0.0** — Windows · macOS · Linux  
Requirements: Windows 10+ · macOS 11+ · Linux (Ubuntu 20.04+)

### iOS

In development

---

## Game Overview

**Tarati** is a strategic board game created by George Spencer-Brown, author of *Laws of Form*, which applies his
calculus of distinctions to gameplay. The game is minimalist in structure yet deep in consequence: players move, flip
enemy pieces, and promote their own through a board of 23 vertices arranged in concentric zones.

<img src="screenshots/board.png" alt="Board" style="display: block; margin: 0 auto;">

### Origin

Designed as a practical application of Brown's distinction calculus, Tarati embodies the mathematical and philosophical
principles from *Laws of Form* — a foundational work exploring logic through the concept of distinction.

---

## Core Rules

### Objective

Capture the last enemy piece in a single move (Mit) or leave the opponent with no legal moves (Stalemit).

### Board Structure

The board has 23 vertices distributed across four zones (not fully disjoint):

- **Absolute center**: 1 vertex (A1)
- **Bridge**: 6 vertices connecting the center to the circumference
- **Circumference**: 12 vertices forming the outer ring
- **Domestic bases**: 4 vertices per player (8 total); 2 are exclusive D-ring vertices and 2 are C-ring vertices shared
  with the circumference

Each player starts with 4 Cobs placed on their domestic base.

### Pieces

**Cob** — the basic piece. Moves forward only, along edges toward the opponent's side. Cannot move backward unless
promoted.

**Rok** — an upgraded Cob. Moves freely in any direction along any edge. A Cob becomes a Rok by entering any vertex of
the opponent's domestic base. A captured Rok retains its Rok status: it changes color but remains a Rok.

### Movement

A piece moves to an adjacent free vertex. Upon arriving, it flips every eligible enemy piece directly connected to that
vertex to the moving player's color.

Exception: a Cob on its own domestic base may move in any direction, but only if that move produces at least one
capture.

### Pre-Adjacency Rule

An enemy piece can only be captured if the moving piece **was not adjacent to it before the move**. Only enemy pieces
that are new neighbors of the destination — and were not already neighbors of the origin — are flipped. Pieces that were
already adjacent to the origin are protected.

This is the most important tactical rule in the game: you must approach from outside.

### Dead Pieces and Forced Promotion

A Cob can become **dead** — trapped with no way to advance. The patent defines two situations where this happens:

**Primary death.** A Cob that is captured and flipped onto one of the two outermost vertices of the opponent's base is
immediately dead. From those vertices there is no forward path: the piece cannot move on its own.

**Chain death.** A Cob is also dead if every forward-adjacent vertex is occupied by dead Cobs of the same color. This
condition propagates: a piece can die because it is blocked by another that died for the same reason, and so on. The
chain always ends at a primary dead vertex.

**What does not cause death.** A piece does not become dead from being blocked by an enemy piece, a live friendly Cob,
or a Rok of either color — because any of those blockers can move away and free the path. Only dead Cobs of the same
color block permanently. Roks are never dead.

**When a dead piece can be promoted.** Promoting a dead Cob to a Rok is not automatic. It only happens when the player
cannot make any normal move. In that case, the player may promote one of their dead Cobs — but only if the resulting Rok
would have at least one move available. If the promotion does not resolve the immobility, that piece cannot be promoted.

**Special case — last piece.** If a Cob is the player's only remaining piece on the board, it must be promoted to a Rok
unconditionally, regardless of where it is and even if the player can still move other pieces.

### End Conditions

The game ends when:

- **Mit**: a player captures all enemy pieces in a single move.
- **Stalemit**: the active player has no normal moves and no forced promotions available. The opponent wins.
- **Triple repetition**: the same board position appears three times with the same player to move. The player who caused
  the third repetition loses.
- **50-move rule**: if 100 consecutive half-moves pass without a Cob move or any promotion, the active player may claim
  a draw. A player with a winning move available may not claim.
- **Time out**: in timed games, the player who exceeds their time limit loses.

---

## Multiplayer Mode — Tarati Six (2–6 players)

Beyond the classic two-player game, Tarati includes a distinct **multiplayer mode** based on the *cubic* variant from
Spencer-Brown's patent: an expanded board and cube-shaped pieces for **2 to 6 players**. It is a separate game engine —
it does not touch the published two-player Tarati.

### Board and pieces

- Expanded board of **49 vertices** with **6 equidistant bases** (the classic board's A–B–C centre is embedded intact,
  plus two new rings).
- **Cubic pieces** in up to 6 colors — one color per player (each cube face is a player). Each player starts with 4
  pieces on their base.
- With fewer than 6 players, bases are placed as far apart as possible (2 → opposite, 3 → alternating, …).

### Rules (distinct from classic Tarati)

- Players move in seat order; a piece slides along a line to an adjacent free stop in **any direction**.
- **Base exit**: a piece leaves its base only through its two radial edges — no outer-ring move on its first step.
- **Capture by conversion**: finishing adjacent to other players' pieces flips them to your color (they change owner,
  they are not removed). The **pre-adjacency rule** applies (you must approach from outside), as in classic Tarati.
- A player who cannot move, or whose pieces are all captured, **retires**. The game ends when **two players remain**:
  the one with the most pieces on the board wins; a tie is a **shared victory**. Stalemates resolve by piece majority
  (triple repetition · no-capture limit · per-turn timer).
- A single piece type (the cube): no promotion, no roks, no dead pieces.

### Ways to play

- **Local** — hot-seat (2–6 humans) or against greedy bots, on any platform. Undo/redo, pre-moves and board rotation.
- **Online tables** — create or join a public table (2–6 seats), fill empty seats with bots, and play once the host
  starts. Spectator mode, per-turn timer, reconnection with grace. Casual (unrated) in the current MVP.

---

## Achievements

Tarati got achievements! (Android)

**Just starting out**

<img src="screenshots/achievements_1.png" alt="Board" style="display: block; margin: 0 auto;">

- Welcome to Tarati — Finish the tutorial
- First Capture — Flip your first piece
- First Promotion — Upgrade a Cob to Rok
- First Victory — Beat the AI

**The grind**

<img src="screenshots/achievements_2.png" alt="Board" style="display: block; margin: 0 auto;">

- Play 10 Games — Exactly what it says
- The Flipper — 50 captures total
- Rok Master — 25 promotions across all games
- Unstoppable — 10 wins vs AI
- Champion — Win on max difficulty

And there are **secret ones** hiding. Figure them out yourself.

---

## Screenshots

### Android

| <img src="/screenshots/screenshot8.png" width="300"/> | <img src="/screenshots/screenshot10.png" width="300"/> | <img src="/screenshots/screenshot4.png" width="300"/> |
|-------------------------------------------------------|--------------------------------------------------------|-------------------------------------------------------|
| <img src="/screenshots/screenshot3.png" width="300"/> | <img src="/screenshots/screenshot2.png" width="300"/>  | <img src="/screenshots/screenshot9.png" width="300"/> |

### Desktop (Windows)

| <img src="/screenshots/screenshot11.png" width="450"/> | <img src="/screenshots/screenshot13.png" width="450"/> |
|--------------------------------------------------------|--------------------------------------------------------|
| <img src="/screenshots/screenshot12.png" width="450"/> | <img src="/screenshots/screenshot14.png" width="450"/> |

### Multiplayer (Android)

| <img src="/screenshots/screenshot15.png" width="300"/> | <img src="/screenshots/screenshot16.png" width="300"/> | <img src="/screenshots/screenshot17.png" width="300"/> |
|--------------------------------------------------------|--------------------------------------------------------|--------------------------------------------------------|

Intuitive interface built with Jetpack Compose / Compose Multiplatform.

---

## Download

### Android

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="70">](https://play.google.com/store/apps/details?id=com.agustin.tarati)

[![Download APK](https://img.shields.io/badge/Download_APK-v1.0.0-success?style=for-the-badge&logo=android)](https://github.com/AgustinGomila/tarati-kmp/releases)

**Requirements:**

- Android 8.0 (API 26) or higher
- 5–10 MB free space
- Touch screen

### Desktop

[![Download Windows](https://img.shields.io/badge/Windows-v1.0.0-0078D6?style=for-the-badge&logo=windows)](https://github.com/AgustinGomila/tarati-kmp/releases)
[![Download macOS](https://img.shields.io/badge/macOS-v1.0.0-000000?style=for-the-badge&logo=apple)](https://github.com/AgustinGomila/tarati-kmp/releases)
[![Download Linux](https://img.shields.io/badge/Linux-v1.0.0-FCC624?style=for-the-badge&logo=linux&logoColor=black)](https://github.com/AgustinGomila/tarati-kmp/releases)

**Requirements:**

- Windows 10+, macOS 11+, or Linux (Ubuntu 20.04+)
- Self-contained — no Java runtime required
- 50–100 MB free space

### Code signing

Windows installers are code-signed for free by [SignPath.io](https://signpath.io),
with a certificate provided by the [SignPath Foundation](https://signpath.io/open-source)
program for open-source projects.

### itch.io

All desktop installers and the Android APK are also published on itch.io:

[![Download on itch.io](https://img.shields.io/badge/itch.io-Download-FA5C5C?style=for-the-badge&logo=itchdotio&logoColor=white)](https://6e61646965.itch.io/tarati)

---

## Features

- **AI engine with four difficulty levels** — Minimax with Alpha-Beta pruning, iterative deepening and transposition
  table
- **Time control** — Unlimited, Sudden Death, Fischer, Bronstein and Byoyomi with adaptive display formats
- **Pre-moves** — anticipate your move during the AI's turn
- **Swappable color palettes** — including seasonal themes and special palettes
- **Customizable piece types** — visual selector with animation
- **Game library** — full history with move-by-move navigation
- **Game analysis (chess-style)** — live evaluation bar, win-probability breakdown by term, and a post-game graph
  with move classification (best, inaccuracy, mistake, blunder). Available in every mode — vs AI, local or online —
  on all platforms (web included), no account required
- **Interactive tutorial** — guide bubbles overlaid on the board
- **Achievements** — cross-platform, synced to server; Google Play Games integration on Android
- **Bilingual support** — Spanish and English with in-app selector
- **Online play (classic 2-player)** — matchmaking, Glicko-2 rating, leaderboard, public profiles, follows, challenges,
  spectator mode, rematches, reconnection and tournaments (Round Robin, Swiss, Arena & single-elimination)
  at [tarati.tech](https://tarati.tech)
- **Multiplayer mode (Tarati Six)** — a second game on an expanded 49-vertex board with cubic pieces for 2–6 players:
  local hot-seat, vs bots, or online public tables with spectators
  (see [Multiplayer Mode](#multiplayer-mode--tarati-six-26-players))
- **Guest access** — play online without registering
- **Adaptive sidebar** — in wide-screen layouts, lobby, settings and library coexist alongside the board
- **Multiplatform** — same shared codebase across Android, Desktop and Web

---

## Technologies

| Component            | Library / Version                                                         |
|----------------------|---------------------------------------------------------------------------|
| Language             | Kotlin 2.4.10                                                             |
| Architecture         | Kotlin Multiplatform (KMP)                                                |
| UI                   | Compose Multiplatform 1.11.1, Material Design 3                           |
| Dependency injection | Koin 4.2.2                                                                |
| Local storage        | Room 2.8.4 (Android/Desktop), DataStore 1.2.1 (Android)                   |
| Testing              | JUnit 4.13.2, MockK 1.14.11, Coroutines Test 1.11.0                       |
| Concurrency          | Kotlin Coroutines 1.11.0                                                  |
| Networking           | Ktor 3.5.1 (client + server, online play at tarati.tech)                  |
| Redis client         | Kreds 0.9.1 (Kotlin-native, coroutines-first)                             |
| Database (server)    | PostgreSQL 16, Exposed ORM 1.3.1                                          |
| Auth (server)        | Auth0 java-jwt 4.6.0, jBCrypt 0.4                                         |
| Serialization        | kotlinx-serialization 1.11.0                                              |
| Time                 | kotlinx-datetime 0.8.0                                                    |
| AI                   | Minimax with Alpha-Beta pruning, iterative deepening, transposition table |

### Multiplatform Architecture

```
Tarati/
├── shared/              # Kotlin Multiplatform (~90% shared code)
│   ├── commonMain/     # Common code (UI, logic, AI, ViewModels)
│   ├── androidMain/    # Android-specific (graphics APIs)
│   ├── jvmMain/        # Desktop-specific (Skia, java.util)
│   └── wasmJsMain/     # Web (Compose WASM)
├── androidApp/         # Android app (MainActivity, Services)
├── desktopApp/         # Desktop app (Main.kt, window)
└── webApp/             # Web app (Compose for Web / Kotlin WASM)
```

### Project Structure

```
shared/commonMain/
├── core/
│   ├── data/
│   │   ├── database/          # Room database and DAOs
│   │   └── repositories/      # Repository implementations
│   ├── domain/
│   │   ├── ai/                # Engine, evaluator, minimax strategy
│   │   ├── game/              # Board, game state, move logic (classic 2-player)
│   │   │   └── time/          # Time control (modes, clock state)
│   │   ├── game6/             # Multiplayer engine — Board25 (49 vertices), cubic pieces, MP rules, greedy bot, MP notation
│   │   ├── repository/        # Repository interfaces
│   │   └── tutorial/          # Tutorial step definitions
│   └── utils/
│       ├── helpers/            # Date/time helpers
│       └── logging/            # Logging system
├── di/
│   └── SharedModule.kt        # Shared Koin modules
├── features/
│   ├── achievements/          # Achievements screen, badges, ViewModel
│   ├── game/                  # Main game screen and ViewModels (classic 2-player)
│   ├── game6/                 # Multiplayer mode UI — Board25 renderer, adaptive scaffold, local & online screens, tables lobby
│   ├── detail/                # Game detail screen
│   ├── library/               # Saved games library
│   ├── online/                # Online multiplayer
│   │   ├── auth/              # Auth flow, JWT, session management
│   │   ├── connection/        # ConnectionViewModel, WebSocket lifecycle
│   │   ├── game/              # OnlineGameViewModel, OnlineGameClient
│   │   ├── lobby/             # OnlineLobbyScreen, matchmaking UI
│   │   ├── social/            # Leaderboard, profiles, follow, feed
│   │   ├── supporter/         # Supporter screen (Polar / Play checkout)
│   │   ├── tournament/        # TournamentViewModel, TournamentDetailScreen
│   │   └── ui/                # OnlineGameBar, UIMessageBus
│   ├── seasonal/              # Seasonal events
│   ├── settings/              # Settings screen
│   └── store/                 # Store showcase (live board preview)
├── network/
│   ├── client/                # TaratiWebSocketClient, HTTP client
│   ├── models/                # DTOs shared with server
│   └── protocol/              # ClientMessage / ServerMessage sealed classes (incl. nested MP protocol)
├── services/
│   ├── achievements/          # Cross-platform achievements, server sync
│   ├── ai/                    # AI service and ViewModel
│   ├── billing/               # Billing interface (expect/actual)
│   ├── clipboard/             # Game export via clipboard
│   ├── clock/                 # Game clock and time control logic
│   ├── dialogs/               # Dialog system
│   ├── localization/          # Language management
│   ├── notifications/         # UIMessageBus (Toast + Alert)
│   ├── pwa/                   # PWA install (expect/actual)
│   ├── sound/                 # Sound service interface (expect/actual)
│   └── url/                   # URL resolver (dev/prod)
└── ui/
    ├── components/
    │   ├── game/              # Board rendering, highlights, animations, pre-moves
    │   ├── bottombar/         # Bottom game bar
    │   ├── carditem/          # Game card item
    │   ├── editor/            # Board editor
    │   ├── library/           # Static board renderer
    │   ├── movelist/          # Move history list
    │   ├── navigation/        # Navigation graph
    │   ├── sidebar/           # Sidebar panel
    │   ├── topbar/            # Top bar
    │   ├── turnIndicator/     # Turn indicator
    │   └── tutorial/          # Tutorial UI components
    ├── layout/                # Adaptive layout (companion panel)
    ├── splash/                # Splash screen
    └── theme/                 # Design system, theming and palettes

androidApp/
├── features/
│   ├── online/
│   │   └── auth/              # Android auth repository (SharedPreferences)
│   ├── seasonal/              # Seasonal events (Android)
│   └── settings/              # Android-specific settings
└── services/
    ├── achievements/          # Google Play Games
    ├── billing/               # Google Play Billing
    ├── clipboard/             # Android ClipboardManager
    ├── localization/          # Android locale provider
    ├── sound/                 # Android MediaPlayer
    └── url/                   # Android URL intent handler

desktopApp/
├── desktop/
│   ├── data/                  # Desktop database builder
│   ├── di/                    # Desktop Koin modules
│   └── services/              # Desktop platform services
├── features/
│   ├── online/
│   │   └── auth/              # Desktop auth repository (java.util.prefs)
│   └── settings/              # Desktop SettingsViewModel
├── services/
│   ├── clipboard/             # Desktop ClipboardManager
│   ├── sound/                 # Desktop sound service
│   └── url/                   # Desktop URL opener
└── Main.kt                    # Desktop entry point

webApp/
└── web/
    └── di/                    # Web Koin modules, platform implementations

server/
├── Application.kt             # Entry point, Ktor plugins, module setup
├── ConnectionManager.kt       # WebSocket sessions, presence (facade)
├── ChallengeManager.kt        # Direct challenges (extracted from ConnectionManager)
├── auth/                      # JWT config, WebSocket auth
├── billing/                   # Stripe & Polar checkout clients, webhooks
├── bots/                      # BotService, BotManager, BotAgent, BotPlayer
├── config/                    # ServerConfig, AuthRateLimiter
├── database/
│   ├── dao/                   # UserDao, GameDao, SessionDao, FollowDao, TournamentDao, AchievementDao, EntitlementDao
│   └── tables/                # Exposed table definitions (PostgreSQL) — incl. mp_games (multiplayer results)
├── entitlements/              # EntitlementService, GooglePlayValidator
├── game/                      # GameSessionManager (facade), ClockManager, PostGameProcessor, SessionRecovery, RematchCoordinator, SessionStore, SessionBroadcaster
│   └── mp/                    # Multiplayer sessions & tables — MpGameSessionManager, MpTableManager, MpGameRecord
├── matchmaking/               # MatchmakingEngine (Glicko-2 queue)
├── metrics/                   # TaratiMetrics (Prometheus)
├── models/                    # Role, User, AuthResponse
├── rating/                    # Glicko-2 RatingCalculator, RatingService
├── redis/                     # TaratiRedisClient (Kreds)
├── routes/                    # Auth/Admin/Tournament + Profile/Social/Game/Achievement/Billing/Lobby + MP tables/live Routes
├── services/                  # AuthService, EmailService, GuestCleanupJob
└── tournament/                # TournamentEngine (Round Robin, Swiss, Arena & Elimination), TournamentManager (facade), TournamentNotifier, ArenaCoordinator, EliminationCoordinator
```

600+ tests (client · server) and Compose previews — including the game6 multiplayer engine (Board25, rules, notation,
cut/stalemate, greedy bot) and the multiplayer server (tables, sessions, persistence).

---

## Documentation

Technical documentation — online architecture, server integration context, the AGP 9 migration log and store
listing copy — lives in [`docs/`](docs/README.md).

---

## Credits

- **Original concept**: George Spencer-Brown — *Laws of Form*
- **React implementation reference**: [Adam Blvck](https://github.com/adamblvck/tarati-react)
- **Sound effects**: [MattRuthSound](https://freesound.org/people/MattRuthSound)

### Further Reading

Tarati is grounded in George Spencer-Brown's *Laws of Form*, which introduces a mathematically complete calculus built
entirely on the concept of distinction.

- [Louis Kauffman — Laws of Form (video)](https://youtu.be/UqMl_Wb04nU)
- [LoF24: Divyamaan Sahoo — How to Play Tarati](https://www.youtube.com/watch?v=HUCVEcbehaM)
- [LoF Mini Course by Leon Conrad](https://www.youtube.com/playlist?list=PLoK3NtWr5NbqEOdjQrWaq1sDweF7NJ5NB)

---

<div style="text-align: center;">

_"To teach pride in knowledge is to put up an effective barrier against any advance upon what is already known."_
— George Spencer-Brown

</div>

---

*The source code is released under the [MIT License](LICENSE). Tarati is an educational implementation; the original
game concept and "Laws of Form" belong to George Spencer-Brown.*