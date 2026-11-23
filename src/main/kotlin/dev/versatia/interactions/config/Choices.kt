package dev.versatia.interactions.config

import dev.versatia.interactions.request.ButtonState

/** The name of a constant in the configuration and the messages: lower case, words joined by `-`. */
internal val Enum<*>.configName get() = name.lowercase().replace('_', '-')

/** The constant of [T] named [name] in the configuration, `world-change` for `WORLD_CHANGE`; null when none is. */
internal inline fun <reified T : Enum<T>> configConstantOrNull(name: String) = enumValues<T>().firstOrNull { it.configName == name.trim().lowercase() }

/** The names the configuration accepts for [T], for error messages. */
internal inline fun <reified T : Enum<T>> configNames() = enumValues<T>().joinToString { it.configName }

/** The states of a button, as `icons` and the messages under `button` name them. */
internal enum class ButtonKind {
    AVAILABLE,
    WAITING,
    OFFERED,
    BUSY,
    DENIED,
    ACTIVE,
}

internal val ButtonState.kind get() = when (this) {
    ButtonState.Available -> ButtonKind.AVAILABLE
    is ButtonState.Waiting -> ButtonKind.WAITING
    is ButtonState.Offered -> ButtonKind.OFFERED
    is ButtonState.Busy -> ButtonKind.BUSY
    is ButtonState.Denied -> ButtonKind.DENIED
    ButtonState.Active -> ButtonKind.ACTIVE
}

/** The moments a sound marks, as `sounds` names them. */
internal enum class Transition {
    /** The sender sent an offer. */
    OFFERED,

    /** The receiver got one. */
    RECEIVED,

    /** An offer was accepted; both hear it. */
    ACCEPTED,

    /** The receiver declined; both hear it. */
    DECLINED,

    /** An offer waited its whole time; the sender hears it. */
    EXPIRED,

    /** An offer ended otherwise, a player walking away, say; the sender hears it. */
    CANCELLED,

    /** A click on something that is not possible now. */
    BUSY,
}

/** What cancels a waiting offer besides its timeout, as `requests.cancel-on` names it. */
internal enum class CancelCause {
    /** Accepted for the file's sake: leaving the server always cancels. */
    QUIT,
    DISTANCE,
    WORLD_CHANGE,
    DEATH,
}

/** How the receiver hears of an offer besides the menu, as `receiver.notify` names it. */
internal enum class ReceiverNotice {
    CHAT,
    DIALOG,
    BOTH,
}
