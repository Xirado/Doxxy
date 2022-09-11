package com.freya02.bot.commands.slash.versioning

import com.freya02.bot.Main.BRANCH_VERSIONS_FOLDER_PATH
import com.freya02.bot.commands.slash.DeleteButtonListener.Companion.messageDeleteButton
import com.freya02.bot.utils.CryptoUtils
import com.freya02.bot.utils.Utils.isBCGuild
import com.freya02.bot.versioning.LibraryType
import com.freya02.bot.versioning.github.*
import com.freya02.bot.versioning.maven.MavenBranchProjectDependencyVersionChecker
import com.freya02.bot.versioning.supplier.BuildToolType
import com.freya02.bot.versioning.supplier.DependencySupplier
import com.freya02.botcommands.api.annotations.CommandMarker
import com.freya02.botcommands.api.commands.CommandPath
import com.freya02.botcommands.api.commands.application.ApplicationCommand
import com.freya02.botcommands.api.commands.application.CommandScope
import com.freya02.botcommands.api.commands.application.GuildApplicationCommandManager
import com.freya02.botcommands.api.commands.application.annotations.AppDeclaration
import com.freya02.botcommands.api.commands.application.annotations.AppOption
import com.freya02.botcommands.api.commands.application.slash.GuildSlashEvent
import com.freya02.botcommands.api.commands.application.slash.autocomplete.annotations.AutocompleteHandler
import com.freya02.botcommands.api.commands.application.slash.autocomplete.annotations.CacheAutocomplete
import com.freya02.botcommands.api.commands.application.slash.autocomplete.annotations.CompositeKey
import com.freya02.botcommands.api.commands.application.slash.builder.SlashCommandBuilder
import com.freya02.botcommands.api.components.Components
import dev.minn.jda.ktx.messages.Embed
import me.xdrop.fuzzywuzzy.FuzzySearch
import me.xdrop.fuzzywuzzy.ToStringFunction
import me.xdrop.fuzzywuzzy.model.BoundExtractedResult
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import java.io.IOException
import java.nio.file.Path
import java.util.*
import kotlin.time.Duration.Companion.minutes

//TODO refactor
@CommandMarker
class SlashJitpack(private val components: Components) : ApplicationCommand() {
    private val bcPullRequestCache = PullRequestCache("freya022", "BotCommands", null)
    private val jdaPullRequestCache = PullRequestCache("DV8FromTheWorld", "JDA", "master")
    private val jdaKtxPullRequestCache = PullRequestCache("MinnDevelopment", "jda-ktx", "master")
    private val branchNameToJdaVersionChecker: MutableMap<String, MavenBranchProjectDependencyVersionChecker> =
        Collections.synchronizedMap(hashMapOf())
    private val updateCountdownMap: MutableMap<String, UpdateCountdown> = HashMap()
    private val updateMap: MutableMap<LibraryType, UpdateCountdown> = EnumMap(LibraryType::class.java)
    private val branchMap: MutableMap<LibraryType, GithubBranchMap> = EnumMap(LibraryType::class.java)

    override fun getOptionChoices(guild: Guild?, commandPath: CommandPath, optionName: String): List<Command.Choice> {
        if (optionName == "library_type") {
            return when {
                guild.isBCGuild() -> listOf(
                    Command.Choice("BotCommands", LibraryType.BOT_COMMANDS.name),
                    Command.Choice("JDA 5", LibraryType.JDA5.name),
                    Command.Choice("JDA-KTX", LibraryType.JDA_KTX.name)
                )
                else -> listOf(
                    Command.Choice("JDA 5", LibraryType.JDA5.name),
                    Command.Choice("JDA-KTX", LibraryType.JDA_KTX.name)
                )
            }
        }

        return super.getOptionChoices(guild, commandPath, optionName)
    }

    @CommandMarker
    fun onSlashJitpackPR(event: GuildSlashEvent, libraryType: LibraryType, buildToolType: BuildToolType, issueNumber: Int) {
        val pullRequest = when (libraryType) {
            LibraryType.BOT_COMMANDS -> bcPullRequestCache.pullRequests[issueNumber]
            LibraryType.JDA5 -> jdaPullRequestCache.pullRequests[issueNumber]
            LibraryType.JDA_KTX -> jdaKtxPullRequestCache.pullRequests[issueNumber]
            else -> throw IllegalArgumentException()
        } ?: run {
            event.reply("Unknown Pull Request").setEphemeral(true).queue()
            return
        }

        val dependencyStr: String = when (libraryType) {
            LibraryType.BOT_COMMANDS -> {
                val branch = pullRequest.branch
                val jdaVersionChecker = branchNameToJdaVersionChecker.getOrPut(branch.branchName) {
                    try {
                        return@getOrPut MavenBranchProjectDependencyVersionChecker(
                            getBranchFileName(branch),
                            branch.ownerName,
                            branch.repoName,
                            "JDA",
                            branch.branchName
                        )
                    } catch (e: IOException) {
                        throw RuntimeException("Unable to create branch specific JDA version checker", e)
                    }
                }
                checkGithubBranchUpdates(event, branch, jdaVersionChecker)
                val latestBotCommands = pullRequest.toJitpackArtifact()
                val jdaVersionFromBotCommands = jdaVersionChecker.latest
                DependencySupplier.formatBC(buildToolType, jdaVersionFromBotCommands, latestBotCommands)
            }
            LibraryType.JDA5, LibraryType.JDA_KTX -> DependencySupplier.formatJitpack(buildToolType, pullRequest.toJitpackArtifact())
            else -> throw IllegalArgumentException("Invalid library type: $libraryType")
        }

        val embed = Embed {
            title =
                "${buildToolType.humanName} dependencies for ${libraryType.displayString} @ PR #${pullRequest.number}"
            url = pullRequest.pullUrl

            field("PR Link", pullRequest.pullUrl, false)

            description = when (buildToolType) {
                BuildToolType.MAVEN -> "```xml\n$dependencyStr```"
                BuildToolType.GRADLE, BuildToolType.GRADLE_KTS -> "```gradle\n$dependencyStr```"
            }
        }

        event.replyEmbeds(embed)
            .addActionRow(
                components.messageDeleteButton(event.user),
                link("https://jda.wiki/using-jda/using-new-features/", "How ? (Wiki)", EmojiUtils.resolveJDAEmoji("face_with_monocle"))
            )
            .queue()
    }

    @CacheAutocomplete
    @AutocompleteHandler(name = BRANCH_NUMBER_AUTOCOMPLETE_NAME, showUserInput = false)
    fun onBranchNumberAutocomplete(
        event: CommandAutoCompleteInteractionEvent,
        @CompositeKey @AppOption libraryType: LibraryType?
    ): Collection<Command.Choice> {
        val pullRequests = when (libraryType) {
            LibraryType.BOT_COMMANDS -> bcPullRequestCache.pullRequests.values(arrayOfNulls(0))
            LibraryType.JDA5 -> jdaPullRequestCache.pullRequests.values(arrayOfNulls(0))
            LibraryType.JDA_KTX -> jdaKtxPullRequestCache.pullRequests.values(arrayOfNulls(0))
            else -> throw IllegalArgumentException()
        }
        return fuzzyMatching(
            pullRequests.asList(),
            { referent: PullRequest -> referent.asHumanDescription },
            event
        ).map { r: BoundExtractedResult<PullRequest> ->
            Command.Choice(
                r.referent.asHumanDescription,
                r.referent.number.toLong()
            )
        }
    }

    @CacheAutocomplete
    @AutocompleteHandler(name = BRANCH_NAME_AUTOCOMPLETE_NAME)
    fun onBranchNameAutocomplete(
        event: CommandAutoCompleteInteractionEvent,
        @CompositeKey @AppOption libraryType: LibraryType
    ): Collection<String> {
        return getBranchMap(libraryType).branches.keys
    }

    @AppDeclaration
    fun declare(manager: GuildApplicationCommandManager) {
        BuildToolType.values().forEach { toolType ->
            manager.slashCommand("jitpack", "branch", toolType.cmdName, CommandScope.GUILD) {
                description = "Shows you how to use a branch for your bot"

                addCommonJitpackOptions(manager, toolType)
                option("branchName") {
                    autocompleteReference(BRANCH_NAME_AUTOCOMPLETE_NAME)
                }

                function = SlashJitpack::onSlashJitpackBranch
            }

            manager.slashCommand("jitpack", "pr", toolType.cmdName, CommandScope.GUILD) {
                description = "Shows you how to use Pull Requests for your bot"

                addCommonJitpackOptions(manager, toolType)
                option("issueNumber") {
                    description = "The number of the issue"

                    autocompleteReference(BRANCH_NUMBER_AUTOCOMPLETE_NAME) //TODO rename constant
                }

                function = SlashJitpack::onSlashJitpackPR
            }
        }
    }

    private fun SlashCommandBuilder.addCommonJitpackOptions(manager: GuildApplicationCommandManager, toolType: BuildToolType) {
        when {
            manager.guild.isBCGuild() -> option("libraryType") {
                description = "Type of library"

                choices = listOf(
                    Command.Choice("BotCommands", LibraryType.BOT_COMMANDS.name),
                    Command.Choice("JDA 5", LibraryType.JDA5.name)
                )
            }
            else -> generatedOption("libraryType") { LibraryType.JDA5 }
        }

        generatedOption("buildToolType") { toolType }
    }

    @CommandMarker
    fun onSlashJitpackBranch(event: GuildSlashEvent, libraryType: LibraryType, buildToolType: BuildToolType, branchName: String?) {
        val githubBranchMap = getBranchMap(libraryType)
        val branch = when (branchName) {
            null -> githubBranchMap.defaultBranch
            else -> githubBranchMap.branches[branchName] ?: run {
                event.reply("Unknown branch '$branchName'").setEphemeral(true).queue()
                return
            }
        }

        onSlashJitpackBranchImpl(event, libraryType, buildToolType, branch)
    }

    private fun onSlashJitpackBranchImpl(
        event: GuildSlashEvent,
        libraryType: LibraryType,
        buildToolType: BuildToolType,
        branch: GithubBranch
    ) {
        val branchName = branch.branchName

        val dependencyStr = when (libraryType) {
            LibraryType.JDA5, LibraryType.JDA_KTX -> DependencySupplier.formatJitpack(buildToolType, branch.asJitpackArtifact)
            LibraryType.BOT_COMMANDS -> {
                val jdaVersionChecker = branchNameToJdaVersionChecker.getOrPut(branchName) {
                    try {
                        return@getOrPut MavenBranchProjectDependencyVersionChecker(
                            getBranchFileName(branch),
                            branch.ownerName,
                            branch.repoName,
                            "JDA",
                            branch.branchName
                        )
                    } catch (e: IOException) {
                        throw RuntimeException("Unable to create branch specific JDA version checker", e)
                    }
                }
                checkGithubBranchUpdates(event, branch, jdaVersionChecker)
                DependencySupplier.formatBC(
                    buildToolType,
                    jdaVersionChecker.latest,
                    branch.asJitpackArtifact
                )
            }
            else -> throw IllegalArgumentException("Invalid lib type: $libraryType")
        }

        val embed = Embed {
            title =
                buildToolType.humanName + " dependencies for " + libraryType.displayString + " @ branch '" + branchName + "'"
            url = branch.asURL

            field("Branch Link", branch.asURL, false)

            description = when (buildToolType) {
                BuildToolType.MAVEN -> "```xml\n$dependencyStr```"
                BuildToolType.GRADLE, BuildToolType.GRADLE_KTS -> "```gradle\n$dependencyStr```"
            }
        }

        event.replyEmbeds(embed)
            .addActionRow(components.messageDeleteButton(event.user))
            .queue()
    }

    private fun checkGithubBranchUpdates(
        event: GuildSlashEvent,
        branch: GithubBranch,
        checker: MavenBranchProjectDependencyVersionChecker
    ) {
        val updateCountdown = updateCountdownMap.getOrPut(branch.branchName) { UpdateCountdown(1.minutes) }
        if (updateCountdown.needsUpdate()) {
            checker.checkVersion()
            event.context.invalidateAutocompleteCache(BRANCH_NUMBER_AUTOCOMPLETE_NAME)
            checker.saveVersion()
        }
    }

    private fun getBranchMap(libraryType: LibraryType): GithubBranchMap {
        val updateCountdown =
            updateMap.computeIfAbsent(libraryType) { UpdateCountdown(1.minutes) }

        synchronized(branchMap) {
            branchMap[libraryType].let { githubBranchMap: GithubBranchMap? ->
                if (githubBranchMap == null || updateCountdown.needsUpdate()) {
                    return retrieveBranchList(libraryType)
                        .also { updatedMap -> branchMap[libraryType] = updatedMap }
                }

                return githubBranchMap
            }
        }
    }

    private fun retrieveBranchList(libraryType: LibraryType): GithubBranchMap {
        val (ownerName: String, repoName: String) = when (libraryType) {
            LibraryType.JDA5 -> arrayOf("DV8FromTheWorld", "JDA")
            LibraryType.BOT_COMMANDS -> arrayOf("freya022", "BotCommands")
            LibraryType.JDA_KTX -> arrayOf("MinnDevelopment", "jda-ktx")
            else -> throw IllegalArgumentException("No branches for $libraryType")
        }

        val map: Map<String, GithubBranch> = GithubUtils.getBranches(ownerName, repoName).associateBy { it.branchName }
        val defaultBranchName = GithubUtils.getDefaultBranchName(ownerName, repoName)
        val defaultBranch = map[defaultBranchName]!!
        return GithubBranchMap(defaultBranch, map)
    }

    private fun getBranchFileName(branch: GithubBranch): Path {
        return BRANCH_VERSIONS_FOLDER_PATH.resolve(
            "%s-%s-%s.txt".format(
                branch.ownerName,
                branch.repoName,
                CryptoUtils.hash(branch.branchName)
            )
        )
    }

    companion object {
        private const val BRANCH_NUMBER_AUTOCOMPLETE_NAME = "SlashJitpack: branchNumber"
        private const val BRANCH_NAME_AUTOCOMPLETE_NAME = "SlashJitpack: branchName"

        private fun fuzzyMatching(
            items: Collection<PullRequest>,
            toStringFunction: ToStringFunction<PullRequest>,
            event: CommandAutoCompleteInteractionEvent
        ): List<BoundExtractedResult<PullRequest>> {
            val list = items.sortedWith(Comparator.comparingInt { obj: PullRequest -> obj.number }.reversed())
            val autoCompleteQuery = event.focusedOption
            if (autoCompleteQuery.value.isBlank()) {
                return list.mapIndexed { i, it -> BoundExtractedResult(it, "", 100, i) }
            }

            //First sort the results by similarities but by taking into account an incomplete input
            val bigLengthDiffResults = FuzzySearch.extractTop(
                autoCompleteQuery.value,
                list,
                toStringFunction, { s1: String, s2: String -> FuzzySearch.partialRatio(s1, s2) },
                OptionData.MAX_CHOICES
            )

            //Then sort the results by similarities but don't take length into account
            return FuzzySearch.extractTop(
                autoCompleteQuery.value,
                bigLengthDiffResults.stream().map { obj: BoundExtractedResult<PullRequest> -> obj.referent }.toList(),
                toStringFunction, { s1: String, s2: String -> FuzzySearch.ratio(s1, s2) },
                OptionData.MAX_CHOICES
            )
        }
    }
}