package dev.versatia.interactions.config

import dev.versatia.util.config.ConfigurationValueException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import net.kyori.adventure.key.Key
import org.bukkit.configuration.file.YamlConfiguration

class MappersTest {
    private fun yaml(text: String) = YamlConfiguration().apply { loadFromString(text) }

    private val shipped = yaml(MappersTest::class.java.getResource("/resources/config.yml")!!.readText())

    @Test
    fun `the shipped file reads`() {
        assertEquals(15.seconds, PositiveDuration.map(shipped, "requests.timeout"))
        assertEquals(16, Blocks.map(shipped, "requests.max-distance"))
        assertEquals(setOf(CancelCause.DISTANCE, CancelCause.WORLD_CHANGE, CancelCause.DEATH), CancelCauses.map(shipped, "requests.cancel-on"))
        assertEquals(3, Rows.map(shipped, "window.rows"))
        assertEquals(Key.key("minecraft:clock"), Icons.map(shipped, "icons").getValue(ButtonKind.WAITING))
        assertEquals(Transition.entries.toSet(), Sounds.map(shipped, "sounds").keys)
        val actions = Actions.map(shipped, "actions")
        assertEquals(11, actions.getValue("trade").slot)
        assertEquals(Key.key("minecraft:emerald"), actions.getValue("trade").icons[ButtonKind.AVAILABLE])
        assertEquals(15, actions.getValue("duel").slot)
        assertTrue(actions.getValue("duel").icons.isEmpty())
    }

    @Test
    fun `cancel-on takes the names with hyphens and quit, which always applies`() {
        assertEquals(setOf(CancelCause.QUIT, CancelCause.WORLD_CHANGE), CancelCauses.map(yaml("c: [quit, World-Change]"), "c"))
        val error = assertFailsWith<ConfigurationValueException> { CancelCauses.map(yaml("c: [teleport]"), "c") }
        assertTrue("quit, distance, world-change, death" in error.message!!, error.message)
    }

    @Test
    fun `every state needs an icon, and an icon is a namespaced key`() {
        val missing = assertFailsWith<ConfigurationValueException> { Icons.map(yaml("icons: { available: 'minecraft:paper' }"), "icons") }
        assertTrue("waiting" in missing.message!!, missing.message)
        val unknown = assertFailsWith<ConfigurationValueException> { Icons.map(yaml("icons: { glowing: 'minecraft:paper' }"), "icons") }
        assertTrue("glowing" in unknown.message!!, unknown.message)
        assertFailsWith<ConfigurationValueException> { Actions.map(yaml("actions: { trade: { icons: { available: 'Not A Key' } } }"), "actions") }
    }

    @Test
    fun `an empty sound is silence, for every moment or for one action`() {
        val sounds = Sounds.map(yaml(Transition.entries.joinToString("\n", "sounds:\n") { "  ${it.configName}: ''" }), "sounds")
        assertTrue(sounds.values.all { it == null })
        val action = Actions.map(yaml("actions: { duel: { sounds: { received: '', accepted: 'myserver:voice.duel_accepted' } } }"), "actions").getValue("duel")
        assertNull(action.sounds.getValue(Transition.RECEIVED))
        assertEquals(Key.key("myserver:voice.duel_accepted"), action.sounds[Transition.ACCEPTED])
        assertNull(action.slot)
    }

    @Test
    fun `numbers out of their range fail naming the key`() {
        assertFailsWith<ConfigurationValueException> { PositiveDuration.map(yaml("t: 0s"), "t") }
        assertFailsWith<ConfigurationValueException> { Blocks.map(yaml("d: 0"), "d") }
        assertTrue("window.rows" in assertFailsWith<ConfigurationValueException> { Rows.map(yaml("window: { rows: 7 }"), "window.rows") }.message!!)
        assertFailsWith<ConfigurationValueException> { Actions.map(yaml("actions: { trade: { slot: 54 } }"), "actions") }
    }

    @Test
    fun `a slot outside the window or shared by two actions fails the start`() {
        val settings = InteractionSettings().apply { actions = mapOf("trade" to ActionSettings(27, emptyMap(), emptyMap())) }
        assertTrue("trade" in assertFailsWith<IllegalStateException> { settings.checkSlots() }.message!!)
        settings.actions = mapOf("trade" to ActionSettings(11, emptyMap(), emptyMap()), "duel" to ActionSettings(11, emptyMap(), emptyMap()))
        assertTrue("share slot 11" in assertFailsWith<IllegalStateException> { settings.checkSlots() }.message!!)
        settings.rows = 6
        settings.actions = mapOf("trade" to ActionSettings(53, emptyMap(), emptyMap()))
        settings.checkSlots()
    }
}
