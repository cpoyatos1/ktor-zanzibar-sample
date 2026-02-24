package com.example.openfga

import com.fasterxml.jackson.databind.ObjectMapper
import dev.openfga.sdk.api.model.WriteAuthorizationModelRequest

private val mapper = ObjectMapper().findAndRegisterModules()

/**
 * Loads the OpenFGA authorization model from the JSON resource file.
 *
 * Edit the DSL in `resources/openfga/model.fga`, then convert to JSON:
 * ```
 * fga model transform --inputfile src/main/resources/openfga/model.fga > src/main/resources/openfga/model.json
 * ```
 */
fun loadAuthorizationModel(
    resourcePath: String = "openfga/model.json",
): WriteAuthorizationModelRequest {
    val json = Thread.currentThread().contextClassLoader
        .getResourceAsStream(resourcePath)
        ?: error("Authorization model not found at classpath:$resourcePath")
    return mapper.readValue(json, WriteAuthorizationModelRequest::class.java)
}
