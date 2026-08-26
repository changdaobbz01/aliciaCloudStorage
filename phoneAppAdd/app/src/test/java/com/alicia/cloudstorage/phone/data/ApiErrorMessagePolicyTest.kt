package com.alicia.cloudstorage.phone.data

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ApiErrorMessagePolicyTest {
    @Test
    fun `cloud json error uses backend message`() {
        val message = """{"message":"请至少选择一个项目。"}"""
            .toReadableCloudError(status = 400, fallback = "操作失败。")

        assertEquals("请至少选择一个项目。", message)
    }

    @Test
    fun `cloud plain text error is preserved`() {
        val message = "文件名已存在。"
            .toReadableCloudError(status = 400, fallback = "操作失败。")

        assertEquals("文件名已存在。", message)
    }

    @Test
    fun `cloud html error uses status message`() {
        val message = "<html><body>Bad Gateway</body></html>"
            .toReadableCloudError(status = 502, fallback = "操作失败。")

        assertEquals("服务暂时不可用，请稍后再试。", message)
    }

    @Test
    fun `rag detail field uses backend message`() {
        val message = """{"detail":"没有找到流式接口。"}"""
            .toReadableRagError(status = 404, fallback = "安安暂时连接不上，请稍后再试。")

        assertEquals("没有找到流式接口。", message)
    }

    @Test
    fun `signed download xml error uses status message`() {
        val response = Response.Builder()
            .request(
                Request.Builder()
                    .url("https://windwindwind-alicia.cn/download")
                    .build(),
            )
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .body("<?xml version=\"1.0\"?><Error><Code>NoSuchKey</Code></Error>".toResponseBody())
            .build()

        try {
            response.requireSuccessfulSignedDownload("下载文件失败。")
            fail("Expected ApiException")
        } catch (error: ApiException) {
            assertEquals(404, error.status)
            assertEquals("请求的资源不存在。", error.message)
        }
    }
}
