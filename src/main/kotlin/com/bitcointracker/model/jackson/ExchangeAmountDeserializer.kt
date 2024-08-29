package com.bitcointracker.model.jackson
import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode

class ExchangeAmountDeserializer : JsonDeserializer<ExchangeAmount>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ExchangeAmount {
        val exchangeAmountSerialized: String = p.text.trim()
        val components = exchangeAmountSerialized.split(" ")
        val amount = components[0].toDouble()
        val unit = components[1]
        return ExchangeAmount(amount, unit)
    }
}