package com.spendingapp.core.model

enum class AccountType { BANK, CASH, WALLET, SAVING, INVESTMENT }

enum class TransactionType { INCOME, EXPENSE, TRANSFER, ADJUSTMENT }

enum class TransactionStatus { PENDING_CATEGORY, CATEGORIZED, IGNORED, DUPLICATED, ADJUSTED }

enum class TransactionSource { MANUAL, SEPAY_API, WEBHOOK_ENDPOINT, IMPORT_FILE }

enum class CategoryType { INCOME, EXPENSE, BOTH }

enum class GoalPriority { HIGH, MEDIUM, LOW }

enum class GoalStatus { ACTIVE, COMPLETED, PAUSED, CANCELLED }

enum class SyncStatus { IDLE, RUNNING, SUCCESS, FAILED }
