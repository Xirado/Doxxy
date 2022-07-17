package com.freya02.bot.docs.index

import com.freya02.bot.db.Database
import com.freya02.bot.db.Transaction
import com.freya02.bot.docs.DocEmbeds.toEmbed
import com.freya02.botcommands.api.Logging
import com.freya02.docs.ClassDocs
import com.freya02.docs.DocSourceType
import com.freya02.docs.DocsSession
import com.freya02.docs.data.BaseDoc
import com.freya02.docs.data.ClassDoc
import com.google.gson.GsonBuilder
import net.dv8tion.jda.api.entities.MessageEmbed

private val LOGGER = Logging.getLogger()

internal class DocIndexWriter(private val database_: Database, private val docsSession: DocsSession, private val sourceType: DocSourceType) {
    suspend fun doReindex() = database_.transactional {
        val updatedSource = ClassDocs.getUpdatedSource(sourceType)

        preparedStatement("delete from doc where source_id = ?") {
            executeUpdate(sourceType.id)
        }

        for ((className, classUrl) in updatedSource.getSimpleNameToUrlMap()) {
            try {
                val classDoc = docsSession.retrieveDoc(classUrl)

                if (classDoc == null) {
                    LOGGER.warn("Unable to get docs of '${className}' at '${classUrl}', javadoc version or source type may be incorrect")
                    continue
                }

                val classEmbed = toEmbed(classDoc).build()
                val classEmbedJson = GSON.toJson(classEmbed)
                val sourceLink = run {
                    when (sourceType.githubSourceURL) {
                        null -> null
                        else -> sourceType.githubSourceURL + classDoc.packageName.replace('.', '/') + "/${classDoc.className}.java"
                    }
                }

                val classDocId = insertDoc(DocType.CLASS, classDoc.className, classDoc, classEmbedJson, sourceLink)
                insertSeeAlso(classDoc, classDocId)

                insertMethodDocs(classDoc)
                insertFieldDocs(classDoc)
            } catch (e: Exception) {
                throw RuntimeException("An exception occurred while reading the docs of '$className' at '$classUrl'", e)
            }
        }
    }

    context(Transaction)
    private suspend fun insertMethodDocs(classDoc: ClassDoc) {
        for (methodDoc in classDoc.getMethodDocs().values) {
            try {
                val methodEmbed = toEmbed(classDoc, methodDoc).build()
                val methodEmbedJson = GSON.toJson(methodEmbed)

                val methodDocId = insertDoc(DocType.METHOD, classDoc.className, methodDoc, methodEmbedJson, null)
                insertSeeAlso(methodDoc, methodDocId)
            } catch (e: Exception) {
                throw RuntimeException(
                    "An exception occurred while reading the docs of " + classDoc.className + "#" + methodDoc.simpleSignature,
                    e
                )
            }
        }
    }

    context(Transaction)
    private suspend fun insertFieldDocs(classDoc: ClassDoc) {
        for (fieldDoc in classDoc.getFieldDocs().values) {
            try {
                val fieldEmbed = toEmbed(classDoc, fieldDoc).build()
                val fieldEmbedJson = GSON.toJson(fieldEmbed)

                val fieldDocId = insertDoc(DocType.FIELD, classDoc.className, fieldDoc, fieldEmbedJson, null)
                insertSeeAlso(fieldDoc, fieldDocId)
            } catch (e: Exception) {
                throw RuntimeException(
                    "An exception occurred while reading the docs of " + classDoc.className + "#" + fieldDoc.simpleSignature,
                    e
                )
            }
        }
    }

    context(Transaction)
    private suspend fun insertDoc(
        docType: DocType,
        className: String,
        baseDoc: BaseDoc,
        embedJson: String,
        sourceLink: String?
    ): Int {
        return preparedStatement("insert into doc (source_id, type, classname, identifier, embed, source_link) VALUES (?, ?, ?, ?, ?, ?) returning id") {
            executeReturningInsert(sourceType.id, docType.id, className, baseDoc.identifier, embedJson, sourceLink).readOnce()!!["id"]
        }
    }

    context(Transaction)
    private suspend fun insertSeeAlso(baseDoc: BaseDoc, docId: Int) {
        baseDoc.seeAlso?.getReferences()?.forEach { seeAlsoReference ->
            preparedStatement("insert into docseealsoreference (doc_id, text, link, target_type, full_signature) VALUES (?, ?, ?, ?, ?)") {
                executeUpdate(
                    docId,
                    seeAlsoReference.text,
                    seeAlsoReference.link,
                    seeAlsoReference.targetType.id,
                    seeAlsoReference.fullSignature
                )
            }
        }
    }

    companion object {
        internal val GSON = GsonBuilder()
            .registerTypeAdapter(MessageEmbed::class.java, MessageEmbedAdapter)
            .create()
    }
}