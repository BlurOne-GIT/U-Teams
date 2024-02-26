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
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class UserTeams : JavaPlugin() {
    private val scoreboard get() = server.scoreboardManager?.mainScoreboard ?: throw NullPointerException("Main scoreboard not found")
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
            )

        if (optionsOptions?.getBoolean("color") != false)
            optionCommands.add(CommandAPICommand("color")
                .withRequirement(::isTeamOwner)
                .withArguments(ChatColorArgument("value"))
            )

        if (optionsOptions?.getBoolean("friendlyFire") != false)
            optionCommands.add(CommandAPICommand("friendlyFire")
                .withRequirement(::isTeamOwner)
                .withArguments(BooleanArgument("value"))
            )

        if (optionsOptions?.getBoolean("seeFriendlyInvisibles") == true)
            optionCommands.add(CommandAPICommand("seeFriendlyInvisibles")
                .withRequirement(::isTeamOwner)
                .withArguments(BooleanArgument("value"))
            )

        if (optionsOptions?.getBoolean("nametagVisibility") == true)
            optionCommands.add(CommandAPICommand("nametagVisibility")
                .withRequirement(::isTeamOwner)
                .withArguments(MultiLiteralArgument("value", "always", "hideForOwnTeam"))
            )

        if (optionsOptions?.getBoolean("deathMessageVisibility") == true)
            optionCommands.add(CommandAPICommand("deathMessageVisibility")
                .withRequirement(::isTeamOwner)
                .withArguments(MultiLiteralArgument("value", "always", "hideForOwnTeam"))
            )

        if (optionsOptions?.getBoolean("collisionRule") == true)
            optionCommands.add(CommandAPICommand("collisionRule")
                .withRequirement(::isTeamOwner)
                .withArguments(MultiLiteralArgument("value", "always", "pushOwnTeam" /* shall be mapped to actual but erroneous pushOtherTeams*/))
            )

        if (optionsOptions?.getBoolean("prefix") != false)
            optionCommands.add(CommandAPICommand("prefix")
                .withRequirement(::isTeamOwner)
                .withArguments(ChatComponentArgument("value"))
            )

        if (optionsOptions?.getBoolean("suffix") != false)
            optionCommands.add(CommandAPICommand("suffix")
                .withRequirement(::isTeamOwner)
                .withArguments(ChatComponentArgument("value"))
            )

        CommandAPICommand("uteams")
            .withAliases("uteam", "userteam", "userteams")
            .withSubcommands(
                CommandAPICommand("create") // /uteams create <codename> [displayName]
                    .withRequirement{ !isInTeam(it) }
                    .withArguments(StringArgument("codename"))
                    .withOptionalArguments(ChatComponentArgument("displayName"))
                    .executesPlayer(PlayerCommandExecutor(::createTeam)),
                CommandAPICommand("disband")
                    .withAliases("disolve")
                    .withRequirement(::isTeamOwner)
                    .executesPlayer(PlayerCommandExecutor(::disbandTeamInitiation))
                    .withSubcommands(
                        CommandAPICommand("confirm")
                            .withRequirement { isInConfirmation(it, "disband") }
                            .executesPlayer(PlayerCommandExecutor { player, args ->

                            }),
                        CommandAPICommand("cancel")
                            .withRequirement { isInConfirmation(it, "disband") }
                            .executesPlayer(PlayerCommandExecutor { player, _ -> cancelConfirmation(player) })
                    ),
                CommandAPICommand("invite")
                    .withRequirement(::isTeamOwner)
                    .withArguments(PlayerArgument("player"))
                    .executesPlayer(PlayerCommandExecutor { player, args ->

                    }),
                CommandAPICommand("kick")
                    .withRequirement(::isTeamOwner)
                    .withArguments(PlayerArgument("player"))
                    .executesPlayer(PlayerCommandExecutor { player, args ->

                    }),
                CommandAPICommand("leave")
                    .withRequirement(::isInTeam)
                    .executesPlayer(PlayerCommandExecutor { player, args ->

                    }),
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

    private fun cancelConfirmation(sender: Player) {
        sender.persistentDataContainer.remove(confirmationNamespacedKey)
        CommandAPI.updateRequirements(sender)
        sender.sendMessage(getTranslation("action_cancelled", sender.locale))
    }

    private fun createTeam(sender: Player, args: CommandArguments) {
        val codename = args["codename"] as String
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
