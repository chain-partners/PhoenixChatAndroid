package com.github.eoinsha.javaphoenixchannels.sample.chat

import android.media.RingtoneManager
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.NullNode
import com.github.eoinsha.javaphoenixchannels.sample.util.Utils
import org.phoenixframework.PhoenixResponse
import org.phoenixframework.PhoenixResponseCallback
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

    val toolbar = findViewById(R.id.chat_toolbar) as Toolbar
    setSupportActionBar(toolbar)

    btnSend = findViewById(R.id.button_send) as Button
    btnSend!!.isEnabled = false
    messageField = findViewById(R.id.message_text) as EditText
    val messagesListView = findViewById(R.id.messages_list_view) as ListView
    messagesListView.divider = null
    messagesListView.dividerHeight = 0
    listAdapter = MessageArrayAdapter(this, android.R.layout.simple_list_item_1)
    messagesListView.adapter = listAdapter

    val utils = Utils(applicationContext)
    url = utils.url
    topic = utils.topic

    socket = Socket(url)
    socket.registerPhoenixSocketListener(this)
    socket.connect()
  }

  private fun sendMessage() {
    val messageBody = messageField!!.text.toString().trim { it <= ' ' }
    channel?.let {
      val payload = objectMapper.createObjectNode()
      payload.put("body", messageBody)
      val pushDate = Date()
      try {
        it.pushRequest("new:msg", payload.toString())
            .receive("ok") {
              val message = ChatMessage()
              message.body = messageBody
              message.insertedDate = pushDate
              message.isFromMe = true
              Log.i(TAG, "MESSAGE: " + message)
              addToList(message)
            }
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

  companion object {
    private val TAG = ChatActivity::class.java.simpleName
  }

  override fun onClosed(code: Int?, reason: String?) {
    showToast("Closed")
  }

  override fun onClosing(code: Int?, reason: String?) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun onFailure(t: Throwable?) {
    // TODO(changhee): Get reason.
    handleTerminalError(t?.message ?: "")
  }

  override fun onMessage(text: String?) {

  }

  override fun onOpen() {
    showToast("Connected")
    channel = socket.channel(topic)

    try {
      channel!!.join().receive("ok") {
        showToast("You have joined '$topic'")
      }
      channel!!.on("user:entered", object : PhoenixResponseCallback {
        override fun onResponse(response: PhoenixResponse?) {
          // TODO(changhee): Remove JsonNode
          val user = response?.payload?.get("user")
          if (user == null || user is NullNode) {
            showToast("An anonymous user entered")
          } else {
            showToast("User '" + user.toString() + "' entered")
          }
        }

        override fun onFailure(throwable: Throwable?, response: PhoenixResponse?) {

        }
      }).on("new:msg", object : PhoenixResponseCallback {
        override fun onResponse(response: PhoenixResponse?) {
          val message: ChatMessage
          try {
            message = objectMapper.treeToValue(response?.payload, ChatMessage::class.java)
            Log.i(TAG, "MESSAGE: " + message)
            if (message.userId != null && message.userId != "SYSTEM") {
              addToList(message)
              notifyMessageReceived()
            }
          } catch (e: JsonProcessingException) {
            onFailure(e)
            Log.e(TAG, "Unable to parse message", e)
          }
        }

        override fun onFailure(throwable: Throwable?, response: PhoenixResponse?) {

        }
      })
    } catch (e: Exception) {
      Log.e(TAG, "Failed to join channel " + topic, e)
      handleTerminalError(e)
    }

    btnSend!!.setOnClickListener {
      sendMessage()
      messageField!!.setText("")
    }
    runOnUiThread { btnSend!!.isEnabled = true }
  }
}
