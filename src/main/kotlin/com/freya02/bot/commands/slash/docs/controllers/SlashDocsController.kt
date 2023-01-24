package com.freya02.bot.commands.slash.docs.controllers

import com.freya02.bot.commands.controllers.CommonDocsController
import com.freya02.bot.docs.cached.CachedDoc
import com.freya02.bot.docs.index.DocIndex
import com.freya02.bot.docs.index.DocSuggestion
import com.freya02.botcommands.api.commands.application.slash.GuildSlashEvent
import com.freya02.botcommands.api.core.annotations.BService
import dev.minn.jda.ktx.messages.reply_
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.exceptions.ErrorHandler
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import java.util.concurrent.TimeUnit

@BService
class SlashDocsController(private val commonDocsController: CommonDocsController) {
    fun sendClass(event: IReplyCallback, ephemeral: Boolean, cachedDoc: CachedDoc) {
        event.reply(commonDocsController.getDocMessageData(event.member!!, ephemeral, false, cachedDoc))
            .setEphemeral(ephemeral)
            .queue()
    }

    suspend fun handleClass(event: GuildSlashEvent, className: String, docIndex: DocIndex, block: suspend () -> List<DocSuggestion>) {
        val cachedClass = docIndex.getClassDoc(className) ?: run {
            val menu = getDocSuggestionsMenu(event, docIndex, block)

            event.reply(MessageCreateData.fromEditData(menu.get()))
                .setEphemeral(true)
                .queue()

            return
        }

        sendClass(event, false, cachedClass)
    }

    suspend fun handleMethodDocs(event: GuildSlashEvent, className: String, identifier: String, docIndex: DocIndex, block: suspend () -> List<DocSuggestion>) {
        val cachedMethod = docIndex.getMethodDoc(className, identifier) ?: run {
            val menu = getDocSuggestionsMenu(event, docIndex, block)

            event.reply(MessageCreateData.fromEditData(menu.get()))
                .setEphemeral(true)
                .queue()

            return
        }

        sendClass(event, false, cachedMethod)
    }

    suspend fun handleFieldDocs(event: GuildSlashEvent, className: String, identifier: String, docIndex: DocIndex, block: suspend () -> List<DocSuggestion>) {
        val cachedField = docIndex.getFieldDoc(className, identifier) ?: run {
            val menu = getDocSuggestionsMenu(event, docIndex, block)

            event.reply(MessageCreateData.fromEditData(menu.get()))
                .setEphemeral(true)
                .queue()

            return
        }

        sendClass(event, false, cachedField)
    }

    private suspend fun getDocSuggestionsMenu(
        event: GuildSlashEvent,
        docIndex: DocIndex,
        block: suspend () -> List<DocSuggestion>
    ) = commonDocsController.buildDocSuggestionsMenu(docIndex, block(), event.user) {
        setTimeout(2, TimeUnit.MINUTES) { menu, _ ->
            menu.cleanup()
            event.hook
                .deleteOriginal()
                .queue(null, ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE, ErrorResponse.UNKNOWN_WEBHOOK))
        }

        setCallback { buttonEvent, entry ->
            event.hook.deleteOriginal().queue()

            val identifier = entry.identifier
            val doc = runBlocking {
                when {
                    '(' in identifier -> docIndex.getMethodDoc(identifier)
                    '#' in identifier -> docIndex.getFieldDoc(identifier)
                    else -> docIndex.getClassDoc(identifier)
                }
            }

            when (doc) {
                null -> buttonEvent.reply_("This item is now invalid, try again", ephemeral = true).queue()
                else -> buttonEvent.deferEdit().flatMap {
                    event.channel.sendMessage(
                        commonDocsController.getDocMessageData(
                            buttonEvent.member!!,
                            ephemeral = false,
                            showCaller = false,
                            cachedDoc = doc
                        )
                    )
                }.queue()
            }
        }
    }
}