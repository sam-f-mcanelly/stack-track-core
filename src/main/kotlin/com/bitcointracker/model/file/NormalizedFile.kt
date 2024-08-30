package com.bitcointracker.model.file

data class NormalizedFile(
    val fileType: FileType,
    val fileLines: List<String>,
)
