import com.bitcointracker.core.local.StrikeAccountStatementFileLoader

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Please provide the file location as an argument.")
        return
    }

    val fileLocation = args[0]
    val parser = StrikeAccountStatementFileLoader()
    val transactions = parser.readStrikeAccountStatementCsv(fileLocation)

    transactions.forEach { println(it) }
}