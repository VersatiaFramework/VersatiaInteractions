package dev.versatia.interactions.menu

import dev.versatia.interactions.request.InteractionHub
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.EquipmentSlot

/** Shift + right click on a player opens the interaction menu for them, for players with `versatia.interactions.menu`. */
internal class MenuTrigger(private val hub: InteractionHub) : Listener {
    @EventHandler(ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEntityEvent) {
        val target = event.rightClicked as? Player ?: return
        val viewer = event.player
        if (event.hand != EquipmentSlot.HAND || !viewer.isSneaking || !viewer.hasPermission(MENU_PERMISSION)) return
        if (hub.openMenu(viewer, target)) event.isCancelled = true
    }

    private companion object {
        const val MENU_PERMISSION = "versatia.interactions.menu"
    }
}
