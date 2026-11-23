package dev.versatia.interactions.request

import dev.versatia.interactions.InteractionCheck
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import net.kyori.adventure.text.Component

class ButtonStateTest {
    private val book = RequestBook(TestTimeSource())
    private val situation = Table()
    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()
    private val carol = UUID.randomUUID()
    private val trade = ActionKind("trade", exclusive = true)
    private val duel = ActionKind("duel", exclusive = true)
    private val inspect = ActionKind("inspect", exclusive = false)

    private fun state(action: ActionKind, viewer: UUID, target: UUID) = book.stateOf(action, viewer, target, situation)

    @Test
    fun `a request waits for the sender and is offered to the receiver`() {
        book.send(RequestKey("trade", alice, bob), 10.seconds)
        assertEquals(ButtonState.Waiting(10), state(trade, alice, bob))
        assertEquals(ButtonState.Offered(10), state(trade, bob, alice))
        assertEquals(ButtonState.Available, state(trade, alice, carol))
        assertEquals(ButtonState.Available, state(duel, alice, bob))
    }

    @Test
    fun `two players engaged with each other see the action in progress`() {
        situation.engage(trade, alice, bob)
        assertEquals(ButtonState.Active, state(trade, alice, bob))
        assertEquals(ButtonState.Active, state(trade, bob, alice))
    }

    @Test
    fun `a player engaged in an exclusive action is busy for every exclusive action, on both sides`() {
        situation.engage(trade, alice, bob)
        assertEquals(ButtonState.Busy(alice, trade), state(trade, carol, alice))
        assertEquals(ButtonState.Busy(alice, trade), state(duel, carol, alice))
        assertEquals(ButtonState.Busy(alice, trade), state(duel, alice, carol))
        assertEquals(ButtonState.Busy(alice, trade), state(duel, alice, bob))
    }

    @Test
    fun `a non-exclusive action neither waits for engagements nor makes anyone busy`() {
        situation.engage(trade, alice, bob)
        assertEquals(ButtonState.Available, state(inspect, carol, alice))
        situation.engage(inspect, carol, bob)
        assertEquals(ButtonState.Active, state(inspect, carol, bob))
        assertEquals(ButtonState.Available, state(duel, carol, UUID.randomUUID()))
    }

    @Test
    fun `the action's denial is shown with its sentence, whichever side it is on`() {
        val notHere = Component.text("You cannot fight here")
        val notThere = Component.text("Bob cannot fight now")
        situation.deny(duel, alice, bob, notHere)
        situation.deny(duel, carol, bob, notThere)
        assertSame(notHere, assertIs<ButtonState.Denied>(state(duel, alice, bob)).reason)
        assertSame(notThere, assertIs<ButtonState.Denied>(state(duel, carol, bob)).reason)
    }

    @Test
    fun `an offer is judged by the check of the one who sent it`() {
        val reason = Component.text("Bob cannot fight now")
        book.send(RequestKey("duel", bob, alice), 10.seconds)
        situation.deny(duel, alice, bob, Component.text("You cannot challenge anyone"))
        assertEquals(ButtonState.Offered(10), state(duel, alice, bob))
        situation.deny(duel, bob, alice, reason)
        assertSame(reason, assertIs<ButtonState.Denied>(state(duel, alice, bob)).reason)
    }

    @Test
    fun `a pending request shows the engagement that stands in its way`() {
        book.send(RequestKey("duel", bob, alice), 10.seconds)
        situation.engage(trade, bob, carol)
        assertEquals(ButtonState.Busy(bob, trade), state(duel, alice, bob))
    }

    @Test
    fun `the check of the pending requests ends those an engagement or a denial now blocks`() {
        book.send(RequestKey("duel", bob, alice), 10.seconds)
        book.send(RequestKey("trade", alice, carol), 10.seconds)
        book.send(RequestKey("inspect", bob, carol), 10.seconds)
        book.send(RequestKey("trade", carol, UUID.randomUUID()), 10.seconds)
        situation.engage(trade, bob, UUID.randomUUID())
        situation.deny(trade, alice, carol, Component.text("You cannot trade here"))
        val kinds = listOf(trade, duel, inspect).associateBy { it.id }
        val ended = book.takeObstructed(situation, kinds::getValue)
        assertEquals(setOf(RequestKey("duel", bob, alice), RequestKey("trade", alice, carol)), ended.map { it.first.key }.toSet())
        assertEquals(Obstacle.Busy(bob, trade), ended.single { it.first.key.action == "duel" }.second)
        assertNull(book[RequestKey("duel", bob, alice)])
        assertEquals(2, book.requests.size)
        assertTrue(book.takeObstructed(situation, kinds::getValue).isEmpty())
    }

    /** Engagements and denials written into a table, as the actions would report them. */
    private class Table : Situation {
        private val engaged = HashMap<UUID, MutableList<Engagement>>()
        private val denials = HashMap<Triple<String, UUID, UUID>, Component>()

        fun engage(action: ActionKind, a: UUID, b: UUID) {
            engaged.getOrPut(a) { mutableListOf() } += Engagement(action, b)
            engaged.getOrPut(b) { mutableListOf() } += Engagement(action, a)
        }

        fun deny(action: ActionKind, from: UUID, to: UUID, reason: Component) {
            denials[Triple(action.id, from, to)] = reason
        }

        override fun engagements(player: UUID) = engaged[player].orEmpty()

        override fun check(action: ActionKind, from: UUID, to: UUID) =
            denials[Triple(action.id, from, to)]?.let(InteractionCheck::Denied) ?: InteractionCheck.Allowed
    }
}
