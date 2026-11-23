package dev.versatia.interactions.action

import dev.versatia.EnhancementContext
import dev.versatia.EnhancementProcessor
import dev.versatia.StateKey
import dev.versatia.di.BeanResolver
import dev.versatia.engine.descriptor.ClassTarget
import dev.versatia.engine.descriptor.EnhancedElement
import dev.versatia.engine.descriptor.Parameter
import dev.versatia.failure
import dev.versatia.interactions.Interaction
import dev.versatia.interactions.Interactions
import dev.versatia.interactions.InteractionsImpl
import dev.versatia.interactions.request.ActionKind
import dev.versatia.messages.Messages
import dev.versatia.targetAs

/**
 * Registers the `@InteractionAction` beans of a plugin with VersatiaInteractions once its beans
 * exist, and withdraws them when the plugin stops: the registration is an entry of the plugin's
 * state, closed when that state goes, whether the plugin stops or its start fails. Loaded by
 * VersatiaCore through the class loader of the plugin using the annotation, which reaches this
 * class through `softdepend: [VersatiaInteractions]`. See docs/actions.md.
 */
class InteractionActionEnhancementProcessor : EnhancementProcessor {
    override fun process(elements: List<EnhancedElement>, context: EnhancementContext) = context.onPluginEnhanced { register(elements, context) }

    private fun register(elements: List<EnhancedElement>, context: EnhancementContext) {
        val beans = context.state[BeanResolver.KEY]
            ?: throw context.failure("${elements.first().target.className} is an @InteractionAction, but the plugin has no bean container; extend VersatiaPlugin")
        val (interactions, messages) = beans.resolve("@InteractionAction", listOf(Parameter(Interactions::class.java.name), Parameter(Messages::class.java.name)))
        val hub = interactions as? InteractionsImpl
            ?: throw context.failure("the Interactions bean is ${interactions.javaClass.name}, not the one of VersatiaInteractions")
        val actions = elements.map { actionOf(it, beans, messages as Messages, context) }
        try {
            hub.register(actions)
        } catch (e: IllegalArgumentException) {
            throw context.failure(e.message ?: "its interactions conflict with others", e)
        }
        context.state.getOrPut(REGISTERED) { Registered(hub, context.plugin.name) }
    }

    private fun actionOf(element: EnhancedElement, beans: BeanResolver, messages: Messages, context: EnhancementContext): RegisteredAction {
        val target = element.targetAs<ClassTarget>(this)
        val interaction = beans.instanceOf(target.className) as? Interaction
            ?: throw context.failure("${target.className} is an @InteractionAction, but does not implement ${Interaction::class.java.name}")
        val arguments = element.annotation.arguments
        val id = arguments["id"] as? String ?: ""
        if (!ID.matches(id)) throw context.failure("@InteractionAction(\"$id\") on ${target.className}: an id is lower-case letters, digits, _ and -, such as trade")
        val exclusive = arguments["exclusive"] as? Boolean ?: true
        val prefix = (arguments["messages"] as? String)?.takeIf { it.isNotEmpty() } ?: DEFAULT_PREFIX
        return RegisteredAction(ActionKind(id, exclusive), context.plugin.name, interaction, messages, prefix).also {
            if (!it.hasName())
                throw context.failure("${target.className} registers the interaction $id, but the plugin's messages have no ${it.nameKey}; add the action's bare name there, such as $prefix: { name: \"Trade\" }")
        }
    }

    /** What a plugin registered, withdrawn when its state goes. */
    private class Registered(private val hub: InteractionsImpl, private val owner: String) : AutoCloseable {
        override fun close() = hub.withdraw(owner)
    }

    private companion object {
        val REGISTERED = StateKey<Registered>("interactions")
        val ID = Regex("[a-z0-9_-]+")
        const val DEFAULT_PREFIX = "interaction"
    }
}
