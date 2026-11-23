package dev.versatia.interactions.event

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * A request of the action [action] (its id) that [from] sends to [to]. VersatiaInteractions fires
 * the subclasses on the server thread; listen to them, not to this class.
 */
abstract class InteractionRequestEvent(val from: Player, val to: Player, val action: String) : Event()

/**
 * An [InteractionRequestEvent] another plugin may [deny], such as a combat tag or a region rule:
 * the request then does not go ahead, and the player who clicked sees [reason], a whole sentence
 * from the denying plugin's messages.
 */
abstract class DeniableInteractionRequestEvent(from: Player, to: Player, action: String) : InteractionRequestEvent(from, to, action), Cancellable {
    private var cancelled = false

    /** Why the request was denied; null while it is not, or when a plugin cancelled it without a reason. */
    var reason: Component? = null
        private set

    fun deny(reason: Component) {
        this.reason = reason
        cancelled = true
    }

    override fun isCancelled() = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
        if (!cancel) reason = null
    }
}

/** Before [from] sends the request, once VersatiaInteractions' own rules and the action's check allow it. */
class InteractionRequestSendEvent(from: Player, to: Player, action: String) : DeniableInteractionRequestEvent(from, to, action) {
    override fun getHandlers() = handlerList

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList() = handlerList
    }
}

/** Before [to] accepts the request of [from], or before two requests sent to each other accept each other. */
class InteractionRequestAcceptEvent(from: Player, to: Player, action: String) : DeniableInteractionRequestEvent(from, to, action) {
    override fun getHandlers() = handlerList

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList() = handlerList
    }
}

/** After [to] accepted the request of [from] and the action started. */
class InteractionRequestAcceptedEvent(from: Player, to: Player, action: String) : InteractionRequestEvent(from, to, action) {
    override fun getHandlers() = handlerList

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList() = handlerList
    }
}

/** After [to] declined the request of [from]. */
class InteractionRequestDeclinedEvent(from: Player, to: Player, action: String) : InteractionRequestEvent(from, to, action) {
    override fun getHandlers() = handlerList

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList() = handlerList
    }
}

/** After the request of [from] to [to] waited its whole time without an answer. */
class InteractionRequestExpiredEvent(from: Player, to: Player, action: String) : InteractionRequestEvent(from, to, action) {
    override fun getHandlers() = handlerList

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList() = handlerList
    }
}

/**
 * After the request of [from] to [to] ended for [cause] without its action starting; a declined and
 * an expired request have events of their own.
 */
class InteractionRequestCancelledEvent(from: Player, to: Player, action: String, val cause: Cause) : InteractionRequestEvent(from, to, action) {
    enum class Cause {
        /** The sender took it back. */
        WITHDRAWN,

        /** One of the two left the server. */
        QUIT,

        /** One of the two died. */
        DEATH,

        /** The two moved farther apart than `requests.max-distance`. */
        DISTANCE,

        /** The two are in different worlds now. */
        WORLD_CHANGE,

        /** One of the two became busy, the action's check denies it now, or the action did not start once accepted. */
        UNAVAILABLE,

        /** The plugin providing the action, or VersatiaInteractions itself, stopped. */
        STOPPED,
    }

    override fun getHandlers() = handlerList

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList() = handlerList
    }
}
