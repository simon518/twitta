/*
 * Copyright (C) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dart.android.twitter;

import java.io.IOException;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.util.Log;

import com.dart.android.twitter.TwitterApi.ApiException;
import com.dart.android.twitter.TwitterApi.AuthException;
import com.google.android.photostream.UserTask;

public class TwitterService extends Service {
  private static final String TAG = "TwitterService";

  private TwitterApi mApi;
  private TwitterDbAdapter mDb;
  private SharedPreferences mPreferences;

  private NotificationManager mNotificationManager;

  private ArrayList<Tweet> mNewTweets;
  private ArrayList<Dm> mNewDms;

  private UserTask<Void, Void, RetrieveResult> mRetrieveTask;

  private WakeLock mWakeLock;

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onCreate() {
    super.onCreate();

    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
    mWakeLock.acquire();

    mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

    if (!mPreferences.getBoolean(Preferences.CHECK_UPDATES_KEY, false)) {
      Log.i(TAG, "Check update preference is false.");
      stopSelf();
      return;
    }

    String username = mPreferences.getString(Preferences.USERNAME_KEY, "");
    String password = mPreferences.getString(Preferences.PASSWORD_KEY, "");

    if (!TwitterApi.isValidCredentials(username, password)) {
      Log.i(TAG, "No credentials.");
      stopSelf();
      return;
    }

    mApi = new TwitterApi();
    mApi.setCredentials(username, password);

    mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

    mDb = new TwitterDbAdapter(this);
    mDb.open();

    mNewTweets = new ArrayList<Tweet>();
    mNewDms = new ArrayList<Dm>();

    mRetrieveTask = new RetrieveTask().execute();
  }

  private static final int NOTIFICATION_ID = 0;

  private void processNewTweets() {
    if (mNewTweets.size() <= 0) {
      return;
    }

    Log.i(TAG, mNewTweets.size() + " new tweets.");

    int count = mDb.addNewTweetsAndCountUnread(mNewTweets);

    if (count <= 0) {
      return;
    }

    Tweet latestTweet = mNewTweets.get(0);

    String title;
    String text;

    if (count == 1) {
      title = latestTweet.screenName;
      text = latestTweet.text;
    } else {
      title = getString(R.string.new_twitter_updates);
      text = getString(R.string.x_new_tweets);
      text = MessageFormat.format(text, count);
    }

    PendingIntent intent = PendingIntent.getActivity(this, 0, new Intent(this,
        LoginActivity.class), 0);

    notify(intent, latestTweet.text, title, text);
  }

  private void notify(PendingIntent intent, String tickerText, String title,
      String text) {
    Notification notification = new Notification(
        android.R.drawable.stat_notify_chat, tickerText, System
            .currentTimeMillis());

    notification.setLatestEventInfo(this, title, text, intent);

    notification.flags = Notification.FLAG_AUTO_CANCEL
        | Notification.FLAG_ONLY_ALERT_ONCE | Notification.FLAG_SHOW_LIGHTS;

    notification.ledARGB = 0xFF84E4FA;
    notification.ledOnMS = 5000;
    notification.ledOffMS = 5000;

    String ringtoneUri = mPreferences.getString(Preferences.RINGTONE_KEY, null);

    if (ringtoneUri == null) {
      notification.defaults |= Notification.DEFAULT_SOUND;
    } else {
      notification.sound = Uri.parse(ringtoneUri);
    }

    if (mPreferences.getBoolean(Preferences.VIBRATE_KEY, false)) {
      notification.defaults |= Notification.DEFAULT_VIBRATE;
    }

    mNotificationManager.notify(NOTIFICATION_ID, notification);
  }

  private void processNewDms() {
    if (mNewDms.size() <= 0) {
      return;
    }

    Log.i(TAG, mNewDms.size() + " new DMs.");

    int count = mDb.addNewDmsAndCountUnread(mNewDms);

    if (count <= 0) {
      return;
    }

    Dm latest = mNewDms.get(0);

    String title;
    String text;

    if (count == 1) {
      title = latest.screenName;
      text = latest.text;
    } else {
      title = getString(R.string.new_twitter_updates);
      text = getString(R.string.x_new_tweets);
      text = MessageFormat.format(text, count);
    }

    PendingIntent intent = PendingIntent.getActivity(this, 0,
        new Intent(this, LoginActivity.class), 0);

    notify(intent, latest.text, title, text);
  }

  @Override
  public void onDestroy() {
    Log.i(TAG, "IM DYING!!!");

    if (mRetrieveTask != null
        && mRetrieveTask.getStatus() == UserTask.Status.RUNNING) {
      mRetrieveTask.cancel(true);
    }

    if (mDb != null) {
      mDb.close();
    }

    mWakeLock.release();

    super.onDestroy();
  }

  static void schedule(Context context) {
    SharedPreferences preferences = PreferenceManager
        .getDefaultSharedPreferences(context);

    if (!preferences.getBoolean(Preferences.CHECK_UPDATES_KEY, false)) {
      Log.i(TAG, "Check update preference is false.");
      return;
    }

    String intervalPref = preferences.getString(
        Preferences.CHECK_UPDATE_INTERVAL_KEY, context
            .getString(R.string.pref_check_updates_interval_default));
    int interval = Integer.parseInt(intervalPref);

    Intent intent = new Intent(context, TwitterService.class);
    PendingIntent pending = PendingIntent.getService(context, 0, intent, 0);

    Calendar c = new GregorianCalendar();
    c.add(Calendar.MINUTE, interval);

    DateFormat df = new SimpleDateFormat("h:mm a");
    Log.i(TAG, "Scheduling alarm at " + df.format(c.getTime()));

    AlarmManager alarm = (AlarmManager) context
        .getSystemService(Context.ALARM_SERVICE);
    alarm.cancel(pending);
    alarm.set(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pending);
  }

  static void unschedule(Context context) {
    Intent intent = new Intent(context, TwitterService.class);
    PendingIntent pending = PendingIntent.getService(context, 0, intent, 0);
    AlarmManager alarm = (AlarmManager) context
        .getSystemService(Context.ALARM_SERVICE);
    Log.i(TAG, "Cancelling alarms.");
    alarm.cancel(pending);
  }

  private enum RetrieveResult {
    OK, IO_ERROR, AUTH_ERROR, CANCELLED
  }

  private class RetrieveTask extends UserTask<Void, Void, RetrieveResult> {
    @Override
    public RetrieveResult doInBackground(Void... params) {
      int maxId = mDb.fetchMaxId();
      Log.i(TAG, "Max id is:" + maxId);

      JSONArray jsonArray;

      try {
        jsonArray = mApi.getTimelineSinceId(maxId);
      } catch (IOException e) {
        Log.e(TAG, e.getMessage(), e);
        return RetrieveResult.IO_ERROR;
      } catch (AuthException e) {
        Log.i(TAG, "Invalid authorization.");
        return RetrieveResult.AUTH_ERROR;
      } catch (ApiException e) {
        Log.e(TAG, e.getMessage(), e);
        return RetrieveResult.IO_ERROR;
      }

      for (int i = 0; i < jsonArray.length(); ++i) {
        if (isCancelled()) {
          return RetrieveResult.CANCELLED;
        }

        Tweet tweet;

        try {
          JSONObject jsonObject = jsonArray.getJSONObject(i);
          tweet = Tweet.create(jsonObject);
        } catch (JSONException e) {
          Log.e(TAG, e.getMessage(), e);
          return RetrieveResult.IO_ERROR;
        }

        mNewTweets.add(tweet);
      }

      if (isCancelled()) {
        return RetrieveResult.CANCELLED;
      }

      maxId = mDb.fetchMaxDmId();
      Log.i(TAG, "Max DM id is:" + maxId);

      try {
        jsonArray = mApi.getDmsSinceId(maxId);
      } catch (IOException e) {
        Log.e(TAG, e.getMessage(), e);
        return RetrieveResult.IO_ERROR;
      } catch (AuthException e) {
        Log.i(TAG, "Invalid authorization.");
        return RetrieveResult.AUTH_ERROR;
      } catch (ApiException e) {
        Log.e(TAG, e.getMessage(), e);
        return RetrieveResult.IO_ERROR;
      }

      for (int i = 0; i < jsonArray.length(); ++i) {
        if (isCancelled()) {
          return RetrieveResult.CANCELLED;
        }

        Dm dm;

        try {
          JSONObject jsonObject = jsonArray.getJSONObject(i);
          dm = Dm.create(jsonObject, true);
        } catch (JSONException e) {
          Log.e(TAG, e.getMessage(), e);
          return RetrieveResult.IO_ERROR;
        }

        mNewDms.add(dm);
      }

      if (isCancelled()) {
        return RetrieveResult.CANCELLED;
      }

      return RetrieveResult.OK;
    }

    @Override
    public void onPostExecute(RetrieveResult result) {
      if (result == RetrieveResult.OK) {
        processNewTweets();
        processNewDms();
        schedule(TwitterService.this);
      } else if (result == RetrieveResult.IO_ERROR) {
        schedule(TwitterService.this);
      }

      stopSelf();
    }
  }

}
