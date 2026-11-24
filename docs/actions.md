# Actions

A plugin contributes a button with a class annotated `@InteractionAction(id)` implementing
`Interaction`. The annotation inherits `@ConditionalOnPlugin("VersatiaInteractions")`, `@LocalBean`
and names `InteractionActionEnhancementProcessor`, which lives in this plugin. On a server without
VersatiaInteractions, VersatiaCore drops the elements of the class before it loads any processor,
so neither the processor nor the class is ever loaded; the contributing plugin lists
VersatiaInteractions under `softdepend:` so that it starts first when it is there.

## Registration

The processor runs in the contributing plugin's start (`PLUGIN_START_ONLY`), once its beans exist
(`onPluginEnhanced`). VersatiaCore loads it through the contributing plugin's class loader, which
finds it in this JAR through the soft dependency, so it is the same class this plugin defines. It:

1. resolves `Interactions` and `Messages` through the contributor's bean resolver: the exposed
   singleton `InteractionsImpl` of this plugin, and the contributor's own message bank;
2. builds a `RegisteredAction` per element: the bean instance, which must implement
   `Interaction`; the id, which must be lower-case letters, digits, `_` and `-`; `exclusive`;
   the messages prefix (`interaction` unless the annotation names another), whose `.name` key must
   exist;
3. registers them all or none: an id registered by another plugin, or twice by this one, fails the
   contributor's start naming both plugins;
4. puts an `AutoCloseable` into the contributor's state, which withdraws its actions when that
   state goes: when the plugin stops, or when its start fails after this point.

Withdrawing ends the actions' requests (`Cause.STOPPED`, no message) and closes the menus showing
them. When VersatiaInteractions itself stops, it closes every menu, ends every request and clears
the registry; at server stop the contributors, enabled after it, stop first.

## Sentences

Every sentence about an action comes from its plugin's messages, under the prefix:

| Key | Required | Used for |
|---|---|---|
| `<prefix>.name` | yes | `{action}`, the bare name, written after a colon |
| `<prefix>.busy` | no | the reason on a button when another player is busy with the action; `{target}` is that player |
| `<prefix>.busy-self` | no | the reason when the viewer is busy with it |
| `<prefix>.button.<state>.name`, `.lore` | no | replace VersatiaInteractions' `button.<state>.name` and `.lore` for this action |
| `<prefix>.offer.<event>` | no | replace VersatiaInteractions' chat line `offer.<event>` (`sent`, `received`, `accepted`, `declined`, `expired`, `cancelled`) for this action, in its own words: `{sender} offers you a trade.` |
| `<prefix>.dialog.<part>` | no | replace VersatiaInteractions' `dialog.<part>` (`title`, `body`, `accept`, `decline`) in the dialog of `receiver.notify: dialog` or `both`, for this action: `{sender} offers you a trade.` |

A sentence the plugin does not have comes from VersatiaInteractions' own messages (`busy`,
`busy-self`, `button.*`, `offer.*`, `dialog.*`), which name the action only as a bare name after a colon, so no
language has to inflect it. A plugin's own `offer.received` writes the answer buttons itself, as
VersatiaInteractions' does: `<click:run_command:'/interact accept {sender.name} {action.id}'>`. A denial is a whole sentence the action builds itself:
`InteractionCheck.Denied(messages["interaction.cannot-here"].get())`.

## Calls into an action

`engagedWith` and `check` run for every button drawn (once per second per open menu), before a
request is sent or accepted, and once per second for every waiting request; `accepted` once per
accepted request, after both players' menus closed.

`accepted` answers whether the action started, and only then does the sender read
`offer.accepted` and `InteractionRequestAcceptedEvent` fire. An action that cannot start now (a
plugin denied its own start event, say) tells both players why in its own messages and answers
false: the request ends with `InteractionRequestCancelledEvent` (`UNAVAILABLE`) and
VersatiaInteractions says nothing more.

A call that throws is logged once per action and call: a failing `engagedWith` counts as not
engaged, a failing `check` as denied with `unavailable`, and a failing `accepted` tells both
players `unavailable` and ends the request like a false answer.
