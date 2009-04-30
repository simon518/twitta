package com.dart.android.twitter;

import java.io.IOException;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.dart.android.twitter.TwitterApi.ApiException;
import com.dart.android.twitter.TwitterApi.AuthException;
import com.google.android.photostream.UserTask;

public class UserActivity extends BaseActivity {

  private static final String TAG = "UserActivity";

  private String mUsername;
  private String mMe;  
  private User mUser;
  private boolean mIsFollowing = false;
  private boolean mIsFollower = false;
  
  private TweetArrayAdapter mAdapter;
  
  // Views.
  private ListView mTweetList;
  private TextView mProgressText;  
  private TextView mUserText;
  private ImageView mProfileImage;
  
  // Tasks.
  private UserTask<Void, Void, TaskResult> mRetrieveTask;

  private static final String EXTRA_USER = "user";

  private static final String LAUNCH_ACTION = "com.dart.android.twitter.USER";

  public static Intent createIntent(String user) {    
    Intent intent = new Intent(LAUNCH_ACTION);    
    intent.putExtra(EXTRA_USER, user);
    
    return intent;
  }
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (!getApi().isLoggedIn()) {
      Log.i(TAG, "Not logged in.");
      handleLoggedOut();
      return;
    }
    
    setContentView(R.layout.user);
    
    mMe = TwitterApplication.mApi.getUsername();    
    mTweetList = (ListView) findViewById(R.id.tweet_list);        
    mProgressText = (TextView) findViewById(R.id.progress_text);
    mUserText = (TextView) findViewById(R.id.tweet_user_text);
    mProfileImage = (ImageView) findViewById(R.id.profile_image);
    
    Intent intent = getIntent();
    Uri data = intent.getData();
    
    mUsername = intent.getStringExtra(EXTRA_USER);
    
    if (TextUtils.isEmpty(mUsername)) {    
      mUsername = data.getLastPathSegment();
    }        

    setTitle(mUsername);
    mUserText.setText(mUsername);
    
    mAdapter = new TweetArrayAdapter(this);
    mTweetList.setAdapter(mAdapter);
    registerForContextMenu(mTweetList);    
    
    doRetrieve();    
  }
  
  @Override
  protected void onResume() {
    super.onResume();
    
    if (!getApi().isLoggedIn()) {
      Log.i(TAG, "Not logged in.");
      handleLoggedOut();
      return;
    }               
  }
  
  private static final String SIS_RUNNING_KEY = "running";

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);

    if (mRetrieveTask != null
        && mRetrieveTask.getStatus() == UserTask.Status.RUNNING) {
      outState.putBoolean(SIS_RUNNING_KEY, true);
    }
  }
  
  @Override
  protected void onDestroy() {
    Log.i(TAG, "onDestroy.");

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
    
  private void update(ArrayList<Tweet> tweets) {
    if (tweets.size() > 0) {
      String imageUrl = tweets.get(0).profileImageUrl;
      
      if (!TextUtils.isEmpty(imageUrl)) {
        mProfileImage.setImageBitmap(getImageManager().get(imageUrl));
      }
    }
    
    mAdapter.refresh(tweets);
    mTweetList.setSelection(0);
  }
  
  
  private enum TaskResult {
    OK, IO_ERROR, AUTH_ERROR, CANCELLED
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
  
  private class RetrieveTask extends UserTask<Void, Void, TaskResult> {
    @Override
    public void onPreExecute() {
      onRetrieveBegin();
    }

    ArrayList<Tweet> mTweets = new ArrayList<Tweet>();

    @Override
    public TaskResult doInBackground(Void... params) {
      JSONArray jsonArray;

      TwitterApi api = getApi();
      ImageManager imageManager = getImageManager();
            
      try {
        mIsFollowing = api.isFollows(mMe, mUsername);
        mIsFollower = api.isFollows(mUsername, mMe);        
        jsonArray = api.getUserTimeline(mUsername);
      } catch (IOException e) {
        Log.e(TAG, e.getMessage(), e);
        return TaskResult.IO_ERROR;
      } catch (AuthException e) {
        Log.i(TAG, "Invalid authorization.");
        return TaskResult.AUTH_ERROR;
      } catch (ApiException e) {
        Log.e(TAG, e.getMessage(), e);
        return TaskResult.IO_ERROR;
      }

      for (int i = 0; i < jsonArray.length(); ++i) {
        if (isCancelled()) {
          return TaskResult.CANCELLED;
        }

        Tweet tweet;

        try {
          JSONObject jsonObject = jsonArray.getJSONObject(i);
          tweet = Tweet.create(jsonObject);
          mTweets.add(tweet);
          
          if (mUser == null) {
            mUser = User.create(jsonObject.getJSONObject("user"));
          }
        } catch (JSONException e) {
          Log.e(TAG, e.getMessage(), e);
          return TaskResult.IO_ERROR;
        }

        if (isCancelled()) {
          return TaskResult.CANCELLED;
        }

        if (!Utils.isEmpty(tweet.profileImageUrl)) {
          // Fetch image to cache.
          try {
            imageManager.put(tweet.profileImageUrl);
          } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
          }
        }
      }

      if (isCancelled()) {
        return TaskResult.CANCELLED;
      }

      return TaskResult.OK;
    }

    @Override
    public void onPostExecute(TaskResult result) {
      if (result == TaskResult.AUTH_ERROR) {
        onAuthFailure();
      } else if (result == TaskResult.OK) {
        update(mTweets);
      } else {
        // Do nothing.
      }

      updateProgress("");
    }    
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuItem item = menu.add(0, OPTIONS_MENU_ID_REFRESH, 0, R.string.refresh);
    item.setIcon(R.drawable.refresh);

    // TODO: disable if not following.
    item = menu.add(0, OPTIONS_MENU_ID_DM, 0, R.string.dm);
    item.setIcon(android.R.drawable.ic_menu_send);

    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuItem item = menu.findItem(OPTIONS_MENU_ID_DM);
    item.setEnabled(mIsFollower);

    return super.onPrepareOptionsMenu(menu);
  }
  
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case OPTIONS_MENU_ID_REFRESH:
      doRetrieve();
      return true;
    case OPTIONS_MENU_ID_DM:
      launchActivity(DmActivity.createIntent(mUsername));
      return true;      
    }

    return super.onOptionsItemSelected(item);
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

    MenuItem item = menu.add(0, CONTEXT_DM_ID, 0, R.string.dm);
    item.setEnabled(mIsFollower);
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
    Tweet tweet = (Tweet) mAdapter.getItem(info.position);

    if (tweet == null) {
      Log.w(TAG, "Selected item not available.");
      return super.onContextItemSelected(item);
    }

    switch (item.getItemId()) {
    case CONTEXT_REPLY_ID:
      String replyTo = "@" + tweet.screenName + " ";
      launchNewTweetActivity(replyTo);
      return true;
    case CONTEXT_RETWEET_ID:
      String retweet = "RT @" + tweet.screenName + " " + tweet.text;
      launchNewTweetActivity(retweet);
      return true;
    case CONTEXT_DM_ID:
      launchActivity(DmActivity.createIntent(mUsername));
      return true;
    default:
      return super.onContextItemSelected(item);
    }
  }
  
  private void launchNewTweetActivity(String text) {    
    launchActivity(TwitterActivity.createNewTweetIntent(text));
  }
  
}
