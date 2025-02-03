package com.bitcointracker.model.internal.file

data class NormalizedFile(
    val fileType: FileType,
    val fileLines: List<String>,
)
