package com.freya02.bot.commands.slash.versioning;

import com.freya02.bot.commands.slash.DeleteButtonListener;
import com.freya02.bot.versioning.ArtifactInfo;
import com.freya02.bot.versioning.LibraryType;
import com.freya02.bot.versioning.Versions;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import net.dv8tion.jda.api.EmbedBuilder;
import org.intellij.lang.annotations.Language;

public class SlashMaven extends ApplicationCommand {
	@Language(value = "xml", prefix = "<project>", suffix = "</project>")
	private static final String BC_XML = """
			<repositories>
			    <repository>
			        <id>jitpack</id>
			        <url>https://jitpack.io</url>
			    </repository>
			</repositories>
			
			<dependencies>
				<dependency>
					<groupId>%s</groupId>
					<artifactId>%s</artifactId>
					<version>%s</version>
				</dependency>
				<dependency>
					<groupId>%s</groupId>
					<artifactId>%s</artifactId>
					<version>%s</version>
				</dependency>
			</dependencies>
			""";

	@Language(value = "xml", prefix = "<project>", suffix = "</project>")
	private static final String JDA4_XML = """
			<repository>
			    <id>dv8tion</id>
			    <name>m2-dv8tion</name>
			    <url>https://m2.dv8tion.net/releases</url>
			</repository>
            
			<dependencies>
				<dependency>
					<groupId>%s</groupId>
					<artifactId>%s</artifactId>
					<version>%s</version>
				</dependency>
			</dependencies>
			""";

	@Language(value = "xml", prefix = "<project>", suffix = "</project>")
	private static final String JDA5_XML = """
			<dependencies>
				<dependency>
					<groupId>%s</groupId>
					<artifactId>%s</artifactId>
					<version>%s</version>
				</dependency>
			</dependencies>
			""";

	private final Versions versions;

	public SlashMaven(Versions versions) {
		this.versions = versions;
	}

	@JDASlashCommand(
			name = "maven",
			description = "Shows the maven dependencies for a library"
	)
	public void onSlashMaven(GuildSlashEvent event,
	                         @AppOption(description = "Type of library") LibraryType libraryType) {
		final EmbedBuilder builder = new EmbedBuilder();

		final String xml;
		if (libraryType == LibraryType.BOT_COMMANDS) {
			final ArtifactInfo latestBotCommands = versions.getLatestBotCommandsVersion();
			final ArtifactInfo jdaVersionFromBotCommands = versions.getJdaVersionFromBotCommands();

			builder.setTitle("Maven dependencies for BotCommands");
			xml = BC_XML.formatted(jdaVersionFromBotCommands.groupId(), jdaVersionFromBotCommands.artifactId(), jdaVersionFromBotCommands.version(),
					latestBotCommands.groupId(), latestBotCommands.artifactId(), latestBotCommands.version());
		} else if (libraryType == LibraryType.JDA5) {
			final ArtifactInfo latestJDAVersion = versions.getLatestJDA5Version();

			builder.setTitle("Maven dependencies for JDA 5");

			xml = JDA5_XML.formatted(latestJDAVersion.groupId(), latestJDAVersion.artifactId(), latestJDAVersion.version());
		} else if (libraryType == LibraryType.JDA4) {
			final ArtifactInfo latestJDAVersion = versions.getLatestJDA4Version();

			builder.setTitle("Maven dependencies for JDA 4");

			xml = JDA4_XML.formatted(latestJDAVersion.groupId(), latestJDAVersion.artifactId(), latestJDAVersion.version());
		} else {
			throw new IllegalArgumentException();
		}

		builder.setDescription("```xml\n" + xml + "```");

		event.replyEmbeds(builder.build())
//				.setEphemeral(true)
				.addActionRow(DeleteButtonListener.getDeleteButton(event.getUser()))
				.queue();
	}
}