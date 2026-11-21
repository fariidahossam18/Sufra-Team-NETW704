package com.example.sufraseller

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Patterns
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.sufraseller.databinding.ActivityCallBinding
import java.net.BindException

/**
 * VoIP call screen (Milestone 4, Seller app).
 *
 *  - Shows THIS phone's IP address and ports, so the other person knows where to call.
 *  - The user types the other person's IP address and port and presses Call
 *    (the milestone asks for a manual IP + port).
 *  - While this screen is open it also waits for calls: Accept / Decline.
 *  - During a call: Mute, Speaker, End, and a timer.
 *  - The SIP log at the bottom shows every SIP message sent (➡) and received (⬅).
 *
 * The networking is in VoipCall (SIP signaling) and RtpAudioStream (RTP voice).
 * Ports: Buyer app SIP 5060 / RTP 6060, Seller app SIP 5062 / RTP 6062,
 * so both apps can run on the same phone and call each other with 127.0.0.1.
 */
class CallActivity : AppCompatActivity(), VoipCall.Listener {

    companion object {
        /** This app's SIP port. */
        const val MY_SIP_PORT = 5062

        /** The other app's SIP port (pre-filled in the port box). */
        const val OTHER_APP_PORT = 5060

        private const val MAX_LOG_LINES = 14
    }

    private lateinit var binding: ActivityCallBinding
    private var voip: VoipCall? = null
    private val logLines = ArrayDeque<String>()
    private var muted = false
    private var speakerOn = true

    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    /** The microphone permission (needed to send our voice). */
    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) toast("Without the microphone the other person won't hear you")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdge(binding.root, binding.header)

        // keep the screen on while this screen is open (calls)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.tvMyIp.text = VoipCall.localIpAddress()
        binding.tvMyPorts.text = "SIP port $MY_SIP_PORT  ·  RTP port ${MY_SIP_PORT + 1000}"
        binding.etPort.setText(OTHER_APP_PORT.toString())

        binding.btnBack.setOnClickListener { finish() }
        binding.btnCall.setOnClickListener { startCall() }
        binding.btnAccept.setOnClickListener { askMicThen { voip?.accept() } }
        binding.btnDecline.setOnClickListener { voip?.decline() }
        binding.btnHangUp.setOnClickListener { voip?.hangUp() }
        binding.btnMute.setOnClickListener { toggleMute() }
        binding.btnSpeaker.setOnClickListener { toggleSpeaker() }

        if (!hasMic()) micPermission.launch(Manifest.permission.RECORD_AUDIO)
        startListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        voip?.close()                 // hangs up if needed and frees the ports
        voip = null
        resetAudio()
    }

    // ================================================================== actions

    /** Opens our SIP port so others can call us. */
    private fun startListening() {
        val name = FirebaseRefs.auth.currentUser?.displayName?.ifBlank { null }
            ?: FirebaseRefs.auth.currentUser?.email?.substringBefore('@')
            ?: "Sufra user"
        voip = VoipCall(MY_SIP_PORT, name, this)
        try {
            voip?.start()
        } catch (e: BindException) {
            binding.tvState.text = "Port $MY_SIP_PORT is busy"
            binding.tvInfo.text = "Close the other call screen of this app and open this one again."
            binding.btnCall.isEnabled = false
        } catch (e: Exception) {
            binding.tvState.text = "Can't start calls"
            binding.tvInfo.text = e.message
            binding.btnCall.isEnabled = false
        }
    }

    /** Checks the IP and port typed by the user, then sends the INVITE. */
    private fun startCall() {
        val ip = binding.etIp.value()
        val port = binding.etPort.value().toIntOrNull()
        binding.tilIp.error = if (Patterns.IP_ADDRESS.matcher(ip).matches()) null else "Enter an IP like 192.168.1.20"
        binding.tilPort.error = if (port != null && port in 1..65535) null else "Port must be 1 to 65535"
        if (binding.tilIp.error != null || binding.tilPort.error != null || port == null) return
        if ((ip == "127.0.0.1" || ip == binding.tvMyIp.text.toString()) && port == MY_SIP_PORT) {
            binding.tilPort.error = "That's this app's own port. Use the other app's port (${OTHER_APP_PORT})"
            return
        }
        askMicThen { voip?.call(ip, port) }
    }

    private fun toggleMute() {
        muted = !muted
        voip?.setMuted(muted)
        binding.btnMute.text = if (muted) "Unmute" else "Mute"
        binding.btnMute.setIconResource(if (muted) R.drawable.ic_mic_off else R.drawable.ic_mic)
    }

    @Suppress("DEPRECATION")   // setSpeakerphoneOn still works and is the simplest way
    private fun toggleSpeaker() {
        speakerOn = !speakerOn
        audioManager.isSpeakerphoneOn = speakerOn
        binding.btnSpeaker.text = if (speakerOn) "Speaker" else "Earpiece"
    }

    // ================================================================== VoipCall.Listener

    override fun onStateChanged(state: VoipCall.State, info: String) {
        binding.tvInfo.text = info
        when (state) {
            VoipCall.State.IDLE, VoipCall.State.LISTENING -> {
                binding.tvState.text = "Ready"
                binding.tvAvatar.text = "📞"
                showButtons(form = true)
                stopTimer()
                resetAudio()
            }
            VoipCall.State.CALLING -> {
                binding.tvState.text = "Calling…"
                binding.tvAvatar.text = "📲"
                showButtons(call = true)
            }
            VoipCall.State.INCOMING -> {
                binding.tvState.text = "Incoming call"
                binding.tvAvatar.text = "🔔"
                showButtons(incoming = true)
            }
            VoipCall.State.IN_CALL -> {
                binding.tvState.text = "Connected"
                binding.tvAvatar.text = "🗣️"
                showButtons(call = true)
                startAudioMode()
                startTimer()
            }
        }
    }

    override fun onIncomingCall(callerName: String, callerAddress: String) {
        binding.tvInfo.text = "$callerName  ($callerAddress)"
        toast("📞 Incoming call from $callerName")
    }

    override fun onSipLog(line: String) {
        logLines.addLast(line)
        while (logLines.size > MAX_LOG_LINES) logLines.removeFirst()
        binding.tvLog.text = logLines.joinToString("\n")
    }

    // ================================================================== helpers

    /** Shows only the buttons that make sense for the current state. */
    private fun showButtons(form: Boolean = false, incoming: Boolean = false, call: Boolean = false) {
        binding.callForm.visibility = if (form) View.VISIBLE else View.GONE
        binding.incomingButtons.visibility = if (incoming) View.VISIBLE else View.GONE
        binding.callButtons.visibility = if (call) View.VISIBLE else View.GONE
    }

    private fun startTimer() {
        binding.chronometer.base = SystemClock.elapsedRealtime()
        binding.chronometer.visibility = View.VISIBLE
        binding.chronometer.start()
    }

    private fun stopTimer() {
        binding.chronometer.stop()
        binding.chronometer.visibility = View.GONE
    }

    /** Phone-call audio mode, speaker on by default (easier on the emulator). */
    @Suppress("DEPRECATION")
    private fun startAudioMode() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = speakerOn
        muted = false
        binding.btnMute.text = "Mute"
        binding.btnMute.setIconResource(R.drawable.ic_mic)
    }

    @Suppress("DEPRECATION")
    private fun resetAudio() {
        audioManager.isSpeakerphoneOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    private fun hasMic() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Runs [action]; if the mic permission is missing, asks for it first (the call still works without it). */
    private fun askMicThen(action: () -> Unit) {
        if (!hasMic()) micPermission.launch(Manifest.permission.RECORD_AUDIO)
        action()
    }
}
