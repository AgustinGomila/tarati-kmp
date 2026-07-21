package com.agustin.tarati.features.online.social


import com.agustin.tarati.features.online.devServerUrl
import com.agustin.tarati.network.authDelete
import com.agustin.tarati.network.authGet
import com.agustin.tarati.network.authPost
import com.agustin.tarati.network.models.FollowStatusDto
import com.agustin.tarati.network.models.GameHistoryDto
import com.agustin.tarati.network.models.LeaderboardEntryDto
import com.agustin.tarati.network.models.PagedResponse
import com.agustin.tarati.network.models.PublicProfileDto
import com.agustin.tarati.network.models.ServerAchievementDto
import com.agustin.tarati.network.models.UserSummaryDto
import io.ktor.client.HttpClient

/**
 * Repositorio para endpoints de perfil público y clasificación.
 *
 * Consume:
 * - GET /api/users/:id             → [getUserProfile]
 * - GET /api/users/:id/games       → [getUserGames]
 * - GET /api/leaderboard/:tc       → [getLeaderboard]
 */
class SocialRepository(
    private val httpClient: HttpClient,
) {
    private val baseUrl = devServerUrl

    suspend fun getUserProfile(token: String, userId: String): Result<PublicProfileDto> =
        httpClient.authGet("$baseUrl/api/users/$userId", token)

    suspend fun getUserGames(
        token: String,
        userId: String,
        page: Int = 0,
        limit: Int = 20,
        timeControl: String? = null,
        result: String? = null,
        rated: Boolean? = null,
    ): Result<PagedResponse<GameHistoryDto>> = httpClient.authGet(
        "$baseUrl/api/users/$userId/games", token,
        "page" to page,
        "limit" to limit,
        "timeControl" to timeControl,
        "result" to result,
        "rated" to rated,
    )

    suspend fun getLeaderboard(
        token: String,
        timeControl: String,
        limit: Int = 100,
    ): Result<List<LeaderboardEntryDto>> = httpClient.authGet(
        "$baseUrl/api/leaderboard/$timeControl", token,
        "limit" to limit,
    )

    suspend fun getFollowStatus(token: String, userId: String): Result<FollowStatusDto> =
        httpClient.authGet("$baseUrl/api/users/$userId/follow-status", token)

    suspend fun followUser(token: String, userId: String): Result<Unit> =
        httpClient.authPost("$baseUrl/api/users/$userId/follow", token)

    suspend fun unfollowUser(token: String, userId: String): Result<Unit> =
        httpClient.authDelete("$baseUrl/api/users/$userId/follow", token)

    suspend fun getFollowers(
        token: String, userId: String, page: Int = 0, limit: Int = 20,
    ): Result<PagedResponse<UserSummaryDto>> = httpClient.authGet(
        "$baseUrl/api/users/$userId/followers", token,
        "page" to page,
        "limit" to limit,
    )

    suspend fun getFollowing(
        token: String, userId: String, page: Int = 0, limit: Int = 20,
    ): Result<PagedResponse<UserSummaryDto>> = httpClient.authGet(
        "$baseUrl/api/users/$userId/following", token,
        "page" to page,
        "limit" to limit,
    )

    suspend fun getUserAchievements(
        token: String, userId: String,
    ): Result<List<ServerAchievementDto>> =
        httpClient.authGet("$baseUrl/api/users/$userId/achievements", token)
}
