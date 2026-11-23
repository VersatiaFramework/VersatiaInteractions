package dev.versatia.interactions

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/**
 * What an [InteractionAction] does. VersatiaInteractions calls it on the server thread: [engagedWith]
 * and [check] for every button it shows, before a request is sent or accepted and once per second
 * while a request waits; [accepted] once a request is accepted. Keep the first two cheap and free
 * of side effects.
 */
interface Interaction {
    /**
     * Who [player] is engaged with in this action right now, however it started: their trade partner,
     * their opponent in a duel; null when nobody. While a player is engaged in an exclusive action
     * they can neither send, receive nor accept a request of any exclusive action, and the buttons
     * of both players say who is busy; two players engaged with each other see this action's button
     * as in progress.
     */
    fun engagedWith(player: Player): Player? = null

    /** Whether [from] may ask [to] for this action now, by the action's own rules; engagements are checked already. */
    fun check(from: Player, to: Player): InteractionCheck = InteractionCheck.Allowed

    /**
     * [to] accepted the request [from] sent, or the two sent one to each other: start the action and
     * answer true. False when it cannot start now, another plugin's event keeping it from starting
     * say, once both players were told why in the action's own messages; VersatiaInteractions adds
     * nothing then.
     */
    fun accepted(from: Player, to: Player): Boolean
}

/** The answer of [Interaction.check]. */
sealed interface InteractionCheck {
    /** The request may be sent and accepted. */
    data object Allowed : InteractionCheck

    /**
     * Not possible now, for [reason]: a whole sentence from the action's messages saying whose side
     * it is on, "You cannot fight here" or "Bob cannot fight now". The button shows it, and a waiting
     * request is cancelled with it.
     */
    class Denied(val reason: Component) : InteractionCheck
}
