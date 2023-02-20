package com.freya02.bot.commands.slash.docs

import com.freya02.bot.commands.slash.docs.CommonDocsHandlers.Companion.SEARCH_AUTOCOMPLETE_NAME
import com.freya02.bot.commands.slash.docs.controllers.SlashDocsController
import com.freya02.botcommands.api.annotations.CommandMarker
import com.freya02.botcommands.api.commands.application.CommandScope
import com.freya02.botcommands.api.commands.application.GuildApplicationCommandManager
import com.freya02.botcommands.api.commands.application.annotations.AppDeclaration
import com.freya02.docs.DocSourceType

@CommandMarker
class SlashSearch(private val slashDocsController: SlashDocsController) {
    @AppDeclaration
    fun declare(manager: GuildApplicationCommandManager) {
        manager.slashCommand("search", CommandScope.GUILD) {
            description = "Searches the documentation for classes, methods and fields"

            DocSourceType.typesForGuild(manager.guild).forEach { sourceType ->
                subcommand(sourceType.cmdName) {
                    description = "Searches the documentation for classes, methods and fields"

                    generatedOption("sourceType") { sourceType }

                    option("query") {
                        description = "Search query, # only searches methods, all uppercase for constant fields"
                        autocompleteReference(SEARCH_AUTOCOMPLETE_NAME)
                    }

                    function = slashDocsController::onSearchSlashCommand
                }
            }
        }
    }
}
