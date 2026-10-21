package me.kavishdevar.librepods.bluetooth

/** Recognizes the observed length-prefixed envelopes without interpreting their status payload. */
internal fun isHeadTrackingControlFrame(packet: ByteArray): Boolean {
    if (packet.size < 12 || packet.size >= 70) return false
    if (!packet.copyOfRange(0, 6).contentEquals(byteArrayOf(4, 0, 4, 0, 0x17, 0))) return false
    val envelope = packet.copyOfRange(6, 10)
    if (!envelope.contentEquals(byteArrayOf(0, 0, 0x10, 0)) &&
        !envelope.contentEquals(byteArrayOf(0, 4, 0, 0))) return false
    val payloadSize = (packet[10].toInt() and 0xff) or ((packet[11].toInt() and 0xff) shl 8)
    return payloadSize == packet.size - 12
}
