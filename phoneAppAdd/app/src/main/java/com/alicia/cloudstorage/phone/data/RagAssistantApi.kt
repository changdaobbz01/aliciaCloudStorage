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

    @POST("api/assistant/feedback")
    suspend fun feedback(
        @Header("Authorization") authorization: String,
        @Body payload: RagCategoryFeedbackRequest,
    ): Response<RagCategoryFeedbackResponse>

    @POST("api/assistant/feedback/candidate/preview")
    suspend fun previewCandidate(
        @Header("Authorization") authorization: String,
        @Body payload: RagCandidatePreviewRequest,
    ): Response<RagCandidatePreviewResponse>

    @POST("api/assistant/feedback/candidate")
    suspend fun submitCandidate(
        @Header("Authorization") authorization: String,
        @Body payload: RagCandidateSubmission,
    ): Response<RagCandidateReceipt>
}
