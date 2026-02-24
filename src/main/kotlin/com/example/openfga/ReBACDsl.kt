package com.example.openfga

import dev.openfga.sdk.api.client.model.ClientCheckRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.concurrent.atomic.AtomicInteger

// ──────────────────────────────────────────────────────────────
//  Kotlin DSL for OpenFGA Relationship-Based Access Control
// ──────────────────────────────────────────────────────────────

@DslMarker
annotation class ReBACMarker

/**
 * Scoped builder created by [rebac]. Groups multiple [permission]
 * checks that share the same object type and path-parameter source.
 *
 * ```kotlin
 * route("/documents/{documentId}") {
 *     rebac("document", "documentId") {
 *         permission("view") {
 *             get { call.respondText("content") }
 *         }
 *         permission("edit") {
 *             put { call.respondText("updated") }
 *         }
 *     }
 * }
 * ```
 */
@ReBACMarker
class ReBACScope(
    private val route: Route,
    val objectType: String,
    val idFromPath: String,
) {
    /**
     * Protects the enclosed routes with an OpenFGA permission check.
     *
     * Before the route handler executes, the plugin will:
     * 1. Read the user identity from the configured header (default `X-User`)
     * 2. Resolve the object ID from the path parameter [idFromPath]
     * 3. Call `fgaClient.check(user:<userId>, [permission], <objectType>:<objectId>)`
     * 4. Respond **403 Forbidden** if the check returns `allowed = false`
     */
    fun permission(
        permission: String,
        build: Route.() -> Unit,
    ): Route = route.requirePermission(objectType, idFromPath, permission, build)
}

// ──────────────────────────────────────────────────────────────
//  Top-level DSL entry points
// ──────────────────────────────────────────────────────────────

/**
 * Opens a **ReBAC scope** where every nested [ReBACScope.permission] block
 * shares the same [objectType] and [idFromPath] parameter.
 *
 * ```kotlin
 * route("/documents/{documentId}") {
 *     rebac("document", "documentId") {
 *         permission("view") { get { ... } }
 *         permission("edit") { put { ... } }
 *     }
 * }
 * ```
 */
fun Route.rebac(
    objectType: String,
    idFromPath: String,
    build: ReBACScope.() -> Unit,
) {
    ReBACScope(this, objectType, idFromPath).build()
}

/**
 * Protects the enclosed routes with a single OpenFGA permission check.
 *
 * This is the low-level primitive - use [rebac] if you want to group
 * multiple permission checks for the same object type.
 *
 * ```kotlin
 * route("/documents/{documentId}") {
 *     requirePermission("document", "documentId", "view") {
 *         get { call.respondText("content") }
 *     }
 * }
 * ```
 */
fun Route.requirePermission(
    objectType: String,
    idFromPath: String,
    permission: String,
    build: Route.() -> Unit,
): Route {
    val child = createChild(ReBACSelector(objectType, permission))

    val pluginId = pluginCounter.incrementAndGet()
    val rebacPlugin =
        createRouteScopedPlugin("ReBAC-$objectType-$permission-$pluginId") {
            onCall { call ->
                val fgaClient =
                    call.application.attributes.getOrNull(OpenFgaClientKey)
                        ?: error("OpenFGA plugin is not installed - call install(OpenFga) first")

                val userHeader = call.application.attributes.getOrNull(OpenFgaUserHeaderKey) ?: "X-User"
                val userId = call.request.headers[userHeader]
                if (userId == null) {
                    call.respondText(
                        """{"error":"Missing $userHeader header"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Unauthorized,
                    )
                    return@onCall
                }

                val objectId = (call as io.ktor.server.routing.RoutingPipelineCall).pathParameters[idFromPath]
                if (objectId == null) {
                    call.respondText(
                        """{"error":"Missing path parameter: $idFromPath"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                    return@onCall
                }

                val allowed =
                    try {
                        val req =
                            ClientCheckRequest()
                                .user("user:$userId")
                                .relation(permission)
                                ._object("$objectType:$objectId")
                        fgaClient.check(req).get().allowed ?: false
                    } catch (e: Exception) {
                        call.application.log.error("OpenFGA check failed", e)
                        false
                    }

                if (!allowed) {
                    call.respondText(
                        """{"error":"Forbidden - requires '$permission' on $objectType:$objectId"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Forbidden,
                    )
                    return@onCall
                }
            }
        }

    child.install(rebacPlugin)
    child.build()
    return child
}

// ──────────────────────────────────────────────────────────────
//  Internals
// ──────────────────────────────────────────────────────────────

private val pluginCounter = AtomicInteger(0)

/**
 * Transparent [RouteSelector] that annotates the route tree for
 * debugging / logging without affecting URL resolution.
 */
private class ReBACSelector(
    private val objectType: String,
    private val permission: String,
) : RouteSelector() {
    override suspend fun evaluate(
        context: RoutingResolveContext,
        segmentIndex: Int,
    ) = RouteSelectorEvaluation.Transparent

    override fun toString() = "(rebac: $objectType/$permission)"
}
