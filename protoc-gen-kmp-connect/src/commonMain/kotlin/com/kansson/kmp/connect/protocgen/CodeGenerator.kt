package com.kansson.kmp.connect.protocgen

import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import com.google.protobuf.compiler.PluginProtos
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE_ARRAY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.U_INT
import com.squareup.kotlinpoet.U_LONG
import com.squareup.kotlinpoet.asClassName
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoOneOf

@OptIn(ExperimentalSerializationApi::class)
public object CodeGenerator {
    public fun run(request: PluginProtos.CodeGeneratorRequest): PluginProtos.CodeGeneratorResponse {
        val descriptors = mutableMapOf<String, Descriptors.FileDescriptor>()
        val files = request.protoFileList
            .map { proto ->
                descriptors.computeIfAbsent(proto.name) {
                    val dependencies = proto.dependencyList.map(descriptors::getValue)
                    Descriptors.FileDescriptor.buildFrom(proto, dependencies.toTypedArray())
                }
            }
            .map(::file)
            .map {
                val name = it.name
                    .substringAfterLast("/")
                    .removeSuffix(".proto")
                    .toPascalCase()

                PluginProtos.CodeGeneratorResponse.File
                    .newBuilder()
                    .setName("${it.packageName.replace(".", "/")}/$name.kt")
                    .setContent(it.toString())
                    .build()
            }

        return PluginProtos.CodeGeneratorResponse
            .newBuilder()
            .setSupportedFeatures(
                PluginProtos.CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL_VALUE.toLong()
                    or PluginProtos.CodeGeneratorResponse.Feature.FEATURE_SUPPORTS_EDITIONS_VALUE.toLong(),
            )
            .setMinimumEdition(DescriptorProtos.Edition.EDITION_PROTO2_VALUE)
            .setMaximumEdition(DescriptorProtos.Edition.EDITION_2023_VALUE)
            .addAllFile(files)
            .build()
    }

    private fun file(descriptor: Descriptors.FileDescriptor): FileSpec {
        val spec = FileSpec.builder(descriptor.`package`, descriptor.name)

        val types = descriptor.messageTypes.map(::message) +
            descriptor.enumTypes.map(::enum)

        spec.addTypes(types)
        return spec.build()
    }

    private fun message(descriptor: Descriptors.Descriptor): TypeSpec {
        if (descriptor.fields.isEmpty()) {
            return TypeSpec.objectBuilder(descriptor.name)
                .addAnnotation(Serializable::class)
                .build()
        }

        val constructor = FunSpec.constructorBuilder()
        val spec = TypeSpec.classBuilder(descriptor.name)
            .addModifiers(KModifier.DATA)
            .addAnnotation(Serializable::class)

        descriptor.fields
            .filter { it.realContainingOneof == null }
            .forEach {
                val name = it.name.toCamelCase()
                val typeName = it.typeName()

                val parameter = ParameterSpec.builder(name, typeName)
                    .defaultValue("%L", it.defaultCodeBlock())
                constructor.addParameter(parameter.build())

                val property = PropertySpec.builder(name, typeName)
                    .initializer(name)
                    .addAnnotation(protoNumberAnnotation(it.number))
                spec.addProperty(property.build())
            }

        descriptor.realOneofs.forEach {
            val type = oneof(it)
            spec.addType(type)

            val typeName = ClassName(it.fullName.substringBeforeLast("."), it.name.toPascalCase())

            val parameter = ParameterSpec.builder(it.name.toCamelCase(), typeName.copy(nullable = true))
                .defaultValue("%L", null)
            constructor.addParameter(parameter.build())

            val property = PropertySpec.builder(it.name.toCamelCase(), typeName.copy(nullable = true))
                .initializer(it.name.toCamelCase())
                .addAnnotation(ProtoOneOf::class)
            spec.addProperty(property.build())
        }

        val nested = descriptor.enumTypes.map(::enum) +
            descriptor.nestedTypes.map(::message)
        spec.addTypes(nested)

        return spec
            .primaryConstructor(constructor.build())
            .build()
    }

    private fun oneof(descriptor: Descriptors.OneofDescriptor): TypeSpec {
        val base = TypeSpec.interfaceBuilder(descriptor.name.toPascalCase())
            .addModifiers(KModifier.SEALED)
            .addAnnotation(Serializable::class)

        val values = descriptor.fields.map {
            val constructor = FunSpec.constructorBuilder()
            val spec = TypeSpec.classBuilder(it.name.toPascalCase())
                .addAnnotation(Serializable::class)
                .addSuperinterface(
                    ClassName(descriptor.fullName.substringBeforeLast("."), descriptor.name.toPascalCase()),
                )

            val parameter = ParameterSpec.builder(name = "value", it.typeName())
                .defaultValue("%L", it.defaultCodeBlock())
            constructor.addParameter(parameter.build())

            val property = PropertySpec.builder(name = "value", it.typeName())
                .initializer("value")
                .addAnnotation(protoNumberAnnotation(it.number))
            spec.addProperty(property.build())

            spec
                .primaryConstructor(constructor.build())
                .build()
        }

        return base
            .addTypes(values)
            .build()
    }

    private fun enum(descriptor: Descriptors.EnumDescriptor): TypeSpec {
        val spec = TypeSpec.enumBuilder(descriptor.name)
            .addAnnotation(Serializable::class)

        descriptor.values.forEach {
            val constant = TypeSpec.anonymousClassBuilder()
                .addAnnotation(protoNumberAnnotation(it.number))
                .build()
            spec.addEnumConstant(it.name, constant)
        }

        return spec.build()
    }

    private fun protoNumberAnnotation(number: Int): AnnotationSpec = AnnotationSpec.builder(ProtoNumber::class)
        .addMember("%L", number)
        .build()

    private fun Descriptors.FieldDescriptor.typeName(): TypeName = when {
        isMapField -> {
            val key = messageType.fields[0].typeName()
            val value = messageType.fields[1].typeName()
            Map::class.asClassName().parameterizedBy(key, value)
        }

        else -> {
            val typeName = when (type) {
                Descriptors.FieldDescriptor.Type.DOUBLE -> DOUBLE
                Descriptors.FieldDescriptor.Type.FLOAT -> FLOAT
                Descriptors.FieldDescriptor.Type.INT32 -> INT
                Descriptors.FieldDescriptor.Type.INT64 -> LONG
                Descriptors.FieldDescriptor.Type.UINT32 -> U_INT
                Descriptors.FieldDescriptor.Type.UINT64 -> U_LONG
                Descriptors.FieldDescriptor.Type.SINT32 -> INT
                Descriptors.FieldDescriptor.Type.SINT64 -> LONG
                Descriptors.FieldDescriptor.Type.FIXED32 -> INT
                Descriptors.FieldDescriptor.Type.FIXED64 -> LONG
                Descriptors.FieldDescriptor.Type.SFIXED32 -> INT
                Descriptors.FieldDescriptor.Type.SFIXED64 -> LONG
                Descriptors.FieldDescriptor.Type.BOOL -> BOOLEAN
                Descriptors.FieldDescriptor.Type.STRING -> STRING
                Descriptors.FieldDescriptor.Type.BYTES -> BYTE_ARRAY
                Descriptors.FieldDescriptor.Type.MESSAGE -> ClassName(
                    messageType.fullName.substringBeforeLast("."),
                    messageType.name,
                )
                Descriptors.FieldDescriptor.Type.ENUM -> ClassName(
                    enumType.fullName.substringBeforeLast("."),
                    enumType.name,
                )
                else -> Any::class.asClassName()
            }

            when {
                isRepeated -> LIST.parameterizedBy(typeName)
                hasPresence() -> typeName.copy(nullable = true)
                else -> typeName
            }
        }
    }

    private fun Descriptors.FieldDescriptor.defaultCodeBlock(): CodeBlock? = when {
        isMapField -> CodeBlock.of("emptyMap()")
        isRepeated -> CodeBlock.of("emptyList()")
        else -> when (type) {
            Descriptors.FieldDescriptor.Type.DOUBLE -> CodeBlock.of("%L", defaultValue)
            Descriptors.FieldDescriptor.Type.FLOAT -> CodeBlock.of("%Lf", defaultValue)
            Descriptors.FieldDescriptor.Type.INT32 -> CodeBlock.of("%L", defaultValue)
            Descriptors.FieldDescriptor.Type.INT64 -> CodeBlock.of("%LL", defaultValue)
            Descriptors.FieldDescriptor.Type.UINT32 -> CodeBlock.of("%Lu", defaultValue)
            Descriptors.FieldDescriptor.Type.UINT64 -> CodeBlock.of("%LuL", defaultValue)
            Descriptors.FieldDescriptor.Type.SINT32 -> CodeBlock.of("%L", defaultValue)
            Descriptors.FieldDescriptor.Type.SINT64 -> CodeBlock.of("%LL", defaultValue)
            Descriptors.FieldDescriptor.Type.FIXED32 -> CodeBlock.of("%L", defaultValue)
            Descriptors.FieldDescriptor.Type.FIXED64 -> CodeBlock.of("%LL", defaultValue)
            Descriptors.FieldDescriptor.Type.SFIXED32 -> CodeBlock.of("%L", defaultValue)
            Descriptors.FieldDescriptor.Type.SFIXED64 -> CodeBlock.of("%LL", defaultValue)
            Descriptors.FieldDescriptor.Type.BOOL -> CodeBlock.of("false")
            Descriptors.FieldDescriptor.Type.STRING -> CodeBlock.of("%S", defaultValue)
            Descriptors.FieldDescriptor.Type.BYTES -> CodeBlock.of("byteArrayOf()")
            Descriptors.FieldDescriptor.Type.ENUM -> CodeBlock.of("%L.%L", enumType.fullName, defaultValue)
            else -> null
        }
    }

    private fun String.toPascalCase(): String = split("_")
        .joinToString("") { part ->
            part.replaceFirstChar { it.uppercase() }
        }

    private fun String.toCamelCase(): String = toPascalCase()
        .replaceFirstChar { it.lowercase() }
}
