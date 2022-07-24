package com.freya02.docs.data

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.nodeTypes.NodeWithTypeArguments

data class MethodDocParameters(val asString: String) {
    val parameters: List<MethodDocParameter> = asString
        .drop(1)
        .dropLast(1)
        .split(", ")
        .map { parameter ->
            try {
                val parsedParameter = synchronized(parser) {
                    parser.parseParameter(parameter).result.get()
                }.also {
                    val type = it.type
                    if (type is NodeWithTypeArguments<*>) type.removeTypeArguments()
                }

                val annotations = parsedParameter.annotations.map { it.nameAsString }.toSet()
                val type = parsedParameter.typeAsString + if (parsedParameter.isVarArgs) "..." else ""
                val name = parsedParameter.nameAsString

                MethodDocParameter(annotations, type, name)
            } catch (e: Exception) {
                throw RuntimeException("Unable to parse parameter '$parameter'", e)
            }
        }

    companion object {
        private val parser = JavaParser()
    }
}