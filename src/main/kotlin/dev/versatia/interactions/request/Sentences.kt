package dev.versatia.interactions.request

import dev.versatia.VersatiaEngine
import dev.versatia.di.LocalBean
import dev.versatia.interactions.action.ActionRegistry
import dev.versatia.messages.Messages
import dev.versatia.playerOrNull
import dev.versatia.text.arg
import java.util.UUID
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/**
 * The arguments of VersatiaInteractions' messages, and the sentences saying why something cannot
 * happen: the action's own sentences first, VersatiaInteractions' defaults otherwise.
 */
@LocalBean
internal class Sentences(private val messages: Messages, private val registry: ActionRegistry) {
    /** The five arguments every message may use; `action` is a registered action's argument or, for one that is gone, its id. */
    fun arguments(target: Any?, sender: Any?, action: Any?, seconds: Int = 0, reason: Component = Component.empty()) =
        arrayOf(arg("target", target), arg("sender", sender), arg("action", action), arg("seconds", seconds), arg("reason", reason))

    /** The sentence telling [viewer] that [player] is busy with [action]. */
    fun busy(player: UUID, action: ActionKind, viewer: Player): Component {
        val busyAction = registry[action.id]
        val self = player == viewer.uniqueId
        val arguments = arguments(VersatiaEngine.playerOrNull(player) ?: viewer, viewer, busyAction?.argument() ?: action.id)
        val own = if (self) busyAction?.busySelf(*arguments) else busyAction?.busy(*arguments)
        return own ?: messages[if (self) "busy-self" else "busy"].get(*arguments)
    }

    /** The sentence telling [viewer] what [obstacle] is. */
    fun reason(obstacle: Obstacle, viewer: Player) = when (obstacle) {
        is Obstacle.Busy -> busy(obstacle.player, obstacle.action, viewer)
        is Obstacle.Denied -> obstacle.reason
    }
}
