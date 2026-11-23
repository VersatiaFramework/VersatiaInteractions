package dev.versatia.interactions

import dev.versatia.di.ExposeSingleton
import dev.versatia.interactions.action.ActionRegistry
import dev.versatia.interactions.action.RegisteredAction
import dev.versatia.interactions.request.InteractionHub
import org.bukkit.entity.Player

/** The [Interactions] of the server, and the door through which the plugins' actions come and go. */
@ExposeSingleton
internal class InteractionsImpl(private val hub: InteractionHub, private val registry: ActionRegistry) : Interactions {
    override val actionIds get() = registry.all.map { it.id }

    override fun request(from: Player, to: Player, id: String) = hub.send(from, to, registry[id] ?: throw IllegalArgumentException(
        "No interaction $id is registered; registered: ${actionIds.joinToString().ifEmpty { "none" }}",
    ))

    override fun openMenu(viewer: Player, target: Player) {
        hub.openMenu(viewer, target)
    }

    override fun refresh(player: Player) = hub.refresh(player)

    /** Registers the actions of one plugin; see `InteractionActionEnhancementProcessor`. */
    fun register(actions: List<RegisteredAction>) = registry.register(actions)

    /** Withdraws the actions of [owner], which stops, ending their requests. */
    fun withdraw(owner: String) = hub.forget(registry.withdraw(owner))
}
