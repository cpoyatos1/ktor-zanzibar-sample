package com.example

import com.example.openfga.OpenFga
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain
        .main(args)
}

fun Application.module() {
    install(ContentNegotiation) { jackson() }
    install(OpenFga)
    configureRouting()
}
