package com.dart.android.twitter;

import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.dart.android.twitter.TwitterApi.AuthException;
import com.google.android.photostream.UserTask;

public class Twitter extends Activity {
  private static final String TAG = "Twitter";
  
  private static final int MAX_TWEET_LENGTH = 140; 
    
  // Views.
  private View mLoginUi;
  private ListView mTweetList;
  
  private EditText mUsernameEdit;
  private EditText mPasswordEdit;
  private EditText mTweetEdit;
  
  private TextView mCharsText;  
  private TextView mStatusText;
  
  private Button mSigninButton;
  private ImageButton mSendButton;
  
  // Sources.
  private TwitterApi mApi;
  private TwitterDbAdapter mDb;
  
  // Preferences.
  // Shared because the update check service needs the credentials too.
  private SharedPreferences mPreferences;    
  private SharedPreferences.Editor mEditor;
  
  private static final String PREFERENCES_USERNAME_KEY = "username";
  private static final String PREFERENCES_PASSWORD_KEY = "password";
        
  // Tasks.
  private UserTask<Void, String, Boolean> mLoginTask;
  private UserTask<Void, String, RetrieveResult> mRetrieveTask;
  private UserTask<Void, String, SendResult> mSendTask;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    mApi = new TwitterApi();
    mDb = new TwitterDbAdapter(this);
    mDb.open();    
    mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    
    setContentView(R.layout.main);
            
    mTweetList = (ListView) findViewById(R.id.tweet_list);
    mLoginUi = (LinearLayout) findViewById(R.id.login_ui);

    mUsernameEdit = (EditText) findViewById(R.id.username_edit);
    mPasswordEdit = (EditText) findViewById(R.id.password_edit);
    
    mTweetEdit = (EditText) findViewById(R.id.tweet_edit);
    mTweetEdit.addTextChangedListener(mTextWatcher);
    mTweetEdit.setFilters(new InputFilter [] {
        new InputFilter.LengthFilter(MAX_TWEET_LENGTH) });
    mTweetEdit.setOnKeyListener(tweetEnterHandler);
    
    mCharsText = (TextView) findViewById(R.id.chars_text);
    mStatusText = (TextView) findViewById(R.id.status_text);
       
    mUsernameEdit.setOnKeyListener(loginEnterHandler);
    mPasswordEdit.setOnKeyListener(loginEnterHandler);
    
    mSigninButton = (Button) findViewById(R.id.signin_button);
    mSendButton = (ImageButton) findViewById(R.id.send_button);
    
    mSigninButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        doLogin();
      }
    });    

    mSendButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        doSend();
      }
    });
    
    String username = mPreferences.getString(PREFERENCES_USERNAME_KEY, "");
    String password = mPreferences.getString(PREFERENCES_PASSWORD_KEY, "");

    if (isValidCredentials(username, password)) {
      Log.i(TAG, "Setting credentials.");
      mApi.setCredentials(username, password);
      doRetrieve();
    } else {      
      switchLoginUi();
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);  
  }
  
  @Override
  protected void onRestoreInstanceState(Bundle bundle) {
    super.onRestoreInstanceState(bundle);

    // Update the char remaining message.
    int remaining = MAX_TWEET_LENGTH - mTweetEdit.length();
    updateChars(remaining + "");
  } 
  
  @Override
  protected void onDestroy() {    
    mDb.close();    
    
    super.onDestroy();    
  }
    
  // UI helpers.

  private void updateStatus(String status) {
    mStatusText.setText(status);
  }
  
  private void updateChars(String chars) {
    mCharsText.setText(chars);
  }
    
  private void switchLoginUi() {
    mLoginUi.setVisibility(View.VISIBLE);    
    mTweetList.setVisibility(View.GONE);
    enableLogin();
    disableEntry();
    // TODO: cancel pending stuff?
    // TODO: kill any timers.
  }

  private void switchTweetsUi() {
    mLoginUi.setVisibility(View.GONE);    
    mTweetList.setVisibility(View.VISIBLE);
    enableEntry();
    // TODO: cancel pending stuff?    
  }
  
  private void updateTweets() {
    Cursor cursor = mDb.fetchAllTweets();
    startManagingCursor(cursor);
    
    String[] from = new String[] { 
        TwitterDbAdapter.KEY_USER,
        TwitterDbAdapter.KEY_TEXT
    };
    
    int[] to = new int[] { R.id.tweet_user_text, R.id.tweet_text };
    
    SimpleCursorAdapter tweets = 
          new SimpleCursorAdapter(this, R.layout.tweet, cursor, from, to);
    mTweetList.setAdapter(tweets); 
  }

  
  private void enableLogin() {
    mUsernameEdit.setEnabled(true);
    mPasswordEdit.setEnabled(true);
    mSigninButton.setEnabled(true);    
  }

  private void disableLogin() {
    mUsernameEdit.setEnabled(false);
    mPasswordEdit.setEnabled(false);
    mSigninButton.setEnabled(false);    
  }

  private void enableEntry() {
    mTweetEdit.setEnabled(true);
    mSendButton.setEnabled(true);
  }

  private void disableEntry() {
    mTweetEdit.setEnabled(false);
    mSendButton.setEnabled(false);
  }
  
  // Helpers.
  
  public static boolean isValidCredentials(String username, String password) {
    return !Utils.isEmpty(username) && !Utils.isEmpty(password);
  }
  
  // Actions.

  private void doLogin() {
    mLoginTask = new LoginTask().execute();
  }      

  private void onLoginBegin() {
    disableLogin();        
  }
  
  private void onLoginSuccess() {
    updateStatus("");
    
    String username = mUsernameEdit.getText().toString();
    String password = mPasswordEdit.getText().toString();
    mUsernameEdit.setText("");
    mPasswordEdit.setText("");
        
    Log.i(TAG, "Storing credentials.");
    SharedPreferences.Editor editor = mPreferences.edit();
    editor.putString(PREFERENCES_USERNAME_KEY, username);
    editor.putString(PREFERENCES_PASSWORD_KEY, password);
    editor.commit();
    
    switchTweetsUi();
    doRetrieve();    
  }

  private void onLoginFailure() {
    enableLogin();
  }
  
  private void logout() {    
    SharedPreferences.Editor editor = mPreferences.edit();
    editor.putString(PREFERENCES_USERNAME_KEY, "");
    editor.putString(PREFERENCES_PASSWORD_KEY, "");
    editor.commit();
    
    mApi.setCredentials("", "");
    mDb.deleteAllTweets();
    updateTweets();    
    
    // TODO: stop pending tasks?
    updateStatus("");
    mUsernameEdit.setText("");
    mPasswordEdit.setText("");
    mTweetEdit.setText("");
    switchLoginUi();
  }
  
  private void doSend() {
    mSendTask = new SendTask().execute();
  }

  private void doRetrieve() {
    Log.i(TAG, "Attempting retrieve.");

    mRetrieveTask = new RetrieveTask().execute();    
  }
   
  private class LoginTask extends UserTask<Void, String, Boolean> {
    @Override
    public void onPreExecute() {
      onLoginBegin();      
    }

    @Override    
    public Boolean doInBackground(Void... params) {
      String username = mUsernameEdit.getText().toString();
      String password = mPasswordEdit.getText().toString();
            
      publishProgress("Logging in...");

      if (!isValidCredentials(username, password)) {
        publishProgress("Invalid username or password");
        return false;
      }    
      
      try {
        mApi.login(username, password);
      } catch (IOException e) {
        e.printStackTrace();
        publishProgress("Network or connection error");
        return false;
      } catch (AuthException e) {
        publishProgress("Invalid username or password");
        return false;
      }    
            
      return true;
    }

    @Override    
    public void onProgressUpdate(String... progress) {
      updateStatus(progress[0]);
    }
    
    @Override
    public void onPostExecute(Boolean result) {
      if (result) { 
        onLoginSuccess();
      } else {
        onLoginFailure();
      }      
    }
  }
  
  private void onRetrieveSuccess() {
    updateTweets();
  }

  private void onAuthFailure() {
    logout();
  }
  
  private enum SendResult {
    OK, IO_ERROR, AUTH_ERROR, JSON_ERROR
  }
  
  private class SendTask extends UserTask<Void, String, SendResult> {
    @Override
    public void onPreExecute() {
      onSendBegin();      
    }
    
    @Override
    public SendResult doInBackground(Void... params) {
      JSONObject jsonObject;
      
      try {
        String status = mTweetEdit.getText().toString();
        jsonObject = mApi.update(status);
      } catch (IOException e) {
        e.printStackTrace();
        return SendResult.IO_ERROR;
      } catch (AuthException e) {
        Log.i(TAG, "Invalid authorization.");
        return SendResult.AUTH_ERROR;
      }
      
      return SendResult.OK;
    }
    
    @Override
    public void onPostExecute(SendResult result) {
      if (result == SendResult.AUTH_ERROR) { 
        onAuthFailure();
      } else if (result == SendResult.OK) {
        onSendSuccess();
      } else if (result == SendResult.IO_ERROR) {
        onSendFailure();
      }
    }
  }
  
  private void onSendBegin() {
    disableEntry();
    updateStatus("Updating status...");
  }  

  private void onSendSuccess() {
    mTweetEdit.setText("");
    updateStatus("");
    enableEntry();    
    doRetrieve();
  }

  private void onSendFailure() {
    updateStatus("Unable to update status");
    enableEntry();
  }
  
  
  private enum RetrieveResult {
    OK, IO_ERROR, AUTH_ERROR
  }
  
  private class RetrieveTask extends UserTask<Void, String, RetrieveResult> {
    @Override
    public RetrieveResult doInBackground(Void... params) {
      JSONArray jsonArray;
      
      try {
        jsonArray = mApi.getTimeline();
      } catch (IOException e) {
        e.printStackTrace();
        return RetrieveResult.IO_ERROR;
      } catch (AuthException e) {
        Log.i(TAG, "Invalid authorization.");
        return RetrieveResult.AUTH_ERROR;
      }
      
      for (int i = 0; i < jsonArray.length(); ++i) {
        try {
          JSONObject jsonObject = jsonArray.getJSONObject(i);
           
          String tweetId = "";      
          if (jsonObject.has("id")) {
            tweetId = jsonObject.getLong("id") + "";
          }
          
          String message = "";  
          if (jsonObject.has("text")) {
            message = jsonObject.getString("text");
          } 

          String screenName = "";
          String imageUrl = "";       
          if (jsonObject.has("user")) {
            JSONObject user = jsonObject.getJSONObject("user");
            if (user.has("screen_name")) {
              screenName = user.getString("screen_name");
            }
            if (user.has("profile_image_url")) {
              imageUrl = user.getString("profile_image_url");
            }          
          }      
                  
          // TODO: could be interrupted. Should add to a temp array.
          mDb.createTweet(tweetId, screenName, message, imageUrl);      
        } catch (JSONException e) {
          e.printStackTrace();
          return RetrieveResult.IO_ERROR;
        }      
      }      
      
      return RetrieveResult.OK;
    }

    @Override    
    public void onProgressUpdate(String... progress) {
      updateStatus(progress[0]);
    }
    
    @Override
    public void onPostExecute(RetrieveResult result) {
      if (result == RetrieveResult.AUTH_ERROR) { 
        onAuthFailure();
      } else if (result == RetrieveResult.OK) {
        onRetrieveSuccess();
      } else {
        // Do nothing.
      }
    }
  }
  
  // Various handlers.
  
  private static final int OPTIONS_MENU_ID_LOGOUT = 1;
  
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);

    MenuItem item = menu.add(0, OPTIONS_MENU_ID_LOGOUT, 0,
        R.string.signout);           
    item.setIcon(android.R.drawable.ic_notification_clear_all);    
        
    return true;
  }    

  @Override
  public boolean onOptionsItemSelected(MenuItem item){
    switch (item.getItemId()) {
      case OPTIONS_MENU_ID_LOGOUT:
        logout();
        return true;
    }

    return super.onOptionsItemSelected(item);
  }
 
  private TextWatcher mTextWatcher = new TextWatcher() {
    @Override
    public void afterTextChanged(Editable e) {
      int remaining = MAX_TWEET_LENGTH - e.length();            
      updateChars(remaining + "");
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,
        int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before,
        int count) {      
    }    
  };
  
  private View.OnKeyListener loginEnterHandler = new View.OnKeyListener() {
    public boolean onKey(View v, int keyCode, KeyEvent event) {
      if (keyCode == KeyEvent.KEYCODE_ENTER) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
          doLogin();
        }
        return true;
      }      
      return false;
    }
  };

  private View.OnKeyListener tweetEnterHandler = new View.OnKeyListener() {
    public boolean onKey(View v, int keyCode, KeyEvent event) {
      if (keyCode == KeyEvent.KEYCODE_ENTER) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
          doSend();
        }
        return true;
      }      
      return false;
    }
  };
    
}