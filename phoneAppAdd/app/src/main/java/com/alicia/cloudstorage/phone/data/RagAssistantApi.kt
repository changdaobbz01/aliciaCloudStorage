package com.alicia.cloudstorage.phone.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

internal interface RagAssistantService {
    @POST("api/assistant/plan")
    suspend fun plan(
        @Header("Authorization") authorization: String?,
        @Body payload: RagAssistantPlanRequest,
    ): Response<RagAssistantPlanResponse>
}
