package code.blurone.uteams

import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIBukkitConfig
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.SuggestionInfo
import dev.jorel.commandapi.arguments.*
import dev.jorel.commandapi.executors.CommandArguments
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.hover.content.Text
import net.minecraft.server.ServerScoreboard
import org.bukkit.NamespacedKey
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld
import org.bukkit.craftbukkit.v1_20_R3.scoreboard.CraftScoreboard
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team
import java.io.File

// I'm not proud of this code. It looks horrible.
// Copilot opted to add: "I'm sorry. I'll refactor it later. I promise. I'm sorry."

class UserTeams : JavaPlugin(), Listener {
    private val mainScoreboard get() = server.scoreboardManager?.mainScoreboard ?: throw NullPointerException("Main scoreboard not found")
    private lateinit var ownerScoreboard: Scoreboard
    private lateinit var invitationScoreboard: Scoreboard
    private val confirmationNamespacedKey = NamespacedKey(this, "confirmation")

    override fun onLoad() {
        CommandAPI.onLoad(CommandAPIBukkitConfig(this)
            .verboseOutput(true) // DEBUG
            .silentLogs(false)
        )

        val optionsOptions = config.getConfigurationSection("modifiable_team_options")

        val optionCommands = mutableListOf<CommandAPICommand>() // /uteam option <subcommands> <value>

        if (optionsOptions?.getBoolean("displayName") != false)
            optionCommands.add(CommandAPICommand("displayName")
                .withRequirement(::isTeamOwner)
                .withArguments(ChatComponentArgument("value"))
                .executesPlayer(PlayerCommandExecutor(::optionDisplayName))
            )

        if (optionsOptions?.getBoolean("color") != false)
            optionCommands.add(CommandAPICommand("color")
                .withRequirement(::isTeamOwner)
                .withArguments(ChatColorArgument("value"))
                .executesPlayer(PlayerCommandExecutor(::optionColor))
            )

        if (optionsOptions?.getBoolean("friendlyFire") != false)
            optionCommands.add(CommandAPICommand("friendlyFire")
                .withRequirement(::isTeamOwner)
                .withArguments(BooleanArgument("value"))
                .executesPlayer(PlayerCommandExecutor(::optionFriendlyFire))
            )

        if (optionsOptions?.getBoolean("seeFriendlyInvisibles") == true)
            optionCommands.add(CommandAPICommand("seeFriendlyInvisibles")
                .withRequirement(::isTeamOwner)
                .withArguments(BooleanArgument("value"))
                .executesPlayer(PlayerCommandExecutor(::optionSeeFriendlyInvisibles))
            )

        if (optionsOptions?.getBoolean("nametagVisibility") == true)
            optionCommands.add(CommandAPICommand("nametagVisibility")
                .withRequirement(::isTeamOwner)
                .withArguments(MultiLiteralArgument("value", "always", "hideForOwnTeam"))
                .executesPlayer(PlayerCommandExecutor(::optionNametagVisibility))
            )

        if (optionsOptions?.getBoolean("deathMessageVisibility") == true)
            optionCommands.add(CommandAPICommand("deathMessageVisibility")
                .withRequirement(::isTeamOwner)
                .withArguments(MultiLiteralArgument("value", "always", "hideForOwnTeam"))
                .executesPlayer(PlayerCommandExecutor(::optionDeathMessageVisibility))
            )

        if (optionsOptions?.getBoolean("collisionRule") == true)
            optionCommands.add(CommandAPICommand("collisionRule")
                .withRequirement(::isTeamOwner)
                .withArguments(MultiLiteralArgument("value", "always", "pushOtherTeam"))
                .executesPlayer(PlayerCommandExecutor(::optionCollisionRule))
            )

        if (optionsOptions?.getBoolean("prefix") != false)
            optionCommands.add(CommandAPICommand("prefix")
                .withRequirement(::isTeamOwner)
                .withArguments(ChatComponentArgument("value"))
                .executesPlayer(PlayerCommandExecutor(::optionPrefix))
            )

        if (optionsOptions?.getBoolean("suffix") != false)
            optionCommands.add(CommandAPICommand("suffix")
                .withRequirement(::isTeamOwner)
                .withArguments(ChatComponentArgument("value"))
                .executesPlayer(PlayerCommandExecutor(::optionSuffix))
            )

        CommandAPICommand("uteam")
            .apply {
                if (config.getBoolean("override_team_command", false))
                    withAliases("team")
            }
            .withSubcommands(
                CommandAPICommand("create") // /uteam create <codename> [displayName]
                    .withRequirement{ !isInTeam(it) }
                    .withArguments(StringArgument("codename"))
                    .withOptionalArguments(ChatComponentArgument("displayName"))
                    .executesPlayer(PlayerCommandExecutor(::createTeam)),
                CommandAPICommand("disband") // /uteam disband
                    .withRequirement(::isTeamOwner)
                    .executesPlayer(PlayerCommandExecutor(::disbandTeamInitiation))
                    .withSubcommands(
                        CommandAPICommand("confirm") // /uteam disband confirm
                            .withRequirement { isInConfirmation(it, "disband") }
                            .executesPlayer(PlayerCommandExecutor(::disbandConfirm)),
                        CommandAPICommand("cancel") // /uteam disband cancel
                            .withRequirement { isInConfirmation(it, "disband") }
                            .executesPlayer(PlayerCommandExecutor(::cancelConfirmation))
                    ),
                CommandAPICommand("invite")
                    .withRequirement(::isTeamOwner)
                    .withArguments(OfflinePlayerArgument("player")
                        .replaceSafeSuggestions(SafeSuggestions.suggest(::teamlessFilter))
                    )
                    .executesPlayer(PlayerCommandExecutor(::invitePlayer)),
                CommandAPICommand("join")
                    .withRequirement(::hasInvites)
                    .withArguments(TeamArgument("team").replaceSafeSuggestions(SafeSuggestions.suggest(::invitedTeamsFilter)))
                    .withSubcommands(
                        CommandAPICommand("accept")
                            .withRequirement(::hasInvites)
                            .executesPlayer(PlayerCommandExecutor(::acceptInvite)),
                        CommandAPICommand("decline")
                            .withRequirement(::hasInvites)
                            .executesPlayer(PlayerCommandExecutor(::declineInvite))
                    ),
                CommandAPICommand("kick")
                    .withRequirement(::isTeamOwner)
                    .withArguments(OfflinePlayerArgument("player")
                        .replaceSafeSuggestions(SafeSuggestions.suggest(::teammatesFilter))
                    )
                    .executesPlayer(PlayerCommandExecutor(::kickPlayerInitiation))
                    .withSubcommands(
                        CommandAPICommand("confirm")
                            .withRequirement { isInConfirmation(it, "kick") }
                            .executesPlayer(PlayerCommandExecutor(::kickPlayerConfirm)),
                        CommandAPICommand("cancel")
                            .withRequirement { isInConfirmation(it, "kick") }
                            .executesPlayer(PlayerCommandExecutor(::cancelConfirmation))
                    ),
                CommandAPICommand("leave")
                    .withRequirement(::isInTeam)
                    .withRequirement { !isTeamOwner(it) }
                    .executesPlayer(PlayerCommandExecutor(::leaveTeamInitiation))
                    .withSubcommands(
                        CommandAPICommand("confirm")
                            .withRequirement { isInConfirmation(it, "leave") }
                            .executesPlayer(PlayerCommandExecutor(::leaveTeamConfirm)),
                        CommandAPICommand("cancel")
                            .withRequirement { isInConfirmation(it, "leave") }
                            .executesPlayer(PlayerCommandExecutor(::cancelConfirmation))
                    ),
                CommandAPICommand("modify")
                    .withRequirement(::isTeamOwner)
                    .withSubcommands(*optionCommands.toTypedArray()),
                CommandAPICommand("list")
                    .executesPlayer(PlayerCommandExecutor(::listTeams)),
                CommandAPICommand("transfer")
                    .withRequirement(::isTeamOwner)
                    .withArguments(OfflinePlayerArgument("player")
                        .replaceSafeSuggestions(SafeSuggestions.suggest(::teammatesFilter))
                    )
                    .executesPlayer(PlayerCommandExecutor(::transferOwnershipInitialize))
                    .withSubcommands(
                        CommandAPICommand("confirm")
                            .withRequirement { isInConfirmation(it, "transfer") }
                            .executesPlayer(PlayerCommandExecutor(::transferOwnershipConfirm)),
                        CommandAPICommand("cancel")
                            .withRequirement { isInConfirmation(it, "transfer") }
                            .executesPlayer(PlayerCommandExecutor(::cancelConfirmation))
                    )
            )
            .register()
    }

    override fun onEnable() {
        // Plugin startup logic
        saveDefaultConfig()
        ownerScoreboard = loadScoreboard("owner_scoreboard")
        invitationScoreboard = loadScoreboard("invitation_scoreboard")
        CommandAPI.onEnable()
        saveResource("translations/default.yml", true)
        supportedTranslations.forEach {
            if (!File(dataFolder, "translations/$it.yml").exists())
                saveResource("translations/$it.yml", false)
        }
        for (file in dataFolder.resolve("translations").listFiles()!!) {
            if (file.extension != "yml") continue

            translations[file.nameWithoutExtension.lowercase()] = try {
                val yaml = YamlConfiguration()
                yaml.load(file)
                yaml
            } catch (e: Exception) {
                logger.warning("Failed to load ${file.name}.")
                continue
            }
        }
        server.pluginManager.registerEvents(this, this)
    }

    override fun onDisable() {
        // Plugin shutdown logic
        //saveScoreboards()
        CommandAPI.onDisable()
    }

    /*
    @EventHandler
    private fun onWorldSave(event: WorldSaveEvent) {
        if (event.world == server.worlds[0])
            saveScoreboards()
    }

    private fun saveScoreboards() {
        logger.info("attempting to save scoreboards")
        ((ownerScoreboard as CraftScoreboard).handle as ServerScoreboard).dataFactory().constructor.get().let {
            it.setDirty()
            it.save(
                (if (saveInWorld) server.worlds[0].worldFolder else dataFolder)
                    .resolve("data/ownerScoreboard.dat").apply { parentFile.mkdirs(); createNewFile() }
            )
        }
        ((invitationScoreboard as CraftScoreboard).handle as ServerScoreboard).dataFactory().constructor.get().let {
            it.setDirty()
            it.save(
                (if (saveInWorld) server.worlds[0].worldFolder else dataFolder)
                    .resolve("data/invitationScoreboard.dat").apply { parentFile.mkdirs(); createNewFile() }
            )
        }
    }
    */

    private fun loadScoreboard(scoreboardFile: String): Scoreboard {
        logger.info("attempting to load $scoreboardFile")
        val scoreboard = server.scoreboardManager?.newScoreboard!! as CraftScoreboard
        (server.worlds.first() as CraftWorld).handle.dataStorage.computeIfAbsent((scoreboard.handle as ServerScoreboard).dataFactory(), scoreboardFile)
        /*
        NbtIo.read(
            (if (saveInWorld) server.worlds[0].worldFolder else dataFolder)
                .resolve(scoreboardFile).toPath()
        )?.let {
            ((scoreboard as CraftScoreboard).handle as ServerScoreboard).dataFactory().deserializer.apply(it.getCompound("data"))
            logger.info("should have loaded $scoreboardFile")
        }
        */
        return scoreboard
    }

    private fun isTeamOwner(sender: CommandSender): Boolean {
        return ownerScoreboard.getEntryTeam(sender.name) != null
    }

    private fun isInTeam(sender: CommandSender): Boolean {
        return mainScoreboard.getEntryTeam(sender.name) != null
    }

    private fun hasInvites(sender: CommandSender): Boolean {
        return invitationScoreboard.teams.any { it.entries.contains(sender.name) }
    }

    private fun teammatesFilter(info: SuggestionInfo<CommandSender>): Array<OfflinePlayer> {
        val team = mainScoreboard.getEntryTeam(info.sender.name)
        return server.offlinePlayers.filter { mainScoreboard.getEntryTeam(it.name!!) == team && it.name != info.sender.name }.toTypedArray()
    }

    private fun teamlessFilter(info: SuggestionInfo<CommandSender>): Array<OfflinePlayer> {
        return server.offlinePlayers.filter { mainScoreboard.getEntryTeam(it.name!!) == null }.toTypedArray()
    }

    private fun invitedTeamsFilter(info: SuggestionInfo<CommandSender>): Array<Team> {
        return invitedTeamsFilter(info.sender.name)
    }

    private fun invitedTeamsFilter(playerName: String): Array<Team> {
        return invitationScoreboard.teams.filter { it.entries.any { jt -> jt.startsWith(playerName) } }.map { mainScoreboard.getTeam(it.name)!! }.toTypedArray()
    }

    private fun isInConfirmation(sender: CommandSender, action: String): Boolean {
        return sender is Player && sender.persistentDataContainer.get(confirmationNamespacedKey, PersistentDataType.STRING) == action
    }

    private fun cancelConfirmation(sender: Player, args: CommandArguments) {
        sender.persistentDataContainer.remove(confirmationNamespacedKey)
        CommandAPI.updateRequirements(sender)
        sender.sendMessage(getTranslation("action_cancelled", sender.locale))
    }

    private fun createTeam(sender: Player, args: CommandArguments) {
        val codename = args["codename"] as String
        if (mainScoreboard.getTeam(codename) != null)
            return sender.sendMessage(getTranslation("team_same_name", sender.locale))

        mainScoreboard.registerNewTeam(codename).let {
            it.addEntry(sender.name)
            args["displayName"]?.let { displayComponents ->
                it.displayName = (displayComponents as Array<*>).joinToString("") {
                    component -> (component as BaseComponent).toLegacyText()
                }
            }
        }
        ownerScoreboard.registerNewTeam(codename).addEntry(sender.name)
        invitationScoreboard.registerNewTeam(codename)
        CommandAPI.updateRequirements(sender)
        sender.sendMessage(getTranslation("team_created", sender.locale))
    }

    private fun disbandTeamInitiation(sender: Player, args: CommandArguments) {
        sender.persistentDataContainer.set(confirmationNamespacedKey, PersistentDataType.STRING, "disband")
        CommandAPI.updateRequirements(sender)
        sender.spigot().sendMessage(
            *ComponentBuilder(getTranslation("disband_initiation", sender.locale))
                .append("\n[").color(ChatColor.GRAY)
                .append(getTranslation("yes", sender.locale)).color(ChatColor.GREEN).bold(true)
                .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/uteam disband confirm"))
                .append("/").color(ChatColor.GRAY).bold(false)
                .append(getTranslation("no", sender.locale)).color(ChatColor.RED).bold(true)
                .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/uteam disband cancel"))
                .append("]").color(ChatColor.GRAY).bold(false)
                .create()
        )
    }

    private fun disbandConfirm(sender: Player, args: CommandArguments) {
        sender.persistentDataContainer.remove(confirmationNamespacedKey)
        val team = mainScoreboard.getEntryTeam(sender.name)!!
        val teamName = team.name
        val members = team.entries
        team.unregister()
        ownerScoreboard.getTeam(teamName)?.unregister()
        invitationScoreboard.getTeam(teamName)?.unregister()
        CommandAPI.updateRequirements(sender)
        members.forEach {
            val player = server.getPlayerExact(it)
            player?.sendMessage(getTranslation("team_disbanded", player.locale))
        }
    }

    private fun kickPlayerInitiation(sender: Player, args: CommandArguments) {
        val playerName = (args["player"] as OfflinePlayer).name!!
        sender.persistentDataContainer.set(confirmationNamespacedKey, PersistentDataType.STRING, "kick")
        CommandAPI.updateRequirements(sender)
        sender.spigot().sendMessage(
            *ComponentBuilder(getTranslation("kick_initiation", sender.locale).replace("%s", playerName))
                .append("\n[").color(ChatColor.GRAY)
                .append(getTranslation("yes", sender.locale)).color(ChatColor.GREEN).bold(true)
                .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/uteam kick $playerName confirm"))
                .append("/").color(ChatColor.GRAY).bold(false)
                .append(getTranslation("no", sender.locale)).color(ChatColor.RED).bold(true)
                .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/uteam kick $playerName cancel"))
                .append("]").color(ChatColor.GRAY).bold(false)
                .create()
        )
    }

    private fun kickPlayerConfirm(sender: Player, args: CommandArguments) {
        val offPlayer = args["player"] as OfflinePlayer
        val playerName = offPlayer.name!!
        val team = mainScoreboard.getEntryTeam(playerName)
        team?.removeEntry(playerName)
        offPlayer.player?.sendMessage(getTranslation("you_kicked", offPlayer.player!!.locale))
        server.getPlayerExact(playerName)?.sendMessage(getTranslation("kicked", server.getPlayerExact(playerName)!!.locale))
        team?.entries?.forEach {
            val player = server.getPlayerExact(it)
            player?.sendMessage(getTranslation("player_kicked", player.locale).replace("%s", playerName))
        }
        sender.persistentDataContainer.remove(confirmationNamespacedKey)
        CommandAPI.updateRequirements(sender)
    }

    private fun leaveTeamInitiation(sender: Player, args: CommandArguments) {
        sender.persistentDataContainer.set(confirmationNamespacedKey, PersistentDataType.STRING, "leave")
        CommandAPI.updateRequirements(sender)
        sender.spigot().sendMessage(
            *ComponentBuilder(getTranslation("leave_initiation", sender.locale))
                .append("\n[").color(ChatColor.GRAY)
                .append(getTranslation("yes", sender.locale)).color(ChatColor.GREEN).bold(true)
                .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/uteam leave confirm"))
                .append("/").color(ChatColor.GRAY).bold(false)
                .append(getTranslation("no", sender.locale)).color(ChatColor.RED).bold(true)
                .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/uteam leave cancel"))
                .append("]").color(ChatColor.GRAY).bold(false)
                .create()
        )
    }

    private fun leaveTeamConfirm(sender: Player, args: CommandArguments) {
        val team = mainScoreboard.getEntryTeam(sender.name)
        team?.removeEntry(sender.name)
        team?.entries?.forEach {
            val player = server.getPlayerExact(it)
            player?.sendMessage(getTranslation("player_left", player.locale).replace("%s", sender.name))
        }
        sender.sendMessage(getTranslation("you_left", sender.locale))
        sender.persistentDataContainer.remove(confirmationNamespacedKey)
        CommandAPI.updateRequirements(sender)
    }

    private fun transferOwnershipInitialize(sender: Player, args: CommandArguments) {
        val playerName = (args["player"] as OfflinePlayer).name!!
        sender.persistentDataContainer.set(confirmationNamespacedKey, PersistentDataType.STRING, "transfer")
        CommandAPI.updateRequirements(sender)
        sender.spigot().sendMessage(
            *ComponentBuilder(getTranslation("transfer_initiation", sender.locale).replace("%s", playerName))
                .append("\n[").color(ChatColor.GRAY)
                .append(getTranslation("yes", sender.locale)).color(ChatColor.GREEN).bold(true)
                .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/uteam transfer $playerName confirm"))
                .append("/").color(ChatColor.GRAY).bold(false)
                .append(getTranslation("no", sender.locale)).color(ChatColor.RED).bold(true)
                .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/uteam transfer $playerName cancel"))
                .append("]").color(ChatColor.GRAY).bold(false)
                .create()
        )
    }

    private fun transferOwnershipConfirm(sender: Player, args: CommandArguments) {
        val recipient = args["player"] as OfflinePlayer
        val ownerTeam = invitationScoreboard.getEntryTeam(sender.name)
        ownerTeam?.removeEntry(sender.name)
        ownerTeam?.addEntry(recipient.name!!)
        sender.persistentDataContainer.remove(confirmationNamespacedKey)
        CommandAPI.updateRequirements(sender)
        recipient.player?.let {
            CommandAPI.updateRequirements(it)
        }
        mainScoreboard.getEntryTeam(sender.name)?.entries?.forEach {
            val player = server.getPlayerExact(it)
            player?.sendMessage(getTranslation("ownership_transferred", player.locale).replace("%s", player.name))
        }
    }

    private fun optionDisplayName(sender: Player, args: CommandArguments) {
        mainScoreboard.getEntryTeam(sender.name)?.displayName = (args["value"] as Array<*>).joinToString("") {
            component -> (component as BaseComponent).toLegacyText()
        }
    }

    private fun optionColor(sender: Player, args: CommandArguments) {
        mainScoreboard.getEntryTeam(sender.name)?.color = (args["value"] as org.bukkit.ChatColor)
    }

    private fun optionFriendlyFire(sender: Player, args: CommandArguments) {
        mainScoreboard.getEntryTeam(sender.name)?.setAllowFriendlyFire(args["value"] as Boolean)
    }

    private fun optionSeeFriendlyInvisibles(sender: Player, args: CommandArguments) {
        mainScoreboard.getEntryTeam(sender.name)?.setCanSeeFriendlyInvisibles(args["value"] as Boolean)
    }

    private fun optionNametagVisibility(sender: Player, args: CommandArguments) {
        mainScoreboard.getEntryTeam(sender.name)?.setOption(Team.Option.NAME_TAG_VISIBILITY, if ((args["value"] as String) == "always") Team.OptionStatus.ALWAYS else Team.OptionStatus.FOR_OTHER_TEAMS)
    }

    private fun optionDeathMessageVisibility(sender: Player, args: CommandArguments) {
        mainScoreboard.getEntryTeam(sender.name)?.setOption(Team.Option.DEATH_MESSAGE_VISIBILITY, if ((args["value"] as String) == "always") Team.OptionStatus.ALWAYS else Team.OptionStatus.FOR_OTHER_TEAMS)
    }

    private fun optionCollisionRule(sender: Player, args: CommandArguments) {
        mainScoreboard.getEntryTeam(sender.name)?.setOption(Team.Option.COLLISION_RULE, if ((args["value"] as String) == "always") Team.OptionStatus.ALWAYS else Team.OptionStatus.FOR_OTHER_TEAMS)
    }

    private fun optionPrefix(sender: Player, args: CommandArguments) {
        mainScoreboard.getEntryTeam(sender.name)?.prefix = (args["value"] as Array<*>).joinToString("") {
            component -> (component as BaseComponent).toLegacyText()
        }
    }

    private fun optionSuffix(sender: Player, args: CommandArguments) {
        mainScoreboard.getEntryTeam(sender.name)?.suffix = (args["value"] as Array<*>).joinToString("") {
            component -> (component as BaseComponent).toLegacyText()
        }
    }

    private fun invitePlayer(sender: Player, args: CommandArguments) {
        val player = args["player"] as OfflinePlayer
        val team = mainScoreboard.getEntryTeam(sender.name)!!
        val teamName = team.name
        invitationScoreboard.getTeam(teamName)?.addEntry("${player.name}+$teamName")
        player.player?.let { sendInvite(it, team) }
    }

    @EventHandler
    private fun onPlayerJoin(event: PlayerJoinEvent) {
        invitedTeamsFilter(event.player.name).forEach { sendInvite(event.player, it) }

        CommandAPI.updateRequirements(event.player)
    }

    private fun sendInvite(player: Player, team: Team) {
        val translation = getTranslation("invite_received", player.locale).split("%s")
        try {
            player.spigot().sendMessage(
                *ComponentBuilder(translation[0])
                    .append(team.displayName).color(team.color.asBungee())
                    .append(translation.getOrNull(1) ?: "").color(ChatColor.WHITE)
                    .append("\n[").color(ChatColor.GRAY)
                    .append(getTranslation("accept", player.locale)).color(ChatColor.GREEN).bold(true)
                    .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/uteam join ${team.name} accept"))
                    .append("/").reset().color(ChatColor.GRAY)
                    .append(getTranslation("decline", player.locale)).color(ChatColor.RED).bold(true)
                    .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/uteam join ${team.name} decline"))
                    .append("]").reset().color(ChatColor.GRAY)
                    .create()
            )
        } catch (e: Exception) {
            logger.warning(e.stackTraceToString())
            player.sendMessage(getTranslation("invite_received", player.locale).replace("%s", team.displayName) + "\n" +
                    getTranslation("accept", player.locale) + " /uteam join ${team.name} accept\n" +
                    getTranslation("decline", player.locale) + " /uteam join ${team.name} decline")
        }
    }

    private fun acceptInvite(sender: Player, args: CommandArguments) {
        val team = args["team"] as Team
        val members = team.entries
        team.addEntry(sender.name)
        invitedTeamsFilter(sender.name).forEach { invitationScoreboard.getTeam(it.name)!!.removeEntry("${sender.name}+${it.name}") }
        CommandAPI.updateRequirements(sender)
        sender.spigot().sendMessage(
            *ComponentBuilder(getTranslation("invite_accepted", sender.locale).replace("%s", team.displayName))
                .color(team.color.asBungee())
                .create()
        )
        members.forEach {
            val player = server.getPlayerExact(it)
            player?.sendMessage(getTranslation("player_joined", player.locale).replace("%s", sender.name))
        }
    }

    private fun declineInvite(sender: Player, args: CommandArguments) {
        val team = args["team"] as Team
        val lookingFor = "${sender.name}:${team.name}"
        invitationScoreboard.getEntryTeam(lookingFor)?.removeEntry(lookingFor)
        CommandAPI.updateRequirements(sender)
        sender.sendMessage(getTranslation("invite_declined", sender.locale))
        team.entries.forEach {
            val player = server.getPlayerExact(it)
            player?.sendMessage(getTranslation("player_declined", player.locale).replace("%s", sender.name))
        }
    }

    private fun listTeams(sender: Player, args: CommandArguments) {
        val teams = ownerScoreboard.teams.map { mainScoreboard.getTeam(it.name)!! }
        if (teams.isEmpty())
            return sender.sendMessage(getTranslation("no_teams", sender.locale))
        val builder = ComponentBuilder(getTranslation("teams", sender.locale).replace("%s", teams.size.toString()) + "\n")
        teams.forEach {
            val owner = ownerScoreboard.getTeam(it.name)!!.entries.first()
            val membersComponent = ComponentBuilder(owner).bold(true).append("").bold(false)
            it.entries.forEach { entry ->
                if (entry != owner)
                    membersComponent.append("\n").append(entry)
            }
            /*
            val memberList = it.entries.filterNot { entry -> entry == owner }.map { entry -> Text(entry) }.toMutableList()
            memberList.add(0, Text(ComponentBuilder(owner).bold(true).create()))
            */

            builder
                .append("[").color(it.color.asBungee())
                .append(it.displayName).event(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text(it.name)))
                .append("]").reset().color(it.color.asBungee())
                .append(" - ").reset().color(ChatColor.GRAY)
                .append(getTranslation("members", sender.locale).replace("%s", it.entries.size.toString())).color(ChatColor.WHITE)
                .event(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text(membersComponent.create())))
        }
        sender.spigot().sendMessage(*builder.create())
    }

    companion object {
        private val supportedTranslations = listOf<String>(/*"en", "es"*/) // TODO: uncomment when translations are ready
        private val translations = mutableMapOf<String, YamlConfiguration>()

        fun getTranslation(key: String, locale: String): String
        {
            return translations[locale.lowercase()]?.getString(key) ?:
            translations[locale.split('_')[0].lowercase()]?.getString(key) ?:
            translations["default"]?.getString(key) ?:
            key
        }
    }
}
