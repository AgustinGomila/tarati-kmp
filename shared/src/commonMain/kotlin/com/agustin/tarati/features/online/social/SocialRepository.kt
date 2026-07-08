package com.agustin.tarati.features.online.social


import com.agustin.tarati.features.online.devServerUrl
import com.agustin.tarati.network.models.FollowStatusDto
import com.agustin.tarati.network.models.GameHistoryDto
import com.agustin.tarati.network.models.LeaderboardEntryDto
import com.agustin.tarati.network.models.PagedResponse
import com.agustin.tarati.network.models.PublicProfileDto
import com.agustin.tarati.network.models.ServerAchievementDto
import com.agustin.tarati.network.models.UserSummaryDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post

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

    suspend fun getUserProfile(token: String, userId: String): Result<PublicProfileDto> = runCatching {
        httpClient.get("$baseUrl/api/users/$userId") {
            bearerAuth(token)
        }.body()
    }

    suspend fun getUserGames(
        token: String,
        userId: String,
        page: Int = 0,
        limit: Int = 20,
        timeControl: String? = null,
        result: String? = null,
        rated: Boolean? = null,
    ): Result<PagedResponse<GameHistoryDto>> = runCatching {
        httpClient.get("$baseUrl/api/users/$userId/games") {
            bearerAuth(token)
            parameter("page", page)
            parameter("limit", limit)
            if (timeControl != null) parameter("timeControl", timeControl)
            if (result != null) parameter("result", result)
            if (rated != null) parameter("rated", rated)
        }.body()
    }

    suspend fun getLeaderboard(
        token: String,
        timeControl: String,
        limit: Int = 100,
    ): Result<List<LeaderboardEntryDto>> = runCatching {
        httpClient.get("$baseUrl/api/leaderboard/$timeControl") {
            bearerAuth(token)
            parameter("limit", limit)
        }.body()
    }

    suspend fun getFollowStatus(token: String, userId: String): Result<FollowStatusDto> = runCatching {
        httpClient.get("$baseUrl/api/users/$userId/follow-status") {
            bearerAuth(token)
        }.body()
    }

    suspend fun followUser(token: String, userId: String): Result<Unit> = runCatching {
        httpClient.post("$baseUrl/api/users/$userId/follow") {
            bearerAuth(token)
        }
    }

    suspend fun unfollowUser(token: String, userId: String): Result<Unit> = runCatching {
        httpClient.delete("$baseUrl/api/users/$userId/follow") {
            bearerAuth(token)
        }
    }

    suspend fun getFollowers(
        token: String, userId: String, page: Int = 0, limit: Int = 20,
    ): Result<PagedResponse<UserSummaryDto>> = runCatching {
        httpClient.get("$baseUrl/api/users/$userId/followers") {
            bearerAuth(token)
            parameter("page", page)
            parameter("limit", limit)
        }.body()
    }

    suspend fun getFollowing(
        token: String, userId: String, page: Int = 0, limit: Int = 20,
    ): Result<PagedResponse<UserSummaryDto>> = runCatching {
        httpClient.get("$baseUrl/api/users/$userId/following") {
            bearerAuth(token)
            parameter("page", page)
            parameter("limit", limit)
        }.body()
    }

    suspend fun getUserAchievements(
        token: String, userId: String,
    ): Result<List<ServerAchievementDto>> = runCatching {
        httpClient.get("$baseUrl/api/users/$userId/achievements") {
            bearerAuth(token)
        }.body()
    }
}
