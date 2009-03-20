package com.dart.android.twitter;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Selection;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

/**
 * A BaseActivity has common routines and variables for an Activity
 * that contains a list of tweets and a text input field.
 * 
 * Not the cleanest design, but works okay for several Activities in this app.
 */

public class BaseActivity extends Activity {
  
  private static final String TAG = "BaseActivity";

  protected SharedPreferences mPreferences;    
  protected TwitterApi mApi;
  protected TwitterDbAdapter mDb;
  protected static ImageManager sImageManager;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
   
    PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
    
    mPreferences = PreferenceManager.getDefaultSharedPreferences(this);        

    manageUpdateChecks();
    
    String username = mPreferences.getString(Preferences.USERNAME_KEY, "");
    String password = mPreferences.getString(Preferences.PASSWORD_KEY, "");
    
    mApi = new TwitterApi();
    mApi.setCredentials(username, password);
        
    if (sImageManager == null) {
      Log.i(TAG, "Creating ImageManager.");
      sImageManager = new ImageManager(this);
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
    sImageManager.clear();

    Intent intent = new Intent();
    intent.setClass(this, LoginActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);    
    startActivity(intent);
    finish();
  }

  protected void manageUpdateChecks() {
    boolean isEnabled = mPreferences.getBoolean(
        Preferences.CHECK_UPDATES_KEY, false);
      
    if (isEnabled) {
      TwitterService.schedule(this);
    } else {
      TwitterService.unschedule(this);
    }
  }
  
  // Menus.
  
  protected static final int OPTIONS_MENU_ID_LOGOUT = 1;
  protected static final int OPTIONS_MENU_ID_PREFERENCES = 2;
  protected static final int OPTIONS_MENU_ID_ABOUT = 3;

  protected static final int OPTIONS_MENU_ID_REFRESH = 4;
  protected static final int OPTIONS_MENU_ID_REPLIES = 5;  
  protected static final int OPTIONS_MENU_ID_DM = 6;

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);

    MenuItem item = menu.add(0, OPTIONS_MENU_ID_LOGOUT, 0, R.string.signout);
    item.setIcon(android.R.drawable.ic_menu_revert);

    item = menu.add(0, OPTIONS_MENU_ID_PREFERENCES, 0, R.string.preferences);
    item.setIcon(android.R.drawable.ic_menu_preferences);

    item = menu.add(0, OPTIONS_MENU_ID_ABOUT, 0, R.string.about);
    item.setIcon(android.R.drawable.ic_menu_info_details);

    return true;
  }

  private static final int REQUEST_CODE_PREFERENCES = 1;
  
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case OPTIONS_MENU_ID_LOGOUT:
      logout();
      return true;
    case OPTIONS_MENU_ID_PREFERENCES:
      Intent launchPreferencesIntent = new Intent().setClass(this,
          PreferencesActivity.class);
      startActivityForResult(launchPreferencesIntent, REQUEST_CODE_PREFERENCES);
      return true;
    case OPTIONS_MENU_ID_ABOUT:
      AboutDialog.show(this);
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == REQUEST_CODE_PREFERENCES && resultCode == RESULT_OK) {
      manageUpdateChecks();
    }
  }

}
