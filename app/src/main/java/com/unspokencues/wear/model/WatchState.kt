package com.unspokencues.wear.model

/** Values and accessible foreground colors match the phone's CueStatus and Color.kt. */
enum class CueStatus(val label: String, val meaning: String, val argb: Long, val ink: Long) {
    GREEN("Green", "Open to connect", 0xFF2E9E5B, 0xFF15171C),
    YELLOW("Yellow", "Proceed with care", 0xFFE8B517, 0xFF15171C),
    RED("Red", "Please give me space", 0xFFCF3A2D, 0xFFFCFBF8),
    PURPLE("Purple", "Maybe open to connect", 0xFF7B4FC9, 0xFFFCFBF8);

    companion object {
        fun fromWire(value: String?): CueStatus? = entries.firstOrNull { it.name == value }
    }
}

data class SharedProfile(val name: String, val token: String, val isPublic: Boolean) {
    val link: String?
        get() = token.takeIf { Regex("[0-9a-fA-F]{32}").matches(it) }
            ?.let { "unspokencues://card/${it.lowercase()}" }
}

data class PhoneSnapshot(val status: CueStatus?, val profile: SharedProfile?) {
    companion object {
        fun fromWire(status: String?, name: String, token: String, isPublic: Boolean,
                     active: Boolean): PhoneSnapshot? {
            if (!active) return PhoneSnapshot(null, null)
            val cue = CueStatus.fromWire(status) ?: return null
            val profile = SharedProfile(name.take(80), token, isPublic)
            return PhoneSnapshot(cue, profile.takeIf { it.link != null || !it.isPublic })
        }
    }
}

data class WatchState(
    val status: CueStatus? = null,
    val profile: SharedProfile? = null,
    val locked: Boolean = true,
    val receivedAt: Long = 0,
    val revision: Long = 0,
    val publisherId: String = "",
) {
    val shareLink: String? get() = profile?.takeIf { it.isPublic }?.link
    fun unlock(): WatchState = copy(locked = false)
    fun select(cue: CueStatus): WatchState =
        if (locked) this else copy(status = cue, locked = true)
    fun receive(snapshot: PhoneSnapshot, now: Long, remoteRevision: Long = revision): WatchState =
        copy(status = snapshot.status, profile = snapshot.profile, locked = true,
            receivedAt = now, revision = remoteRevision)
}

/** Reinstalled phones start a new generation; retired generations cannot revive cached identities. */
data class SnapshotOrder(val publisherId: String, val revision: Long, val retired: Set<String>) {
    fun accepts(incomingPublisher: String, incomingRevision: Long): Boolean =
        incomingPublisher.isNotBlank() && incomingRevision > 0 && incomingPublisher !in retired &&
            (incomingPublisher != publisherId || incomingRevision > revision)
}
