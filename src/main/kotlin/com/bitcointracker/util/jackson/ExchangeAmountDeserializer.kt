package com.bitcointracker.util.jackson
import com.bitcointracker.model.internal.transaction.normalized.ExchangeAmount
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode

class ExchangeAmountDeserializer : JsonDeserializer<ExchangeAmount>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ExchangeAmount {
        val node = p.codec.readTree<ObjectNode>(p)
        val amount = node.get("amount").asDouble()
        val unit = node.get("unit").asText()
        return ExchangeAmount(amount, unit)
    }
}