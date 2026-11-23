package dev.versatia.interactions.config

import dev.versatia.config.ValueMapper
import dev.versatia.util.config.ConfigurationValueException
import dev.versatia.util.config.getConfigurationSectionOrThrow
import dev.versatia.util.config.getDurationOrThrow
import dev.versatia.util.config.getIntOrThrow
import dev.versatia.util.config.getStringListOrThrow
import dev.versatia.util.config.getStringOrThrow
import dev.versatia.util.config.pathOf
import kotlin.time.Duration
import net.kyori.adventure.key.InvalidKeyException
import net.kyori.adventure.key.Key
import org.bukkit.configuration.ConfigurationSection

/** Everything about one action under `actions.<id>`: its slot, and icons and sounds replacing the defaults for it alone. */
internal class ActionSettings(val slot: Int?, val icons: Map<ButtonKind, Key>, val sounds: Map<Transition, Key?>)

/** A wait that must not be zero, such as `10s`. */
internal object PositiveDuration : ValueMapper<Duration> {
    override fun map(section: ConfigurationSection, key: String) = section.getDurationOrThrow(key).also {
        if (it == Duration.ZERO) throw ConfigurationValueException("Value at \"${section.pathOf(key)}\" must be longer than zero")
    }
}

/** A distance in blocks, at least one. */
internal object Blocks : ValueMapper<Int> {
    override fun map(section: ConfigurationSection, key: String) = section.getIntOrThrow(key).also {
        if (it < 1) throw ConfigurationValueException("Value at \"${section.pathOf(key)}\" must be at least 1 block, got $it")
    }
}

/** The rows of a chest window, 1 to 6. */
internal object Rows : ValueMapper<Int> {
    override fun map(section: ConfigurationSection, key: String) = section.getIntOrThrow(key).also {
        if (it !in 1..6) throw ConfigurationValueException("Value at \"${section.pathOf(key)}\" must be between 1 and 6 rows, got $it")
    }
}

/** `requests.cancel-on`: a list of `distance`, `world-change`, `death` (and `quit`, which always applies). */
internal object CancelCauses : ValueMapper<Set<CancelCause>> {
    override fun map(section: ConfigurationSection, key: String) = section.getStringListOrThrow(key).mapTo(LinkedHashSet()) { name ->
        configConstantOrNull<CancelCause>(name)
            ?: throw ConfigurationValueException("Unknown value \"$name\" in \"${section.pathOf(key)}\"; use ${configNames<CancelCause>()}")
    }
}

/** `icons`: the item model of every state. */
internal object Icons : ValueMapper<Map<ButtonKind, Key>> {
    override fun map(section: ConfigurationSection, key: String) = section.getConfigurationSectionOrThrow(key).let { icons ->
        readKeys<ButtonKind>(icons, allowEmpty = false).also { found ->
            val missing = ButtonKind.entries.filter { it !in found }
            if (missing.isNotEmpty()) throw ConfigurationValueException("\"${icons.currentPath}\" lacks ${missing.joinToString { it.configName }}")
        }.mapValues { (_, value) -> value!! }
    }
}

/** `sounds`: the sound of every moment, `''` for none. */
internal object Sounds : ValueMapper<Map<Transition, Key?>> {
    override fun map(section: ConfigurationSection, key: String) = section.getConfigurationSectionOrThrow(key).let { sounds ->
        readKeys<Transition>(sounds, allowEmpty = true).also { found ->
            val missing = Transition.entries.filter { it !in found }
            if (missing.isNotEmpty()) throw ConfigurationValueException("\"${sounds.currentPath}\" lacks ${missing.joinToString { it.configName }}")
        }
    }
}

/** `actions`: per action id an optional `slot`, `icons` and `sounds`. */
internal object Actions : ValueMapper<Map<String, ActionSettings>> {
    override fun map(section: ConfigurationSection, key: String) = section.getConfigurationSectionOrThrow(key).let { actions ->
        actions.getKeys(false).associateWith { id ->
            val action = actions.getConfigurationSectionOrThrow(id)
            ActionSettings(
                slot = if (action.contains("slot")) action.getIntOrThrow("slot").also { checkSlot(action, it) } else null,
                icons = action.getConfigurationSection("icons")?.let { readKeys<ButtonKind>(it, allowEmpty = false).mapValues { (_, value) -> value!! } }.orEmpty(),
                sounds = action.getConfigurationSection("sounds")?.let { readKeys<Transition>(it, allowEmpty = true) }.orEmpty(),
            )
        }
    }

    private fun checkSlot(action: ConfigurationSection, slot: Int) {
        if (slot !in 0..53) throw ConfigurationValueException("Value at \"${action.pathOf("slot")}\" must be a slot from 0 to 53, got $slot")
    }
}

/** The keys of [section] as constants of [T], each with the namespaced key it holds; `''` gives null where [allowEmpty]. */
private inline fun <reified T : Enum<T>> readKeys(section: ConfigurationSection, allowEmpty: Boolean): Map<T, Key?> = section.getKeys(false).associate { name ->
    val constant = configConstantOrNull<T>(name)
        ?: throw ConfigurationValueException("Unknown entry \"$name\" in \"${section.currentPath}\"; use ${configNames<T>()}")
    val value = section.getStringOrThrow(name).trim()
    constant to when {
        value.isEmpty() -> if (allowEmpty) null else throw ConfigurationValueException("Value at \"${section.pathOf(name)}\" must not be empty")
        else -> try {
            Key.key(value)
        } catch (e: InvalidKeyException) {
            throw ConfigurationValueException("Value at \"${section.pathOf(name)}\" is not a namespaced key such as minecraft:paper: \"$value\" (${e.message})")
        }
    }
}
