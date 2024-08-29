package com.bitcointracker.model.jackson
import com.bitcointracker.model.transaction.normalized.ExchangeAmount
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode

class ExchangeAmountDeserializer : JsonDeserializer<ExchangeAmount>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ExchangeAmount {
        val node: JsonNode = p.codec.readTree(p)
        val amount = node.get("amount").asDouble()
        val unit = node.get("unit").asText()
        return ExchangeAmount(amount, unit)
    }
}