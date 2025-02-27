package com.bitcointracker.model.internal.transaction.normalized

import com.fasterxml.jackson.annotation.JsonProperty
import kotlin.math.absoluteValue

data class ExchangeAmount(
    @JsonProperty("amount")
    val amount: Double,
    @JsonProperty("unit")
    val unit: String, // TODO make type
) : Comparable<ExchangeAmount> {

    val absoluteValue: ExchangeAmount
        get() = ExchangeAmount(amount.absoluteValue, unit)

    // Overload the * operator
    operator fun times(multiplier: Double): ExchangeAmount {
        return this.copy(amount = this.amount * multiplier)
    }

    // Overload the * operator
    operator fun times(other: ExchangeAmount): ExchangeAmount {
        checkUnit(other, "Cannot multiply amounts with different currencies.")
        return this.copy(amount = this.amount * other.amount)
    }

    operator fun div(other: ExchangeAmount): ExchangeAmount {
        checkUnit(other, "Cannot divide amounts with different currencies.")
        return ExchangeAmount(this.amount / other.amount, this.unit)
    }

    operator fun minus(other: ExchangeAmount): ExchangeAmount {
        checkUnit(other, "Cannot subtract amounts with different currencies.")
        return ExchangeAmount(this.amount - other.amount, this.unit)
    }

    operator fun plus(other: ExchangeAmount): ExchangeAmount {
        checkUnit(other, "Cannot add amounts with different currencies.")
        return ExchangeAmount(this.amount + other.amount, this.unit)
    }

     override fun compareTo(other: ExchangeAmount): Int {
         checkUnit(other, "Cannot compare amounts with different currencies")
         return this.amount.compareTo(other.amount)
     }

    private fun checkUnit(
        other: ExchangeAmount,
        message: String,
        ) {
        if (this.unit.trim().uppercase() != other.unit.trim().uppercase()) {
            throw IllegalArgumentException("$message. \n" +
                    " Amount 1: $this \n" +
                    " Amount 2: $other"
            )
        }
    }
}