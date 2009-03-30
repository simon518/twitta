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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
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

import com.dart.android.twitter.TwitterApi.ApiException;
import com.dart.android.twitter.TwitterApi.AuthException;
import com.google.android.photostream.UserTask;

public class TwitterActivity extends BaseActivity {
  private static final String TAG = "TwitterActivity";

  // Views.
  private ListView mTweetList;
  private TweetAdapter mTweetAdapter;

  private TweetEdit mTweetEdit;
  private ImageButton mSendButton;

  private TextView mProgressText;

  // Tasks.
  private UserTask<Void, Void, RetrieveResult> mRetrieveTask;
  private UserTask<Void, Void, SendResult> mSendTask;

  // Refresh data at startup if last refresh was this long ago or greater.   
  private static final long REFRESH_THRESHOLD = 5 * 60 * 1000;
    
  public static Intent createIntent(Context context) {
    Intent intent = new Intent(context, TwitterActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    
    return intent;
  }
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.main);

    mTweetList = (ListView) findViewById(R.id.tweet_list);

    mTweetEdit = new TweetEdit((EditText) findViewById(R.id.tweet_edit),
        (TextView) findViewById(R.id.chars_text));
            
    mTweetEdit.setOnKeyListener(tweetEnterHandler);

    mProgressText = (TextView) findViewById(R.id.progress_text);

    mSendButton = (ImageButton) findViewById(R.id.send_button);
    mSendButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        doSend();
      }
    });

    // Mark all as read.
    mDb.markAllTweetsRead();    
    
    setupAdapter();
    
    boolean shouldRetrieve = false;

    long lastRefreshTime = mPreferences.getLong(
        Preferences.LAST_TWEET_REFRESH_KEY, 0);
    long nowTime = Utils.getNowTime();
    
    long diff = nowTime - lastRefreshTime;
    Log.i(TAG, "Last refresh was " + diff + " ms ago.");
    
    if (diff > REFRESH_THRESHOLD) {
      shouldRetrieve = true;
    } else if (savedInstanceState != null
        && savedInstanceState.containsKey(SIS_RUNNING_KEY)
        && savedInstanceState.getBoolean(SIS_RUNNING_KEY)) {
      // Check to see if it was running a send or retrieve task.
      // It makes no sense to resend the send request (don't want dupes)
      // so we instead retrieve (refresh) to see if the message has posted.
      Log.i(TAG, "Was last running a retrieve or send task. Let's refresh.");
      shouldRetrieve = true;
    }
    
    if (shouldRetrieve) {
      doRetrieve();      
    }
    
    // Want to be able to focus on the items with the trackball.
    // That way, we can navigate up and down by changing item focus.
    mTweetList.setItemsCanFocus(true);
  }
  
  private static final String SIS_RUNNING_KEY = "running";

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);

    if (mRetrieveTask != null
        && mRetrieveTask.getStatus() == UserTask.Status.RUNNING) {
      outState.putBoolean(SIS_RUNNING_KEY, true);
    } else if (mSendTask != null
        && mSendTask.getStatus() == UserTask.Status.RUNNING) {
      outState.putBoolean(SIS_RUNNING_KEY, true);
    }
  }

  @Override
  protected void onRestoreInstanceState(Bundle bundle) {
    super.onRestoreInstanceState(bundle);

    mTweetEdit.updateCharsRemain();
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

    super.onDestroy();
  }

  // UI helpers.

  private void updateProgress(String progress) {
    mProgressText.setText(progress);
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
  private static final int CONTEXT_DM_ID = 2;
  
  @Override
  public void onCreateContextMenu(ContextMenu menu, View v,
      ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    menu.add(0, CONTEXT_REPLY_ID, 0, R.string.reply);
    menu.add(0, CONTEXT_RETWEET_ID, 0, R.string.retweet);
    // TODO: would result in too many "not following" errors.
    // menu.add(0, CONTEXT_DM_ID, 0, R.string.dm);    
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
        String text = mTweetEdit.getText();
        text = replyTo + " " + text.replace(replyTo, "");
        mTweetEdit.setTextAndFocus(text);
  
        return true;
      case CONTEXT_RETWEET_ID:
        String retweet = "RT @" +
            cursor.getString(cursor.getColumnIndexOrThrow(TwitterDbAdapter.KEY_USER)) +
            " " +
            cursor.getString(cursor.getColumnIndexOrThrow(TwitterDbAdapter.KEY_TEXT));
        mTweetEdit.setTextAndFocus(retweet);
  
        return true;
      case CONTEXT_DM_ID:
        String user = cursor.getString(
            cursor.getColumnIndexOrThrow(TwitterDbAdapter.KEY_USER));
        DmActivity.show(this, user);        
        return true;                
      default:
        return super.onContextItemSelected(item);
    }
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

  private class SendTask extends UserTask<Void, Void, SendResult> {
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
        
        if (!Utils.isEmpty(tweet.profileImageUrl)
            && !mImageManager.contains(tweet.profileImageUrl)) {
          // Fetch image to cache.
          try {
            mImageManager.put(tweet.profileImageUrl);
          } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
          }
        }
                
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
      } catch (ApiException e) {
        Log.e(TAG, e.getMessage(), e);
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
      } catch (ApiException e) {
        Log.e(TAG, e.getMessage(), e);
        return RetrieveResult.IO_ERROR;
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
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putLong(Preferences.LAST_TWEET_REFRESH_KEY, Utils.getNowTime());
        editor.commit();        
        update();
      } else {
        // Do nothing.
      }

      updateProgress("");
    }
  }

  // Menu.

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuItem item = menu.add(0, OPTIONS_MENU_ID_REFRESH, 0, R.string.refresh);
    item.setIcon(R.drawable.refresh);

    item = menu.add(0, OPTIONS_MENU_ID_DM, 0, R.string.dm);
    item.setIcon(android.R.drawable.ic_menu_send);
        
    return super.onCreateOptionsMenu(menu);    
  }

  private static final String INTENT_MODE = "mode";  
  private static final int MODE_REPLIES = 1;
  
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case OPTIONS_MENU_ID_REFRESH:
      doRetrieve();
      return true;
    case OPTIONS_MENU_ID_REPLIES:
      Intent repliesIntent = new Intent(this, TwitterActivity.class);
      repliesIntent.putExtra(INTENT_MODE, MODE_REPLIES);
      startActivity(repliesIntent);
      return true;
    case OPTIONS_MENU_ID_DM:
      startActivity(DmActivity.createIntent(this));
      return true;            
    }

    return super.onOptionsItemSelected(item);
  }

  // Various handlers.

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