@file:OptIn(ExperimentalSerializationApi::class)

package raven.compose.mcp

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What a property of a tool's arguments means, for the agent: it becomes the property's
 * `description` in the tool's input schema. On a class, the description of the whole object.
 */
@SerialInfo
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
public annotation class McpDescription(val text: String)

/** JSON Schema from a serial descriptor: an `McpTool`'s input schema. */
internal object McpSchema {
    /** The schema of a tool's arguments, which must be an object. */
    fun of(descriptor: SerialDescriptor): String {
        require(descriptor.kind == StructureKind.CLASS || descriptor.kind == StructureKind.OBJECT) {
            "an McpTool's arguments are an object, not ${descriptor.serialName}"
        }
        return schemaOf(descriptor, emptyList()).toString()
    }

    private fun schemaOf(descriptor: SerialDescriptor, annotations: List<Annotation>): JsonObject = buildJsonObject {
        val description = annotations.description() ?: descriptor.annotations.description()
        when (val kind = descriptor.kind) {
            PrimitiveKind.STRING, PrimitiveKind.CHAR -> put("type", "string")
            PrimitiveKind.BOOLEAN -> put("type", "boolean")
            PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG -> put("type", "integer")
            PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> put("type", "number")
            SerialKind.ENUM -> {
                put("type", "string")
                put("enum", buildJsonArray { repeat(descriptor.elementsCount) { add(JsonPrimitive(descriptor.getElementName(it))) } })
            }
            StructureKind.LIST -> {
                put("type", "array")
                put("items", schemaOf(descriptor.getElementDescriptor(0), descriptor.getElementAnnotations(0)))
            }
            StructureKind.MAP -> {
                put("type", "object")
                put("additionalProperties", schemaOf(descriptor.getElementDescriptor(1), descriptor.getElementAnnotations(1)))
            }
            StructureKind.CLASS, StructureKind.OBJECT -> objectSchema(descriptor)
            // (what the descriptor can't tell, a polymorphic or a contextual value, takes anything)
            is PolymorphicKind, SerialKind.CONTEXTUAL -> Unit
        }
        if (description != null) put("description", description)
    }

    private fun JsonObjectBuilder.objectSchema(descriptor: SerialDescriptor) {
        put("type", "object")
        put("properties", buildJsonObject {
            for (i in 0 until descriptor.elementsCount) {
                put(descriptor.getElementName(i), schemaOf(descriptor.getElementDescriptor(i), descriptor.getElementAnnotations(i)))
            }
        })
        val required = (0 until descriptor.elementsCount).filter { !descriptor.isElementOptional(it) && !descriptor.getElementDescriptor(it).isNullable }
        if (required.isNotEmpty()) put("required", JsonArray(required.map { JsonPrimitive(descriptor.getElementName(it)) }))
    }

    private fun List<Annotation>.description(): String? = filterIsInstance<McpDescription>().firstOrNull()?.text
}
