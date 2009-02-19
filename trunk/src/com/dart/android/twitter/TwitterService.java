package com.dart.android.twitter;

import java.io.IOException;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

import com.dart.android.twitter.TwitterApi.AuthException;

public class TwitterService extends Service {

  private static final String TAG = "TwitterService";
  
  private static final int REFRESH_INTERVAL = 10 * 1000;

  public class LocalBinder extends Binder {
    TwitterService getService() {
      return TwitterService.this;
    }
  }
  
  private TwitterApi mApi;
    
  private final IBinder mBinder = new LocalBinder();

  private TwitterActivity mClient;
    
  private SharedPreferences mPreferences;    
  private SharedPreferences.Editor mEditor;
  
  private TwitterDbAdapter mDbHelper;    
  
  private static final String PREFERENCES_USERNAME_KEY = "username";
  private static final String PREFERENCES_PASSWORD_KEY = "password";
    
  private void setCredentials(String username, String password) {
    mEditor.putString(PREFERENCES_USERNAME_KEY, username);
    mEditor.putString(PREFERENCES_PASSWORD_KEY, password);
    mEditor.commit();
  }
  
  public boolean hasCredentials() {
    String username = mPreferences.getString(PREFERENCES_USERNAME_KEY, "");
    String password = mPreferences.getString(PREFERENCES_PASSWORD_KEY, "");
    
    return true;
    // return TwitterActivity.isValidCredentials(username, password);
  }

  public void login(String username, String password) throws 
      IOException, AuthException {
    mApi.login(username, password);    
    setCredentials(username, password);
  }
  
  public void logout() {
    setCredentials("", "");
    mDbHelper.deleteAllTweets();        
  }  
  
  @Override
  public IBinder onBind(Intent intent) {
    return mBinder;
  }
  
  public void setClient(TwitterActivity client) {
    mClient = client;
  }
  
  public void retrieve() throws IOException, AuthException {
  }

  
  @Override
  public void onCreate() {
    super.onCreate();

    mDbHelper = new TwitterDbAdapter(this);
    mDbHelper.open();
            
    mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    mEditor = mPreferences.edit();    
        
    String username = mPreferences.getString(PREFERENCES_USERNAME_KEY, "");
    String password = mPreferences.getString(PREFERENCES_PASSWORD_KEY, "");
    
    mApi = new TwitterApi();
    
    /*
    if (TwitterActivity.isValidCredentials(username, password)) {
      mApi.setCredentials(username, password);
    }
    */
    
    mHandler.sendEmptyMessage(0);    
  }
  
  @Override
  public void onDestroy() {
    // Right order?
    super.onDestroy();
    
    mDbHelper.close();
    Log.i(TAG, "IM DYING");
  }
     
  private int mValue = 0;
  
  private final Handler mHandler = new Handler() {
    @Override public void handleMessage(Message msg) {
      Log.i(TAG, ++mValue + "");
              
      sendMessageDelayed(obtainMessage(0), REFRESH_INTERVAL);
    }
  };

  public void send(String text) {
  }

  public Cursor fetchAllTweets() {
    return mDbHelper.fetchAllTweets();
  }
  
}
