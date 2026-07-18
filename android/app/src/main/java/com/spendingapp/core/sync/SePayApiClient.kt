package com.spendingapp.core.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SePayApiClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun fetchTransactions(
        token: String,
        fromDate: LocalDate? = null,
        toDate: LocalDate? = null,
        accountNumber: String? = null,
    ): List<SePayTransactionDto> {
        val baseUrl = "https://my.sepay.vn/userapi/transactions/list".toHttpUrl()
        val urlBuilder = baseUrl.newBuilder()
        fromDate?.let { urlBuilder.addQueryParameter("from_date", it.format(DateTimeFormatter.ISO_DATE)) }
        toDate?.let { urlBuilder.addQueryParameter("to_date", it.format(DateTimeFormatter.ISO_DATE)) }
        accountNumber?.takeIf { it.isNotBlank() }?.let { urlBuilder.addQueryParameter("account_number", it) }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw SePaySyncException("SePay API lỗi HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return emptyList()
            val root = json.parseToJsonElement(body)
            val items = findTransactionArray(root) ?: return emptyList()
            return items.mapNotNull { element -> parseTransaction(element.jsonObject) }
        }
    }

    private fun findTransactionArray(root: JsonElement) = when (root) {
        is JsonObject -> {
            val direct = root["transactions"] ?: root["data"] ?: root["items"] ?: root["records"]
            when {
                direct == null -> null
                direct is JsonObject -> direct["transactions"]?.jsonArray ?: direct["data"]?.jsonArray ?: direct["items"]?.jsonArray
                else -> direct.jsonArray
            }
        }
        else -> root.jsonArray
    }

    private fun parseTransaction(item: JsonObject): SePayTransactionDto? {
        val amountIn = item.firstLong("amount_in", "amountIn", "money_in", "moneyIn", "in", "credit", "transfer_in", "transferIn") ?: 0L
        val amountOut = item.firstLong("amount_out", "amountOut", "money_out", "moneyOut", "out", "debit", "transfer_out", "transferOut") ?: 0L
        val amount = when {
            amountIn > 0 -> amountIn
            amountOut > 0 -> amountOut
            else -> item.firstLong("amount", "money", "transfer_amount", "transferAmount") ?: return null
        }
        val type = when {
            amountOut > 0 -> SePayTransactionType.OUT
            amountIn > 0 -> SePayTransactionType.IN
            item.firstString("type", "transaction_type", "transactionType", "transferType")?.contains("out", ignoreCase = true) == true -> SePayTransactionType.OUT
            item.firstString("type", "transaction_type", "transactionType", "transferType")?.contains("debit", ignoreCase = true) == true -> SePayTransactionType.OUT
            else -> SePayTransactionType.IN
        }
        return SePayTransactionDto(
            externalId = item.firstString("id", "transaction_id", "transactionId", "reference_code", "referenceCode", "reference", "code")
                ?: buildFallbackId(item, amount),
            referenceNumber = item.firstString("reference_number", "referenceNumber", "reference", "reference_code", "referenceCode", "code"),
            accountNumber = item.firstString("account_number", "accountNumber", "bank_account", "bankAccount", "account"),
            amount = amount,
            type = type,
            description = item.firstString("transaction_content", "transactionContent", "content", "description", "note"),
            transactionTimeMillis = parseTimeMillis(item.firstString("transaction_date", "transactionDate", "date", "created_at", "createdAt", "time")),
        )
    }

    private fun buildFallbackId(item: JsonObject, amount: Long): String = listOfNotNull(
        item.firstString("transaction_date", "transactionDate", "date", "created_at", "createdAt", "time"),
        item.firstString("transaction_content", "transactionContent", "content", "description", "note"),
        amount.toString(),
    ).joinToString("|")

    private fun parseTimeMillis(value: String?): Long = runCatching {
        if (value.isNullOrBlank()) return@runCatching System.currentTimeMillis()
        val normalized = value.replace(' ', 'T')
        java.time.LocalDateTime.parse(normalized.take(19)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrDefault(System.currentTimeMillis())
}

data class SePayTransactionDto(
    val externalId: String,
    val referenceNumber: String?,
    val accountNumber: String?,
    val amount: Long,
    val type: SePayTransactionType,
    val description: String?,
    val transactionTimeMillis: Long,
)

enum class SePayTransactionType { IN, OUT }

class SePaySyncException(message: String) : RuntimeException(message)

private fun JsonObject.firstString(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}

private fun JsonObject.firstLong(vararg keys: String): Long? = firstString(*keys)
    ?.filter { it.isDigit() || it == '-' }
    ?.toLongOrNull()

