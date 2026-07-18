package com.spendingapp.core.sync

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class WebhookEndpointTester(
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    fun testPost(url: String, apiKey: String? = null): WebhookTestResult {
        require(url.startsWith("https://") || url.startsWith("http://")) { "URL phải bắt đầu bằng http:// hoặc https://" }
        val body = """
            {
              "gateway": "MBBank",
              "transactionDate": "2026-07-16 10:12:00",
              "accountNumber": "0378304963",
              "subAccount": null,
              "code": null,
              "content": "MBCT PGPPT485EFBDC50 D2GDRRRM/202090",
              "transferType": "out",
              "description": "BankAPINotify MBCT PGPPT485EFBDC50 D2GDRRRM/202090",
              "transferAmount": 135000,
              "referenceCode": "FT26197865283722",
              "accumulated": 0,
              "id": 68447906
            }
        """.trimIndent().toRequestBody("application/json".toMediaType())
        val builder = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Accept", "application/json,text/plain,*/*")
            .addHeader("Content-Type", "application/json")
        apiKey?.trim()?.takeIf { it.isNotEmpty() }?.let { builder.addHeader("Authorization", "Apikey $it") }
        httpClient.newCall(builder.build()).execute().use { response ->
            return WebhookTestResult(
                statusCode = response.code,
                success = response.isSuccessful,
                message = response.body?.string()?.take(300).orEmpty(),
            )
        }
    }
}

data class WebhookTestResult(
    val statusCode: Int,
    val success: Boolean,
    val message: String,
)
