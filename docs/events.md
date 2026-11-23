# Events

Every event VersatiaInteractions fires, all in the package `dev.versatia.interactions.event` and on
the server thread: the life of a request, from sending to its answer. Each names `from`, who sent
the request, `to`, who received it, and `action`, the id of the action (`trade`). An event fired
before something happens is named in the present and may be denied; one fired after it is named in
the past and only informs.

| Event | When | A listener can |
|---|---|---|
| [`InteractionRequestSendEvent`](#interactionrequestsendevent) | before a request is sent | deny it, with a reason the sender reads |
| [`InteractionRequestAcceptEvent`](#interactionrequestacceptevent) | before a request is accepted | deny it, with a reason the accepting player reads |
| [`InteractionRequestAcceptedEvent`](#interactionrequestacceptedevent) | after a request was accepted and the action started | follow it |
| [`InteractionRequestDeclinedEvent`](#interactionrequestdeclinedevent) | after the receiver declined | follow it |
| [`InteractionRequestExpiredEvent`](#interactionrequestexpiredevent) | after a request waited its whole time | follow it |
| [`InteractionRequestCancelledEvent`](#interactionrequestcancelledevent) | after a request ended otherwise | read why (`cause`) |

A request that was sent ends with exactly one of the last four.

## Listening

A plugin listening to these events compiles against VersatiaInteractions
(`compileOnly("dev.versatia:VersatiaInteractions:<version>")`) and names it in `plugin.yml`: under
`depend` when it needs it, under `softdepend` when it runs without it too. A Versatia plugin of the
second kind marks the listener `@ConditionalOnPlugin("VersatiaInteractions")`, so that the
listener, which names VersatiaInteractions' classes, exists only on a server running it.

A deniable event takes a whole sentence from the denying plugin's messages; one cancelled without
a reason (`isCancelled = true`) shows VersatiaInteractions' own `unavailable` sentence.

```kotlin
@ConditionalOnPlugin("VersatiaInteractions")
class ArenaRules(private val arenas: Arenas, private val messages: Messages) : Listener {
    @EventHandler
    fun onSend(event: InteractionRequestSendEvent) {
        if (event.action == "trade" && arenas.isInside(event.from)) event.deny(messages["arena.no-trade"].get())
    }
}
```

## InteractionRequestSendEvent

Before `from` sends a request of `action` to `to`, once VersatiaInteractions' own rules (exempt
players, distance, engagements) and the action's `check` allow it. Denied, the request is not sent
and `from` reads the reason. When `to` had asked `from` for the same action already, the click
accepts that request instead, and `InteractionRequestAcceptEvent` fires in place of this one.

## InteractionRequestAcceptEvent

Before `to` accepts the request `from` sent, by a click in the menu, `/interact accept` or the
dialog, and before two requests sent to each other accept each other (`to` is then the player who
clicked last). Denied, the request keeps waiting and the accepting player reads the reason.

## InteractionRequestAcceptedEvent

After `to` accepted the request of `from` and the action started: its `accepted` answered true (a
trade window is open, say). An action that did not start ends the request with
[`InteractionRequestCancelledEvent`](#interactionrequestcancelledevent) (`UNAVAILABLE`) instead.

## InteractionRequestDeclinedEvent

After `to` declined the request of `from`.

## InteractionRequestExpiredEvent

After the request waited its whole time (`requests.timeout`) without an answer.

## InteractionRequestCancelledEvent

After the request ended without its action starting, neither declined nor expired, for `cause`:

| `cause` | When |
|---|---|
| `WITHDRAWN` | `from` clicked the waiting button, taking the request back |
| `QUIT` | one of the two left the server |
| `DEATH` | one of the two died, while `requests.cancel-on` lists `death` |
| `DISTANCE` | the two moved farther apart than `requests.max-distance`, while `cancel-on` lists `distance` |
| `WORLD_CHANGE` | the two are in different worlds, while `cancel-on` lists `world-change` |
| `UNAVAILABLE` | one of the two became busy, the action's check denies the request now, or the action did not start once accepted (its `accepted` answered false or failed) |
| `STOPPED` | the plugin of the action, or VersatiaInteractions, stopped |
