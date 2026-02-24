package com.example.openfga

import dev.openfga.sdk.api.client.OpenFgaClient
import dev.openfga.sdk.api.configuration.ClientConfiguration
import dev.openfga.sdk.api.model.WriteAuthorizationModelRequest
import io.ktor.server.application.*
import io.ktor.util.*

val OpenFgaClientKey = AttributeKey<OpenFgaClient>("OpenFgaClient")
val OpenFgaUserHeaderKey = AttributeKey<String>("OpenFgaUserHeader")

/**
 * Configuration for the OpenFGA Ktor plugin.
 */
class OpenFgaConfig {
    /** OpenFGA HTTP API URL (used in production; ignored when dev containers are active). */
    var apiUrl: String = "http://localhost:8080"

    /** OpenFGA store ID (auto-configured in dev mode). */
    var storeId: String = ""

    /** OpenFGA authorization model ID (auto-configured in dev mode). */
    var authorizationModelId: String = ""

    /** HTTP header used to identify the current user (e.g. "X-User"). */
    var userHeader: String = "X-User"

    /** Whether to start TestContainers automatically in development mode. */
    var useDevContainers: Boolean = true

    /** The authorization model to apply when starting dev containers. */
    var authorizationModel: WriteAuthorizationModelRequest = loadAuthorizationModel()
}

/**
 * Ktor application plugin that initialises the OpenFGA client.
 *
 * In **development mode** (with [OpenFgaConfig.useDevContainers] = true) it will
 * automatically spin up PostgreSQL + OpenFGA via TestContainers, run migrations,
 * create a store, and write the authorization model.
 */
val OpenFga =
    createApplicationPlugin("OpenFga", ::OpenFgaConfig) {
        val cfg = pluginConfig

        // Read optional overrides from application.yaml
        val envConfig = environment.config
        envConfig.propertyOrNull("openfga.apiUrl")?.getString()?.let { cfg.apiUrl = it }
        envConfig.propertyOrNull("openfga.storeId")?.getString()?.let { cfg.storeId = it }
        envConfig.propertyOrNull("openfga.authorizationModelId")?.getString()?.let { cfg.authorizationModelId = it }
        envConfig.propertyOrNull("openfga.userHeader")?.getString()?.let { cfg.userHeader = it }

        // In dev mode, start TestContainers and auto-configure
        if (application.developmentMode && cfg.useDevContainers) {
            try {
                val ctx = DevContainers.start(cfg.authorizationModel)
                cfg.apiUrl = ctx.apiUrl
                cfg.storeId = ctx.storeId
                cfg.authorizationModelId = ctx.authorizationModelId
            } catch (e: Exception) {
                application.log.error("Failed to start OpenFGA dev containers (is Docker running?)", e)
                throw e
            }
        }

        val clientConfig =
            ClientConfiguration()
                .apiUrl(cfg.apiUrl)
                .storeId(cfg.storeId)
                .authorizationModelId(cfg.authorizationModelId)

        val client = OpenFgaClient(clientConfig)

        application.attributes.put(OpenFgaClientKey, client)
        application.attributes.put(OpenFgaUserHeaderKey, cfg.userHeader)

        application.log.info(
            "OpenFGA plugin installed (url={}, store={}, model={})",
            cfg.apiUrl,
            cfg.storeId,
            cfg.authorizationModelId,
        )

        // Graceful shutdown
        application.monitor.subscribe(ApplicationStopped) {
            if (cfg.useDevContainers) {
                DevContainers.stop()
            }
        }
    }

/** Convenience accessor for the OpenFGA client from any [ApplicationCall]. */
val ApplicationCall.openFgaClient: OpenFgaClient
    get() = application.attributes[OpenFgaClientKey]
