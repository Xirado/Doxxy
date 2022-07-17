package com.freya02.bot.commands.text

import com.freya02.botcommands.api.Logging
import com.freya02.botcommands.api.annotations.RequireOwner
import com.freya02.botcommands.api.prefixed.BaseCommandEvent
import com.freya02.botcommands.api.prefixed.TextCommand
import com.freya02.botcommands.api.prefixed.annotations.JDATextCommand
import net.dv8tion.jda.api.JDA
import kotlin.system.exitProcess

class Exit : TextCommand() {
    private val logger = Logging.getLogger()

    @RequireOwner
    @JDATextCommand(name = "exit")
    fun exit(event: BaseCommandEvent) {
        logger.warn("Shutdown initiated by {} ({})", event.author.asTag, event.author.id)
        event.reactSuccess().mapToResult().complete()

        event.jda.shutdown()
        while (event.jda.status != JDA.Status.SHUTDOWN) {
            Thread.sleep(15)
            Thread.onSpinWait()
        }

        exitProcess(0)
    }
}