package dev.versatia.interactions.menu

import dev.versatia.di.LocalBean
import dev.versatia.interactions.action.RegisteredAction
import dev.versatia.interactions.config.InteractionSettings
import dev.versatia.interactions.request.ButtonState
import dev.versatia.interactions.request.Sentences
import dev.versatia.messages.Messages
import java.util.UUID
import java.util.logging.Logger
import org.bukkit.entity.Player
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemWrapper
import xyz.xenondevs.invui.window.Window

/**
 * The interaction menus open now, one per viewer, each about one target. A button asks for its
 * state whenever it is drawn, so [refresh] after a change redraws both players' menus, and once
 * per second while a request waits redraws its countdown. Server thread only.
 */
@LocalBean
internal class MenuWindows(
    private val settings: InteractionSettings,
    private val renderer: ButtonRenderer,
    private val messages: Messages,
    private val sentences: Sentences,
    private val logger: Logger,
) {
    private val open = HashMap<UUID, OpenMenu>()
    private val unplaced = HashSet<String>()

    private class OpenMenu(val window: Window, val viewer: Player, val target: Player, val actions: Set<String>, val buttons: List<Item>)

    /** Opens [viewer]'s menu for [target] with a button per action; [state] draws a button, [click] answers a click on it. */
    fun open(viewer: Player, target: Player, actions: List<RegisteredAction>, state: (RegisteredAction) -> ButtonState, click: (RegisteredAction) -> Unit) {
        val gui = Gui.empty(9, settings.rows)
        val slots = assignSlots(actions.map { it.id }, settings.actions.mapNotNull { (id, action) -> action.slot?.let { id to it } }.toMap(), gui.size)
        reportUnplaced(actions.filter { it.id !in slots })
        val buttons = actions.filter { it.id in slots }.map { action ->
            Item.builder()
                .setItemProvider { _: Player -> ItemWrapper(renderer.render(action, state(action), viewer, target)) }
                .addClickHandler { _, _ -> click(action) }
                .build()
                .also { gui.setItem(slots.getValue(action.id), it) }
        }
        val window = Window.builder()
            .setUpperGui(gui)
            .setTitle(messages["window.title"].get(*sentences.arguments(target, viewer, null)))
            .setViewer(viewer)
            .build()
        val menu = OpenMenu(window, viewer, target, slots.keys, buttons)
        window.addCloseHandler { open.remove(viewer.uniqueId, menu) }
        open[viewer.uniqueId] = menu
        window.open()
    }

    /** Redraws the menus whose viewer or target is one of [players]. */
    fun refresh(vararg players: UUID) = open.values.filter { it.viewer.uniqueId in players || it.target.uniqueId in players }.forEach(::redraw)

    /** Whether a menu is open with [player] as its viewer or its target. */
    fun involves(player: UUID) = open.values.any { it.viewer.uniqueId == player || it.target.uniqueId == player }

    /** Closes the menus of [viewers], before an action opens a window of its own for them. */
    fun closeFor(vararg viewers: UUID) = viewers.mapNotNull(open::get).forEach { it.window.close() }

    fun closeIf(predicate: (viewer: Player, target: Player) -> Boolean) = open.values.filter { predicate(it.viewer, it.target) }.forEach { it.window.close() }

    /** Closes the menus showing a button of one of [actions], whose plugin stopped. */
    fun closeShowing(actions: Set<String>) = open.values.filter { menu -> menu.actions.any { it in actions } }.forEach { it.window.close() }

    fun closeAll() = open.values.toList().forEach { it.window.close() }

    private fun redraw(menu: OpenMenu) = menu.buttons.forEach(Item::notifyWindows)

    private fun reportUnplaced(actions: List<RegisteredAction>) = actions.filter { unplaced.add(it.id) }.forEach {
        logger.warning("The interaction ${it.id} of ${it.owner} has no free slot in a window of ${settings.rows} rows; raise window.rows or give it a slot in config.yml")
    }
}

/**
 * The slot of each of [ids] in a window of [size] slots: its [configured] one where it fits, else the
 * first slot nothing else takes, in order; ids beyond the free slots get none.
 */
internal fun assignSlots(ids: List<String>, configured: Map<String, Int>, size: Int): Map<String, Int> {
    val fixed = ids.mapNotNull { id -> configured[id]?.takeIf { it < size }?.let { id to it } }.toMap()
    val free = (0 until size).filter { it !in fixed.values }.iterator()
    val rest = ids.filter { it !in fixed }.mapNotNull { id -> if (free.hasNext()) id to free.next() else null }
    return fixed + rest
}
