@file:OptIn(UnstableMetadataApi::class)

package com.github.jengelman.gradle.plugins.shadow.internal

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.lang.reflect.Type
import kotlin.metadata.ClassName
import kotlin.metadata.KmClass
import kotlin.metadata.jvm.JvmMetadataVersion
import kotlin.metadata.jvm.KmModule
import kotlin.metadata.jvm.KmPackageParts
import kotlin.metadata.jvm.KotlinModuleMetadata
import kotlin.metadata.jvm.UnstableMetadataApi

internal val moshi: Moshi = Moshi.Builder()
  .add(KotlinJsonAdapterFactory())
  .add(KotlinModuleMetadataAdapterFactory())
  .build()

private class KotlinModuleMetadataAdapterFactory : JsonAdapter.Factory {
  override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
    return when (type) {
      KotlinModuleMetadata::class.java -> {
        val kmModuleAdapter = moshi.adapter(KmModule::class.java)
        val versionAdapter = moshi.adapter(JvmMetadataVersion::class.java)
        KotlinModuleMetadataAdapter(kmModuleAdapter, versionAdapter)
      }
      JvmMetadataVersion::class.java -> JvmMetadataVersionAdapter()
      KmModule::class.java -> {
        val mapAdapter = moshi.adapter<Map<String, KmPackageParts>>(
          Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            KmPackageParts::class.java,
          ),
        )
        val listKmClassAdapter = moshi.adapter<List<KmClass>>(
          Types.newParameterizedType(List::class.java, KmClass::class.java),
        )
        KmModuleAdapter(mapAdapter, listKmClassAdapter)
      }
      KmPackageParts::class.java -> {
        val listStringAdapter = moshi.adapter<List<String>>(
          Types.newParameterizedType(List::class.java, String::class.java),
        )
        val mapStringStringAdapter = moshi.adapter<Map<String, String>>(
          Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            String::class.java,
          ),
        )
        KmPackagePartsAdapter(listStringAdapter, mapStringStringAdapter)
      }
      KmClass::class.java -> {
        // This adapter is simplified. A full adapter would be very large.
        val classNameAdapter = moshi.adapter(ClassName::class.java)
        KmClassJsonAdapter(classNameAdapter)
      }
      else -> null
    }
  }
}

private class KotlinModuleMetadataAdapter(
  private val kmModuleAdapter: JsonAdapter<KmModule>,
  private val versionAdapter: JsonAdapter<JvmMetadataVersion>,
) : JsonAdapter<KotlinModuleMetadata>() {
  override fun fromJson(reader: JsonReader): KotlinModuleMetadata {
    reader.beginObject()
    var kmModule: KmModule? = null
    var version: JvmMetadataVersion? = null
    while (reader.hasNext()) {
      when (reader.nextName()) {
        "kmModule" -> kmModule = kmModuleAdapter.fromJson(reader)
        "version" -> version = versionAdapter.fromJson(reader)
        else -> reader.skipValue()
      }
    }
    reader.endObject()
    return KotlinModuleMetadata(
      kmModule ?: throw JsonDataException("Required property 'kmModule' is missing"),
      version ?: throw JsonDataException("Required property 'version' is missing"),
    )
  }

  override fun toJson(writer: JsonWriter, value: KotlinModuleMetadata?) {
    if (value == null) {
      writer.nullValue()
      return
    }
    writer.beginObject()
    writer.name("kmModule")
    kmModuleAdapter.toJson(writer, value.kmModule)
    writer.name("version")
    versionAdapter.toJson(writer, value.version)
    writer.endObject()
  }
}

private class JvmMetadataVersionAdapter : JsonAdapter<JvmMetadataVersion>() {
  override fun fromJson(reader: JsonReader): JvmMetadataVersion {
    reader.beginObject()
    var major = -1
    var minor = -1
    var patch = -1
    while (reader.hasNext()) {
      when (reader.nextName()) {
        "major" -> major = reader.nextInt()
        "minor" -> minor = reader.nextInt()
        "patch" -> patch = reader.nextInt()
        else -> reader.skipValue()
      }
    }
    reader.endObject()
    return JvmMetadataVersion(major, minor, patch)
  }

  override fun toJson(writer: JsonWriter, value: JvmMetadataVersion?) {
    if (value == null) {
      writer.nullValue()
      return
    }
    writer.beginObject()
    writer.name("major").value(value.major)
    writer.name("minor").value(value.minor)
    writer.name("patch").value(value.patch)
    writer.endObject()
  }
}

private class KmModuleAdapter(
  private val packagePartsAdapter: JsonAdapter<Map<String, KmPackageParts>>,
  private val optionalClassesAdapter: JsonAdapter<List<KmClass>>,
) : JsonAdapter<KmModule>() {
  override fun fromJson(reader: JsonReader): KmModule {
    val kmModule = KmModule()
    reader.beginObject()
    while (reader.hasNext()) {
      when (reader.nextName()) {
        "packageParts" -> {
          packagePartsAdapter.fromJson(reader)?.let {
            kmModule.packageParts.putAll(it)
          }
        }
        "optionalAnnotationClasses" -> {
          optionalClassesAdapter.fromJson(reader)?.let {
            kmModule.optionalAnnotationClasses.addAll(it)
          }
        }
        else -> reader.skipValue()
      }
    }
    reader.endObject()
    return kmModule
  }

  override fun toJson(writer: JsonWriter, value: KmModule?) {
    if (value == null) {
      writer.nullValue()
      return
    }
    writer.beginObject()
    writer.name("packageParts")
    packagePartsAdapter.toJson(writer, value.packageParts)
    writer.name("optionalAnnotationClasses")
    optionalClassesAdapter.toJson(writer, value.optionalAnnotationClasses)
    writer.endObject()
  }
}

private class KmPackagePartsAdapter(
  private val listStringAdapter: JsonAdapter<List<String>>,
  private val mapStringStringAdapter: JsonAdapter<Map<String, String>>,
) : JsonAdapter<KmPackageParts>() {
  override fun fromJson(reader: JsonReader): KmPackageParts {
    var fileFacades: MutableList<String>? = null
    var multiFileClassParts: MutableMap<String, String>? = null
    reader.beginObject()
    while (reader.hasNext()) {
      when (reader.nextName()) {
        "fileFacades" -> fileFacades = listStringAdapter.fromJson(reader)?.toMutableList()
        "multiFileClassParts" ->
          multiFileClassParts =
            mapStringStringAdapter.fromJson(reader)?.toMutableMap()
        else -> reader.skipValue()
      }
    }
    reader.endObject()
    return KmPackageParts(
      fileFacades ?: mutableListOf(),
      multiFileClassParts ?: mutableMapOf(),
    )
  }

  override fun toJson(writer: JsonWriter, value: KmPackageParts?) {
    if (value == null) {
      writer.nullValue()
      return
    }
    writer.beginObject()
    writer.name("fileFacades")
    listStringAdapter.toJson(writer, value.fileFacades)
    writer.name("multiFileClassParts")
    mapStringStringAdapter.toJson(writer, value.multiFileClassParts)
    writer.endObject()
  }
}

// Simplified adapter for KmClass
private class KmClassJsonAdapter(
  private val classNameAdapter: JsonAdapter<ClassName>,
) : JsonAdapter<KmClass>() {
  override fun fromJson(reader: JsonReader): KmClass {
    val kmClass = KmClass()
    reader.beginObject()
    while (reader.hasNext()) {
      when (reader.nextName()) {
        "name" -> kmClass.name = classNameAdapter.fromJson(reader)
          ?: throw JsonDataException("Required property 'name' is missing for KmClass")
        // Other properties would be deserialized here
        else -> reader.skipValue()
      }
    }
    reader.endObject()
    return kmClass
  }

  override fun toJson(writer: JsonWriter, value: KmClass?) {
    if (value == null) {
      writer.nullValue()
      return
    }
    writer.beginObject()
    writer.name("name")
    classNameAdapter.toJson(writer, value.name)
    // Other properties would be serialized here.
    // For brevity, we are only serializing the name.
    writer.endObject()
  }
}
