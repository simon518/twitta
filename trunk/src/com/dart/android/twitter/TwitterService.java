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
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.dart.android.twitter.TwitterApi.AuthException;
import com.google.android.photostream.UserTask;

public class TwitterService extends Service {
  private static final String TAG = "TwitterService";
  
  private TwitterApi mApi;
  private TwitterDbAdapter mDb;
  private SharedPreferences mPreferences;  
  
  private NotificationManager mNotificationManager;
  
  private ArrayList<Tweet> mNewTweets;           
  
  private UserTask<Void, Void, RetrieveResult> mRetrieveTask;
  
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
  
  @Override
  public void onCreate() {
    super.onCreate();

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

    mNotificationManager =
      (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    
    mDb = new TwitterDbAdapter(this);
    mDb.open();                
    
    mNewTweets = new ArrayList<Tweet>();           
    
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
           
    Notification notification = new Notification(
        android.R.drawable.stat_notify_chat,
        latestTweet.text,
        System.currentTimeMillis());
    
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
    
    PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
        new Intent(this, LoginActivity.class), 0);
    
    notification.setLatestEventInfo(this, title, text, contentIntent);
    
    notification.flags = 
        Notification.FLAG_AUTO_CANCEL |
        Notification.FLAG_ONLY_ALERT_ONCE |
        Notification.FLAG_SHOW_LIGHTS;    
    
    if (mPreferences.getBoolean(Preferences.VIBRATE_KEY, false)) {    
      notification.defaults = Notification.DEFAULT_ALL;
    } else {
      notification.defaults = Notification.DEFAULT_SOUND |
          Notification.DEFAULT_LIGHTS;
    }
    
    mNotificationManager.notify(NOTIFICATION_ID, notification);       
  }
  
  @Override
  public void onDestroy() {
    Log.i(TAG, "IM DYING!!!");
    
    if (mRetrieveTask != null &&
        mRetrieveTask.getStatus() == UserTask.Status.RUNNING) {
      mRetrieveTask.cancel(true);
    }    
    
    if (mDb != null) {
      mDb.close();
    }
    
    super.onDestroy();
  }
          
  static void schedule(Context context) {    
    SharedPreferences preferences = 
        PreferenceManager.getDefaultSharedPreferences(context);
    
    String intervalPref = preferences.getString(
        Preferences.CHECK_UPDATE_INTERVAL_KEY,
        context.getString(
            R.string.pref_check_updates_interval_default));    
    int interval = Integer.parseInt(intervalPref);
    
    Intent intent = new Intent(context, TwitterService.class);
    PendingIntent pending = PendingIntent.getService(context, 0, intent, 0);

    Calendar c = new GregorianCalendar();
    c.add(Calendar.MINUTE, interval);
    
    DateFormat df = new SimpleDateFormat("h:mm a");
    Log.i(TAG, "Scheduling alarm at " + df.format(c.getTime()));

    AlarmManager alarm =
        (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    alarm.cancel(pending);
    alarm.set(AlarmManager.RTC, c.getTimeInMillis(), pending);
  }
  
  static void unschedule(Context context) {
    Intent intent = new Intent(context, TwitterService.class);
    PendingIntent pending = PendingIntent.getService(context, 0, intent, 0);
    AlarmManager alarm =
      (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
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
        e.printStackTrace();
        return RetrieveResult.IO_ERROR;
      } catch (AuthException e) {
        Log.i(TAG, "Invalid authorization.");
        return RetrieveResult.AUTH_ERROR;
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
          e.printStackTrace();
          return RetrieveResult.IO_ERROR;
        }
        
        mNewTweets.add(tweet);
        
        if (isCancelled()) {
          return RetrieveResult.CANCELLED;
        }                  
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
        schedule(TwitterService.this);
      } else if (result == RetrieveResult.IO_ERROR) {
        schedule(TwitterService.this);
      }
      
      stopSelf();      
    }
  }
  
}
