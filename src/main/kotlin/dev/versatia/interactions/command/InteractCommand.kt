package dev.versatia.interactions.command

import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.kotlindsl.commandAPICommand
import dev.jorel.commandapi.kotlindsl.playerExecutor
import dev.jorel.commandapi.kotlindsl.stringArgument
import dev.jorel.commandapi.kotlindsl.subcommand
import dev.versatia.ConditionalOnPlugin
import dev.versatia.Phase
import dev.versatia.interactions.InteractionsPlugin
import dev.versatia.interactions.request.InteractionHub
import dev.versatia.lifecycle.AutoInvoke
import org.bukkit.entity.Player

/**
 * `/interact accept <player> [action]` and `/interact decline <player> [action]`: the answers the
 * receiver's chat line runs, typed by hand as well; without the action, the only offer waiting
 * from that player. Registered only while the CommandAPI plugin is on the server.
 */
@ConditionalOnPlugin("CommandAPI")
internal object InteractCommand {
    const val NAME = "interact"
    const val PERMISSION = "versatia.interactions.command.interact"

    @AutoInvoke(Phase.PLUGIN_START_ONLY)
    fun register(plugin: InteractionsPlugin, hub: InteractionHub) = commandAPICommand(NAME, plugin) {
        withPermission(PERMISSION)
        for (answer in listOf("accept", "decline")) subcommand(answer) {
            stringArgument("player") {
                replaceSuggestions(ArgumentSuggestions.stringCollection { info -> (info.sender as? Player)?.let(hub::waitingSenders).orEmpty() })
            }
            stringArgument("action", optional = true) {
                replaceSuggestions(ArgumentSuggestions.stringCollection { info ->
                    val sender = info.previousArgs["player"] as? String
                    if (sender == null) emptyList() else (info.sender as? Player)?.let { hub.waitingActions(it, sender) }.orEmpty()
                })
            }
            playerExecutor { player, args -> hub.answer(player, args["player"] as String, args["action"] as String?, yes = answer == "accept") }
        }
    }

    @AutoInvoke(Phase.PLUGIN_STOP)
    fun unregister() = CommandAPI.unregister(NAME, true)
}
