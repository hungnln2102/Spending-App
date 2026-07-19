package com.spendingapp.core.event

import com.spendingapp.core.database.SpendingDatabase
import com.spendingapp.core.database.entity.DomainEventEntity

class DomainEventPublisher(
    private val database: SpendingDatabase,
) {
    suspend fun publish(
        type: DomainEventType,
        aggregateType: String,
        aggregateId: Long? = null,
        actionId: String? = null,
        payloadJson: String? = null,
    ): Long = database.domainEventDao().insert(
        DomainEventEntity(
            type = type,
            feature = type.feature,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            actionId = actionId,
            payloadJson = payloadJson,
        ),
    )
}
