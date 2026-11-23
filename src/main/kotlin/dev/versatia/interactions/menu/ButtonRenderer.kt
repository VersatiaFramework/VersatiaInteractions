package dev.versatia.interactions.menu

import dev.versatia.di.LocalBean
import dev.versatia.interactions.action.RegisteredAction
import dev.versatia.interactions.config.InteractionSettings
import dev.versatia.interactions.config.configName
import dev.versatia.interactions.config.kind
import dev.versatia.interactions.request.ButtonState
import dev.versatia.interactions.request.Sentences
import dev.versatia.messages.Messages
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemLore
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * The item of one button: the state's item model, its name as `item_name` and its lore without the
 * default italics, from the action's plugin where it has its own, else from VersatiaInteractions'
 * messages; while waiting, the stack size counts the seconds down.
 */
@LocalBean
internal class ButtonRenderer(private val settings: InteractionSettings, private val messages: Messages, private val sentences: Sentences) {
    fun render(action: RegisteredAction, state: ButtonState, viewer: Player, target: Player): ItemStack {
        val kind = state.kind
        val sender = if (state is ButtonState.Offered) target else viewer
        val arguments = sentences.arguments(target, sender, action.argument(), state.secondsLeft, reason(state, viewer))
        val name = action.buttonName(kind, arguments) ?: messages["button.${kind.configName}.name"].get(*arguments)
        val lore = action.buttonLore(kind, arguments) ?: messages.getOrNull("button.${kind.configName}.lore")?.lines(*arguments).orEmpty()
        return ItemStack.of(Material.PAPER).apply {
            setData(DataComponentTypes.ITEM_MODEL, settings.icon(action.id, kind))
            setData(DataComponentTypes.ITEM_NAME, name)
            if (lore.isNotEmpty()) setData(DataComponentTypes.LORE, ItemLore.lore(lore.map { it.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE) }))
            if (state is ButtonState.Waiting && settings.countdown) {
                setData(DataComponentTypes.MAX_STACK_SIZE, MAX_COUNT)
                amount = state.secondsLeft.coerceIn(1, MAX_COUNT)
            }
        }
    }

    private val ButtonState.secondsLeft get() = when (this) {
        is ButtonState.Waiting -> secondsLeft
        is ButtonState.Offered -> secondsLeft
        else -> 0
    }

    private fun reason(state: ButtonState, viewer: Player) = when (state) {
        is ButtonState.Busy -> sentences.busy(state.player, state.action, viewer)
        is ButtonState.Denied -> state.reason
        else -> Component.empty()
    }

    private companion object {
        /** The largest stack the game shows. */
        const val MAX_COUNT = 99
    }
}
