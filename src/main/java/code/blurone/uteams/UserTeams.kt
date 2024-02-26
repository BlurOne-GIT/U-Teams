package code.blurone.uteams

import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIBukkitConfig
import org.bukkit.plugin.java.JavaPlugin

class UserTeams : JavaPlugin() {
    override fun onLoad() {
        CommandAPI.onLoad(CommandAPIBukkitConfig(this)
            .verboseOutput(true) // DEBUG
            .silentLogs(false)
        )
    }

    override fun onEnable() {
        // Plugin startup logic
        CommandAPI.onEnable()
    }

    override fun onDisable() {
        // Plugin shutdown logic
        CommandAPI.onDisable()
    }
}
