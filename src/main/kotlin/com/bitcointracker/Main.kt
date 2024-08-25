import com.bitcointracker.core.NormalizedTransactionAnalyzer
import com.bitcointracker.core.local.StrikeAccountStatementFileLoader
import com.bitcointracker.core.local.UniversalFileLoader
import com.bitcointracker.core.local.report.ReportGenerator
import com.bitcointracker.core.mapper.StrikeTransactionNormalizingMapper
import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Please provide the file location as an argument.")
        return
    }

    val folder = args[0]
    val fileLoader = UniversalFileLoader()
    val transactionAnalyzer = NormalizedTransactionAnalyzer()
    val reportGenerator = ReportGenerator()
    val transactions = fileLoader.loadFiles(listFilesInDirectory(folder))


    // transactions.forEach { println(it) }
    println("\n\n\n\n")
    println("Normalized Transactions")
    println("\n\n\n\n")
    transactions.forEach { println(it) }
    println("\n\n\n\n")
    println("Total bitcoin purchased: " + transactionAnalyzer.calculateAssetPurchased(transactions, "BTC"))
    println("Total spent on bitcoin: " + transactionAnalyzer.calculateUSDSpentOnAsset(transactions, "BTC"))
    println("Total USD withdrawn: " + transactionAnalyzer.calculateWithdrawals(transactions, "USD"))
    println("Profit statement: \n" + reportGenerator.generatePrettyProfitStatement(transactionAnalyzer.calculateProfitStatement(transactions)))
}

fun listFilesInDirectory(directoryPath: String): List<File> {
    val directory = File(directoryPath)

    // Check if the path is a directory
    if (!directory.isDirectory) {
        throw IllegalArgumentException("The provided path is not a directory.")
    }

    // Get all files in the directory
    return directory.listFiles()?.toList() ?: emptyList()
}