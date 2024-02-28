package code.blurone.uteams

import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIBukkitConfig
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.*
import dev.jorel.commandapi.executors.CommandArguments
import dev.jorel.commandapi.executors.CommandExecutor
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import org.bukkit.NamespacedKey
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scoreboard.Team
import java.io.File

class UserTeams : JavaPlugin() {
    private val scoreboard get() = server.scoreboardManager?.mainScoreboard ?: throw NullPointerException("Main scoreboard not found")
    private val offlineFunctions = config.getBoolean("offline_functions", false)
    private val confirmationNamespacedKey = NamespacedKey(this, "confirmation")

    override fun onLoad() {
        CommandAPI.onLoad(CommandAPIBukkitConfig(this)
            .verboseOutput(true) // DEBUG
            .silentLogs(false)
        )

        val optionsOptions = config.getConfigurationSection("modifiable_team_options")

        val optionCommands = mutableListOf<CommandAPICommand>() // /uteams option <subcommands> <value>

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

        CommandAPICommand("uteams")
            .withAliases("uteam", "userteam", "userteams")
            .withSubcommands(
                CommandAPICommand("create") // /uteams create <codename> [displayName]
                    .withRequirement{ !isInTeam(it) }
                    .withArguments(StringArgument("codename"))
                    .withOptionalArguments(ChatComponentArgument("displayName"))
                    .executesPlayer(PlayerCommandExecutor(::createTeam)),
                CommandAPICommand("disband") // /uteams disband
                    .withAliases("disolve")
                    .withRequirement(::isTeamOwner)
                    .executesPlayer(PlayerCommandExecutor(::disbandTeamInitiation))
                    .withSubcommands(
                        CommandAPICommand("confirm") // /uteams disband confirm
                            .withRequirement { isInConfirmation(it, "disband") }
                            .executesPlayer(PlayerCommandExecutor(::disbandConfirm)),
                        CommandAPICommand("cancel") // /uteams disband cancel
                            .withRequirement { isInConfirmation(it, "disband") }
                            .executesPlayer(PlayerCommandExecutor(::cancelConfirmation))
                    ),
                CommandAPICommand("invite")
                    .withRequirement(::isTeamOwner)
                    .withArguments(if (offlineFunctions) OfflinePlayerArgument("player") else PlayerArgument("player"))
                    .executesPlayer(PlayerCommandExecutor { player, args ->

                    }),
                CommandAPICommand("kick")
                    .withRequirement(::isTeamOwner)
                    .withArguments(if (offlineFunctions) OfflinePlayerArgument("player") else PlayerArgument("player"))
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
                CommandAPICommand("option")
                    .withRequirement(::isTeamOwner)
                    .withAliases("modify", "customize", "configure")
                    .withSubcommands(*optionCommands.toTypedArray()),
                CommandAPICommand("list")
                    .executes(CommandExecutor { sender, args ->

                    })
            )
            .register()
    }

    override fun onEnable() {
        // Plugin startup logic
        saveDefaultConfig()
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
    }

    override fun onDisable() {
        // Plugin shutdown logic
        CommandAPI.onDisable()
    }

    private fun isTeamOwner(sender: CommandSender): Boolean {
        return scoreboard.getEntryTeam("+${sender.name}") != null
    }

    private fun isInTeam(sender: CommandSender): Boolean {
        return scoreboard.getEntryTeam(sender.name) != null
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
        if (scoreboard.getTeam(codename) != null)
            return sender.sendMessage(getTranslation("team_same_name", sender.locale))

        scoreboard.registerNewTeam(codename).let {
            it.addEntry(sender.name)
            args["displayName"]?.let { displayComponents ->
                it.displayName = (displayComponents as Array<*>).joinToString("") {
                    component -> (component as BaseComponent).toLegacyText()
                }
            }
        }
        scoreboard.registerNewTeam("$codename+owner").addEntry("+${sender.name}")
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
                .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/uteams disband confirm"))
                .append("/").color(ChatColor.GRAY).bold(false)
                .append(getTranslation("no", sender.locale)).color(ChatColor.RED).bold(true)
                .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/uteams disband cancel"))
                .append("]").color(ChatColor.GRAY).bold(false)
                .create()
        )
    }

    private fun disbandConfirm(sender: Player, args: CommandArguments) {
        sender.persistentDataContainer.remove(confirmationNamespacedKey)
        val team = scoreboard.getEntryTeam(sender.name)
        val members = team?.entries
        team?.unregister()
        scoreboard.getEntryTeam("+${sender.name}")?.unregister()
        CommandAPI.updateRequirements(sender)
        members?.forEach {
            val player = server.getPlayerExact(it)
            player?.sendMessage(getTranslation("team_disbanded", player.locale))
        }
    }

    private fun kickPlayerInitiation(sender: Player, args: CommandArguments) {
        val playerName = (args["player"] as OfflinePlayer).name!!
        if (scoreboard.getEntryTeam(sender.name) != scoreboard.getEntryTeam(playerName))
            return sender.sendMessage(getTranslation("player_not_in_team", sender.locale))
        else if (playerName == sender.name)
            return sender.sendMessage(getTranslation("owner_cant_leave", sender.locale))

        sender.persistentDataContainer.set(confirmationNamespacedKey, PersistentDataType.STRING, "kick")
        CommandAPI.updateRequirements(sender)
        sender.spigot().sendMessage(
            *ComponentBuilder(getTranslation("kick_initiation", sender.locale).replace("%s", playerName))
                .append("\n[").color(ChatColor.GRAY)
                .append(getTranslation("yes", sender.locale)).color(ChatColor.GREEN).bold(true)
                .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/uteams kick $playerName confirm"))
                .append("/").color(ChatColor.GRAY).bold(false)
                .append(getTranslation("no", sender.locale)).color(ChatColor.RED).bold(true)
                .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/uteams kick $playerName cancel"))
                .append("]").color(ChatColor.GRAY).bold(false)
                .create()
        )
    }

    private fun kickPlayerConfirm(sender: Player, args: CommandArguments) {
        val offPlayer = args["player"] as OfflinePlayer
        val playerName = offPlayer.name!!
        val team = scoreboard.getEntryTeam(playerName)
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
                .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/uteams leave confirm"))
                .append("/").color(ChatColor.GRAY).bold(false)
                .append(getTranslation("no", sender.locale)).color(ChatColor.RED).bold(true)
                .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/uteams leave cancel"))
                .append("]").color(ChatColor.GRAY).bold(false)
                .create()
        )
    }

    private fun leaveTeamConfirm(sender: Player, args: CommandArguments) {
        val team = scoreboard.getEntryTeam(sender.name)
        team?.removeEntry(sender.name)
        team?.entries?.forEach {
            val player = server.getPlayerExact(it)
            player?.sendMessage(getTranslation("player_left", player.locale).replace("%s", sender.name))
        }
        sender.sendMessage(getTranslation("you_left", sender.locale))
        sender.persistentDataContainer.remove(confirmationNamespacedKey)
        CommandAPI.updateRequirements(sender)
    }

    private fun optionDisplayName(sender: Player, args: CommandArguments) {
        scoreboard.getEntryTeam(sender.name)?.displayName = (args["value"] as Array<*>).joinToString("") {
            component -> (component as BaseComponent).toLegacyText()
        }
    }

    private fun optionColor(sender: Player, args: CommandArguments) {
        scoreboard.getEntryTeam(sender.name)?.color = (args["value"] as org.bukkit.ChatColor)
    }

    private fun optionFriendlyFire(sender: Player, args: CommandArguments) {
        scoreboard.getEntryTeam(sender.name)?.setAllowFriendlyFire(args["value"] as Boolean)
    }

    private fun optionSeeFriendlyInvisibles(sender: Player, args: CommandArguments) {
        scoreboard.getEntryTeam(sender.name)?.setCanSeeFriendlyInvisibles(args["value"] as Boolean)
    }

    private fun optionNametagVisibility(sender: Player, args: CommandArguments) {
        scoreboard.getEntryTeam(sender.name)?.setOption(Team.Option.NAME_TAG_VISIBILITY, if ((args["value"] as String) == "always") Team.OptionStatus.ALWAYS else Team.OptionStatus.FOR_OTHER_TEAMS)
    }

    private fun optionDeathMessageVisibility(sender: Player, args: CommandArguments) {
        scoreboard.getEntryTeam(sender.name)?.setOption(Team.Option.DEATH_MESSAGE_VISIBILITY, if ((args["value"] as String) == "always") Team.OptionStatus.ALWAYS else Team.OptionStatus.FOR_OTHER_TEAMS)
    }

    private fun optionCollisionRule(sender: Player, args: CommandArguments) {
        scoreboard.getEntryTeam(sender.name)?.setOption(Team.Option.COLLISION_RULE, if ((args["value"] as String) == "always") Team.OptionStatus.ALWAYS else Team.OptionStatus.FOR_OTHER_TEAMS)
    }

    private fun optionPrefix(sender: Player, args: CommandArguments) {
        scoreboard.getEntryTeam(sender.name)?.prefix = (args["value"] as Array<*>).joinToString("") {
            component -> (component as BaseComponent).toLegacyText()
        }
    }

    private fun optionSuffix(sender: Player, args: CommandArguments) {
        scoreboard.getEntryTeam(sender.name)?.suffix = (args["value"] as Array<*>).joinToString("") {
            component -> (component as BaseComponent).toLegacyText()
        }
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
