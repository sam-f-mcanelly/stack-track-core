package com.bitcointracker.model.transaction.normalized

data class ExchangeAmount(
    val amount: Double,
    val unit: String, // TODO make type
) {
    // Overload the * operator
    operator fun times(multiplier: Double): ExchangeAmount {
        return this.copy(amount = this.amount * multiplier)
    }

    operator fun plus(other: ExchangeAmount): ExchangeAmount {
        if (this.unit != other.unit) {
            throw IllegalArgumentException("Cannot add amounts with different currencies")
        }
        return ExchangeAmount(this.amount + other.amount, this.unit)
    }
}