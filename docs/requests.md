# Requests

`RequestBook` keeps the pending requests of the server: at most one per action and ordered pair of
players, each with the moment it expires. It knows nothing of Bukkit or messages; the caller tells
the players about what each call returns. What the players are engaged in and what the actions'
own checks say comes through a `Situation`, which the server layer answers by asking every
registered action and a test answers from a table. Tests drive the time with a `TestTimeSource`.

## Sending

`send(key, timeout)` returns one of:

| Outcome | When | The caller |
|---|---|---|
| `Sent` | nothing was pending between the two for the action | notifies both, starts the countdown |
| `Mutual` | the receiver had asked the sender for the same action | accepts that request: `accepted(from = receiver, to = sender)` |
| `AlreadyPending` | the same request is pending | does nothing; the time is not extended |

A player cannot send a request to themselves. Before calling `send` the caller asks
`Situation.obstacle` and fires `InteractionRequestSendEvent`; before accepting, the same with
`InteractionRequestAcceptEvent`.

## Engagements and obstacles

An action reports who a player is engaged with (`Interaction.engagedWith`). An exclusive action
(a trade, a duel) occupies both players: while either of them is engaged in any exclusive action,
no request of an exclusive action can be sent, received or accepted between them and anyone. A
non-exclusive action (a look at someone's equipment) is never blocked by engagements and blocks
nothing. `obstacle(action, from, to)` returns, in this order:

1. `Busy(player, action)`: for an exclusive action, the sender's exclusive engagement, then the
   receiver's.
2. `Denied(reason)`: the action's own check, a whole sentence from its plugin.

## Ending

Every way a request ends removes it, so a request is answered at most once: `take(key)` for
accepting, declining and withdrawing, `takeExpired()` when its time is up, `takeInvolving(player)`
when a player quits or dies, `takeAction(id)` when the plugin providing the action stops, and
`takeObstructed(situation)` once per second and right after an acceptance, which ends the requests
an engagement or a denial now blocks. Distance and world are the server layer's: it ends those
requests with `take(predicate)`.

## Events, not polling

`InteractionHub` acts on events: clicks and commands, `PlayerQuitEvent`, `PlayerDeathEvent`,
`PlayerMoveEvent` when a player enters another block, `PlayerTeleportEvent` (checked a tick later,
where the player landed) and `PlayerChangedWorldEvent`; a move of a player with no waiting request
and no open menu costs a lookup. A plugin tells it of changes it cannot see, an engagement starting
or ending, with `Interactions.refresh(player)`. Only while a request waits, a check once per second
ends the requests that expired or that an engagement or another plugin's rule now blocks, and
redraws the menus of the players waiting, for the countdown; it stops when no request waits. An
open menu without a waiting request is redrawn only when something changes, and every click checks
everything again, so a rule of another plugin that changed meanwhile shows at the latest then.

## The button

`stateOf(action, viewer, target, situation)` decides what the viewer's button shows, in this order:

1. `Active`: the two are engaged in this action with each other.
2. `Offered`: the target asked the viewer; a click accepts. Judged by the target's request: an
   obstacle to the target asking the viewer shows instead.
3. `Waiting`: the viewer asked the target; a click withdraws. An obstacle shows instead.
4. `Busy` or `Denied`: what stands in the way of the viewer asking.
5. `Available`: a click sends a request.

A pending request with an obstacle shows the obstacle, because the next check of the pending
requests ends it with that reason anyway. The countdown shows whole seconds rounded up, so a
request with 9.001 s left shows 10 and one with 0.5 s left shows 1.

## Events

Every step of a request fires an event other plugins listen to, and the two before sending and
accepting can be denied: all of them in [events.md](events.md).
