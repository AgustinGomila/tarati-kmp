package com.agustin.tarati.network.models


import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Resumen de un usuario para listas de seguidores / seguidos.
 * Respuesta de GET /api/users/:id/followers y GET /api/users/:id/following.
 */
@Immutable
@Serializable
data class UserSummaryDto(
    val id: String,
    val username: String,
    val displayName: String?,
    val country: String?,
    val rating: Int,
    val isBot: Boolean = false,
)

/**
 * Estado de seguimiento entre el usuario autenticado y otro usuario.
 * Respuesta de GET /api/users/:id/follow-status.
 */
@Immutable
@Serializable
data class FollowStatusDto(
    val isFollowing: Boolean,
    val followersCount: Long,
    val followingCount: Long,
)

/**
 * Entrada de la tabla de clasificación.
 * Respuesta del endpoint GET /api/leaderboard/:timeControl.
 */
@Immutable
@Serializable
data class LeaderboardEntryDto(
    val rank: Int,
    val id: String,
    val username: String,
    val displayName: String?,
    val country: String?,
    val avatarUrl: String?,
    val rating: Int,
    val games: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    /** True si el jugador es supporter — habilita badge + color de nombre (C4). */
    val isSupporter: Boolean = false,
)

/**
 * Perfil público de un usuario.
 * Respuesta del endpoint GET /api/users/:id.
 *
 * Espeja la estructura de [UserProfile] del servidor sin importar módulos server.
 */
@Immutable
@Serializable
data class PublicProfileDto(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val country: String?,
    val bio: String?,
    val ratings: ProfileRatingsDto,
    val stats: ProfileStatsDto,
    /** Rating multijugador (Tarati Six) — bucket único, sin time control. */
    val mpRating: ProfileRatingDto = ProfileRatingDto(),
    /** Estadísticas multijugador (games/wins/shared/losses). */
    val mpStats: MpStatsDto = MpStatsDto(),
    val createdAt: Instant,
    val isVerified: Boolean,
    val isBot: Boolean = false,
    /** True para cuentas temporales. La UI lo usa para forzar partidas no-puntuadas en desafíos. */
    val isGuest: Boolean = false,
    /** Si el usuario acepta recibir desafíos de partida. */
    val acceptsChallenges: Boolean = true,
    /** True si el usuario es supporter — habilita badge + color de nombre (C4). */
    val isSupporter: Boolean = false,
)

/**
 * Respuesta del endpoint GET/PUT /api/profile (perfil propio).
 * Subconjunto de [PublicProfileDto] con los campos editables + isVisible,
 * que no se expone en perfiles públicos ajenos.
 */
@Serializable
data class OwnProfileDto(
    val bio: String?,
    val isVisible: Boolean = true,
    val acceptsChallenges: Boolean = true,
)

@Immutable
@Serializable
data class ProfileRatingsDto(
    val bullet: ProfileRatingDto = ProfileRatingDto(),
    val blitz: ProfileRatingDto = ProfileRatingDto(),
    val rapid: ProfileRatingDto = ProfileRatingDto(),
    val classical: ProfileRatingDto = ProfileRatingDto(),
)

@Immutable
@Serializable
data class ProfileRatingDto(
    val rating: Int = 1200,
    val rd: Int = 350,
    val peak: Int = 1200,
)

@Immutable
@Serializable
data class ProfileStatsDto(
    val total: ProfileTimeControlStatsDto = ProfileTimeControlStatsDto(),
    val bullet: ProfileTimeControlStatsDto = ProfileTimeControlStatsDto(),
    val blitz: ProfileTimeControlStatsDto = ProfileTimeControlStatsDto(),
    val rapid: ProfileTimeControlStatsDto = ProfileTimeControlStatsDto(),
    val classical: ProfileTimeControlStatsDto = ProfileTimeControlStatsDto(),
)

@Immutable
@Serializable
data class ProfileTimeControlStatsDto(
    val games: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
) {
    fun winrate(): Double = if (games > 0) wins.toDouble() / games else 0.0
}

/**
 * Estadísticas multijugador (Tarati Six). MP no tiene empates: [shared] = victoria compartida
 * (varios ganadores por igualdad de piezas). Espeja `MpStats` del servidor.
 */
@Immutable
@Serializable
data class MpStatsDto(
    val games: Int = 0,
    val wins: Int = 0,
    val shared: Int = 0,
    val losses: Int = 0,
) {
    fun winrate(): Double = if (games > 0) (wins + shared).toDouble() / games else 0.0
}
