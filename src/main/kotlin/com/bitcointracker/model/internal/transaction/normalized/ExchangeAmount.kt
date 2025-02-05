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
    operator fun times(multiplier: ExchangeAmount): ExchangeAmount {
        return this.copy(amount = this.amount * multiplier.amount)
    }

    operator fun div(other: ExchangeAmount): ExchangeAmount {
        if (this.unit != other.unit) {
            throw IllegalArgumentException("Cannot divide amounts with different currencies.\n" +
                " Amount 1: $this \n" +
                " Amount 2: $other"
            )
        }
        return ExchangeAmount(this.amount / other.amount, this.unit)
    }

    operator fun minus(other: ExchangeAmount): ExchangeAmount {
        if (this.unit != other.unit) {
            throw IllegalArgumentException("Cannot subtract amounts with different currencies. \n" +
                " Amount 1: $this \n" +
                " Amount 2: $other"
            )
        }
        return ExchangeAmount(this.amount - other.amount, this.unit)
    }

    operator fun plus(other: ExchangeAmount): ExchangeAmount {
        if (this.unit != other.unit) {
            throw IllegalArgumentException("Cannot add amounts with different currencies. \n" +
                " Amount 1: $this \n" +
                " Amount 2: $other"
            )
        }
        return ExchangeAmount(this.amount + other.amount, this.unit)
    }

     override fun compareTo(other: ExchangeAmount): Int {
         if (this.unit != other.unit) {
             throw IllegalArgumentException("Cannot compare amounts with different currencies. \n" +
                 " Amount 1: $this \n" +
                 " Amount 2: $other"
             )
         }
         return this.amount.compareTo(other.amount)
     }
}