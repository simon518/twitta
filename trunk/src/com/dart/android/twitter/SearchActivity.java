package com.dart.android.twitter;

import java.io.IOException;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.dart.android.twitter.TwitterApi.ApiException;
import com.dart.android.twitter.TwitterApi.AuthException;
import com.google.android.photostream.UserTask;

public class SearchActivity extends BaseActivity {
  private static final String TAG = "SearchActivity";

  // Views.
  private ListView mTweetList;
  private TextView mProgressText;

  // State.
  private String mSearchQuery;
  private ArrayList<Tweet> mTweets;
  private TweetArrayAdapter mAdapter;

  private static class State {
    State(SearchActivity activity) {
      mTweets = activity.mTweets;
    }

    public ArrayList<Tweet> mTweets;
  }

  // Tasks.
  private UserTask<Void, Void, RetrieveResult> mSearchTask;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (!getApi().isLoggedIn()) {
      Log.i(TAG, "Not logged in.");
      handleLoggedOut();
      return;
    }

    setContentView(R.layout.search);

    Intent intent = getIntent();
    // Assume it's SEARCH.
    // String action = intent.getAction();
    mSearchQuery = intent.getStringExtra(SearchManager.QUERY);
    setTitle(mSearchQuery);

    mTweetList = (ListView) findViewById(R.id.tweet_list);
    mAdapter = new TweetArrayAdapter(this);
    mTweetList.setAdapter(mAdapter);
    registerForContextMenu(mTweetList);

    mProgressText = (TextView) findViewById(R.id.progress_text);

    State state = (State) getLastNonConfigurationInstance();

    boolean wasRunning = savedInstanceState != null
        && savedInstanceState.containsKey(SIS_RUNNING_KEY)
        && savedInstanceState.getBoolean(SIS_RUNNING_KEY);

    if (state != null && !wasRunning) {
      mTweets = state.mTweets;
      draw();
    } else {
      doSearch();
    }
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

  @Override
  public Object onRetainNonConfigurationInstance() {
      return new State(this);
  }

  private static final String SIS_RUNNING_KEY = "running";

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);

    if (mSearchTask != null
        && mSearchTask.getStatus() == UserTask.Status.RUNNING) {
      outState.putBoolean(SIS_RUNNING_KEY, true);
    }
  }

  @Override
  protected void onDestroy() {
    Log.i(TAG, "onDestroy.");

    if (mSearchTask != null && mSearchTask.getStatus() == UserTask.Status.RUNNING) {
      mSearchTask.cancel(true);
    }

    super.onDestroy();
  }

  // UI helpers.

  private void updateProgress(String progress) {
    mProgressText.setText(progress);
  }

  private void draw() {
    mAdapter.refresh(mTweets);
  }

  private void onAuthFailure() {
    logout();
  }

  private enum RetrieveResult {
    OK, IO_ERROR, AUTH_ERROR, CANCELLED
  }

  private void doSearch() {
    Log.i(TAG, "Attempting search.");

    if (mSearchTask != null
        && mSearchTask.getStatus() == UserTask.Status.RUNNING) {
      Log.w(TAG, "Already searching.");
    } else {
      mSearchTask = new SearchTask().execute();
    }
  }

  private class SearchTask extends UserTask<Void, Void, RetrieveResult> {
    @Override
    public void onPreExecute() {
      updateProgress("Searching...");
    }

    ArrayList<Tweet> mTweets = new ArrayList<Tweet>();

    @Override
    public RetrieveResult doInBackground(Void... params) {
      JSONArray jsonArray;

      try {
        jsonArray = getApi().search(mSearchQuery);
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

      for (int i = 0; i < jsonArray.length(); ++i) {
        if (isCancelled()) {
          return RetrieveResult.CANCELLED;
        }

        Tweet tweet;

        try {
          JSONObject jsonObject = jsonArray.getJSONObject(i);
          tweet = Tweet.createFromSearchApi(jsonObject);
          mTweets.add(tweet);
        } catch (JSONException e) {
          Log.e(TAG, e.getMessage(), e);
          return RetrieveResult.IO_ERROR;
        }

        if (isCancelled()) {
          return RetrieveResult.CANCELLED;
        }
      }

      return RetrieveResult.OK;
    }

    @Override
    public void onPostExecute(RetrieveResult result) {
      if (result == RetrieveResult.AUTH_ERROR) {
        onAuthFailure();
      } else if (result == RetrieveResult.OK) {
        SearchActivity.this.mTweets = mTweets;
        draw();
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

    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case OPTIONS_MENU_ID_REFRESH:
      doSearch();
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  private static final int CONTEXT_MORE_ID = 3;
  private static final int CONTEXT_REPLY_ID = 0;
  private static final int CONTEXT_RETWEET_ID = 1;
  private static final int CONTEXT_DM_ID = 2;

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v,
      ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);

    AdapterView.AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
    Tweet tweet = (Tweet) mAdapter.getItem(info.position);
    menu.add(0, CONTEXT_MORE_ID, 0, tweet.screenName);
    menu.add(0, CONTEXT_REPLY_ID, 0, R.string.reply);
    menu.add(0, CONTEXT_RETWEET_ID, 0, R.string.retweet);

    /*
    MenuItem item = menu.add(0, CONTEXT_DM_ID, 0, R.string.dm);
    item.setEnabled(mIsFollower);
    */
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
    case CONTEXT_MORE_ID:
      launchActivity(UserActivity.createIntent(tweet.screenName));
      return true;
    case CONTEXT_REPLY_ID:
      String replyTo = "@" + tweet.screenName + " ";
      launchNewTweetActivity(replyTo);
      return true;
    case CONTEXT_RETWEET_ID:
      String retweet = "RT @" + tweet.screenName + " " + tweet.text;
      launchNewTweetActivity(retweet);
      return true;
    /*
    case CONTEXT_DM_ID:
      launchActivity(DmActivity.createIntent(mUsername));
      return true;
      */
    default:
      return super.onContextItemSelected(item);
    }
  }

  private void launchNewTweetActivity(String text) {
    launchActivity(TwitterActivity.createNewTweetIntent(text));
  }

}
