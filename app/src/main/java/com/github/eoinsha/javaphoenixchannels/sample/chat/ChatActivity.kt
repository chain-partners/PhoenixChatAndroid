package com.github.eoinsha.javaphoenixchannels.sample.chat

import android.media.RingtoneManager
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.github.eoinsha.javaphoenixchannels.sample.util.Utils
import kotlinx.android.synthetic.main.activity_chat.*
import org.phoenixframework.channel.Channel
import org.phoenixframework.socket.PhoenixSocketEventListener
import org.phoenixframework.socket.Socket
import java.io.IOException
import java.util.Date
import java.util.concurrent.TimeoutException

class ChatActivity : AppCompatActivity(), PhoenixSocketEventListener {

  private var btnSend: Button? = null
  private var messageField: EditText? = null
  private var listAdapter: MessageArrayAdapter? = null
  private lateinit var socket: Socket
  private var channel: Channel? = null
  private lateinit var url: String
  private lateinit var topic: String

  private val objectMapper = ObjectMapper()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_chat)

    val toolbar = chat_toolbar
    setSupportActionBar(toolbar)

    btnSend = button_send
    btnSend!!.isEnabled = false
    messageField = message_text
    val messagesListView = messages_list_view
    messagesListView.divider = null
    messagesListView.dividerHeight = 0
    listAdapter = MessageArrayAdapter(this, android.R.layout.simple_list_item_1)
    messagesListView.adapter = listAdapter

    val utils = Utils(applicationContext)
    url = utils.url
    topic = utils.topic

    socket = Socket(endpointUri = url)
    socket.registerEventListener(this)
    socket.connect()
  }

  override fun onDestroy() {
    super.onDestroy()
    socket.unregisterEventListener(this)
  }

  private fun sendMessage() {
    val messageBody = messageField!!.text.toString().trim { it <= ' ' }
    channel?.let {
      val payload = objectMapper.createObjectNode()
      payload.put("body", messageBody)
      val pushDate = Date()
      try {
        it.pushRequest(
            event = "new:msg",
            payload = payload.toString(),
            success = { message ->
              if (message?.status == "ok") {
                val sentChat = ChatMessage()
                sentChat.body = messageBody
                sentChat.insertedDate = pushDate
                sentChat.isFromMe = true
                Log.i(TAG, "MESSAGE: $sentChat")
                addToList(sentChat)
              }
            })
      } catch (e: IOException) {
        Log.e(TAG, "Failed to send", e)
        showToast("Failed to send")
      } catch (e: TimeoutException) {
        Log.w(TAG, "MESSAGE timed out")
      }
    }
  }

  private fun showToast(toastText: String) {
    runOnUiThread { Toast.makeText(applicationContext, toastText, Toast.LENGTH_SHORT).show() }
  }

  private fun notifyMessageReceived() {
    try {
      val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
      val r = RingtoneManager.getRingtone(applicationContext, notification)
      r.play()
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun handleTerminalError(t: Throwable) {
    handleTerminalError(t.toString())
  }

  private fun handleTerminalError(s: String) {
    showToast(s)
  }

  private fun addToList(message: ChatMessage) {
    runOnUiThread { listAdapter!!.add(message) }
  }

  /**
   * Implements [PhoenixSocketEventListener].
   */

  override fun onClosed(socket: Socket, code: Int?, reason: String?) {
    showToast("Closed")
  }

  override fun onClosing(socket: Socket, code: Int?, reason: String?) {
    // does nothing.
  }

  override fun onFailure(socket: Socket, t: Throwable?) {
    handleTerminalError(t?.message ?: "")
  }

  override fun onMessage(socket: Socket, text: String?) {
    // does nothing
  }

  override fun onOpen(socket: Socket) {
    showToast("Connected")
    channel = socket.channel(topic)

    try {
      channel!!.join(success = { message ->
        if (message?.status == "ok") {
          showToast("You have joined '$topic'")
        }
      })
      channel!!
          .on("user:entered",
              success = { response ->
                val payload = with(response?.payload) {
                  objectMapper.readTree(this)
                }
                val user = payload.get("user")
                if (user == null || user is NullNode) {
                  showToast("An anonymous user entered")
                } else {
                  showToast("User '$user' entered")
                }
              })
          .on("new:msg",
              success = { response ->
                var chatMessage: ChatMessage
                response?.payload?.let {
                  try {
                    chatMessage = objectMapper.readValue<ChatMessage>(it, jacksonTypeRef<ChatMessage>())
                    Log.i(TAG, "MESSAGE: $chatMessage")
                    if (chatMessage.userId != null && chatMessage.userId != "SYSTEM") {
                      addToList(chatMessage)
                      notifyMessageReceived()
                      return@let
                    }
                  } catch (e: JsonProcessingException) {
                    onFailure(socket, e)
                    Log.e(TAG, "Unable to parse message", e)
                  }
                }
              })
    } catch (e: Exception) {
      Log.e(TAG, "Failed to join channel $topic", e)
      handleTerminalError(e)
    }

    btnSend!!.setOnClickListener {
      sendMessage()
      messageField!!.setText("")
    }
    runOnUiThread { btnSend!!.isEnabled = true }
  }

  companion object {

    private val TAG = ChatActivity::class.java.simpleName
  }
}
