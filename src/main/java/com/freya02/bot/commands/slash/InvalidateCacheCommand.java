package com.freya02.bot.commands.slash;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.Test;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;

import static com.freya02.bot.commands.slash.CommonDocsHandlers.AUTOCOMPLETE_NAMES;

public class InvalidateCacheCommand extends ApplicationCommand {
	@Test(guildIds = 722891685755093072L)
	@JDASlashCommand(
			name = "invalidate"
	)
	public void onSlashInvalidate(GuildSlashEvent event) {
		for (String autocompleteName : AUTOCOMPLETE_NAMES) {
			event.getContext().invalidateAutocompletionCache(autocompleteName);
		}

		event.reply("Done").setEphemeral(true).queue();
	}
}
