package com.freya02.bot.commands;

import com.freya02.bot.utils.HTMLElement;
import com.freya02.botcommands.api.Logging;
import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.AutocompletionHandler;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.docs.ClassDoc;
import com.freya02.docs.ClassDocs;
import com.freya02.docs.MethodDoc;
import com.freya02.docs.SeeAlso;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.CommandAutoCompleteEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyAction;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ClassCommand extends ApplicationCommand {
	private static final Logger LOGGER = Logging.getLogger();
//	private static final Emoji CLIPBOARD_EMOJI = EmojiUtils.resolveJDAEmoji("clipboard");
//	private static final int MAX_CHOICES = 25;
	private final List<String> classNameList;

	//Performance could be better without nested maps but this is way easier for the autocompletion to understand what to show
	//Map<Class name, Map<Method short signature, MethodDoc>>
	private final Map<String, Map<String, MethodDoc>> methodChoiceList = new HashMap<>();

	private static record MapEntry<K, V>(K key, V value) {}

	public ClassCommand() {
		LOGGER.info("Loading docs");
		ClassDocs.loadAllDocs("http://localhost:63342/DocsBot/test_docs/allclasses-index.html");

		this.classNameList = ClassDocs.getDocNamesMap().values()
				.stream()
				.map(ClassDoc::getClassName)
				.sorted()
				.toList();

		for (ClassDoc doc : ClassDocs.getDocNamesMap().values()) {
			methodChoiceList.put(doc.getClassName(), doc.getMethodDocs()
					.values()
					.stream()
					.map(m -> new MapEntry<>(m.getSimpleSignature(), m))
					.collect(Collectors.toMap(MapEntry::key, MapEntry::value))
			);
		}
	}
	@JDASlashCommand(name = "class")
	public void showClass(GuildSlashEvent event,
	                      @AppOption(description = "Name of the Java class", autocomplete = "autoClass") String className,
	                      @Optional @AppOption(description = "ID of the Java method for this class", autocomplete = "autoMethod") String methodId) throws IOException {

		final ClassDoc classDoc = ClassDocs.ofName(className);

		if (classDoc == null) {
			event.reply("Unknown class").setEphemeral(true).queue();

			return;
		}

		if (methodId != null) {
			final MethodDoc methodDoc = methodChoiceList.getOrDefault(className, Map.of()).get(methodId);

			if (methodDoc == null) {
				event.reply("Unknown method").setEphemeral(true).queue();

				return;
			}

			sendMethod(event, true, classDoc, methodDoc);
		} else {
			sendClass(event, true, classDoc);
		}
	}

	private void sendMethod(GuildSlashEvent event, boolean ephemeral, ClassDoc classDoc, MethodDoc methodDoc) {
		ReplyAction replyAction = event.replyEmbeds(toEmbed(classDoc, methodDoc).build());

		replyAction
				.setEphemeral(ephemeral)
				.queue();
	}

	private void sendClass(Interaction event, boolean ephemeral, ClassDoc docs) {
		ReplyAction replyAction = event.replyEmbeds(toEmbed(docs).build());

		// Much more work to do for this to really work
		// The link could target method without knowing it, it could also target weird internal sun classes
//		final SeeAlso seeAlso = docs.getSeeAlso();
//		if (seeAlso != null) {
//			final List<SeeAlso.SeeAlsoReference> references = seeAlso.getReferences();
//			final LambdaSelectionMenuBuilder selectionMenuBuilder = Components.selectionMenu(evt -> onSeeAlsoClicked(evt, references));
//
//			for (int i = 0, referencesSize = Math.min(MAX_CHOICES, references.size()); i < referencesSize; i++) {
//				SeeAlso.SeeAlsoReference reference = references.get(i);
//
//				final ClassDoc docOpt = ClassDocs.getOrNull(reference.link());
//				if (docOpt != null) {
//					selectionMenuBuilder.addOption(reference.text(), String.valueOf(i), CLIPBOARD_EMOJI);
//				}
//			}
//
//			replyAction = replyAction.addActionRow(selectionMenuBuilder.build());
//		}

		replyAction
				.setEphemeral(ephemeral)
				.queue();
	}

	private EmbedBuilder toEmbed(ClassDoc doc) {
		final EmbedBuilder builder = new EmbedBuilder();

		builder.setTitle(doc.getDocTitleElement().getTargetElement().text(), doc.getURL());

		final HTMLElement descriptionElement = doc.getDescriptionElement();
		if (descriptionElement != null) {
			final String description = descriptionElement.getMarkdown3();
			if (description.length() > MessageEmbed.DESCRIPTION_MAX_LENGTH) {
				builder.setDescription("Description is too long, please look at the [docs page](" + doc.getURL() + ")");
			} else {
				builder.setDescription(description);
			}
		} else {
			builder.setDescription("No description");
		}

		final List<HTMLElement> typeParameters = doc.getTypeParameterElements();
		if (typeParameters != null) {
			builder.addField("Type parameters", typeParameters.stream().map(HTMLElement::getMarkdown3).collect(Collectors.joining("\n")), false);
		}

		final SeeAlso seeAlso = doc.getSeeAlso();
		if (seeAlso != null) {
			final String seeAlsoMd = seeAlso.getMarkdown3();

			if (seeAlsoMd.length() <= MessageEmbed.VALUE_MAX_LENGTH) {
				builder.addField("See Also", seeAlsoMd, false);
			}
		}

		return builder;
	}

	private EmbedBuilder toEmbed(ClassDoc classDoc, MethodDoc methodDoc) {
		final EmbedBuilder builder = new EmbedBuilder();

		builder.setTitle(classDoc.getClassName() + '#' + methodDoc.getSimpleSignature(), methodDoc.getURL());

		if (classDoc != methodDoc.getClassDocs()) {
			builder.setDescription("**Inherited from " + methodDoc.getClassDocs().getClassName() + "**\n\n");
		}

		final HTMLElement descriptionElement = methodDoc.getDescriptionElement();
		if (descriptionElement != null) {
			final String description = descriptionElement.getMarkdown3();
			if (description.length() + builder.getDescriptionBuilder().length() > MessageEmbed.DESCRIPTION_MAX_LENGTH) {
				builder.appendDescription("Description is too long, please look at the [docs page](" + methodDoc.getURL() + ")");
			} else {
				builder.appendDescription(description);
			}
		} else {
			builder.appendDescription("No description");
		}

		final List<HTMLElement> parameterElements = methodDoc.getParameterElements();
		if (parameterElements != null) {
			builder.addField("Parameters", parameterElements.stream().map(HTMLElement::getMarkdown3).collect(Collectors.joining("\n")), false);
		}

		final HTMLElement returnsElement = methodDoc.getReturnsElement();
		if (returnsElement != null) {
			builder.addField("Returns", returnsElement.getMarkdown3(), false);
		}

		final HTMLElement incubatingElement = methodDoc.getIncubatingElement();
		if (incubatingElement != null) {
			builder.addField("Incubating", incubatingElement.getMarkdown3(), false);
		}

		final List<HTMLElement> typeParameters = methodDoc.getTypeParameterElements();
		if (typeParameters != null) {
			builder.addField("Type parameters", typeParameters.stream().map(HTMLElement::getMarkdown3).collect(Collectors.joining("\n")), false);
		}

		final SeeAlso seeAlso = methodDoc.getSeeAlso();
		if (seeAlso != null) {
			final String seeAlsoMd = seeAlso.getMarkdown3();

			if (seeAlsoMd.length() <= MessageEmbed.VALUE_MAX_LENGTH) {
				builder.addField("See Also", seeAlsoMd, false);
			}
		}

		return builder;
	}

	@AutocompletionHandler(name = "autoClass", showUserInput = false)
	public List<String> autoClass(CommandAutoCompleteEvent event) {
		return classNameList;
	}

	@AutocompletionHandler(name = "autoMethod", showUserInput = false)
	public List<String> autoMethod(CommandAutoCompleteEvent event, @AppOption String className) {
		return new ArrayList<>(methodChoiceList.getOrDefault(className, Map.of()).keySet());
	}

//	private void onSeeAlsoClicked(SelectionEvent event, List<SeeAlso.SeeAlsoReference> references) {
//		try {
//			final SeeAlso.SeeAlsoReference reference = references.get(Integer.parseInt(event.getValues().get(0)));
//
//			sendDocs(event, true, ClassDocs.of(reference.link()));
//		} catch (IOException e) {
//			event.reply("Couldn't send the docs").setEphemeral(true).queue();
//		}
//	}
}
