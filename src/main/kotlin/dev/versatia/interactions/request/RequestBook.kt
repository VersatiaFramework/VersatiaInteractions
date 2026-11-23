package dev.versatia.interactions.request

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource

/** A request of the action [action] from the player [from] to the player [to]. */
internal data class RequestKey(val action: String, val from: UUID, val to: UUID) {
    /** The same request the other way round: sending it accepts this one. */
    val reversed get() = copy(from = to, to = from)
}

/** A pending request, waiting until [expiresAt]. */
internal class Request(val key: RequestKey, val expiresAt: ComparableTimeMark)

/** What [RequestBook.send] did. */
internal sealed interface SendOutcome {
    /** The request is pending now. */
    class Sent(val request: Request) : SendOutcome

    /** The receiver had asked the sender for the same action: that request is taken, as both want it. */
    class Mutual(val accepted: Request) : SendOutcome

    /** The same request is pending already; nothing changed, its time included. */
    class AlreadyPending(val request: Request) : SendOutcome
}

/**
 * The pending requests of the server, at most one per action and ordered pair of players, timed by
 * [time]. Bookkeeping only, without Bukkit or messages: the caller tells the players about what
 * each call returns. Changed on the server thread only; read from any, as command suggestions
 * are. See docs/requests.md.
 */
internal class RequestBook(private val time: TimeSource.WithComparableMarks) {
    private val pending = ConcurrentHashMap<RequestKey, Request>()

    /** A snapshot of the pending requests. */
    val requests get() = pending.values.toList()

    operator fun get(key: RequestKey) = pending[key]

    fun send(key: RequestKey, timeout: Duration): SendOutcome {
        require(key.from != key.to) { "A player cannot send a request to themselves: $key" }
        pending.remove(key.reversed)?.let { return SendOutcome.Mutual(it) }
        pending[key]?.let { return SendOutcome.AlreadyPending(it) }
        return SendOutcome.Sent(Request(key, time.markNow() + timeout).also { pending[key] = it })
    }

    /** Removes the request at [key], accepted, declined or withdrawn; null when none is pending. */
    fun take(key: RequestKey) = pending.remove(key)

    /** Removes and returns the requests matching [predicate]. */
    fun take(predicate: (Request) -> Boolean) = pending.values.filter(predicate).onEach { pending.remove(it.key) }

    /** Removes and returns the requests whose time is up. */
    fun takeExpired() = take { it.expiresAt.hasPassedNow() }

    /** Removes and returns every request [player] sent or received, as when they quit. */
    fun takeInvolving(player: UUID) = take { player == it.key.from || player == it.key.to }

    /** Removes and returns every request of [action], as when the plugin providing it stops. */
    fun takeAction(action: String) = take { it.key.action == action }

    /** The time [request] has left, zero once it is up. */
    fun remaining(request: Request) = (-request.expiresAt.elapsedNow()).coerceAtLeast(Duration.ZERO)

    /** The whole seconds [request] has left, rounded up, as the countdown shows them. */
    fun secondsLeft(request: Request) = remaining(request).inWholeMilliseconds.let { ((it + 999) / 1000).toInt() }
}
