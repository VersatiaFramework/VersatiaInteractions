package dev.versatia.interactions.request

import dev.versatia.Phase
import dev.versatia.VersatiaEngine
import dev.versatia.di.LocalBean
import dev.versatia.interactions.InteractionCheck
import dev.versatia.interactions.action.ActionRegistry
import dev.versatia.interactions.action.RegisteredAction
import dev.versatia.interactions.config.CancelCause
import dev.versatia.interactions.config.InteractionSettings
import dev.versatia.interactions.event.InteractionRequestAcceptEvent
import dev.versatia.interactions.event.InteractionRequestAcceptedEvent
import dev.versatia.interactions.event.InteractionRequestCancelledEvent
import dev.versatia.interactions.event.InteractionRequestCancelledEvent.Cause
import dev.versatia.interactions.event.InteractionRequestDeclinedEvent
import dev.versatia.interactions.event.InteractionRequestExpiredEvent
import dev.versatia.interactions.event.InteractionRequestSendEvent
import dev.versatia.interactions.menu.MenuWindows
import dev.versatia.lifecycle.AutoInvoke
import dev.versatia.messages.Messages
import dev.versatia.playerOrNull
import dev.versatia.task.PluginScope
import dev.versatia.task.delayTicks
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent

/**
 * The requests of the server and everything around them: the rules VersatiaInteractions applies
 * itself (exempt players, the distance), the actions' engagements and checks, the events other
 * plugins may deny, what the players read and hear, and the menus to redraw. Events drive it:
 * clicks, quitting, dying, moving and teleporting act at once, and a plugin tells it of other changes
 * with [refresh]; only while a request waits, a check once per second ends those that expired or
 * became impossible and redraws the countdowns. Server thread only. See docs/requests.md.
 */
@LocalBean
internal class InteractionHub(
    private val settings: InteractionSettings,
    private val registry: ActionRegistry,
    private val feedback: RequestFeedback,
    private val menus: MenuWindows,
    private val messages: Messages,
    private val scope: PluginScope,
    private val logger: Logger,
) : Listener {
    private val book = RequestBook(TimeSource.Monotonic)
    private val reportedFailures = HashSet<String>()

    /** What the actions report now, each call guarded so a failing action cannot break the others. */
    private val situation = object : Situation {
        override fun engagements(player: UUID): List<Engagement> {
            val online = VersatiaEngine.playerOrNull(player) ?: return emptyList()
            return registry.all.mapNotNull { action ->
                guarded(action, "engagedWith") { action.interaction.engagedWith(online) }?.let { Engagement(action.kind, it.uniqueId) }
            }
        }

        override fun check(action: ActionKind, from: UUID, to: UUID): InteractionCheck {
            val registered = registry[action.id] ?: return InteractionCheck.Allowed
            val sender = VersatiaEngine.playerOrNull(from) ?: return InteractionCheck.Allowed
            val receiver = VersatiaEngine.playerOrNull(to) ?: return InteractionCheck.Allowed
            return guarded(registered, "check") { registered.interaction.check(sender, receiver) } ?: InteractionCheck.Denied(messages["unavailable"].get())
        }
    }

    /** The once-per-second check of the waiting requests; runs only while one waits. */
    private var ticker: Job? = null

    @AutoInvoke(Phase.PLUGIN_STOP)
    fun stop() {
        menus.closeAll()
        book.take { true }.forEach { cancelled(it, Cause.STOPPED) }
        registry.clear()
    }

    /** Opens [viewer]'s menu for [target]; false when there is nothing to open, so the click does what it would do anyway. */
    fun openMenu(viewer: Player, target: Player): Boolean {
        val actions = registry.all
        if (actions.isEmpty() || exempt(viewer, target)) return false
        if (withinReach(viewer, target)) menus.open(viewer, target, actions, { stateOf(it, viewer, target) }) { clicked(viewer, target, it) }
        else feedback.tooFar(viewer, target, null)
        return true
    }

    /** [from] asks [to] for [action], or accepts [to]'s request of it when there is one. */
    fun send(from: Player, to: Player, action: RegisteredAction) {
        if (exempt(from, to)) return feedback.denied(from, action, null)
        if (!withinReach(from, to)) return feedback.tooFar(from, to, action)
        val key = keyOf(action, from, to)
        val mutual = book[key.reversed] != null
        val obstacle = if (mutual) situation.obstacle(action.kind, to.uniqueId, from.uniqueId) else situation.obstacle(action.kind, from.uniqueId, to.uniqueId)
        if (obstacle != null) return feedback.blocked(from, action, obstacle)
        val event = if (mutual) InteractionRequestAcceptEvent(to, from, action.id) else InteractionRequestSendEvent(from, to, action.id)
        if (!event.callEvent()) return feedback.denied(from, action, event.reason)
        when (val outcome = book.send(key, settings.timeout)) {
            is SendOutcome.Sent -> {
                feedback.sent(from, to, action)
                feedback.received(to, from, action) { yes -> if (yes) accept(from, to, action) else decline(from, to, action) }
                menus.refresh(from.uniqueId, to.uniqueId)
                ensureTicking()
            }
            is SendOutcome.Mutual -> carryOut(to, from, action)
            is SendOutcome.AlreadyPending -> Unit
        }
    }

    /** [receiver] accepts the request [sender] sent them of [action]. */
    fun accept(sender: Player, receiver: Player, action: RegisteredAction) {
        val key = keyOf(action, sender, receiver)
        if (book[key] == null) return
        if (!withinReach(sender, receiver)) return feedback.tooFar(receiver, sender, action)
        situation.obstacle(action.kind, sender.uniqueId, receiver.uniqueId)?.let { obstacle ->
            book.take(key)?.let { cancelled(it, Cause.UNAVAILABLE) }
            return feedback.blocked(receiver, action, obstacle)
        }
        val event = InteractionRequestAcceptEvent(sender, receiver, action.id)
        if (!event.callEvent()) return feedback.denied(receiver, action, event.reason)
        if (book.take(key) != null) carryOut(sender, receiver, action)
    }

    /** [receiver] declines the request [sender] sent them of [action]. */
    fun decline(sender: Player, receiver: Player, action: RegisteredAction) {
        book.take(keyOf(action, sender, receiver)) ?: return
        feedback.declined(sender, receiver, action)
        InteractionRequestDeclinedEvent(sender, receiver, action.id).callEvent()
        menus.refresh(sender.uniqueId, receiver.uniqueId)
    }

    /** [receiver] answers by command the request of the player named [senderName]: of [actionId], or the only one waiting. */
    fun answer(receiver: Player, senderName: String, actionId: String?, yes: Boolean) {
        val sender = VersatiaEngine.playerOrNull(senderName)?.takeIf(receiver::canSee) ?: return feedback.noOffer(receiver, senderName)
        val waiting = book.requests.filter { it.key.from == sender.uniqueId && it.key.to == receiver.uniqueId && (actionId == null || it.key.action == actionId) }
        if (waiting.size > 1) return feedback.choose(receiver, sender)
        val action = waiting.singleOrNull()?.let { registry[it.key.action] } ?: return feedback.noOffer(receiver, sender)
        if (yes) accept(sender, receiver, action) else decline(sender, receiver, action)
    }

    /** The names of the players whose requests wait for [receiver]'s answer, for command suggestions. */
    fun waitingSenders(receiver: Player) = book.requests.filter { it.key.to == receiver.uniqueId }.mapNotNull { VersatiaEngine.playerOrNull(it.key.from)?.name }.distinct()

    /** The ids of the actions [sender] waits for [receiver]'s answer to. */
    fun waitingActions(receiver: Player, sender: String) =
        book.requests.filter { it.key.to == receiver.uniqueId && VersatiaEngine.playerOrNull(it.key.from)?.name.equals(sender, ignoreCase = true) }.map { it.key.action }

    /**
     * Something [player]'s buttons depend on changed, an engagement starting or ending say: the waiting
     * requests are checked and the menus showing [player] redrawn at once.
     */
    fun refresh(player: Player) {
        endObstructed()
        menus.refresh(player.uniqueId)
    }

    /** Ends the requests of [actions], whose plugin stopped, and closes the menus showing them. */
    fun forget(actions: List<RegisteredAction>) {
        val ids = actions.map { it.id }.toSet()
        if (ids.isEmpty()) return
        menus.closeShowing(ids)
        ids.forEach { id -> book.takeAction(id).forEach { cancelled(it, Cause.STOPPED) } }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        book.takeInvolving(event.player.uniqueId).forEach { cancelled(it, Cause.QUIT, event.player) }
        menus.closeIf { _, target -> target == event.player }
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        if (settings.cancels(CancelCause.DEATH)) book.takeInvolving(event.player.uniqueId).forEach { cancelled(it, Cause.DEATH) }
    }

    @EventHandler(ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        if (event.hasChangedBlock()) moved(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        // The event comes before the teleport; the distance counts from where the player lands
        scope.launch {
            delayTicks(1)
            if (event.player.isOnline) moved(event.player)
        }
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) = moved(event.player)

    /** [player] moved: their requests and the menus about them end when the two are out of reach. */
    private fun moved(player: Player) {
        val id = player.uniqueId
        if (book.requests.any { id == it.key.from || id == it.key.to }) endOutOfReach()
        if (menus.involves(id)) menus.closeIf { viewer, target -> player in listOf(viewer, target) && !withinReach(viewer, target) }
    }

    private fun stateOf(action: RegisteredAction, viewer: Player, target: Player) = book.stateOf(action.kind, viewer.uniqueId, target.uniqueId, situation)

    private fun clicked(viewer: Player, target: Player, action: RegisteredAction) {
        if (registry[action.id] !== action) return
        when (stateOf(action, viewer, target)) {
            ButtonState.Available -> send(viewer, target, action)
            is ButtonState.Waiting -> book.take(keyOf(action, viewer, target))?.let { cancelled(it, Cause.WITHDRAWN) }
            is ButtonState.Offered -> accept(target, viewer, action)
            is ButtonState.Busy, is ButtonState.Denied -> feedback.busyClick(viewer, action)
            ButtonState.Active -> Unit
        }
    }

    /**
     * Starts [action] for an accepted request: the two players' menus close first, as the action may
     * open a window of its own. An action that did not start told the players why; one that failed
     * did not, so they read `unavailable`.
     */
    private fun carryOut(sender: Player, receiver: Player, action: RegisteredAction) {
        menus.closeFor(sender.uniqueId, receiver.uniqueId)
        val started = guarded(action, "accepted") { action.interaction.accepted(sender, receiver) }
        if (started == true) {
            feedback.accepted(sender, receiver, action)
            InteractionRequestAcceptedEvent(sender, receiver, action.id).callEvent()
        } else {
            if (started == null) listOf(sender, receiver).forEach { feedback.denied(it, action, null) }
            InteractionRequestCancelledEvent(sender, receiver, action.id, Cause.UNAVAILABLE).callEvent()
        }
        endObstructed()
        menus.refresh(sender.uniqueId, receiver.uniqueId)
    }

    /** Starts the once-per-second check of the waiting requests, unless it runs; it stops once none waits. */
    private fun ensureTicking() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (book.requests.isNotEmpty()) {
                delay(1.seconds)
                tick()
            }
        }
    }

    /**
     * Ends the requests that expired or that an engagement or another plugin's rule now blocks, and
     * redraws the countdowns; distance, quitting and dying end requests by their events instead.
     */
    private fun tick() {
        book.takeExpired().forEach(::expired)
        endObstructed()
        menus.refresh(*book.requests.flatMap { listOf(it.key.from, it.key.to) }.distinct().toTypedArray())
    }

    private fun expired(request: Request) {
        val sender = VersatiaEngine.playerOrNull(request.key.from)
        val receiver = VersatiaEngine.playerOrNull(request.key.to)
        if (sender != null && receiver != null) {
            registry[request.key.action]?.let { feedback.expired(sender, receiver, it) }
            InteractionRequestExpiredEvent(sender, receiver, request.key.action).callEvent()
        }
        menus.refresh(request.key.from, request.key.to)
    }

    /** Ends a request for [cause]; [leaving] is a player who quits, whom the server no longer lists. */
    private fun cancelled(request: Request, cause: Cause, leaving: Player? = null) {
        fun player(id: UUID) = leaving?.takeIf { it.uniqueId == id } ?: VersatiaEngine.playerOrNull(id)
        val sender = player(request.key.from)
        val receiver = player(request.key.to)
        if (sender != null && receiver != null) {
            InteractionRequestCancelledEvent(sender, receiver, request.key.action, cause).callEvent()
            val action = registry[request.key.action]
            if (action != null && cause != Cause.WITHDRAWN && cause != Cause.STOPPED && sender != leaving) feedback.cancelled(sender, receiver, action)
        }
        menus.refresh(request.key.from, request.key.to)
    }

    private fun endObstructed() = book.takeObstructed(situation) { id -> registry[id]?.kind ?: ActionKind(id, exclusive = false) }
        .forEach { (request, _) -> cancelled(request, Cause.UNAVAILABLE) }

    private fun endOutOfReach() = book.requests.mapNotNull { request -> reachCause(request)?.let { request to it } }
        .forEach { (request, cause) -> book.take(request.key)?.let { cancelled(it, cause) } }

    private fun reachCause(request: Request): Cause? {
        val sender = VersatiaEngine.playerOrNull(request.key.from) ?: return Cause.QUIT
        val receiver = VersatiaEngine.playerOrNull(request.key.to) ?: return Cause.QUIT
        return when {
            sender.world != receiver.world -> Cause.WORLD_CHANGE.takeIf { settings.cancels(CancelCause.WORLD_CHANGE) }
            !withinReach(sender, receiver) -> Cause.DISTANCE.takeIf { settings.cancels(CancelCause.DISTANCE) }
            else -> null
        }
    }

    private fun withinReach(a: Player, b: Player) = a.world == b.world && a.location.distanceSquared(b.location) <= settings.maxDistance.toDouble() * settings.maxDistance

    /** Staff with the exemption, players the viewer cannot see or a vanish plugin hides, and NPCs cannot be asked. */
    private fun exempt(viewer: Player, target: Player) = viewer == target || target.hasPermission(EXEMPT_PERMISSION) || !viewer.canSee(target) ||
        target.hasMetadata("NPC") || target.getMetadata("vanished").any { it.asBoolean() }

    private fun keyOf(action: RegisteredAction, from: Player, to: Player) = RequestKey(action.id, from.uniqueId, to.uniqueId)

    /** Runs a call into an action's plugin; a failure is logged once per action and call, and gives null. */
    private inline fun <T> guarded(action: RegisteredAction, call: String, block: () -> T): T? = try {
        block()
    } catch (e: RuntimeException) {
        if (reportedFailures.add("${action.id}.$call")) logger.log(Level.SEVERE, "The interaction ${action.id} of ${action.owner} failed in $call(); later failures of it are not logged", e)
        null
    }

    private companion object {
        const val EXEMPT_PERMISSION = "versatia.interactions.exempt"
    }
}
