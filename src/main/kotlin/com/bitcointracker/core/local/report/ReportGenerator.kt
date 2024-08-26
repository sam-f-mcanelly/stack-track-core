package com.bitcointracker.core.local.report

import com.bitcointracker.model.report.ProfitStatement
import java.text.DecimalFormat

class ReportGenerator {

    fun generatePrettyProfitStatement(profitStatement: ProfitStatement): String {
        val decimalFormat = DecimalFormat("#,###.##")
        val btcFormat =  DecimalFormat("#,###.00000000")

        // TODO update
        return """
            Currently Owned Units: ${btcFormat.format(profitStatement.remainingUnits.amount)} ${profitStatement.remainingUnits.unit}
            Sold Units: ${btcFormat.format(profitStatement.soldUnits.amount)} ${profitStatement.soldUnits.unit}
            Present Value: ${decimalFormat.format(profitStatement.currentValue.amount)} ${profitStatement.currentValue.unit}
            Realized Profit: ${decimalFormat.format(profitStatement.realizedProfit.amount)} ${profitStatement.realizedProfit.unit} - ${decimalFormat.format(profitStatement.realizedProfitPercentage)}%
            Unrealized Profit: ${decimalFormat.format(profitStatement.unrealizedProfit.amount)} ${profitStatement.unrealizedProfit.unit} - ${decimalFormat.format(profitStatement.unrealizedProfitPercentage)}%
        """.trimIndent()
    }
}