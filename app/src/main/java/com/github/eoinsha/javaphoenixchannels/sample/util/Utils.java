package com.github.eoinsha.javaphoenixchannels.sample.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.github.eoinsha.javaphoenixchannels.sample.chat.R;

public class Utils {

    private Context context;
    private SharedPreferences sharedPref;

    private static final String KEY_SHARED_PREF = "PHOENIX_CHAT_SAMPLE";
    private static final int KEY_MODE_PRIVATE = 0;
    private static final String KEY_TOPIC = "topic";
    private static final String KEY_URL = "url";

    public Utils(Context context) {
        this.context = context;
        sharedPref = this.context.getSharedPreferences(KEY_SHARED_PREF, KEY_MODE_PRIVATE);
    }

    public void storeChannelDetails(final String topic, final String url) {
        sharedPref.edit()
            .putString(KEY_TOPIC, topic)
            .putString(KEY_URL, url)
            .commit();
    }

    public String getTopic() {
        final String prevTopic = sharedPref.getString(KEY_TOPIC, null);
        return prevTopic != null ? prevTopic : context.getText(R.string.default_topic).toString();
    }

    public String getUrl() {
        final String prevUrl = sharedPref.getString(KEY_URL, null);
        return prevUrl != null ? prevUrl : context.getText(R.string.default_url).toString();
    }
}