package com.strawmanmobile.chat

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.StatFs
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var statusText: TextView
    private lateinit var actionButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var messages: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var activeTurn: Job? = null
    private var busy = false

    private val modelFile: File
        get() = File(filesDir, MODEL_FILENAME)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildInterface()
        actionButton.setOnClickListener {
            if (modelFile.exists()) loadModel() else downloadModel()
        }
        sendButton.setOnClickListener { sendMessage() }
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
        if (modelFile.exists()) loadModel()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        setContentView(root)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = text("RoboJimmy9000", 21f, TEXT, Typeface.DEFAULT_BOLD)
        header.addView(title, LinearLayout.LayoutParams(0, dp(52), 1f))
        val localBadge = text("ON DEVICE", 11f, GREEN, Typeface.DEFAULT_BOLD).apply {
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = rounded(0xFFE4F1EC.toInt(), 30)
        }
        header.addView(localBadge)
        root.addView(header)

        statusText = text("Private, on-device AI", 13f, MUTED).apply {
            setPadding(0, dp(2), 0, dp(8))
        }
        root.addView(statusText)

        actionButton = button("Download Qwen3 model · 475 MiB")
        root.addView(actionButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(46)
        ))
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        root.addView(progressBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(5)
        ))

        scrollView = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, dp(16), 0, dp(16))
        }
        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        scrollView.addView(messages)
        root.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(dp(10), dp(8), dp(6), dp(8))
            background = rounded(Color.WHITE, 24)
            elevation = dp(2).toFloat()
        }
        input = EditText(this).apply {
            hint = "Message RoboJimmy9000..."
            textSize = 16f
            setTextColor(TEXT)
            setHintTextColor(MUTED)
            minLines = 1
            maxLines = 5
            setPadding(dp(8), dp(7), dp(8), dp(7))
            background = null
            setSingleLine(false)
        }
        composer.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        sendButton = button("Send").apply {
            minWidth = dp(66)
            contentDescription = "Send message"
        }
        composer.addView(sendButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)
        ))
        root.addView(composer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        addWelcomeMessage()
    }

    private fun addWelcomeMessage() {
        val welcome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(Color.WHITE, 20)
        }
        welcome.addView(text("A little AI, right here.", 20f, TEXT, Typeface.DEFAULT_BOLD))
        welcome.addView(text(
            "Download the local model once. After that, you can chat privately—even without internet.",
            15f, MUTED
        ).apply {
            setPadding(0, dp(8), 0, 0)
        })
        messages.addView(welcome, bubbleLayout(false))
    }

    private fun downloadModel() {
        if (busy) return
        setBusy(true)
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        statusText.text = "Connecting to the model download..."
        activityScope.launch {
            val partialFile = File(filesDir, "$MODEL_FILENAME.part")
            try {
                withContext(Dispatchers.IO) {
                    val available = StatFs(filesDir.path).availableBytes
                    if (available < MODEL_SIZE_BYTES + 100L * 1024 * 1024) {
                        throw IOException("At least 575 MiB of free storage is needed to download the model.")
                    }
                    downloadModelFile(partialFile)
                    if (modelFile.exists() && !modelFile.delete()) {
                        throw IOException("Could not replace the existing model file.")
                    }
                    if (!partialFile.renameTo(modelFile)) {
                        throw IOException("Could not save the downloaded model.")
                    }
                }
                progressBar.progress = 100
                statusText.text = "Model downloaded · loading on this device..."
                actionButton.text = "Loading model..."
                setBusy(false)
                loadModel()
            } catch (error: CancellationException) {
                partialFile.delete()
                throw error
            } catch (error: Exception) {
                partialFile.delete()
                progressBar.visibility = View.GONE
                statusText.text = "Model download failed"
                actionButton.text = "Retry model download"
                Toast.makeText(this@MainActivity, error.message ?: "Model download failed.", Toast.LENGTH_LONG).show()
                setBusy(false)
            }
        }
    }

    private suspend fun downloadModelFile(destination: File) {
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Model server returned HTTP ${connection.responseCode}.")
            }
            val totalBytes = connection.contentLengthLong
            if (totalBytes > 0 && totalBytes != MODEL_SIZE_BYTES) {
                throw IOException("The model server returned an unexpected file size.")
            }
            progressBar.isIndeterminate = totalBytes <= 0
            connection.inputStream.use { input ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        if (downloaded + read > MODEL_SIZE_BYTES) {
                            throw IOException("The downloaded model exceeds the expected size.")
                        }
                        output.write(buffer, 0, read)
                        downloaded += read
                        val percent = (downloaded * 100 / MODEL_SIZE_BYTES).toInt()
                        withContext(Dispatchers.Main) {
                            progressBar.isIndeterminate = false
                            progressBar.progress = percent
                            statusText.text = "Downloading Qwen3 · $percent%"
                        }
                    }
                    output.flush()
                    if (downloaded != MODEL_SIZE_BYTES) {
                        throw IOException("The model download was incomplete.")
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun loadModel() {
        if (busy || !modelFile.exists()) return
        setBusy(true)
        actionButton.text = "Loading model..."
        actionButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        statusText.text = "Loading the local model. This can take a few seconds..."
        activityScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val newEngine = Engine(
                        EngineConfig(
                            modelPath = modelFile.absolutePath,
                            backend = Backend.CPU(),
                            cacheDir = cacheDir.absolutePath,
                        )
                    )
                    try {
                        newEngine.initialize()
                        val newConversation = newEngine.createConversation(
                            ConversationConfig(
                                systemInstruction = Contents.of("You are a helpful, concise assistant.")
                            )
                        )
                        newEngine to newConversation
                    } catch (error: Exception) {
                        newEngine.close()
                        throw error
                    }
                }
                engine = loaded.first
                conversation = loaded.second
                statusText.text = "Ready · running locally on your device"
                actionButton.text = "Model ready"
                progressBar.visibility = View.GONE
                setBusy(false)
                updateSendButton()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                statusText.text = "Could not load model"
                actionButton.text = "Retry loading model"
                progressBar.visibility = View.GONE
                Toast.makeText(this@MainActivity, error.message ?: "Could not load the local model.", Toast.LENGTH_LONG).show()
                setBusy(false)
            }
        }
    }

    private fun sendMessage() {
        val prompt = input.text.toString().trim()
        val activeConversation = conversation
        if (prompt.isEmpty() || activeConversation == null || busy) return

        input.text.clear()
        addMessage(prompt, fromUser = true)
        val responseView = addMessage("", fromUser = false)
        setBusy(true)
        activeTurn = activityScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    activeConversation.sendMessageAsync(prompt).collect { piece ->
                        val nextText = piece.toString()
                        withContext(Dispatchers.Main) {
                            responseView.text = responseView.text.toString() + nextText
                            scrollToBottom()
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                responseView.text = "Sorry, I couldn't generate a response. ${error.message ?: "Please try again."}"
            } finally {
                setBusy(false)
                activeTurn = null
            }
        }
    }

    private fun addMessage(message: String, fromUser: Boolean): TextView {
        val view = text(message, 16f, TEXT).apply {
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = rounded(if (fromUser) 0xFFE4F1EC.toInt() else Color.WHITE, 18)
        }
        messages.addView(view, bubbleLayout(fromUser))
        scrollToBottom()
        return view
    }

    private fun bubbleLayout(fromUser: Boolean): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            if (fromUser) LinearLayout.LayoutParams.WRAP_CONTENT else LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = if (fromUser) Gravity.END else Gravity.START
            if (!fromUser) {
                marginEnd = dp(28)
            } else {
                marginStart = dp(28)
            }
            bottomMargin = dp(12)
        }

    private fun scrollToBottom() {
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        actionButton.isEnabled = !value && conversation == null
        input.isEnabled = !value && conversation != null
        updateSendButton()
    }

    private fun updateSendButton() {
        sendButton.isEnabled = !busy && conversation != null
        sendButton.alpha = if (sendButton.isEnabled) 1f else 0.5f
        actionButton.alpha = if (actionButton.isEnabled) 1f else 0.65f
    }

    private fun text(value: String, size: Float, color: Int, face: Typeface? = null) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            face?.let { typeface = it }
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun button(label: String) = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(GREEN, 24)
        stateListAnimator = null
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        activeTurn?.cancel()
        activityScope.cancel()
        val activeConversation = conversation
        val activeEngine = engine
        conversation = null
        engine = null
        CoroutineScope(Dispatchers.IO).launch {
            activeConversation?.close()
            activeEngine?.close()
        }
        super.onDestroy()
    }

    companion object {
        private const val MODEL_FILENAME = "qwen3_0_6b_mixed_int4.litertlm"
        private const val MODEL_SIZE_BYTES = 497_516_544L
        private const val MODEL_URL =
            "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/qwen3_0_6b_mixed_int4.litertlm"
        private const val BACKGROUND = 0xFFF7F7F4.toInt()
        private const val TEXT = 0xFF202522.toInt()
        private const val MUTED = 0xFF6E7772.toInt()
        private const val GREEN = 0xFF167D68.toInt()
    }
}
