package com.kansson.kmp.connect.protocgen

import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import com.google.protobuf.compiler.PluginProtos
import com.kansson.kmp.connect.core.ConnectTransport
import com.kansson.kmp.connect.core.ResponseMessage
import com.kansson.kmp.connect.core.Spec
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
import io.ktor.http.Headers
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoOneOf
import kotlin.time.Duration

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
            descriptor.enumTypes.map(::enum) +
            descriptor.services.map(::service)

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

    private fun service(descriptor: Descriptors.ServiceDescriptor): TypeSpec {
        val constructor = FunSpec.constructorBuilder()
        val spec = TypeSpec.classBuilder(descriptor.name)

        constructor.addParameter("transport", ConnectTransport::class)
        val property = PropertySpec.builder("transport", ConnectTransport::class)
            .addModifiers(KModifier.PRIVATE)
            .initializer("transport")

        spec.addProperty(property.build())
        spec.primaryConstructor(constructor.build())

        val functions = descriptor.methods.map(::method)
        spec.addFunctions(functions)

        return spec.build()
    }

    private fun method(descriptor: Descriptors.MethodDescriptor): FunSpec {
        val type = when {
            descriptor.isServerStreaming && descriptor.isClientStreaming -> Spec.Type.BIDIRECTIONAL
            descriptor.isServerStreaming -> Spec.Type.SERVER
            descriptor.isClientStreaming -> Spec.Type.CLIENT
            else -> Spec.Type.UNARY
        }

        val spec = FunSpec.builder(descriptor.name.toCamelCase())
        if (type != Spec.Type.UNARY) {
            return spec
                .addStatement("return throw %T()", NotImplementedError::class)
                .build()
        }

        val procedure = "${descriptor.file.`package`}.${descriptor.service.name}/${descriptor.name}"
        val input = ClassName(descriptor.inputType.file.`package`, descriptor.inputType.name)
        val output = ClassName(descriptor.inputType.file.`package`, descriptor.outputType.name)
        val idempotency = when (descriptor.options.idempotencyLevel) {
            DescriptorProtos.MethodOptions.IdempotencyLevel.NO_SIDE_EFFECTS -> Spec.Idempotency.NO_SIDE_EFFECTS
            DescriptorProtos.MethodOptions.IdempotencyLevel.IDEMPOTENT -> Spec.Idempotency.IDEMPOTENT
            else -> Spec.Idempotency.UNKNOWN
        }

        val methodSpecBlock = CodeBlock.builder()
            .addStatement("%T(", Spec::class)
            .indent()
            .addStatement("procedure = %S,", procedure)
            .addStatement("type = %T.UNARY,", Spec.Type::class)
            .addStatement("idempotency = %T.%L,", Spec.Idempotency::class, idempotency)
            .addStatement("serializer = %T.serializer(),", input)
            .addStatement("deserializer = %T.serializer(),", output)
            .unindent()
            .addStatement(")")

        val headers = ParameterSpec.builder("headers", Headers::class)
            .defaultValue("%T.Empty", Headers::class)
        val timeout = ParameterSpec.builder("timeout", Duration::class.asClassName().copy(nullable = true))
            .defaultValue("null")

        spec
            .addParameter("input", input)
            .addParameter(headers.build())
            .addParameter(timeout.build())
            .addModifiers(KModifier.SUSPEND)
            .returns(ResponseMessage::class.asClassName().parameterizedBy(output))
            .addCode("val spec = %L", methodSpecBlock.build())
            .addStatement("return transport.unary(input, spec, headers, timeout)")

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
                Descriptors.FieldDescriptor.Type.MESSAGE -> when (messageType.name) {
                    com.google.protobuf.Any.getDescriptor().name ->
                        com.kansson.kmp.connect.core.wkt.Any::class.asClassName()
                    else -> ClassName(
                        messageType.fullName.substringBeforeLast("."),
                        messageType.name,
                    )
                }
                Descriptors.FieldDescriptor.Type.ENUM,
                -> ClassName(
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
        hasPresence() -> null
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
