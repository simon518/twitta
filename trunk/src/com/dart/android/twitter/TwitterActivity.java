package com.dart.android.twitter;

import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.dart.android.twitter.TwitterApi.AuthException;
import com.google.android.photostream.UserTask;

public class TwitterActivity extends Activity {
  private static final String TAG = "TwitterActivity";
  
  private static final int MAX_TWEET_LENGTH = 140; 
    
  // Views.
  private ListView mTweetList;
  private TwitterAdapter mTweetAdapter;
  
  private EditText mTweetEdit;
  private ImageButton mSendButton;
  
  private TextView mCharsText;  
  private TextView mStatusText;
    
  // Sources.
  private TwitterApi mApi;
  private TwitterDbAdapter mDb;
  private ImageManager mImageManager;
  
  // Preferences.
  private SharedPreferences mPreferences;
 
  // Tasks.
  private UserTask<Void, Void, RetrieveResult> mRetrieveTask;
  private UserTask<Void, String, SendResult> mSendTask;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    mApi = new TwitterApi();
    mDb = new TwitterDbAdapter(this);
    mDb.open();
    mImageManager = new ImageManager();

    mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    
    setContentView(R.layout.main);
            
    mTweetList = (ListView) findViewById(R.id.tweet_list);
    
    mTweetEdit = (EditText) findViewById(R.id.tweet_edit);
    mTweetEdit.addTextChangedListener(mTextWatcher);
    mTweetEdit.setFilters(new InputFilter [] {
        new InputFilter.LengthFilter(MAX_TWEET_LENGTH) });
    mTweetEdit.setOnKeyListener(tweetEnterHandler);
    
    mCharsText = (TextView) findViewById(R.id.chars_text);
    mStatusText = (TextView) findViewById(R.id.status_text);
       
    mSendButton = (ImageButton) findViewById(R.id.send_button);    
    mSendButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        doSend();
      }
    });
    
    String username = mPreferences.getString(Preferences.USERNAME_KEY, "");
    String password = mPreferences.getString(Preferences.PASSWORD_KEY, "");
    mApi.setCredentials(username, password);
  
    setupAdapter();    
    doRetrieve();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);  
  }
  
  @Override
  protected void onRestoreInstanceState(Bundle bundle) {
    super.onRestoreInstanceState(bundle);

    // TODO: why do we need to do this? Can't we detect the box is being set?
    // Update the char remaining message.
    int remaining = MAX_TWEET_LENGTH - mTweetEdit.length();
    updateChars(remaining + "");
  } 
  
  @Override
  protected void onDestroy() {
    Log.i(TAG, "onDestroy");    
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

  private void setupAdapter() {
    Cursor cursor = mDb.fetchAllTweets();
    startManagingCursor(cursor);
    
    mTweetAdapter = new TwitterAdapter(this, cursor);
    mTweetList.setAdapter(mTweetAdapter);
  }
  
  private class TwitterAdapter extends CursorAdapter {

    public TwitterAdapter(Context context, Cursor cursor) {
      super(context, cursor);
      
      mInflater = LayoutInflater.from(context);                  
    }

    private LayoutInflater mInflater;
    
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
      return mInflater.inflate(R.layout.tweet, parent, false);
    }
    
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
      TextView tweetUserText;
      TextView tweetText;
      ImageView profileImage;
                  
      tweetUserText = (TextView) view.findViewById(R.id.tweet_user_text);
      tweetText = (TextView) view.findViewById(R.id.tweet_text);
      profileImage = (ImageView) view.findViewById(R.id.profile_image);
      
      int userTextColumn = cursor.getColumnIndexOrThrow(
          TwitterDbAdapter.KEY_USER);
      int textColumn = cursor.getColumnIndexOrThrow(
          TwitterDbAdapter.KEY_TEXT);
      int profileImageUrlColumn = cursor.getColumnIndexOrThrow(
          TwitterDbAdapter.KEY_PROFILE_IMAGE_URL);
      
      tweetUserText.setText(cursor.getString(userTextColumn));
      tweetText.setText(cursor.getString(textColumn));      
      Bitmap profileBitmap = mImageManager.lookup(
          cursor.getString(profileImageUrlColumn));
      
      if (profileBitmap != null) {
        profileImage.setImageBitmap(profileBitmap);        
      } 
    }    
    
  }
    
  private void updateTweets() {
    Cursor cursor = mDb.fetchAllTweets();
    startManagingCursor(cursor);
    mTweetAdapter.changeCursor(cursor);
  }
  
  private void enableEntry() {
    mTweetEdit.setEnabled(true);
    mSendButton.setEnabled(true);
  }

  private void disableEntry() {
    mTweetEdit.setEnabled(false);
    mSendButton.setEnabled(false);
  }
  
  // Actions.

  private void logout() {
    mDb.deleteAllTweets();
    
    SharedPreferences.Editor editor = mPreferences.edit();
    // It is very important to clear these out.
    // Or else LoginActivity may launch TwitterActivity again and so on. 
    editor.putString(Preferences.USERNAME_KEY, "");
    editor.putString(Preferences.PASSWORD_KEY, "");    
    editor.commit();
        
    Intent intent = new Intent(); 
    intent.setClass(this, LoginActivity.class); 
    startActivity(intent); 
    finish();           
  }
  
  private void doSend() {    
    if (mSendTask != null &&
        mSendTask.getStatus() == UserTask.Status.RUNNING) {
      Log.w(TAG, "Already sending.");      
    } else {
      mSendTask = new SendTask().execute();
    }
  }

  private enum SendResult {
    OK, IO_ERROR, AUTH_ERROR
  }
  
  private class SendTask extends UserTask<Void, String, SendResult> {
    @Override
    public void onPreExecute() {
      onSendBegin();      
    }
    
    @Override
    public SendResult doInBackground(Void... params) {      
      try {
        String status = mTweetEdit.getText().toString();
        mApi.update(status);
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
    updateChars("");
    enableEntry();    
    doRetrieve();
  }

  private void onSendFailure() {
    updateStatus("Unable to update status");
    enableEntry();
  }
  
  
  private void doRetrieve() {
    Log.i(TAG, "Attempting retrieve.");
   
    if (mRetrieveTask != null &&
        mRetrieveTask.getStatus() == UserTask.Status.RUNNING) {
      Log.w(TAG, "Already retrieving.");      
    } else {
      mRetrieveTask = new RetrieveTask().execute();
    }
  }
     
  private void onRetrieveBegin() {
    updateStatus("Refreshing...");
  }  
  
  private void onAuthFailure() {
    logout();
  }
    
  private enum RetrieveResult {
    OK, IO_ERROR, AUTH_ERROR
  }
  
  private class RetrieveTask extends UserTask<Void, Void, RetrieveResult> {
    @Override
    public void onPreExecute() {
      onRetrieveBegin();      
    }
    
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
            message = Utils.decodeTwitterJson(jsonObject.getString("text"));
          } 

          String screenName = "";
          String imageUrl = "";       
          if (jsonObject.has("user")) {
            JSONObject user = jsonObject.getJSONObject("user");
            if (user.has("screen_name")) {
              screenName = Utils.decodeTwitterJson(
                  user.getString("screen_name"));
            }
            if (user.has("profile_image_url")) {
              imageUrl = user.getString("profile_image_url");
            }          
          }      
                  
          mDb.createTweet(tweetId, screenName, message, imageUrl);
          
          if (imageUrl != null &&
              mImageManager.lookup(imageUrl) == null) {            
            // Download image to cache.
            try {
              mImageManager.fetch(imageUrl);
            } catch (IOException e) {
              e.printStackTrace();            
            }
          }          
        } catch (JSONException e) {
          e.printStackTrace();
          return RetrieveResult.IO_ERROR;
        }      
      }      
      
      return RetrieveResult.OK;
    }

    @Override    
    public void onProgressUpdate(Void... progress) {
    }
    
    @Override
    public void onPostExecute(RetrieveResult result) {
      if (result == RetrieveResult.AUTH_ERROR) { 
        onAuthFailure();
      } else if (result == RetrieveResult.OK) {
        updateTweets();
      } else {
        // Do nothing.
      }
      
      updateStatus("");      
    }
  }
  
  // Menu.
  
  private static final int OPTIONS_MENU_ID_LOGOUT = 1;
  private static final int OPTIONS_MENU_ID_REFRESH = 2;
  
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);

    MenuItem item = menu.add(0, OPTIONS_MENU_ID_LOGOUT, 0,
        R.string.signout);           
    item.setIcon(android.R.drawable.ic_menu_revert);
    
    item = menu.add(0, OPTIONS_MENU_ID_REFRESH, 0,
        R.string.refresh);           
    item.setIcon(android.R.drawable.stat_notify_sync);    
        
    return true;
  }    

  @Override
  public boolean onOptionsItemSelected(MenuItem item){
    switch (item.getItemId()) {
      case OPTIONS_MENU_ID_LOGOUT:
        logout();
        return true;
      case OPTIONS_MENU_ID_REFRESH:
        doRetrieve();
        return true;        
    }

    return super.onOptionsItemSelected(item);
  }
  
  // Various handlers.  
 
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