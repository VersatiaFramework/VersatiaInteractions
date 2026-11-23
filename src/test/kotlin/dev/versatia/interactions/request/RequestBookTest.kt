package dev.versatia.interactions.request

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class RequestBookTest {
    private val time = TestTimeSource()
    private val book = RequestBook(time)
    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()
    private val carol = UUID.randomUUID()

    private fun trade(from: UUID, to: UUID) = RequestKey("trade", from, to)

    @Test
    fun `a sent request is pending with its timeout`() {
        val sent = assertIs<SendOutcome.Sent>(book.send(trade(alice, bob), 10.seconds))
        assertSame(sent.request, book[trade(alice, bob)])
        assertEquals(10, book.secondsLeft(sent.request))
        assertNull(book[trade(bob, alice)])
    }

    @Test
    fun `the countdown rounds up and the request expires when its time is up`() {
        val request = assertIs<SendOutcome.Sent>(book.send(trade(alice, bob), 10.seconds)).request
        time += 9.seconds + 1.milliseconds
        assertEquals(1, book.secondsLeft(request))
        assertTrue(book.takeExpired().isEmpty())
        time += 999.milliseconds
        assertEquals(listOf(trade(alice, bob)), book.takeExpired().map { it.key })
        assertNull(book[trade(alice, bob)])
    }

    @Test
    fun `the time left never goes below zero`() {
        val request = assertIs<SendOutcome.Sent>(book.send(trade(alice, bob), 5.seconds)).request
        time += 60.seconds
        assertEquals(0.seconds, book.remaining(request))
        assertEquals(0, book.secondsLeft(request))
    }

    @Test
    fun `asking someone who asked first accepts their request`() {
        book.send(trade(alice, bob), 10.seconds)
        val mutual = assertIs<SendOutcome.Mutual>(book.send(trade(bob, alice), 10.seconds))
        assertEquals(trade(alice, bob), mutual.accepted.key)
        assertTrue(book.requests.isEmpty())
    }

    @Test
    fun `sending the same request again changes nothing, its time included`() {
        val first = assertIs<SendOutcome.Sent>(book.send(trade(alice, bob), 10.seconds)).request
        time += 4.seconds
        val again = assertIs<SendOutcome.AlreadyPending>(book.send(trade(alice, bob), 10.seconds))
        assertSame(first, again.request)
        assertEquals(6, book.secondsLeft(first))
    }

    @Test
    fun `actions are independent between the same two players`() {
        book.send(trade(alice, bob), 10.seconds)
        assertIs<SendOutcome.Sent>(book.send(RequestKey("duel", bob, alice), 10.seconds))
        assertEquals(2, book.requests.size)
    }

    @Test
    fun `a player cannot ask themselves`() {
        assertFailsWith<IllegalArgumentException> { book.send(trade(alice, alice), 10.seconds) }
    }

    @Test
    fun `accepting, declining or withdrawing takes the request once`() {
        book.send(trade(alice, bob), 10.seconds)
        assertEquals(trade(alice, bob), book.take(trade(alice, bob))?.key)
        assertNull(book.take(trade(alice, bob)))
    }

    @Test
    fun `quitting cancels every request the player sent or received, and only those`() {
        book.send(trade(alice, bob), 10.seconds)
        book.send(trade(carol, alice), 10.seconds)
        book.send(trade(bob, carol), 10.seconds)
        assertEquals(setOf(trade(alice, bob), trade(carol, alice)), book.takeInvolving(alice).map { it.key }.toSet())
        assertEquals(listOf(trade(bob, carol)), book.requests.map { it.key })
    }

    @Test
    fun `a stopping plugin cancels the requests of its action only`() {
        book.send(trade(alice, bob), 10.seconds)
        book.send(RequestKey("duel", alice, bob), 10.seconds)
        assertEquals(listOf(trade(alice, bob)), book.takeAction("trade").map { it.key })
        assertEquals(listOf(RequestKey("duel", alice, bob)), book.requests.map { it.key })
    }
}
