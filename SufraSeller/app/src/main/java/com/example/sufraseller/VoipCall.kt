package com.example.sufraseller

import android.os.Handler
import android.os.Looper
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * VoIP call engine (Milestone 4): SIP signaling + RTP audio, both over UDP.
 *
 * SIGNALING (SIP, RFC 3261 style text messages on [localSipPort]):
 *
 *     Caller                         Callee
 *       | ---- INVITE (+ SDP) ------->  |   "I want to call you, send my voice to port X"
 *       | <--- 180 Ringing ----------   |   the callee's phone shows Accept / Decline
 *       | <--- 200 OK (+ SDP) -------   |   accepted, "send my voice to port Y"
 *       | ---- ACK ------------------>  |
 *       | <======= RTP audio =======>   |   (RtpAudioStream, both directions)
 *       | ---- BYE ------------------>  |   one side hangs up
 *       | <--- 200 OK --------------    |
 *
 *   Declined: 486 Busy Here.   Caller gives up while ringing: CANCEL -> 200 OK + 487.
 *
 * MEDIA (RTP): each side sends audio to the IP the other side's SIP messages came from,
 * on the RTP port written in its SDP (m=audio <port>). Our RTP port = SIP port + 1000.
 *
 * All network sending happens on a background thread (Android forbids network on the main thread).
 * The [listener] is always called on the main thread, so the screen can update directly.
 */
class VoipCall(
    val localSipPort: Int,
    private val myName: String,
    private val listener: Listener
) {

    enum class State { IDLE, LISTENING, CALLING, INCOMING, IN_CALL }

    interface Listener {
        /** The call state changed; [info] is a short text to show ("Ringing…", "Call ended", ...). */
        fun onStateChanged(state: State, info: String)

        /** Someone is calling us: show Accept / Decline. */
        fun onIncomingCall(callerName: String, callerAddress: String)

        /** A SIP message was sent or received (shown in the log on the call screen). */
        fun onSipLog(line: String)
    }

    val localRtpPort: Int get() = localSipPort + 1000

    @Volatile var state = State.IDLE
        private set

    private var socket: DatagramSocket? = null
    private var receiveThread: Thread? = null
    @Volatile private var running = false
    private val sender = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    // ---- the current call ----
    private var remoteAddress: InetAddress? = null
    private var remoteSipPort = 0
    private var remoteRtpPort = 0
    private var callId = ""
    private var localTag = ""
    private var remoteTag = ""
    private var cseq = 1
    private var remoteName = ""
    private var lastInvite: SipMessage? = null          // the INVITE we received (callee side)
    private var inviteText = ""                         // the INVITE we sent (caller side, for resending)
    private var audio: RtpAudioStream? = null
    @Volatile private var heardBack = false             // caller: got any answer to the INVITE
    private val noAnswerTimeout = Runnable { if (state == State.CALLING) cancelOutgoing() }

    // ================================================================== public API

    /** Opens the SIP port and starts waiting for calls. */
    fun start() {
        if (running) return
        socket = DatagramSocket(null as java.net.SocketAddress?).apply {
            reuseAddress = true
            soTimeout = 1000
            bind(InetSocketAddress(localSipPort))
        }
        running = true
        receiveThread = Thread({ receiveLoop() }, "sip-receive").apply { start() }
        setState(State.LISTENING, "Waiting for calls on port $localSipPort")
    }

    /** Calls [ip]:[port] (the other user's SIP address). */
    @Synchronized
    fun call(ip: String, port: Int) {
        if (state != State.LISTENING) return
        sender.execute {
            val address = try { InetAddress.getByName(ip) } catch (e: Exception) { null }
            if (address == null) {
                main.post { listener.onStateChanged(state, "Can't find $ip") }
                return@execute
            }
            synchronized(this) {
                if (state != State.LISTENING) return@execute     // e.g. Call pressed twice
                remoteAddress = address
                remoteSipPort = port
                callId = "${randomId()}@sufra"
                localTag = randomId()
                remoteTag = ""
                cseq = 1
                remoteName = "$ip:$port"
                heardBack = false
                inviteText = buildInvite()
                setState(State.CALLING, "Calling $ip:$port…")
                sendAsync(inviteText)
            }
            // UDP can lose packets: send the INVITE again a few times until someone answers
            for (delay in listOf(1000L, 2000L, 4000L)) {
                main.postDelayed({ if (state == State.CALLING && !heardBack) sendAsync(inviteText) }, delay)
            }
            // nobody answers in 30 s -> give up
            main.removeCallbacks(noAnswerTimeout)
            main.postDelayed(noAnswerTimeout, 30_000)
        }
    }

    /** Accepts the incoming call: 200 OK with our SDP, then the audio starts. */
    @Synchronized
    fun accept() {
        val invite = lastInvite ?: return
        if (state != State.INCOMING) return
        sendAsync(buildResponse(invite, 200, "OK", withSdp = true))
        startAudio()
        setState(State.IN_CALL, "In call with $remoteName")
    }

    /** Declines the incoming call: 486 Busy Here. */
    @Synchronized
    fun decline() {
        val invite = lastInvite ?: return
        if (state != State.INCOMING) return
        sendAsync(buildResponse(invite, 486, "Busy Here"))
        endCall("You declined the call")
    }

    /** Red button: BYE during a call, CANCEL while calling, decline while ringing. */
    @Synchronized
    fun hangUp() {
        when (state) {
            State.IN_CALL -> {
                cseq++
                sendAsync(buildRequest("BYE", cseq))
                endCall("Call ended")
            }
            State.CALLING -> cancelOutgoing()
            State.INCOMING -> decline()
            else -> Unit
        }
    }

    fun setMuted(muted: Boolean) {
        audio?.muted = muted
    }

    /** Closes everything (call when the screen is destroyed). */
    fun close() {
        if (state == State.IN_CALL || state == State.CALLING || state == State.INCOMING) hangUp()
        running = false
        audio?.stop()
        audio = null
        // let the last BYE / CANCEL go out before closing the socket
        sender.execute { socket?.close(); socket = null }
        sender.shutdown()
        main.removeCallbacksAndMessages(null)
        state = State.IDLE
    }

    // ================================================================== receiving

    private fun receiveLoop() {
        val buffer = ByteArray(4096)
        while (running) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket?.receive(packet) ?: break
            } catch (e: SocketTimeoutException) {
                continue
            } catch (e: Exception) {
                break
            }
            val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
            val message = SipMessage.parse(text) ?: continue
            log("⬅ ${message.firstLine}")
            handle(message, packet.address, packet.port)
        }
    }

    @Synchronized
    private fun handle(m: SipMessage, from: InetAddress, fromPort: Int) {
        if (m.isRequest) when (m.method) {
            "INVITE" -> onInvite(m, from, fromPort)
            "ACK" -> Unit                                         // our 200 OK arrived, nothing to do
            "BYE" -> {
                sendTo(buildResponse(m, 200, "OK"), from, fromPort)
                if (m.callId == callId && state == State.IN_CALL) endCall("The other side hung up")
            }
            "CANCEL" -> {
                sendTo(buildResponse(m, 200, "OK"), from, fromPort)
                if (m.callId == callId && state == State.INCOMING) {
                    lastInvite?.let { sendAsync(buildResponse(it, 487, "Request Terminated")) }
                    endCall("Missed call from $remoteName")
                }
            }
            else -> sendTo(buildResponse(m, 405, "Method Not Allowed"), from, fromPort)
        } else onResponse(m, from)
    }

    private fun onInvite(m: SipMessage, from: InetAddress, fromPort: Int) {
        if (m.callId == callId && (state == State.INCOMING || state == State.IN_CALL)) {
            return                                                // the same INVITE sent again: ignore
        }
        if (state != State.LISTENING) {
            sendTo(buildResponse(m, 486, "Busy Here"), from, fromPort)   // already in another call
            return
        }
        lastInvite = m
        remoteAddress = from
        remoteSipPort = fromPort
        remoteRtpPort = m.sdpAudioPort() ?: (fromPort + 1000)
        callId = m.callId
        remoteTag = m.tagOf("From")
        localTag = randomId()
        remoteName = m.displayName("From").ifEmpty { from.hostAddress ?: "Unknown" }
        sendAsync(buildResponse(m, 180, "Ringing"))
        setState(State.INCOMING, "$remoteName is calling…")
        val address = "${from.hostAddress}:$fromPort"
        main.post { listener.onIncomingCall(remoteName, address) }
    }

    private fun onResponse(m: SipMessage, from: InetAddress) {
        if (m.callId != callId) return
        heardBack = true
        when {
            m.cseqMethod == "INVITE" && m.statusCode == 180 ->
                setState(State.CALLING, "Ringing…")
            m.cseqMethod == "INVITE" && m.statusCode == 200 && state == State.CALLING -> {
                remoteTag = m.tagOf("To")
                remoteAddress = from
                remoteRtpPort = m.sdpAudioPort() ?: (remoteSipPort + 1000)
                remoteName = m.displayName("To").ifEmpty { remoteName }
                sendAsync(buildRequest("ACK", 1))
                main.removeCallbacks(noAnswerTimeout)
                startAudio()
                setState(State.IN_CALL, "In call with $remoteName")
            }
            m.cseqMethod == "INVITE" && m.statusCode >= 400 && state == State.CALLING -> {
                sendAsync(buildRequest("ACK", 1))                    // a final response is always ACKed
                endCall(if (m.statusCode == 487) "Call cancelled" else "Call declined (${m.statusCode} ${m.reason})")
            }
        }
    }

    // ================================================================== helpers

    private fun cancelOutgoing() {
        sendAsync(buildRequest("CANCEL", 1))
        endCall("Call cancelled")
    }

    private fun startAudio() {
        val address = remoteAddress ?: return
        audio?.stop()
        audio = RtpAudioStream(localRtpPort, address, remoteRtpPort).also { it.start() }
    }

    private fun endCall(info: String) {
        main.removeCallbacks(noAnswerTimeout)
        audio?.stop()
        audio = null
        lastInvite = null
        callId = ""
        setState(State.LISTENING, info)
    }

    private fun setState(newState: State, info: String) {
        state = newState
        main.post { listener.onStateChanged(newState, info) }
    }

    private fun log(line: String) = main.post { listener.onSipLog(line) }

    private fun sendAsync(text: String) {
        val address = remoteAddress ?: return
        val port = remoteSipPort
        sendTo(text, address, port)
    }

    private fun sendTo(text: String, address: InetAddress, port: Int) {
        log("➡ ${text.substringBefore("\r\n")}")
        try {
            sender.execute {
                try {
                    val bytes = text.toByteArray(Charsets.UTF_8)
                    socket?.send(DatagramPacket(bytes, bytes.size, address, port))
                } catch (e: Exception) {
                    main.post { listener.onStateChanged(state, "Network error: ${e.message}") }
                }
            }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            // the call screen was closed: nothing to send any more
        }
    }

    private fun myIp(): String {
        val remote = remoteAddress
        if (remote != null && remote.isLoopbackAddress) return "127.0.0.1"
        return localIpAddress()
    }

    private fun sdp(): String {
        val ip = myIp()
        return "v=0\r\n" +
                "o=- ${System.currentTimeMillis() / 1000} 1 IN IP4 $ip\r\n" +
                "s=Sufra call\r\n" +
                "c=IN IP4 $ip\r\n" +
                "t=0 0\r\n" +
                "m=audio $localRtpPort RTP/AVP ${RtpAudioStream.PAYLOAD_TYPE_PCMU}\r\n" +
                "a=rtpmap:0 PCMU/8000\r\n" +
                "a=sendrecv\r\n"
    }

    private fun myUri() = "sip:${userPart(myName)}@${myIp()}:$localSipPort"
    private fun remoteUri() = "sip:user@${remoteAddress?.hostAddress}:$remoteSipPort"

    private fun buildInvite(): String {
        val body = sdp()
        return "INVITE ${remoteUri()} SIP/2.0\r\n" +
                "Via: SIP/2.0/UDP ${myIp()}:$localSipPort;branch=z9hG4bK${randomId()}\r\n" +
                "Max-Forwards: 70\r\n" +
                "From: \"$myName\" <${myUri()}>;tag=$localTag\r\n" +
                "To: <${remoteUri()}>\r\n" +
                "Call-ID: $callId\r\n" +
                "CSeq: 1 INVITE\r\n" +
                "Contact: <${myUri()}>\r\n" +
                "User-Agent: Sufra/4.0\r\n" +
                "Content-Type: application/sdp\r\n" +
                "Content-Length: ${body.toByteArray().size}\r\n\r\n" + body
    }

    /** ACK, BYE and CANCEL (requests inside the current call). */
    private fun buildRequest(method: String, number: Int): String {
        val toTag = if (remoteTag.isNotEmpty() && method != "CANCEL") ";tag=$remoteTag" else ""
        return "$method ${remoteUri()} SIP/2.0\r\n" +
                "Via: SIP/2.0/UDP ${myIp()}:$localSipPort;branch=z9hG4bK${randomId()}\r\n" +
                "Max-Forwards: 70\r\n" +
                "From: \"$myName\" <${myUri()}>;tag=$localTag\r\n" +
                "To: <${remoteUri()}>$toTag\r\n" +
                "Call-ID: $callId\r\n" +
                "CSeq: $number $method\r\n" +
                "User-Agent: Sufra/4.0\r\n" +
                "Content-Length: 0\r\n\r\n"
    }

    /** A response copies Via, From, Call-ID and CSeq from the request, and adds our tag to To. */
    private fun buildResponse(request: SipMessage, code: Int, reason: String, withSdp: Boolean = false): String {
        val body = if (withSdp) sdp() else ""
        val to = request.header("To").let { if (it.contains("tag=")) it else "$it;tag=$localTag" }
        val sb = StringBuilder()
        sb.append("SIP/2.0 $code $reason\r\n")
        sb.append("Via: ${request.header("Via")}\r\n")
        sb.append("From: ${request.header("From")}\r\n")
        sb.append("To: $to\r\n")
        sb.append("Call-ID: ${request.callId}\r\n")
        sb.append("CSeq: ${request.header("CSeq")}\r\n")
        if (withSdp) {
            sb.append("Contact: <${myUri()}>\r\n")
            sb.append("Content-Type: application/sdp\r\n")
        }
        sb.append("User-Agent: Sufra/4.0\r\n")
        sb.append("Content-Length: ${body.toByteArray().size}\r\n\r\n")
        sb.append(body)
        return sb.toString()
    }

    private fun userPart(name: String) =
        name.lowercase().replace(Regex("[^a-z0-9]"), "").ifEmpty { "user" }

    private fun randomId() = Random.nextLong(1_000_000_000L, 9_999_999_999L).toString(16)

    companion object {
        /** The phone's IP address on Wi-Fi / the network (shown on the call screen). */
        fun localIpAddress(): String = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress ?: "127.0.0.1"
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }
}

/**
 * One SIP message (request or response) parsed from text.
 *   request:   "INVITE sip:user@10.0.0.5:5062 SIP/2.0"
 *   response:  "SIP/2.0 200 OK"
 */
class SipMessage private constructor(
    val firstLine: String,
    private val headers: Map<String, String>,
    val body: String
) {
    val isRequest: Boolean = !firstLine.startsWith("SIP/2.0")
    val method: String = if (isRequest) firstLine.substringBefore(' ') else ""
    val statusCode: Int = if (isRequest) 0 else firstLine.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
    val reason: String = if (isRequest) "" else firstLine.split(' ', limit = 3).getOrNull(2).orEmpty()

    fun header(name: String): String = headers[name.lowercase()].orEmpty()
    val callId: String get() = header("Call-ID")
    /** "1 INVITE" -> "INVITE" */
    val cseqMethod: String get() = header("CSeq").substringAfter(' ').trim()

    fun tagOf(headerName: String): String =
        header(headerName).substringAfter("tag=", "").substringBefore(';').trim()

    /** "\"Farida\" <sip:farida@...>" -> "Farida" */
    fun displayName(headerName: String): String =
        header(headerName).substringAfter('"', "").substringBefore('"', "")

    /** The audio port from the SDP body: "m=audio 6060 RTP/AVP 0" -> 6060 */
    fun sdpAudioPort(): Int? = body.lines()
        .firstOrNull { it.startsWith("m=audio") }
        ?.split(' ')?.getOrNull(1)?.trim()?.toIntOrNull()

    companion object {
        fun parse(text: String): SipMessage? {
            val headPart = text.substringBefore("\r\n\r\n")
            val body = text.substringAfter("\r\n\r\n", "")
            val lines = headPart.split("\r\n")
            val first = lines.firstOrNull()?.trim().orEmpty()
            if (!first.contains("SIP/2.0")) return null
            val headers = mutableMapOf<String, String>()
            for (line in lines.drop(1)) {
                val colon = line.indexOf(':')
                if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
            return SipMessage(first, headers, body)
        }
    }
}
