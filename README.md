# VersatiaInteractions

Player-to-player requests for Paper 26.3, built on the [Versatia framework](https://versatia.dev/):
Shift + right click on a player opens a menu with one button per action other plugins register,
such as a trade or a duel. A click sends an offer; the button shows its state live in both
players' menus (available, waiting with a countdown, offered to you, busy, unavailable with the
reason, in progress), the receiver gets a clickable line in chat, and offers expire, accept each
other when both players send one, and end when a player quits, dies or walks away. A player busy
in a trade or a duel can neither send, receive nor accept another such offer; staff with
`versatia.interactions.exempt` and vanished players cannot be asked at all.

> In development: complete, not yet tried on a server; VersatiaTrade is its first action.

Documentation (experimental): [for plugin developers](https://versatia.dev/experimental/plugins/interactions)
and [for server admins](https://versatia.dev/admin/experimental/interactions).

## Installing

Put `VersatiaInteractions.jar` into `plugins/` next to VersatiaCore, which it requires. The other
plugins are optional: those providing actions, such as VersatiaTrade, add the menu's buttons, and
with the [CommandAPI](https://commandapi.dev/) plugin installed `/interact accept <player> [action]`
and `/interact decline <player> [action]` answer an offer, which is what the buttons of the
receiver's chat line run; without it the menu still works.

| Permission | Default | Lets the player |
|---|---|---|
| `versatia.interactions.menu` | everyone | open the menu with Shift + right click on a player |
| `versatia.interactions.command.interact` | everyone | answer with `/interact` |
| `versatia.interactions.exempt` | nobody | never be asked: no menu opens for them and no offer reaches them (staff) |

Players a vanish plugin hides, those the viewer cannot see, and NPCs cannot be asked either.

`plugins/VersatiaInteractions/config.yml` sets the timeout (15 s), the distance, what cancels an
offer, how the receiver hears of it (chat line, dialog or both), the window's rows, the item model
of each state of a button, the sound of each moment, and per action its slot, icons and sounds (a
voice line, say). `messages/interactions.yml` holds every text, naming the server's styles; the
sentences about one action come from its plugin's messages.

### Resource pack

The plugin ships no resource pack. Every icon is an item model key, vanilla by default
(`minecraft:clock` while waiting), so a server with a pack points `icons.waiting` at a model whose
texture is animated with an `.mcmeta` file: the client then animates the waiting button by itself,
and the server only redraws the countdown once per second. Sounds are keys too: the pack can add
voice lines and name them under `actions.<id>.sounds`.

## Adding an action

A plugin adds a button with one class. It compiles against this plugin with `compileOnly` and lists
it under `softdepend:`; on a server without VersatiaInteractions the class is skipped and never
loaded.

```kotlin
@InteractionAction("trade")
class TradeInteraction(private val trades: Trades) : Interaction {
    override fun engagedWith(player: Player) = trades.partnerOf(player)
    override fun check(from: Player, to: Player) = trades.check(from, to)
    override fun accepted(from: Player, to: Player) = trades.open(from, to)
}
```

- `engagedWith` tells who a player is engaged with in the action, however it started. An action
  is exclusive unless `@InteractionAction(exclusive = false)` says otherwise: a look at someone's
  equipment does not keep anyone busy.
- `check` answers `InteractionCheck.Allowed` or `InteractionCheck.Denied(reason)`, the reason a
  whole sentence from the plugin's messages saying whose side it is on.
- `accepted` starts the action and answers whether it started. When it cannot start now, another
  plugin denying it say, the action tells both players why and answers false: the menu then
  announces no acceptance and the request ends as unavailable.
- Every sentence about the action comes from the plugin's own messages:

```yaml title="src/main/resources/resources/messages/interaction.yml"
interaction:
  name: "Trade"                          # {action}: the bare name, written after a colon
  busy: "<warning>{target} is trading"   # optional: the reason when another player is busy with it
  button:                                # optional, per state and part: replaces the menu's default
    available:
      lore:
        - "<hint>Exchange items and money with {target}."
        - "<hint>Click to send the offer."
```

A plugin offering its action outside the menu too, such as a command, injects `Interactions` into
a bean carrying `@ConditionalOnPlugin("VersatiaInteractions")` and calls
`request(from, to, "trade")`.

## Events

Other plugins deny a request before it is sent or accepted (`InteractionRequestSendEvent`,
`InteractionRequestAcceptEvent`) and follow how it ends (accepted, declined, expired, cancelled):
every event, with its properties, in [docs/events.md](docs/events.md).

## Building

Requires JDK 25 and the Versatia artifacts in the local Maven repository (see the
[workspace scripts](https://github.com/VersatiaFramework)):

```bash
./gradlew build -x deployPlugin
./gradlew publishToMavenLocal
```

The plugin JAR is `build/libs/VersatiaInteractions-<version>.jar`; it carries InvUI, relocated (see
[docs/build.md](docs/build.md)). Copy `local.properties.example` to `local.properties` and set
`pluginsDir` to have `./gradlew build` copy it to your server.

## License

Apache License 2.0; see [LICENSE](LICENSE).
