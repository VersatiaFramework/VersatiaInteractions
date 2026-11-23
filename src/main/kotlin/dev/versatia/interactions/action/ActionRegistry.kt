package dev.versatia.interactions.action

import dev.versatia.di.LocalBean

/** The actions registered now, in the order their plugins started. Server thread only. */
@LocalBean
internal class ActionRegistry {
    private val actions = LinkedHashMap<String, RegisteredAction>()

    val all get() = actions.values.toList()

    operator fun get(id: String) = actions[id]

    /** Registers every action of one plugin, or none of them when an id is taken already. */
    fun register(added: List<RegisteredAction>) {
        added.groupBy { it.id }.filterValues { it.size > 1 }.keys.firstOrNull()?.let {
            throw IllegalArgumentException("${added.first().owner} registers the interaction $it twice; give each @InteractionAction an id of its own")
        }
        added.firstNotNullOfOrNull { action -> actions[action.id]?.let { action to it } }?.let { (action, taken) ->
            throw IllegalArgumentException("${action.owner} cannot register the interaction ${action.id}: ${taken.owner} registered it already")
        }
        added.forEach { actions[it.id] = it }
    }

    /** Removes the actions of [owner], returning them. */
    fun withdraw(owner: String) = actions.values.filter { it.owner == owner }.onEach { actions.remove(it.id) }

    /** Removes every action, returning them; when VersatiaInteractions stops. */
    fun clear() = all.also { actions.clear() }
}
