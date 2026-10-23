package com.maya.agent.data.backend

import com.maya.agent.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

// ---- DTOs matching the live backend (maya-backend on Render) ----

data class ChatRequest(val message: String)
data class ChatResponse(val reply: String?)

data class MemoryDto(
    val id: String,
    val kind: String,
    val content: String,
    val source: String?,
    val created_at: String?,
    val last_used_at: String?,
)
data class MemoriesResponse(val memories: List<MemoryDto>)
data class MemoryCreate(val kind: String, val content: String, val source: String = "app")
data class IdResponse(val id: String)

data class TaskDto(
    val id: String,
    val title: String,
    val status: String,
    val due: String?,
    val notes: String?,
)
data class TasksResponse(val tasks: List<TaskDto>)

data class HealthResponse(val ok: Boolean, val service: String?, val time: String?)

interface BackendApi {
    @GET("/health")
    suspend fun health(): HealthResponse

    @POST("/v1/chat")
    suspend fun chat(
        @Header("Authorization") bearer: String,
        @Body body: ChatRequest,
    ): ChatResponse

    @GET("/v1/memories")
    suspend fun memories(@Header("Authorization") bearer: String): MemoriesResponse

    @POST("/v1/memories")
    suspend fun addMemory(
        @Header("Authorization") bearer: String,
        @Body body: MemoryCreate,
    ): IdResponse

    @DELETE("/v1/memories/{id}")
    suspend fun deleteMemory(
        @Header("Authorization") bearer: String,
        @Path("id") id: String,
    )

    @GET("/v1/tasks")
    suspend fun tasks(@Header("Authorization") bearer: String): TasksResponse
}

object BackendClient {
    // Long timeout: the Render free tier can take ~50s to wake from sleep.
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                // HEADERS only for debug builds; never log bodies (user text stays private).
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS
                        else HttpLoggingInterceptor.Level.NONE
            }
        )
        .build()

    val api: BackendApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_URL.trimEnd('/') + "/")
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BackendApi::class.java)
    }
}
