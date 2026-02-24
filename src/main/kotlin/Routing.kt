package com.example

import com.example.openfga.OpenFgaClientKey
import com.example.openfga.rebac
import dev.openfga.sdk.api.client.model.ClientTupleKey
import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition
import dev.openfga.sdk.api.client.model.ClientWriteRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }

        // ── ReBAC-protected document routes ──────────────────────
        route("/documents/{documentId}") {
            rebac("document", "documentId") {
                permission("view") {
                    get {
                        val docId = call.pathParameters["documentId"]
                        call.respond(mapOf("document" to docId, "content" to "Secret document content"))
                    }
                }
                permission("edit") {
                    put {
                        val docId = call.pathParameters["documentId"]
                        call.respond(mapOf("document" to docId, "status" to "updated"))
                    }
                }
            }
        }

        // ── Admin helper routes (for testing) ────────────────────
        route("/admin") {
            /**
             * POST /admin/tuples — write a relationship tuple.
             * Body: { "user": "alice", "relation": "reader", "object_type": "document", "object_id": "1" }
             */
            post("/tuples") {
                val body = call.receive<Map<String, String>>()
                val user = body["user"] ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing user"))
                val relation = body["relation"] ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing relation"))
                val objectType =
                    body["object_type"] ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing object_type"))
                val objectId =
                    body["object_id"] ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing object_id"))

                val fga = application.attributes[OpenFgaClientKey]
                val req =
                    ClientWriteRequest().writes(
                        listOf(
                            ClientTupleKey()
                                .user("user:$user")
                                .relation(relation)
                                ._object("$objectType:$objectId"),
                        ),
                    )
                fga.write(req).get()

                call.respond(mapOf("status" to "ok", "tuple" to "user:$user#$relation@$objectType:$objectId"))
            }

            /**
             * DELETE /admin/tuples — delete a relationship tuple.
             * Body: { "user": "alice", "relation": "reader", "object_type": "document", "object_id": "1" }
             */
            delete("/tuples") {
                val body = call.receive<Map<String, String>>()
                val user = body["user"] ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing user"))
                val relation =
                    body["relation"] ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing relation"))
                val objectType =
                    body["object_type"] ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing object_type"))
                val objectId =
                    body["object_id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing object_id"))

                val fga = application.attributes[OpenFgaClientKey]
                val req =
                    ClientWriteRequest().deletes(
                        listOf(
                            ClientTupleKeyWithoutCondition()
                                .user("user:$user")
                                .relation(relation)
                                ._object("$objectType:$objectId"),
                        ),
                    )
                fga.write(req).get()

                call.respond(mapOf("status" to "ok", "deleted" to "user:$user#$relation@$objectType:$objectId"))
            }
        }
    }
}
