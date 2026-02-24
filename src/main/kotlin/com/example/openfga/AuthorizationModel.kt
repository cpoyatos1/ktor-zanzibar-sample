package com.example.openfga

import dev.openfga.sdk.api.model.*

/**
 * Builds the OpenFGA authorization model matching:
 *
 * ```
 * definition user {}
 *
 * definition document {
 *     relation admin: user
 *     relation reader: user
 *     relation writer: user
 *
 *     permission edit = writer
 *     permission view = reader + edit
 * }
 * ```
 */
fun buildDocumentAuthorizationModel(): WriteAuthorizationModelRequest {
    val userType =
        TypeDefinition()
            .type("user")

    val documentType =
        TypeDefinition()
            .type("document")
            .relations(
                mapOf(
                    // Direct relations (assignable) — _this signals "directly assignable"
                    "admin" to Userset()._this(emptyMap<String, Any>()),
                    "reader" to Userset()._this(emptyMap<String, Any>()),
                    "writer" to Userset()._this(emptyMap<String, Any>()),
                    // Computed permissions
                    "edit" to
                        Userset().computedUserset(
                            ObjectRelation().relation("writer"),
                        ),
                    "view" to
                        Userset().union(
                            Usersets().child(
                                listOf(
                                    Userset().computedUserset(ObjectRelation().relation("reader")),
                                    Userset().computedUserset(ObjectRelation().relation("edit")),
                                ),
                            ),
                        ),
                ),
            ).metadata(
                Metadata().relations(
                    mapOf(
                        "admin" to
                            RelationMetadata().directlyRelatedUserTypes(
                                listOf(RelationReference().type("user")),
                            ),
                        "reader" to
                            RelationMetadata().directlyRelatedUserTypes(
                                listOf(RelationReference().type("user")),
                            ),
                        "writer" to
                            RelationMetadata().directlyRelatedUserTypes(
                                listOf(RelationReference().type("user")),
                            ),
                    ),
                ),
            )

    return WriteAuthorizationModelRequest()
        .schemaVersion("1.1")
        .typeDefinitions(listOf(userType, documentType))
}
