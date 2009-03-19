package com.dart.android.twitter;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

public class BaseActivity extends Activity {

  protected static final int MAX_TWEET_LENGTH = 140;
  protected static final int MAX_TWEET_INPUT_LENGTH = 400;

  protected SharedPreferences mPreferences;    
  protected TwitterApi mApi;
  protected TwitterDbAdapter mDb;
  protected static ImageManager mImageManager;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
   
    PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
    
    mPreferences = PreferenceManager.getDefaultSharedPreferences(this);        
    
    String username = mPreferences.getString(Preferences.USERNAME_KEY, "");
    String password = mPreferences.getString(Preferences.PASSWORD_KEY, "");
    
    mApi = new TwitterApi();
    mApi.setCredentials(username, password);
        
    if (mImageManager == null) {
      mImageManager = new ImageManager(this);
    }
    mDb = new TwitterDbAdapter(this);
    mDb.open();
  }
  
  @Override
  protected void onDestroy() {
    mDb.close();

    super.onDestroy();
  }
  
  protected void logout() {
    TwitterService.unschedule(this);

    mDb.clearData();

    // It is very important to clear preferences,
    // in particular the username and password, or else
    // LoginActivity may launch TwitterActivity again because
    // it thinks there are valid credentials.
    SharedPreferences.Editor editor = mPreferences.edit();
    editor.clear();
    editor.commit();

    // Let's cleanup files while we're at it.
    mImageManager.clear();

    Intent intent = new Intent();
    intent.setClass(this, LoginActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);    
    startActivity(intent);
    finish();
  }
    
}
