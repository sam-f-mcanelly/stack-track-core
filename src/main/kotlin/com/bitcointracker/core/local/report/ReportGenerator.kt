package com.bitcointracker.core.local.report

import com.bitcointracker.model.report.ProfitStatement
import java.text.DecimalFormat

class ReportGenerator {

    fun generatePrettyProfitStatement(profitStatement: ProfitStatement): String {
        val decimalFormat = DecimalFormat("#,###.##")
        val btcFormat =  DecimalFormat("#,###.00000000")

        return """
            Asset Units: ${btcFormat.format(profitStatement.units.amount)} ${profitStatement.units.unit}
            Cost Basis: ${decimalFormat.format(profitStatement.costBasis.amount)} ${profitStatement.costBasis.unit}
            Present Value: ${decimalFormat.format(profitStatement.currentValue.amount)} ${profitStatement.costBasis.unit}
            Profit: ${decimalFormat.format(profitStatement.profit.amount)} ${profitStatement.profit.unit}
            Profit Percentage: ${decimalFormat.format(profitStatement.profitPercentage)} %
        """.trimIndent()
    }

}