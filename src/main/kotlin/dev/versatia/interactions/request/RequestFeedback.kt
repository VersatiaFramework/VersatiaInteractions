package dev.versatia.interactions.request

import dev.versatia.di.LocalBean
import dev.versatia.interactions.action.RegisteredAction
import dev.versatia.interactions.config.InteractionSettings
import dev.versatia.interactions.config.ReceiverNotice
import dev.versatia.interactions.config.Transition
import dev.versatia.messages.Messages
import dev.versatia.task.PluginScope
import dev.versatia.text.Argument
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import kotlin.time.toJavaDuration
import kotlinx.coroutines.launch
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.entity.Player

/**
 * What the players read and hear when a request changes: the messages of `offer.*` for the sender,
 * the chat line or dialog for the receiver, an action's own chat line and dialog replacing the
 * defaults, and the sound of each moment, an action's own sound, a voice line say, replacing the
 * default.
 */
@LocalBean
internal class RequestFeedback(
    private val settings: InteractionSettings,
    private val messages: Messages,
    private val sentences: Sentences,
    private val scope: PluginScope,
) {
    private val timeoutSeconds get() = settings.timeout.inWholeSeconds.toInt()

    fun sent(sender: Player, receiver: Player, action: RegisteredAction) {
        offer(action, "sent").send(sender, *sentences.arguments(receiver, sender, action.argument(), timeoutSeconds))
        play(sender, action, Transition.OFFERED)
    }

    /** Tells [receiver] of the offer; [answer] is what a dialog button does, `true` accepting. */
    fun received(receiver: Player, sender: Player, action: RegisteredAction, answer: (Boolean) -> Unit) {
        val arguments = sentences.arguments(receiver, sender, action.argument(), timeoutSeconds)
        if (settings.notify != ReceiverNotice.DIALOG) offer(action, "received").send(receiver, *arguments)
        if (settings.notify != ReceiverNotice.CHAT) showDialog(receiver, action, arguments, answer)
        play(receiver, action, Transition.RECEIVED)
    }

    fun accepted(sender: Player, receiver: Player, action: RegisteredAction) {
        offer(action, "accepted").send(sender, *sentences.arguments(receiver, sender, action.argument()))
        play(sender, action, Transition.ACCEPTED)
        play(receiver, action, Transition.ACCEPTED)
    }

    fun declined(sender: Player, receiver: Player, action: RegisteredAction) {
        offer(action, "declined").send(sender, *sentences.arguments(receiver, sender, action.argument()))
        play(sender, action, Transition.DECLINED)
        play(receiver, action, Transition.DECLINED)
    }

    fun expired(sender: Player, receiver: Player, action: RegisteredAction) {
        offer(action, "expired").send(sender, *sentences.arguments(receiver, sender, action.argument()))
        play(sender, action, Transition.EXPIRED)
    }

    fun cancelled(sender: Player, receiver: Player, action: RegisteredAction) {
        offer(action, "cancelled").send(sender, *sentences.arguments(receiver, sender, action.argument()))
        play(sender, action, Transition.CANCELLED)
    }

    /** [player] tried to reach [other], who is too far away or in another world. */
    fun tooFar(player: Player, other: Player, action: RegisteredAction?) {
        messages["too-far"].send(player, *sentences.arguments(other, player, action?.argument()))
        play(player, action, Transition.BUSY)
    }

    /** [player] tried what [obstacle] stands in the way of. */
    fun blocked(player: Player, action: RegisteredAction, obstacle: Obstacle) {
        player.sendMessage(sentences.reason(obstacle, player))
        play(player, action, Transition.BUSY)
    }

    /** Another plugin stopped what [player] tried, for [reason] or none it gave. */
    fun denied(player: Player, action: RegisteredAction?, reason: Component?) {
        player.sendMessage(reason ?: messages["unavailable"].get())
        play(player, action, Transition.BUSY)
    }

    /** A click on a button that is not possible now; the button says why already. */
    fun busyClick(player: Player, action: RegisteredAction) = play(player, action, Transition.BUSY)

    /** `/interact` named [sender], a player or a name, who has no offer waiting for [player]. */
    fun noOffer(player: Player, sender: Any) {
        messages["command.no-offer"].send(player, *sentences.arguments(sender, sender, null))
        play(player, null, Transition.BUSY)
    }

    /** `/interact` named [sender], who waits for several answers, without naming the action. */
    fun choose(player: Player, sender: Player) {
        messages["command.choose"].send(player, *sentences.arguments(sender, sender, null))
        play(player, null, Transition.BUSY)
    }

    /** The message of [event] of an offer of [action]: its plugin's own, VersatiaInteractions' `offer.<event>` otherwise. */
    private fun offer(action: RegisteredAction, event: String) = action.offer(event) ?: messages["offer.$event"]

    /** The [part] of the dialog of an offer of [action]: its plugin's own, VersatiaInteractions' `dialog.<part>` otherwise. */
    private fun dialog(action: RegisteredAction, part: String) = action.dialog(part) ?: messages["dialog.$part"]

    /** The sound of [transition] for [action], an action's own one as a voice line. */
    private fun play(player: Player, action: RegisteredAction?, transition: Transition) {
        val (key, own) = settings.sound(action?.id, transition)
        player.playSound(Sound.sound(key ?: return, if (own) Sound.Source.VOICE else Sound.Source.UI, 1f, 1f))
    }

    private fun showDialog(receiver: Player, action: RegisteredAction, arguments: Array<Argument>, answer: (Boolean) -> Unit) {
        val options = ClickCallback.Options.builder().uses(1).lifetime(settings.timeout.toJavaDuration()).build()
        // A dialog click may arrive off the server thread; the scope runs the answer on it
        fun button(part: String, accept: Boolean) = ActionButton.builder(dialog(action, part).get(*arguments))
            .action(DialogAction.customClick({ _, _ -> scope.launch { answer(accept) } }, options))
            .build()
        val dialog = Dialog.create { factory ->
            factory.empty()
                .base(DialogBase.builder(dialog(action, "title").get(*arguments)).body(listOf(DialogBody.plainMessage(dialog(action, "body").get(*arguments)))).build())
                .type(DialogType.confirmation(button("accept", true), button("decline", false)))
        }
        receiver.showDialog(dialog)
    }
}
