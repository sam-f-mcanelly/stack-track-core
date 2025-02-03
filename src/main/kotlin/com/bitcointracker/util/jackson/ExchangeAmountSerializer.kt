package com.bitcointracker.util.jackson

import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider

class ExchangeAmountSerializer : JsonSerializer<ExchangeAmount>() {
    override fun serialize(value: ExchangeAmount, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject() // Start JSON object
        gen.writeNumberField("amount", value.amount) // Serialize amount as a number
        gen.writeStringField("unit", value.unit) // Serialize unit as a string
        gen.writeEndObject() // End JSON object
    }
}