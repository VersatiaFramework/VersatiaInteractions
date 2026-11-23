package dev.versatia.interactions.request

import dev.versatia.interactions.InteractionCheck
import java.util.UUID
import net.kyori.adventure.text.Component

/** An action as the bookkeeping sees it: its id, and whether it occupies the players engaged in it. */
internal data class ActionKind(val id: String, val exclusive: Boolean)

/** What a player is engaged in: [action], with the player [with]. */
internal data class Engagement(val action: ActionKind, val with: UUID)

/**
 * What VersatiaInteractions knows when it decides, read from the players and the actions at that
 * moment: the server layer asks every registered action, a test answers from a table.
 */
internal interface Situation {
    /** Every engagement of [player], at most one per action. */
    fun engagements(player: UUID): List<Engagement>

    /** The action's own answer for [from] asking [to]. */
    fun check(action: ActionKind, from: UUID, to: UUID): InteractionCheck
}

/** Why a request cannot go ahead. */
internal sealed interface Obstacle {
    /** [player] is engaged in the exclusive action [action]. */
    data class Busy(val player: UUID, val action: ActionKind) : Obstacle

    /** The action's check denies it, for [reason]. */
    class Denied(val reason: Component) : Obstacle
}

/**
 * Why [from] cannot ask [to] for [action] now, null when nothing stands in the way: for an exclusive
 * action an engagement of either player in an exclusive action, the sender's first, then the
 * action's own check.
 */
internal fun Situation.obstacle(action: ActionKind, from: UUID, to: UUID): Obstacle? {
    if (action.exclusive) (busyIn(from) ?: busyIn(to))?.let { return it }
    return when (val check = check(action, from, to)) {
        InteractionCheck.Allowed -> null
        is InteractionCheck.Denied -> Obstacle.Denied(check.reason)
    }
}

private fun Situation.busyIn(player: UUID) = engagements(player).firstOrNull { it.action.exclusive }?.let { Obstacle.Busy(player, it.action) }

/** Whether [a] and [b] are engaged in [action] with each other, as either of them reports it. */
internal fun Situation.engagedTogether(action: ActionKind, a: UUID, b: UUID) =
    engagements(a).any { it.action.id == action.id && it.with == b } || engagements(b).any { it.action.id == action.id && it.with == a }

/**
 * Removes the pending requests something now stands in the way of, returning each with its
 * obstacle; [kinds] gives the action of an id. Run once per second and right after a request is
 * accepted, so the other requests of two players who just got engaged end at once.
 */
internal fun RequestBook.takeObstructed(situation: Situation, kinds: (String) -> ActionKind): List<Pair<Request, Obstacle>> {
    val obstructed = requests.mapNotNull { request ->
        situation.obstacle(kinds(request.key.action), request.key.from, request.key.to)?.let { request to it }
    }
    obstructed.forEach { (request, _) -> take(request.key) }
    return obstructed
}
