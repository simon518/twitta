package com.dart.android.twitter;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;

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
import android.text.Selection;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.dart.android.twitter.TwitterApi.AuthException;
import com.google.android.photostream.UserTask;

public class TwitterActivity extends Activity {
  private static final String TAG = "TwitterActivity";
  
  private static final int MAX_TWEET_LENGTH = 140; 
    
  // Views.
  private ListView mTweetList;
  private TweetAdapter mTweetAdapter;
  
  private EditText mTweetEdit;
  private ImageButton mSendButton;
  
  private TextView mCharsRemainText;  
  private TextView mProgressText;
    
  // Sources.
  private TwitterApi mApi;
  private TwitterDbAdapter mDb;
  private ImageManager mImageManager;
  
  // Preferences.
  private SharedPreferences mPreferences;
 
  // Tasks.
  private UserTask<Void, Void, RetrieveResult> mRetrieveTask;
  private UserTask<Void, String, SendResult> mSendTask;

  private void controlUpdateChecks() {
    if (mPreferences.getBoolean(Preferences.CHECK_UPDATES_KEY, false)) {
      TwitterService.schedule(this);          
    } else {
      TwitterService.unschedule(this);
    }       
  }
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    PreferenceManager.setDefaultValues(this, R.xml.preferences, false);        
        
    mApi = new TwitterApi();
    mDb = new TwitterDbAdapter(this);
    mDb.open();
    mImageManager = new ImageManager();

    mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
     
    controlUpdateChecks();
    
    setContentView(R.layout.main);
            
    mTweetList = (ListView) findViewById(R.id.tweet_list);
    
    mTweetEdit = (EditText) findViewById(R.id.tweet_edit);
    mTweetEdit.addTextChangedListener(mTextWatcher);
    mTweetEdit.setFilters(new InputFilter [] {
        new InputFilter.LengthFilter(MAX_TWEET_LENGTH) });
    mTweetEdit.setOnKeyListener(tweetEnterHandler);
    
    mCharsRemainText = (TextView) findViewById(R.id.chars_text);
    mProgressText = (TextView) findViewById(R.id.progress_text);
       
    mSendButton = (ImageButton) findViewById(R.id.send_button);    
    mSendButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        doSend();
      }
    });
    
    String username = mPreferences.getString(Preferences.USERNAME_KEY, "");
    String password = mPreferences.getString(Preferences.PASSWORD_KEY, "");
    mApi.setCredentials(username, password);
  
    // Nice optimization which can preserve objects in an Activity 
    // that the system wants to destroyed and recreated immediately.
    // See Activity doc for more.
    Object data = getLastNonConfigurationInstance();
    
    if (data == null) {    
      doRetrieve();
    } else {
      // Use the ImageManager from previous Activity instance.
      mImageManager = ((NonConfigurationState) data).imageManager;
    }
    
    setupAdapter();       
  }

  @Override
  public Object onRetainNonConfigurationInstance() {
    // Save ImageManager for new Activity instance.
    return new NonConfigurationState(mImageManager);  
  }
    
  private class NonConfigurationState {
    public ImageManager imageManager;

    NonConfigurationState(ImageManager imageManager) {
      this.imageManager = imageManager;
    }    
  }
    
  @Override
  protected void onPause() {
    Log.i(TAG, "onPause.");
    // Update checker should be active when Activity is paused. 
    controlUpdateChecks();        
    super.onPause();    
  }

  @Override
  protected void onResume() {
    Log.i(TAG, "onResume.");
    super.onResume();
    // Try to prevent update checks while activity is visible.
    TwitterService.unschedule(this);    
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
    updateCharsRemain(remaining + "");
  } 
  
  @Override
  protected void onDestroy() {
    Log.i(TAG, "onDestroy.");
    
    if (mSendTask != null &&
        mSendTask.getStatus() == UserTask.Status.RUNNING) {
      mSendTask.cancel(true);
    }

    if (mRetrieveTask != null &&
        mRetrieveTask.getStatus() == UserTask.Status.RUNNING) {
      mRetrieveTask.cancel(true);
    }

    mDb.close();        
    
    super.onDestroy();    
  }
    
  // UI helpers.

  private void updateProgress(String progress) {
    mProgressText.setText(progress);
  }
  
  private void updateCharsRemain(String remaining) {
    mCharsRemainText.setText(remaining);
  }

  private void setupAdapter() {
    Cursor cursor = mDb.fetchAllTweets();
    startManagingCursor(cursor);
    
    mTweetAdapter = new TweetAdapter(this, cursor);
    mTweetList.setAdapter(mTweetAdapter);
    registerForContextMenu(mTweetList);    
  }
  
  private static final int CONTEXT_REPLY_ID = 0;
  
  @Override
  public void onCreateContextMenu(ContextMenu menu, View v,
      ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    menu.add(0, CONTEXT_REPLY_ID, 0, R.string.reply);
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
    Cursor cursor = (Cursor) mTweetAdapter.getItem(info.position);
    
    if (cursor == null) {
      Log.w(TAG, "Selected item not available.");
      return super.onContextItemSelected(item);     
    }
    
    switch (item.getItemId()) {
      case CONTEXT_REPLY_ID:
        int userIndex = cursor.getColumnIndexOrThrow(TwitterDbAdapter.KEY_USER);
        // TODO: this isn't quite perfect. Leaves empty spaces if you
        // perform the reply action again.
        String replyTo = "@" + cursor.getString(userIndex);                               
        String text = mTweetEdit.getText().toString();
        text = replyTo + " " + text.replace(replyTo, "");
        mTweetEdit.setText(text);
        Editable editable = mTweetEdit.getText();
        int position = editable.length();
        Selection.setSelection(editable, position);
        
        mTweetEdit.requestFocus();
        
        // TODO: why do we need to do this?
        // Can't we detect the box is being set?
        int remaining = MAX_TWEET_LENGTH - mTweetEdit.length();
        updateCharsRemain(remaining + "");
        
        return true;
      default:
        return super.onContextItemSelected(item);
    }
  }

  private class TweetAdapter extends CursorAdapter {

    public TweetAdapter(Context context, Cursor cursor) {
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
      TextView metaText;
                  
      tweetUserText = (TextView) view.findViewById(R.id.tweet_user_text);
      tweetText = (TextView) view.findViewById(R.id.tweet_text);
      profileImage = (ImageView) view.findViewById(R.id.profile_image);
      metaText = (TextView) view.findViewById(R.id.tweet_meta_text);
      
      int userTextColumn = cursor.getColumnIndexOrThrow(
          TwitterDbAdapter.KEY_USER);
      int textColumn = cursor.getColumnIndexOrThrow(
          TwitterDbAdapter.KEY_TEXT);
      int profileImageUrlColumn = cursor.getColumnIndexOrThrow(
          TwitterDbAdapter.KEY_PROFILE_IMAGE_URL);
      int createdAtColumn = cursor.getColumnIndexOrThrow(
          TwitterDbAdapter.KEY_CREATED_AT);
      
      tweetUserText.setText(cursor.getString(userTextColumn));
      tweetText.setText(cursor.getString(textColumn));
      
      String profileImageUrl = cursor.getString(profileImageUrlColumn);
      
      if (!Utils.isEmpty(profileImageUrl)) {
        Bitmap profileBitmap = mImageManager.lookup(profileImageUrl);
        
        if (profileBitmap != null) {
          profileImage.setImageBitmap(profileBitmap);        
        }         
      }      
      
      String meta = "";
      
      String createdAtString = cursor.getString(createdAtColumn);      
      Date createdAt;
      
      try {
        createdAt = TwitterDbAdapter.DB_DATE_FORMATTER.parse(
            createdAtString);
        meta += Tweet.getRelativeDate(createdAt) + " ";
      } catch (ParseException e) {
        Log.w(TAG, "Invalid created at data.");
      }
      
      metaText.setText(meta);      
    }    
    
    public void refresh() {
      getCursor().requery();
    }
    
  }
    
  private void update() {
    mTweetAdapter.refresh();
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
    TwitterService.unschedule(this);
    
    mDb.deleteAllTweets();
    
    // It is very important to clear preferences,
    // in particular the username and password, or else
    // LoginActivity may launch TwitterActivity again because
    // it thinks there are valid credentials.
    SharedPreferences.Editor editor = mPreferences.edit();
    editor.clear();
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
      String status = mTweetEdit.getText().toString();
      
      if (!Utils.isEmpty(status)) {
        mSendTask = new SendTask().execute();
      }
    }
  }

  private enum SendResult {
    OK, IO_ERROR, AUTH_ERROR, CANCELLED
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
    updateProgress("Updating status...");
  }  

  private void onSendSuccess() {
    mTweetEdit.setText("");
    updateProgress("");
    updateCharsRemain("");
    enableEntry();    
    doRetrieve();
  }

  private void onSendFailure() {
    updateProgress("Unable to update status");
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
    updateProgress("Refreshing...");
  }  
  
  private void onAuthFailure() {
    logout();
  }
    
  private enum RetrieveResult {
    OK, IO_ERROR, AUTH_ERROR, CANCELLED
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
      
      ArrayList<Tweet> tweets = new ArrayList<Tweet>(); 
      
      for (int i = 0; i < jsonArray.length(); ++i) {
        if (isCancelled()) {
          return RetrieveResult.CANCELLED;
        }
        
        Tweet tweet;
        
        try {
          JSONObject jsonObject = jsonArray.getJSONObject(i);
          tweet = Tweet.create(jsonObject);                           
          tweets.add(tweet);
        } catch (JSONException e) {
          e.printStackTrace();
          return RetrieveResult.IO_ERROR;
        }      
          
        if (isCancelled()) {
          return RetrieveResult.CANCELLED;
        }
        
        if (!Utils.isEmpty(tweet.profileImageUrl) &&
            !mImageManager.contains(tweet.profileImageUrl)) {
          // Fetch image to cache.
          try {
            mImageManager.fetch(tweet.profileImageUrl);
          } catch (IOException e) {
            e.printStackTrace();            
          }
        }          
      }      
      
      if (isCancelled()) {
        return RetrieveResult.CANCELLED;
      }
      
      mDb.syncTweets(tweets);
      
      return RetrieveResult.OK;
    }

    @Override
    public void onPostExecute(RetrieveResult result) {
      if (result == RetrieveResult.AUTH_ERROR) { 
        onAuthFailure();
      } else if (result == RetrieveResult.OK) {
        update();
      } else {
        // Do nothing.
      }
      
      updateProgress("");      
    }
  }
  
  // Menu.
  
  private static final int OPTIONS_MENU_ID_LOGOUT = 1;
  private static final int OPTIONS_MENU_ID_REFRESH = 2;
  private static final int OPTIONS_MENU_ID_PREFERENCES = 3;
  
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);

    MenuItem item = menu.add(0, OPTIONS_MENU_ID_LOGOUT, 0,
        R.string.signout);           
    item.setIcon(android.R.drawable.ic_menu_revert);

    item = menu.add(0, OPTIONS_MENU_ID_PREFERENCES, 0,
        R.string.preferences);
    item.setIcon(android.R.drawable.ic_menu_preferences);    
        
    item = menu.add(0, OPTIONS_MENU_ID_REFRESH, 0,
        R.string.refresh);           
    item.setIcon(android.R.drawable.stat_notify_sync);    

    return true;
  }    

  private static final int REQUEST_CODE_PREFERENCES = 1;
  
  @Override
  public boolean onOptionsItemSelected(MenuItem item){
    switch (item.getItemId()) {
      case OPTIONS_MENU_ID_LOGOUT:
        logout();
        return true;
      case OPTIONS_MENU_ID_REFRESH:
        doRetrieve();
        return true;       
      case OPTIONS_MENU_ID_PREFERENCES:
        Intent launchPreferencesIntent =
          new Intent().setClass(this, PreferencesActivity.class);          
        startActivityForResult(launchPreferencesIntent,
          REQUEST_CODE_PREFERENCES);
    }

    return super.onOptionsItemSelected(item);
  }
  
  @Override
  protected void onActivityResult(int requestCode, int resultCode,
      Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == REQUEST_CODE_PREFERENCES &&
        resultCode == RESULT_OK) {
      controlUpdateChecks();
    }
  }  
 
  // Various handlers.  
 
  private TextWatcher mTextWatcher = new TextWatcher() {
    @Override
    public void afterTextChanged(Editable e) {
      int remaining = MAX_TWEET_LENGTH - e.length();            
      updateCharsRemain(remaining + "");
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