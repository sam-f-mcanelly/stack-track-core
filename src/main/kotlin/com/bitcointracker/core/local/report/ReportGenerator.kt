package com.bitcointracker.core.local.report

import com.bitcointracker.model.report.ProfitStatement
import com.bitcointracker.model.tax.TaxLotStatement
import java.text.DecimalFormat

class ReportGenerator {

    fun generatePrettyProfitStatement(profitStatement: ProfitStatement): String {
        val decimalFormat = DecimalFormat("#,###.##")
        val btcFormat =  DecimalFormat("#,###.00000000")

        // TODO update
        return """
            Asset Units: ${btcFormat.format(profitStatement.units.amount)} ${profitStatement.units.unit}
            Present Value: ${decimalFormat.format(profitStatement.currentValue.amount)} ${profitStatement.currentValue.unit}
            Realized Profit: ${decimalFormat.format(profitStatement.realizedProfit.amount)} ${profitStatement.realizedProfit.unit} - ${profitStatement.realizedProfitPercentage}
            Unrealized Profit: ${decimalFormat.format(profitStatement.unrealizedProfit.amount)} ${profitStatement.unrealizedProfit.unit} - ${profitStatement.unrealizedProfitPercentage}
        """.trimIndent()
    }
}