package com.unspokencues.wear.alerts

/** The server sends a new generation on sign-in. Its first result is always a silent baseline. */
internal fun shouldPost(previous: AlertCursor?, next: AlertCursor?): Boolean =
    previous != null && next != null && previous.generation == next.generation && next.micros > previous.micros
