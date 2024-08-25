package com.bitcointracker

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler

class FileDropHandler : RequestHandler<Map<String, String>, String> {

    override fun handleRequest(input: Map<String, String>, context: Context): String {
        val name = input["name"] ?: "world"
        return "Hello, $name!"
    }
}