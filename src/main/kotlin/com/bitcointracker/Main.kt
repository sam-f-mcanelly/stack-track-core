import com.bitcointracker.core.NormalizedTransactionAnalyzer
import com.bitcointracker.core.local.UniversalFileLoader
import com.bitcointracker.core.local.report.ReportGenerator
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
    // transactions.forEach { println(it) }
    println("\n\n\n\n")
    println("Profit statement: \n" + reportGenerator.generatePrettyProfitStatement(transactionAnalyzer.calculateUnrealizedProfit(transactions, "BTC")))
}

fun listFilesInDirectory(directoryPath: String): List<File> {
    val directory = File(directoryPath)

    // Check if the path is a directory
    if (!directory.isDirectory) {
        throw IllegalArgumentException("The provided path is not a directory.")
    }

    // Recursive function to list files
    return listFilesRecursively(directory)
}

fun listFilesRecursively(directory: File): List<File> {
    val files = mutableListOf<File>()

    // Get all files and directories in the current directory
    directory.listFiles()?.forEach { file ->
        if (file.isDirectory) {
            // Recursively list files in the subdirectory
            files.addAll(listFilesRecursively(file))
        } else {
            files.add(file)
        }
    }

    return files
}
