package com.spendingapp.core.money

import java.text.NumberFormat
import java.util.Locale

@JvmInline
value class MoneyVnd(val amount: Long) {
    init {
        require(amount >= 0L) { "Money amount cannot be negative" }
    }

    operator fun plus(other: MoneyVnd): MoneyVnd = MoneyVnd(amount + other.amount)

    operator fun minus(other: MoneyVnd): MoneyVnd {
        require(amount >= other.amount) { "Money amount cannot become negative" }
        return MoneyVnd(amount - other.amount)
    }

    fun format(): String = formatter.format(amount)

    companion object {
        private val formatter = NumberFormat.getCurrencyInstance(Locale("vi", "VN"))

        fun zero(): MoneyVnd = MoneyVnd(0L)
    }
}
