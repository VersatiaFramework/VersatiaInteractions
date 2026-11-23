package dev.versatia.interactions

import dev.versatia.ConditionalOnPlugin
import dev.versatia.Phase
import dev.versatia.VersatiaEnhancement
import dev.versatia.di.LocalBean

/**
 * Adds a button to the interaction menu that VersatiaInteractions opens with Shift + right click
 * on a player: `@InteractionAction("trade") class TradeInteraction(...) : Interaction`. The class
 * implements [Interaction] and is a local bean of its plugin, so its constructor is injected.
 * VersatiaInteractions owns everything around it: the request a click sends, its timeout and
 * countdown, the state of the button in both players' menus, the receiver's chat line, the events
 * and the sounds; the server sets the button's slot and icons in
 * `plugins/VersatiaInteractions/config.yml`, under `actions.<id>`.
 *
 * The annotation carries `@ConditionalOnPlugin("VersatiaInteractions")`: on a server without
 * VersatiaInteractions the class is skipped and never loaded, so the plugin lists
 * VersatiaInteractions under `softdepend:` and compiles against it with `compileOnly`.
 *
 * [id] names the action on the server: lower-case letters, digits, `_` and `-`, one plugin per id.
 * The plugin's own messages hold every sentence about the action, under [messages]:
 * `interaction.name`, required, is the action's bare name; `interaction.busy` and
 * `interaction.busy-self` say that a player is busy with it; `interaction.button.<state>.name`
 * and `.lore` replace the menu's default for one state. An [exclusive] action occupies the two
 * players while they are engaged in it ([Interaction.engagedWith]): a trade or a duel, but not a
 * look at someone's equipment. A plugin with a second action gives it a [messages] key of its own.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
@ConditionalOnPlugin("VersatiaInteractions")
@LocalBean
@VersatiaEnhancement(InteractionProcessors.ACTION, enhancementPhase = Phase.PLUGIN_START_ONLY)
annotation class InteractionAction(val id: String, val exclusive: Boolean = true, val messages: String = "interaction")

/** The processors of VersatiaInteractions, by name: the descriptor of a contributing plugin names them. */
object InteractionProcessors {
    const val ACTION = "dev.versatia.interactions.action.InteractionActionEnhancementProcessor"
}
