package code.blurone.uteams

import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIBukkitConfig
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.*
import dev.jorel.commandapi.executors.CommandArguments
import dev.jorel.commandapi.executors.CommandExecutor
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import net.md_5.bungee.api.chat.BaseComponent
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class UserTeams : JavaPlugin() {
    private val scoreboard get() = server.scoreboardManager?.mainScoreboard ?: throw NullPointerException("Main scoreboard not found")

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
                CommandAPICommand("create")
                    .withRequirement{ !isInTeam(it) }
                    .withArguments(StringArgument("codename"))
                    .withOptionalArguments(ChatComponentArgument("displayName"))
                    .executesPlayer(PlayerCommandExecutor(::createTeam)),
                CommandAPICommand("disband")
                    .withAliases("disolve")
                    .withRequirement(::isTeamOwner)
                    .executesPlayer(PlayerCommandExecutor { player, args ->

                    }),
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
        CommandAPI.onEnable()
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
    }
}
