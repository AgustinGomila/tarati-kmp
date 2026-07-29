package com.agustin.tarati.ui.components.navigation

sealed class ScreenDestinations(
    val route: String,
) {
    object SplashScreenDest : ScreenDestinations(route = "splash_screen")

    object GameScreenDest : ScreenDestinations(route = "game_screen")

    object SettingsScreenDest : ScreenDestinations(route = "settings_screen")

    object OnlineSettingsDest : ScreenDestinations(route = "settings_online")

    object SupporterDest : ScreenDestinations(route = "supporter")

    object StoreDest : ScreenDestinations(route = "store")

    object GamesLibraryDest : ScreenDestinations(route = "games_library")

    object GameDetailsDest : ScreenDestinations(route = "game_details/{gameId}")

    object OnlineLobbyDest : ScreenDestinations(route = "online_lobby")

    object MpLobbyDest : ScreenDestinations(route = "mp_lobby")

    object MpLeaderboardDest : ScreenDestinations(route = "mp_leaderboard")

    object MpGameDetailDest : ScreenDestinations(route = "mp_game_details/{gameId}") {
        fun createRoute(gameId: String): String = "mp_game_details/$gameId"
    }

    object LeaderboardDest : ScreenDestinations(route = "leaderboard")

    object PublicProfileDest : ScreenDestinations(route = "public_profile/{userId}") {
        fun createRoute(userId: String): String = "public_profile/$userId"
    }

    object TournamentDetailDest : ScreenDestinations(route = "tournament/{tournamentId}") {
        fun createRoute(tournamentId: String): String = "tournament/$tournamentId"
    }

    object AchievementsDest : ScreenDestinations(route = "achievements")
}