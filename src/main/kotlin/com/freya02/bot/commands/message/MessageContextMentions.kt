package com.freya02.bot.commands.message

import com.freya02.bot.docs.mentions.DocMentionController
import com.freya02.botcommands.api.annotations.CommandMarker
import com.freya02.botcommands.api.commands.application.ApplicationCommand
import com.freya02.botcommands.api.commands.application.context.annotations.JDAMessageCommand
import com.freya02.botcommands.api.commands.application.context.message.GuildMessageEvent
import dev.minn.jda.ktx.messages.reply_

@CommandMarker
class MessageContextMentions(private val docMentionController: DocMentionController) : ApplicationCommand() {
    @JDAMessageCommand(name = "Find docs")
    suspend fun onMessageContextFindDocs(event: GuildMessageEvent) {
        val docMatches = docMentionController.processMentions(event.target.contentRaw)
        if (!docMatches.isSufficient()) {
            event.reply_("Could not match any docs", ephemeral = true).queue()
            return
        }

        val hook = event.hook
        docMentionController.createDocsMenuMessage(docMatches, event.user.idLong, timeoutCallback = {
            hook.deleteOriginal().queue()
        }).let { event.reply(it).queue() }
    }
}