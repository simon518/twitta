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
import java.text.ParseException;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
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
  private static final int MAX_TWEET_INPUT_LENGTH = 400;

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
    // Controls scheduling of the new tweet checker depending on user preference
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

    // TODO:
    // int mode = getExtra(INTENT_MODE);
    
    mApi = new TwitterApi();
    mDb = new TwitterDbAdapter(this);
    mDb.open();
    mImageManager = new ImageManager(this);

    mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

    controlUpdateChecks();

    setContentView(R.layout.main);

    mTweetList = (ListView) findViewById(R.id.tweet_list);

    mTweetEdit = (EditText) findViewById(R.id.tweet_edit);
    mTweetEdit.addTextChangedListener(mTextWatcher);
    mTweetEdit.setFilters(new InputFilter[] { new InputFilter.LengthFilter(
        MAX_TWEET_INPUT_LENGTH) });
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
    // that is going to be destroyed and recreated immediately by the system.
    // See Activity doc for more.
    Object data = getLastNonConfigurationInstance();

    if (data != null) {
      // Non configuration instance.
      // Use the ImageManager from previous Activity instance.
      mImageManager = ((NonConfigurationState) data).imageManager;
      // Set context to this activity. The old one is of no use.
      mImageManager.setContext(this);

      // Check to see if it was running a send or retrieve task.
      // It makes no sense to resend the send request (don't want dupes)
      // so we instead retrieve (refresh) to see if the message has posted.
      boolean wasRunning = false;

      if (savedInstanceState != null) {
        if (savedInstanceState.containsKey(SIS_RUNNING_KEY)) {
          if (savedInstanceState.getBoolean(SIS_RUNNING_KEY)) {
            wasRunning = true;
          }
        }
      }
      
      if (wasRunning) {
        Log.i(TAG, "Was last running a retrieve or send task. Let's refresh.");
        doRetrieve();
      }      
    } else {
      // Mark all as read.
      mDb.markAllRead();     
      // We want to refresh.     
      doRetrieve();
    }
                
    setupAdapter();

    // Want to be able to focus on the items with the trackball.
    // That way, we can navigate up and down by changing item focus.
    mTweetList.setItemsCanFocus(true);
  }

  @Override
  public Object onRetainNonConfigurationInstance() {
    // Save ImageManager for the subsequent Activity instance.
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

  private static final String SIS_RUNNING_KEY = "running";

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);

    if (mRetrieveTask != null
        && mRetrieveTask.getStatus() == UserTask.Status.RUNNING) {
      outState.putBoolean(SIS_RUNNING_KEY, true);
    } else if (mSendTask != null &&
               mSendTask.getStatus() == UserTask.Status.RUNNING) {
      outState.putBoolean(SIS_RUNNING_KEY, true);
    }
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

    if (mSendTask != null && mSendTask.getStatus() == UserTask.Status.RUNNING) {
      // Doesn't really cancel execution (we let it continue running).
      // See the SendTask code for more details.
      mSendTask.cancel(true);
    }

    if (mRetrieveTask != null
        && mRetrieveTask.getStatus() == UserTask.Status.RUNNING) {
      mRetrieveTask.cancel(true);
    }

    mDb.close();

    mImageManager.cleanup();

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
  private static final int CONTEXT_RETWEET_ID = 1;

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v,
      ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    menu.add(0, CONTEXT_REPLY_ID, 0, R.string.reply);
    menu.add(0, CONTEXT_RETWEET_ID, 0, R.string.retweet);
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
        // TODO: this isn't quite perfect. It leaves extra empty spaces if you
        // perform the reply action again.
        String replyTo = "@" + cursor.getString(userIndex);
        String text = mTweetEdit.getText().toString();
        text = replyTo + " " + text.replace(replyTo, "");
        setAndFocusTweetInput(text);
  
        return true;
      case CONTEXT_RETWEET_ID:
        String retweet = "RT @" +
            cursor.getString(cursor.getColumnIndexOrThrow(TwitterDbAdapter.KEY_USER)) +
            " " +
            cursor.getString(cursor.getColumnIndexOrThrow(TwitterDbAdapter.KEY_TEXT));
        setAndFocusTweetInput(retweet);
  
        return true;        
      default:
        return super.onContextItemSelected(item);
    }
  }
  
  private void setAndFocusTweetInput(String text) {
    mTweetEdit.setText(text);
    Editable editable = mTweetEdit.getText();
    Selection.setSelection(editable, editable.length());
    mTweetEdit.requestFocus();    
    // TODO: why do we need to do this?
    // Can't we detect the box is being set?
    int remaining = MAX_TWEET_LENGTH - mTweetEdit.length();
    updateCharsRemain(remaining + "");    
  }

  private class TweetAdapter extends CursorAdapter {

    public TweetAdapter(Context context, Cursor cursor) {
      super(context, cursor);
      
      mInflater = LayoutInflater.from(context);
      
      mUserTextColumn = cursor
          .getColumnIndexOrThrow(TwitterDbAdapter.KEY_USER);
      mTextColumn = cursor.getColumnIndexOrThrow(TwitterDbAdapter.KEY_TEXT);
      mProfileImageUrlColumn = cursor
          .getColumnIndexOrThrow(TwitterDbAdapter.KEY_PROFILE_IMAGE_URL);
      mCreatedAtColumn = cursor
          .getColumnIndexOrThrow(TwitterDbAdapter.KEY_CREATED_AT);
      mSourceColumn = cursor
          .getColumnIndexOrThrow(TwitterDbAdapter.KEY_SOURCE);
      
      mMetaBuilder = new StringBuilder();
    }

    private LayoutInflater mInflater;
    
    private int mUserTextColumn; 
    private int mTextColumn;
    private int mProfileImageUrlColumn;
    private int mCreatedAtColumn;
    private int mSourceColumn;
    
    private StringBuilder mMetaBuilder;
    
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
      View view = mInflater.inflate(R.layout.tweet, parent, false);
      
      ViewHolder holder = new ViewHolder();      
      holder.tweetUserText = (TextView) view.findViewById(R.id.tweet_user_text);
      holder.tweetText = (TextView) view.findViewById(R.id.tweet_text);
      holder.profileImage = (ImageView) view.findViewById(R.id.profile_image);
      holder.metaText = (TextView) view.findViewById(R.id.tweet_meta_text);      
      view.setTag(holder);
      
      return view;
    }

    class ViewHolder {
      public TextView tweetUserText;
      public TextView tweetText;
      public ImageView profileImage;
      public TextView metaText;      
    }
    
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
      ViewHolder holder = (ViewHolder) view.getTag();

      holder.tweetUserText.setText(cursor.getString(mUserTextColumn));
      holder.tweetText.setText(cursor.getString(mTextColumn));

      String profileImageUrl = cursor.getString(mProfileImageUrlColumn);

      if (!Utils.isEmpty(profileImageUrl)) {
        holder.profileImage.setImageBitmap(mImageManager.get(profileImageUrl));
      }

      mMetaBuilder.setLength(0);

      try {
        mMetaBuilder.append(Utils.getRelativeDate(
            TwitterDbAdapter.DB_DATE_FORMATTER.parse(
                cursor.getString(mCreatedAtColumn))));
        mMetaBuilder.append(" ");
      } catch (ParseException e) {
        Log.w(TAG, "Invalid created at data.");
      }

      mMetaBuilder.append("from ");
      mMetaBuilder.append(cursor.getString(mSourceColumn));

      holder.metaText.setText(mMetaBuilder.toString());
    }

    public void refresh() {
      getCursor().requery();
    }

  }

  private void update() {
    mTweetAdapter.refresh();
    mTweetList.setSelection(0);
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

    // Let's cleanup files while we're at it.
    mImageManager.clear();

    Intent intent = new Intent();
    intent.setClass(this, LoginActivity.class);
    startActivity(intent);
    finish();
  }

  private void doSend() {
    if (mSendTask != null && mSendTask.getStatus() == UserTask.Status.RUNNING) {
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
        JSONObject jsonObject = mApi.update(status);
        Tweet tweet = Tweet.create(jsonObject);
        mDb.createTweet(tweet, false);
      } catch (IOException e) {
        Log.e(TAG, e.getMessage(), e);
        return SendResult.IO_ERROR;
      } catch (AuthException e) {
        Log.i(TAG, "Invalid authorization.");
        return SendResult.AUTH_ERROR;
      } catch (JSONException e) {
        Log.w(TAG, "Could not parse JSON after sending update.");
        return SendResult.IO_ERROR;
      }

      return SendResult.OK;
    }

    @Override
    public void onPostExecute(SendResult result) {
      if (isCancelled()) {
        // Canceled doesn't really mean "canceled" in this task.
        // We want the request to complete, but don't want to update the
        // activity (it's probably dead).
        return;
      }

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
    update();
  }

  private void onSendFailure() {
    updateProgress("Unable to update status");
    enableEntry();
  }

  private void doRetrieve() {
    Log.i(TAG, "Attempting retrieve.");

    if (mRetrieveTask != null
        && mRetrieveTask.getStatus() == UserTask.Status.RUNNING) {
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
        Log.e(TAG, e.getMessage(), e);
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
          Log.e(TAG, e.getMessage(), e);
          return RetrieveResult.IO_ERROR;
        }

        if (isCancelled()) {
          return RetrieveResult.CANCELLED;
        }

        if (!Utils.isEmpty(tweet.profileImageUrl)
            && !mImageManager.contains(tweet.profileImageUrl)) {
          // Fetch image to cache.
          try {
            mImageManager.put(tweet.profileImageUrl);
          } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
          }
        }
      }

      if (isCancelled()) {
        return RetrieveResult.CANCELLED;
      }

      mDb.syncTweets(tweets);

      if (isCancelled()) {
        return RetrieveResult.CANCELLED;
      }

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
  private static final int OPTIONS_MENU_ID_ABOUT = 4;
  private static final int OPTIONS_MENU_ID_REPLIES = 5;  
  private static final int OPTIONS_MENU_ID_DM = 6;

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);

    MenuItem item = menu.add(0, OPTIONS_MENU_ID_LOGOUT, 0, R.string.signout);
    item.setIcon(android.R.drawable.ic_menu_revert);

    item = menu.add(0, OPTIONS_MENU_ID_PREFERENCES, 0, R.string.preferences);
    item.setIcon(android.R.drawable.ic_menu_preferences);

    item = menu.add(0, OPTIONS_MENU_ID_REFRESH, 0, R.string.refresh);
    item.setIcon(R.drawable.refresh);

    item = menu.add(0, OPTIONS_MENU_ID_ABOUT, 0, R.string.about);
    item.setIcon(android.R.drawable.ic_menu_info_details);

    item = menu.add(0, OPTIONS_MENU_ID_DM, 0, R.string.dm);
    item.setIcon(android.R.drawable.ic_menu_send);
    
    return true;
  }

  private static final String INTENT_MODE = "mode";
  
  private static final int MODE_REPLIES = 1;
  
  private static final int REQUEST_CODE_PREFERENCES = 1;

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case OPTIONS_MENU_ID_LOGOUT:
      logout();
      return true;
    case OPTIONS_MENU_ID_REFRESH:
      mImageManager.clear();
      doRetrieve();
      return true;
    case OPTIONS_MENU_ID_PREFERENCES:
      Intent launchPreferencesIntent = new Intent().setClass(this,
          PreferencesActivity.class);
      startActivityForResult(launchPreferencesIntent, REQUEST_CODE_PREFERENCES);
      return true;
    case OPTIONS_MENU_ID_ABOUT:
      AboutDialog.show(this);
      return true;
    case OPTIONS_MENU_ID_REPLIES:
      Intent repliesIntent = new Intent(this, TwitterActivity.class);
      repliesIntent.putExtra(INTENT_MODE, MODE_REPLIES);
      startActivity(repliesIntent);
      return true;
    case OPTIONS_MENU_ID_DM:
      Intent dmIntent = new Intent(this, DmActivity.class);
      startActivity(dmIntent);
      return true;            
    }

    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == REQUEST_CODE_PREFERENCES && resultCode == RESULT_OK) {
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
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }
  };

  private View.OnKeyListener tweetEnterHandler = new View.OnKeyListener() {
    public boolean onKey(View v, int keyCode, KeyEvent event) {
      if (keyCode == KeyEvent.KEYCODE_ENTER
          || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
          doSend();
        }
        return true;
      }
      return false;
    }
  };

}