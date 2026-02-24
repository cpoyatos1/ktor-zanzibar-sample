package com.example.openfga

import dev.openfga.sdk.api.client.OpenFgaClient
import dev.openfga.sdk.api.configuration.ClientConfiguration
import dev.openfga.sdk.api.model.CreateStoreRequest
import dev.openfga.sdk.api.model.WriteAuthorizationModelRequest
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy
import org.testcontainers.containers.wait.strategy.Wait
import java.time.Duration

/**
 * Manages OpenFGA + PostgreSQL TestContainers for development mode.
 * Automatically starts containers, runs migrations, creates a store,
 * and writes the authorization model.
 */
object DevContainers {
    private val log = LoggerFactory.getLogger(DevContainers::class.java)

    data class ContainerContext(
        val apiUrl: String,
        val storeId: String,
        val authorizationModelId: String,
    )

    private var network: Network? = null
    private var postgres: PostgreSQLContainer<*>? = null
    private var openfga: GenericContainer<*>? = null

    fun start(schema: WriteAuthorizationModelRequest): ContainerContext {
        log.info("Starting OpenFGA dev containers...")

        val net = Network.newNetwork()
        network = net

        // 1. Start PostgreSQL
        val pg =
            PostgreSQLContainer("postgres:17")
                .withNetwork(net)
                .withNetworkAliases("postgres")
                .withDatabaseName("openfga")
                .withUsername("postgres")
                .withPassword("postgres")
        pg.start()
        postgres = pg
        log.info("PostgreSQL started on port {}", pg.firstMappedPort)

        // 2. Run OpenFGA database migrations (one-shot container)
        val datastoreUri = "postgres://postgres:postgres@postgres:5432/openfga?sslmode=disable"
        val migrate =
            GenericContainer("openfga/openfga:latest")
                .withNetwork(net)
                .withCommand(
                    "migrate",
                    "--datastore-engine",
                    "postgres",
                    "--datastore-uri",
                    datastoreUri,
                ).withStartupCheckStrategy(
                    OneShotStartupCheckStrategy().withTimeout(Duration.ofSeconds(60)),
                )
        migrate.start()
        log.info("OpenFGA migrations completed")

        // 3. Start OpenFGA server
        val fga =
            GenericContainer("openfga/openfga:latest")
                .withNetwork(net)
                .withExposedPorts(8080, 8081, 3000)
                .withCommand(
                    "run",
                    "--datastore-engine",
                    "postgres",
                    "--datastore-uri",
                    datastoreUri,
                ).waitingFor(Wait.forHttp("/healthz").forPort(8080))
        fga.start()
        openfga = fga

        val apiUrl = "http://${fga.host}:${fga.getMappedPort(8080)}"
        val playgroundUrl = "http://${fga.host}:${fga.getMappedPort(3000)}"
        log.info("OpenFGA server started at {}", apiUrl)
        log.info("OpenFGA playground available at {}", playgroundUrl)

        // 4. Create store and write authorization model
        val tempClient = OpenFgaClient(ClientConfiguration().apiUrl(apiUrl))

        val store = tempClient.createStore(CreateStoreRequest().name("dev-store")).get()
        tempClient.setStoreId(store.id)
        log.info("Created OpenFGA store: {} ({})", store.name, store.id)

        val modelResponse = tempClient.writeAuthorizationModel(schema).get()
        log.info("Authorization model written: {}", modelResponse.authorizationModelId)

        return ContainerContext(
            apiUrl = apiUrl,
            storeId = store.id,
            authorizationModelId = modelResponse.authorizationModelId,
        )
    }

    fun stop() {
        log.info("Stopping OpenFGA dev containers...")
        runCatching { openfga?.stop() }
        runCatching { postgres?.stop() }
        runCatching { network?.close() }
        openfga = null
        postgres = null
        network = null
    }
}
