package com.spendingapp.core.event

enum class DomainEventType(
    val feature: String,
    val action: DomainEventAction,
    val description: String,
) {
    ACCOUNT_CREATED("account", DomainEventAction.CREATED, "Nguon tien duoc tao"),
    ACCOUNT_UPDATED("account", DomainEventAction.UPDATED, "Nguon tien duoc cap nhat"),
    ACCOUNT_BALANCE_CHANGED("account", DomainEventAction.UPDATED, "So du nguon tien thay doi"),
    ACCOUNT_ARCHIVED("account", DomainEventAction.DELETED, "Nguon tien bi an/xoa mem"),

    CATEGORY_CREATED("category", DomainEventAction.CREATED, "Hang muc duoc tao"),
    CATEGORY_UPDATED("category", DomainEventAction.UPDATED, "Hang muc duoc cap nhat"),
    CATEGORY_ARCHIVED("category", DomainEventAction.DELETED, "Hang muc bi an/xoa mem"),

    TRANSACTION_CREATED("transaction", DomainEventAction.CREATED, "Giao dich duoc tao"),
    TRANSACTION_IMPORTED("transaction", DomainEventAction.CREATED, "Giao dich duoc import"),
    TRANSACTION_UPDATED("transaction", DomainEventAction.UPDATED, "Giao dich duoc cap nhat"),
    TRANSACTION_CATEGORIZED("transaction", DomainEventAction.UPDATED, "Giao dich duoc phan loai"),
    TRANSACTION_IGNORED("transaction", DomainEventAction.DELETED, "Giao dich duoc bo qua/xoa mem"),
    TRANSACTION_DELETED("transaction", DomainEventAction.DELETED, "Giao dich bi xoa"),
    TRANSACTION_DUPLICATED("transaction", DomainEventAction.SYSTEM, "Giao dich bi trung"),
    TRANSACTION_EXPENSE_DETECTED("transaction", DomainEventAction.SYSTEM, "Phat hien giao dich chi can phan loai"),

    BUDGET_CREATED("budget", DomainEventAction.CREATED, "Han muc duoc tao"),
    BUDGET_UPDATED("budget", DomainEventAction.UPDATED, "Han muc duoc cap nhat"),
    BUDGET_DELETED("budget", DomainEventAction.DELETED, "Han muc bi xoa"),
    BUDGET_WARNING_TRIGGERED("budget", DomainEventAction.SYSTEM, "Han muc gan vuot nguong"),
    BUDGET_EXCEEDED("budget", DomainEventAction.SYSTEM, "Han muc da vuot"),

    GOAL_CREATED("goal", DomainEventAction.CREATED, "Muc tieu duoc tao"),
    GOAL_PROGRESS_UPDATED("goal", DomainEventAction.UPDATED, "Tien do muc tieu thay doi"),
    GOAL_COMPLETED("goal", DomainEventAction.SYSTEM, "Muc tieu hoan thanh"),
    GOAL_PAUSED("goal", DomainEventAction.UPDATED, "Muc tieu tam dung"),
    GOAL_RESUMED("goal", DomainEventAction.UPDATED, "Muc tieu tiep tuc"),
    GOAL_LINKED_TO_TRANSACTION("goal", DomainEventAction.UPDATED, "Muc tieu gan voi giao dich"),
    GOAL_UNLINKED_FROM_TRANSACTION("goal", DomainEventAction.UPDATED, "Muc tieu go khoi giao dich"),

    SYNC_STARTED("sync", DomainEventAction.SYSTEM, "Dong bo bat dau"),
    SYNC_COMPLETED("sync", DomainEventAction.SYSTEM, "Dong bo hoan tat"),
    SYNC_FAILED("sync", DomainEventAction.SYSTEM, "Dong bo that bai"),

    SETTINGS_UPDATED("settings", DomainEventAction.UPDATED, "Cai dat thay doi"),
    TOKEN_SAVED("security", DomainEventAction.UPDATED, "Token duoc luu"),
    TOKEN_CLEARED("security", DomainEventAction.DELETED, "Token bi xoa"),
    WEBHOOK_SETTINGS_UPDATED("webhook", DomainEventAction.UPDATED, "Cau hinh webhook thay doi"),
    BACKUP_EXPORTED("backup", DomainEventAction.SYSTEM, "Backup duoc xuat"),
    BACKUP_IMPORTED("backup", DomainEventAction.SYSTEM, "Backup duoc import"),
}

enum class DomainEventAction { CREATED, UPDATED, DELETED, SYSTEM }

