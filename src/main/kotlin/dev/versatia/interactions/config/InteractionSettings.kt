package dev.versatia.interactions.config

import dev.versatia.Phase
import dev.versatia.config.Value
import dev.versatia.di.LocalBean
import dev.versatia.lifecycle.AutoInvoke
import kotlin.time.Duration.Companion.seconds
import net.kyori.adventure.key.Key

/** `config.yml` of the data folder, read at start and at every reload. See docs/configuration.md. */
@LocalBean
internal class InteractionSettings {
    @Value("requests.timeout", mapper = PositiveDuration::class)
    var timeout = 10.seconds

    @Value("requests.countdown")
    var countdown = true

    @Value("requests.max-distance", mapper = Blocks::class)
    var maxDistance = 16

    @Value("requests.cancel-on", mapper = CancelCauses::class)
    var cancelOn: Set<CancelCause> = emptySet()

    @Value("receiver.notify")
    var notify = ReceiverNotice.CHAT

    @Value("window.rows", mapper = Rows::class)
    var rows = 3

    @Value("icons", mapper = Icons::class)
    var icons: Map<ButtonKind, Key> = emptyMap()

    @Value("sounds", mapper = Sounds::class)
    var sounds: Map<Transition, Key?> = emptyMap()

    @Value("actions", mapper = Actions::class)
    var actions: Map<String, ActionSettings> = emptyMap()

    /** The item model of the button of [action] in [kind]: the action's own, else the default. */
    fun icon(action: String, kind: ButtonKind) = actions[action]?.icons?.get(kind) ?: icons.getValue(kind)

    /** The sound of [transition] for [action], `null` for silence, and whether it is the action's own, a voice line say. */
    fun sound(action: String?, transition: Transition): Pair<Key?, Boolean> {
        val own = action?.let { actions[it]?.sounds }
        return if (own != null && transition in own) own[transition] to true else sounds[transition] to false
    }

    /** Whether a waiting offer ends when [cause] happens; leaving the server always ends it. */
    fun cancels(cause: CancelCause) = cause == CancelCause.QUIT || cause in cancelOn

    /** The slots of `actions` must fit the window and differ; checked at start and at every reload, so a wrong file fails at once. */
    @AutoInvoke(Phase.PLUGIN_RELOAD_OR_START)
    fun checkSlots() {
        val size = rows * 9
        actions.filterValues { (it.slot ?: 0) >= size }.keys.takeIf { it.isNotEmpty() }?.let {
            throw IllegalStateException("config.yml: the slots of actions ${it.joinToString()} do not fit a window of $rows rows (slots 0 to ${size - 1}); change window.rows or the slots")
        }
        actions.entries.filter { it.value.slot != null }.groupBy({ it.value.slot }, { it.key }).filterValues { it.size > 1 }.entries.firstOrNull()?.let { (slot, ids) ->
            throw IllegalStateException("config.yml: actions ${ids.joinToString()} share slot $slot; give each its own slot")
        }
    }
}
