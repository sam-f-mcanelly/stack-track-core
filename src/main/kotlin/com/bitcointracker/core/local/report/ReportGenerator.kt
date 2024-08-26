package com.bitcointracker.core.local.report

import com.bitcointracker.model.report.ProfitStatement
import java.text.DecimalFormat

class ReportGenerator {

    fun generatePrettyProfitStatement(profitStatement: ProfitStatement): String {
        val decimalFormat = DecimalFormat("#,###.##")
        val btcFormat =  DecimalFormat("#,###.00000000")

        // TODO update
        return """
            Asset Units: ${btcFormat.format(profitStatement.units.amount)} ${profitStatement.units.unit}
            Cost Basis: ${decimalFormat.format(profitStatement.costBasis.amount)} ${profitStatement.costBasis.unit}
            Present Value: ${decimalFormat.format(profitStatement.currentValue.amount)} ${profitStatement.costBasis.unit}
            Realized Profit: ${decimalFormat.format(profitStatement.realizedProfit.amount)} ${profitStatement.realizedProfit.unit}
            Unrealized Profit: ${decimalFormat.format(profitStatement.unrealizedProfit.amount)} ${profitStatement.unrealizedProfit.unit}
        """.trimIndent()
    }

}