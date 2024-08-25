import com.bitcointracker.core.NormalizedTransactionAnalyzer
import com.bitcointracker.core.local.StrikeAccountStatementFileLoader
import com.bitcointracker.core.mapper.StrikeTransactionNormalizingMapper

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Please provide the file location as an argument.")
        return
    }

    val fileLocation = args[0]
    val parser = StrikeAccountStatementFileLoader()
    val mapper = StrikeTransactionNormalizingMapper()
    val transactionAnalyzer = NormalizedTransactionAnalyzer()
    val transactions = parser.readStrikeAccountStatementCsv(fileLocation)
    val normalizedTransactions = mapper.normalizeTransactions(transactions)


    // transactions.forEach { println(it) }
    println("\n\n\n\n")
    println("Normalized Transactions")
    println("\n\n\n\n")
    normalizedTransactions.forEach { println(it) }
    println("\n\n\n\n")
    println("Total bitcoin purchased: " + transactionAnalyzer.calculateAssetPurchased(normalizedTransactions, "BTC"))
    println("Total spent on bitcoin: " + transactionAnalyzer.calculateUSDSpentOnAsset(normalizedTransactions, "BTC"))
    println("Total USD withdrawn: " + transactionAnalyzer.calculateWithdrawals(normalizedTransactions, "USD"))
}