package dev.versatia.interactions.action

import dev.versatia.interactions.Interaction
import dev.versatia.interactions.config.ButtonKind
import dev.versatia.interactions.config.configName
import dev.versatia.interactions.request.ActionKind
import dev.versatia.messages.Messages
import dev.versatia.text.Argument
import dev.versatia.text.Presentable
import dev.versatia.text.presentation
import net.kyori.adventure.text.Component

/**
 * An action a plugin registered with `@InteractionAction`: [interaction] is its bean, and every
 * sentence about it comes from that plugin's [messages], under [prefix] (`interaction` unless the
 * annotation names another).
 */
internal class RegisteredAction(
    val kind: ActionKind,
    /** The name of the plugin that registered it. */
    val owner: String,
    val interaction: Interaction,
    private val messages: Messages,
    private val prefix: String,
) {
    val id get() = kind.id

    /** The `{action}` argument: the bare name, and `{action.id}` for commands. */
    fun argument() = ActionArgument(id, messages["$prefix.name"].get())

    /** The sentence saying that `{target}` is busy with this action; null when the plugin has none. */
    fun busy(vararg arguments: Argument) = messages.getOrNull("$prefix.busy")?.get(*arguments)

    /** The sentence saying that the viewer is busy with this action; null when the plugin has none. */
    fun busySelf(vararg arguments: Argument) = messages.getOrNull("$prefix.busy-self")?.get(*arguments)

    /** The plugin's own name of the button in [state], replacing VersatiaInteractions' one; null when it has none. */
    fun buttonName(state: ButtonKind, arguments: Array<Argument>) = messages.getOrNull("$prefix.button.${state.configName}.name")?.get(*arguments)

    /** The plugin's own lore of the button in [state]; null when it has none. */
    fun buttonLore(state: ButtonKind, arguments: Array<Argument>) = messages.getOrNull("$prefix.button.${state.configName}.lore")?.lines(*arguments)

    /** The plugin's own chat line for [event] of an offer (`sent`, `received`, ...), replacing VersatiaInteractions' `offer.<event>`; null when it has none. */
    fun offer(event: String) = messages.getOrNull("$prefix.offer.$event")

    /** Whether the plugin's messages name the action; checked when it registers. */
    fun hasName() = "$prefix.name" in messages

    /** The key holding the name, for error messages. */
    val nameKey get() = "$prefix.name"
}

/** An action in text: `{action}` its bare name, `{action.id}` its id. */
internal class ActionArgument(private val id: String, private val name: Component) : Presentable {
    override fun presentation() = presentation {
        component { name }
        property("id") { id }
    }
}
