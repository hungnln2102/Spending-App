package com.spendingapp.core.sync

import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.SyncStateEntity
import com.spendingapp.core.event.DomainEventPublisher
import com.spendingapp.core.event.DomainEventType
import com.spendingapp.core.model.SyncStatus
import com.spendingapp.core.model.TransactionSource
import com.spendingapp.core.model.TransactionType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.ZoneId

interface WebhookEndpointClient {
    fun fetchTransactions(endpointUrl: String, apiKey: String?): List<SePayTransactionDto>
}

class WebhookEndpointHttpClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
) : WebhookEndpointClient {
    private val json = Json { ignoreUnknownKeys = true }

    override fun fetchTransactions(endpointUrl: String, apiKey: String?): List<SePayTransactionDto> {
        val url = runCatching { endpointUrl.toHttpUrl() }
            .getOrElse { throw SePaySyncException("Webhook endpoint URL kh?ng h?p l?") }
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
        apiKey?.takeIf { it.isNotBlank() }?.let { key ->
            requestBuilder.addHeader("Authorization", "Bearer $key")
            requestBuilder.addHeader("X-Webhook-Secret", key)
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw SePaySyncException("Webhook endpoint l?i HTTP ${response.code}")
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
        val typeText = item.firstString("type", "transaction_type", "transactionType", "transferType").orEmpty()
        val type = when {
            amountOut > 0 -> SePayTransactionType.OUT
            amountIn > 0 -> SePayTransactionType.IN
            typeText.contains("out", ignoreCase = true) -> SePayTransactionType.OUT
            typeText.contains("debit", ignoreCase = true) -> SePayTransactionType.OUT
            typeText.contains("expense", ignoreCase = true) -> SePayTransactionType.OUT
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

class WebhookEndpointSyncService(
    private val database: SpendingDatabase,
    private val endpointClient: WebhookEndpointClient,
    private val importPipeline: TransactionImportPipeline,
    private val eventPublisher: DomainEventPublisher,
) {
    suspend fun sync(endpointUrl: String, apiKey: String? = null): SePaySyncResult {
        val normalizedUrl = endpointUrl.trim()
        if (normalizedUrl.isBlank()) throw SePaySyncException("B?n c?n nh?p webhook endpoint URL")
        val bankAccount = database.accountDao().getFirstBankAccount()
            ?: throw SePaySyncException("B?n c?n t?o ngu?n ti?n Ng?n h?ng tr??c khi ??ng b? webhook endpoint")
        val source = SOURCE
        val now = System.currentTimeMillis()
        database.syncStateDao().upsert(
            SyncStateEntity(source = source, accountId = bankAccount.id, status = SyncStatus.RUNNING, updatedAt = now),
        )
        eventPublisher.publish(DomainEventType.SYNC_STARTED, "sync", bankAccount.id, actionId = source)

        return try {
            val transactions = endpointClient.fetchTransactions(normalizedUrl, apiKey)
            var imported = 0
            var duplicated = 0
            transactions.forEach { transaction ->
                val result = importPipeline.import(
                    ExternalTransactionInput(
                        accountId = bankAccount.id,
                        type = if (transaction.type == SePayTransactionType.OUT) TransactionType.EXPENSE else TransactionType.INCOME,
                        source = TransactionSource.WEBHOOK_ENDPOINT,
                        amount = transaction.amount,
                        categoryId = null,
                        description = transaction.description,
                        externalTransactionId = transaction.externalId,
                        referenceNumber = transaction.referenceNumber,
                        occurredAt = transaction.transactionTimeMillis,
                    ),
                )
                when (result) {
                    is ImportResult.Imported -> imported++
                    is ImportResult.Duplicate -> duplicated++
                }
            }
            database.syncStateDao().upsert(
                SyncStateEntity(
                    source = source,
                    accountId = bankAccount.id,
                    lastSyncedAt = System.currentTimeMillis(),
                    lastTransactionDate = transactions.maxOfOrNull { it.transactionTimeMillis },
                    status = SyncStatus.SUCCESS,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            eventPublisher.publish(
                DomainEventType.SYNC_COMPLETED,
                "sync",
                bankAccount.id,
                actionId = source,
                payloadJson = """{"imported":$imported,"duplicated":$duplicated,"totalFetched":${transactions.size}}""",
            )
            SePaySyncResult(imported = imported, duplicated = duplicated, totalFetched = transactions.size)
        } catch (error: Throwable) {
            database.syncStateDao().upsert(
                SyncStateEntity(source = source, accountId = bankAccount.id, status = SyncStatus.FAILED, lastError = error.message, updatedAt = System.currentTimeMillis()),
            )
            eventPublisher.publish(DomainEventType.SYNC_FAILED, "sync", bankAccount.id, actionId = source, payloadJson = error.message?.take(160))
            throw error
        }
    }

    companion object {
        const val SOURCE = "webhook_endpoint"
    }
}
