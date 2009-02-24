package com.dart.android.twitter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

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

public class TwitterService extends Service {
  private static final String TAG = "TwitterService";
  
  private static final int REFRESH_INTERVAL = 30 * 1000;

  // Sources.
  private TwitterApi mApi;
  private TwitterDbAdapter mDb;
    
  // Preferences.
  private SharedPreferences mPreferences;    
  
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
  
  @Override
  public void onCreate() {
    super.onCreate();

    mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    String username = mPreferences.getString(Preferences.USERNAME_KEY, "");
    String password = mPreferences.getString(Preferences.PASSWORD_KEY, "");    
        
    mApi = new TwitterApi();
    mDb = new TwitterDbAdapter(this);
    mDb.open();
            
    if (!TwitterApi.isValidCredentials(username, password)) {
      Log.i(TAG, "No credentials.");
      stopSelf();      
      return;
    }        
    
    mApi.setCredentials(username, password);
    
    // TODO: create check update task.
    // Check for updates.
    int maxId = mDb.fetchMaxId();
    
    Log.i(TAG, "Max id is:" + maxId);
    
    // Insert into database in a batch.    
    // If > 1 or others ... say blah new ones.
    
    NotificationManager notificationManager =
      (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    
    Notification notification = new Notification(
        android.R.drawable.stat_notify_chat,
        "snippet goes here",
        System.currentTimeMillis());
    
    PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
        new Intent(this, LoginActivity.class), 0);
    
    notification.setLatestEventInfo(this, "foo",
        "bar", contentIntent);
    
    // Send the notification.
    // We use a string id because it is a unique number.  We use it later to cancel.
    notificationManager.notify(0, notification);
    
    // TODO: vibrate and play sound.
        
    // TODO: should reschedule upon successful task execution.
    schedule(this);
    stopSelf();
  }
  
  @Override
  public void onDestroy() {
    // TODO: kill tasks.
    
    mDb.close();
    Log.i(TAG, "IM DYING!!!");
    
    super.onDestroy();
  }
        
  static void schedule(Context context) {
    Intent intent = new Intent(context, TwitterService.class);
    PendingIntent pending = PendingIntent.getService(context, 0, intent, 0);

    Calendar c = new GregorianCalendar();
    c.add(Calendar.MINUTE, 1);
    
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
  
}
