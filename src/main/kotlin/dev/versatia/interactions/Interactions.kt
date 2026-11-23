package dev.versatia.interactions

import org.bukkit.entity.Player

/**
 * The interactions of the server, a bean VersatiaInteractions exposes to the plugins started after
 * it. A plugin that offers its action outside the menu too, such as a `/trade <player>` command,
 * injects it into a bean carrying `@ConditionalOnPlugin("VersatiaInteractions")`. Server thread
 * only.
 */
interface Interactions {
    /** The ids of the registered actions, in the order of the menu. */
    val actionIds: List<String>

    /**
     * Sends the request of the action [id] from [from] to [to], exactly as a click on its button
     * does, with the same checks, messages and sounds; when [to] already asked [from] for it, that
     * request is accepted instead. Fails naming the registered ids when [id] is not one of them.
     */
    fun request(from: Player, to: Player, id: String)

    /** Opens the interaction menu of [viewer] for [target], as Shift + right click on [target] does. */
    fun openMenu(viewer: Player, target: Player)

    /**
     * Tells VersatiaInteractions that something the buttons of [player] depend on changed, such as
     * an engagement starting or ending: the menus showing [player] are redrawn and the waiting
     * requests checked at once. VersatiaInteractions does not poll the actions for open menus: without
     * it a menu shows the change at the next click, or at the next countdown redraw while a request waits.
     */
    fun refresh(player: Player)
}
