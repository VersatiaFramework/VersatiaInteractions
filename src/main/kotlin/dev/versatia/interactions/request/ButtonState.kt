package dev.versatia.interactions.request

import java.util.UUID
import net.kyori.adventure.text.Component

/** What the button of one action shows a viewer about a target. See docs/requests.md. */
internal sealed interface ButtonState {
    /** A click sends a request. */
    data object Available : ButtonState

    /** The viewer asked the target, [secondsLeft] before the request expires; a click withdraws it. */
    data class Waiting(val secondsLeft: Int) : ButtonState

    /** The target asked the viewer, [secondsLeft] before the request expires; a click accepts it. */
    data class Offered(val secondsLeft: Int) : ButtonState

    /** [player], the viewer or the target, is engaged in the exclusive action [action]. */
    data class Busy(val player: UUID, val action: ActionKind) : ButtonState

    /** The action's check denies it, for [reason]. */
    class Denied(val reason: Component) : ButtonState

    /** The two are engaged in the action with each other. */
    data object Active : ButtonState
}

/**
 * The state of the button of [action] in [viewer]'s menu for [target]: an engagement of the two
 * with each other first; then a request either way, unless something now stands in its way, which
 * the next check of the pending requests cancels anyway; then what would stand in the way of the
 * viewer asking.
 */
internal fun RequestBook.stateOf(action: ActionKind, viewer: UUID, target: UUID, situation: Situation): ButtonState {
    if (situation.engagedTogether(action, viewer, target)) return ButtonState.Active
    this[RequestKey(action.id, target, viewer)]?.let { offered ->
        return situation.obstacle(action, target, viewer)?.state() ?: ButtonState.Offered(secondsLeft(offered))
    }
    this[RequestKey(action.id, viewer, target)]?.let { waiting ->
        return situation.obstacle(action, viewer, target)?.state() ?: ButtonState.Waiting(secondsLeft(waiting))
    }
    return situation.obstacle(action, viewer, target)?.state() ?: ButtonState.Available
}

private fun Obstacle.state() = when (this) {
    is Obstacle.Busy -> ButtonState.Busy(player, action)
    is Obstacle.Denied -> ButtonState.Denied(reason)
}
