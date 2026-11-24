# Configuration

`InteractionSettings` reads `config.yml` of the data folder with `@Value`, at start and at every
reload; the mappers in `config/Mappers.kt` validate each value, so a wrong file fails the start or
the reload naming the key. `checkSlots` then checks that the slots under `actions` fit the window
and differ.

| Key | Mapper | Notes |
|---|---|---|
| `requests.timeout` | `PositiveDuration` | `15s`; longer than zero |
| `requests.countdown` | | the stack size of a waiting button counts the seconds down (up to 99) |
| `requests.max-distance` | `Blocks` | sending and accepting need the two this close, in the same world |
| `requests.cancel-on` | `CancelCauses` | `distance`, `world-change`, `death`; `quit` is accepted and always applies, since nobody offline can answer and the events need both players |
| `receiver.notify` | | `chat`, `dialog`, `both` |
| `window.rows` | `Rows` | 1 to 6 |
| `icons.<state>` | `Icons` | every state: `available`, `waiting`, `offered`, `busy`, `denied`, `active`; item model keys |
| `sounds.<moment>` | `Sounds` | every moment: `offered`, `received`, `accepted`, `declined`, `expired`, `cancelled`, `busy`; `''` is silence |
| `actions.<id>.slot` | `Actions` | 0 to 53, within the window |
| `actions.<id>.icons.<state>` | `Actions` | replaces `icons.<state>` for this action |
| `actions.<id>.sounds.<moment>` | `Actions` | replaces `sounds.<moment>` for this action, played as a voice line |

Versatia reads an enum value only by its constant name, so `world-change` goes through
`CancelCauses`, which compares the names written in lower case with `-`; `configName` gives the
name of a constant as the files write it.

## Buttons

A button is a paper item whose `item_model` is the state's icon, so a resource pack can give each
state a model of its own (an animated texture for `waiting`) and a server without one sees vanilla
items. Its name is the `item_name` component, never italic, and its lore loses the default italics;
colours come from the server's styles. The window redraws every button once per second, so
`{seconds}` counts down in any state that shows it, while the stack size counts only in `waiting`
and only with `requests.countdown`.
