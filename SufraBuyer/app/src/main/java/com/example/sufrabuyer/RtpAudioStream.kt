package com.example.sufrabuyer

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlin.random.Random

/**
 * The voice part of a VoIP call (Milestone 4): RTP over UDP.
 *
 *  - Microphone -> 20 ms of sound (160 samples at 8000 Hz) -> G.711 µ-law (PCMU, 1 byte per sample)
 *    -> 12-byte RTP header (version 2, payload type 0, sequence number, timestamp, SSRC) -> UDP packet.
 *  - UDP packet -> skip the RTP header -> µ-law decode -> speaker.
 *
 * Each direction runs on its own thread because reading the mic and waiting for packets block.
 * These are the RTP packets we will capture with Wireshark in Milestone 5.
 */
class RtpAudioStream(
    private val localPort: Int,
    private val remoteAddress: InetAddress,
    private val remotePort: Int
) {

    companion object {
        const val SAMPLE_RATE = 8000          // telephone quality, what PCMU uses
        const val FRAME_SAMPLES = 160         // 20 ms per packet
        const val PAYLOAD_TYPE_PCMU = 0
        private const val RTP_HEADER = 12
    }

    @Volatile private var running = false
    @Volatile var muted = false

    private var socket: DatagramSocket? = null
    private var sendThread: Thread? = null
    private var receiveThread: Thread? = null

    /** Opens the RTP port and starts sending and playing audio. */
    fun start() {
        if (running) return
        running = true
        socket = DatagramSocket(null as java.net.SocketAddress?).apply {
            reuseAddress = true
            soTimeout = 500
            bind(java.net.InetSocketAddress(localPort))
        }
        sendThread = Thread({ sendLoop() }, "rtp-send").apply { start() }
        receiveThread = Thread({ receiveLoop() }, "rtp-receive").apply { start() }
    }

    /** Stops both threads and frees the microphone, the speaker and the port. */
    fun stop() {
        running = false
        socket?.close()
        socket = null
        sendThread?.join(300)
        receiveThread?.join(300)
    }

    // ------------------------------------------------------------------ sending

    @SuppressLint("MissingPermission")   // CallActivity asks for RECORD_AUDIO before a call starts
    private fun sendLoop() {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, FRAME_SAMPLES * 2 * 4)
            )
        } catch (e: Exception) {
            null
        }
        recorder?.startRecording()

        val pcm = ShortArray(FRAME_SAMPLES)
        val packet = ByteArray(RTP_HEADER + FRAME_SAMPLES)
        val ssrc = Random.nextInt()
        var sequence = Random.nextInt(0, 65535)
        var timestamp = Random.nextInt()

        // RTP header that doesn't change: version 2, no padding/extension/CSRC, payload type 0 (PCMU)
        packet[0] = 0x80.toByte()
        packet[1] = PAYLOAD_TYPE_PCMU.toByte()
        packet[8] = (ssrc shr 24).toByte(); packet[9] = (ssrc shr 16).toByte()
        packet[10] = (ssrc shr 8).toByte(); packet[11] = ssrc.toByte()

        while (running) {
            // 1) 20 ms of sound (if the mic can't be used we still send silence, so the call stays alive)
            val read = recorder?.read(pcm, 0, FRAME_SAMPLES) ?: run { Thread.sleep(20); 0 }
            if (read <= 0 || muted) pcm.fill(0)

            // 2) header fields that change every packet
            packet[2] = (sequence shr 8).toByte(); packet[3] = sequence.toByte()
            packet[4] = (timestamp shr 24).toByte(); packet[5] = (timestamp shr 16).toByte()
            packet[6] = (timestamp shr 8).toByte(); packet[7] = timestamp.toByte()

            // 3) payload: 160 µ-law bytes
            for (i in 0 until FRAME_SAMPLES) packet[RTP_HEADER + i] = linearToUlaw(pcm[i].toInt())

            try {
                socket?.send(DatagramPacket(packet, packet.size, remoteAddress, remotePort))
            } catch (e: Exception) {
                // socket closed while stopping, or network gone
            }
            sequence = (sequence + 1) and 0xFFFF
            timestamp += FRAME_SAMPLES
        }
        try { recorder?.stop() } catch (e: Exception) { }
        recorder?.release()
    }

    // ------------------------------------------------------------------ receiving

    private fun receiveLoop() {
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val player = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuffer, FRAME_SAMPLES * 2 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        player.play()

        val buffer = ByteArray(1500)
        val pcm = ShortArray(1500)
        while (running) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket?.receive(packet) ?: break
            } catch (e: SocketTimeoutException) {
                continue
            } catch (e: Exception) {
                break                                  // socket closed
            }
            if (packet.length <= RTP_HEADER) continue
            val csrcCount = buffer[0].toInt() and 0x0F
            val start = RTP_HEADER + csrcCount * 4
            val samples = packet.length - start
            if (samples <= 0) continue
            for (i in 0 until samples) pcm[i] = ulawToLinear(buffer[start + i])
            player.write(pcm, 0, samples)
        }
        try { player.stop() } catch (e: Exception) { }
        player.release()
    }

    // ------------------------------------------------------------------ G.711 µ-law

    /** 16-bit sample -> 8-bit µ-law (standard G.711 algorithm). */
    private fun linearToUlaw(sample: Int): Byte {
        val bias = 0x84
        val clip = 32635
        var s = sample
        val sign = if (s < 0) { s = -s; 0x80 } else 0
        if (s > clip) s = clip
        s += bias
        var exponent = 7
        var mask = 0x4000
        while ((s and mask) == 0 && exponent > 0) { exponent--; mask = mask shr 1 }
        val mantissa = (s shr (exponent + 3)) and 0x0F
        return (sign or (exponent shl 4) or mantissa).inv().toByte()
    }

    /** 8-bit µ-law -> 16-bit sample. */
    private fun ulawToLinear(value: Byte): Short {
        val u = value.toInt().inv() and 0xFF
        val sign = u and 0x80
        val exponent = (u shr 4) and 0x07
        val mantissa = u and 0x0F
        var sample = ((mantissa shl 3) + 0x84) shl exponent
        sample -= 0x84
        return (if (sign != 0) -sample else sample).toShort()
    }
}
