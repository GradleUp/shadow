package com.github.jengelman.gradle.plugins.shadow.util

import java.nio.file.Path
import kotlin.io.path.readText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

inline fun <reified T : Any> Json.decodeFromPath(path: Path): T = decodeFromString(path.readText())

/**
 * TODO: workaround for https://github.com/Kotlin/kotlinx.serialization/issues/746
 */
private val anySerializer = object : KSerializer<Any> {
  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any")

  override fun serialize(encoder: Encoder, value: Any) {
    val jsonEncoder = encoder as JsonEncoder
    val jsonElement = serializeAny(value)
    jsonEncoder.encodeJsonElement(jsonElement)
  }

  private fun serializeAny(value: Any?): JsonElement = when (value) {
    is Map<*, *> -> {
      val mapContents = value.entries.associate { mapEntry ->
        mapEntry.key.toString() to serializeAny(mapEntry.value)
      }
      JsonObject(mapContents)
    }
    is List<*> -> {
      val arrayContents = value.map { listEntry -> serializeAny(listEntry) }
      JsonArray(arrayContents)
    }
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    else -> JsonPrimitive(value.toString())
  }

  override fun deserialize(decoder: Decoder): Any {
    val jsonDecoder = decoder as JsonDecoder
    val element = jsonDecoder.decodeJsonElement()

    return deserializeJsonElement(element)
  }

  private fun deserializeJsonElement(element: JsonElement): Any = when (element) {
    is JsonObject -> element.mapValues { deserializeJsonElement(it.value) }
    is JsonArray -> element.map { deserializeJsonElement(it) }
    is JsonPrimitive -> element.toString()
  }
}

val kotlinxJson = Json {
  ignoreUnknownKeys = true
  serializersModule = SerializersModule {
    contextual(anySerializer)
  }
}
