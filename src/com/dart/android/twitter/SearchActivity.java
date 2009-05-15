package com.dart.android.twitter;

import java.io.IOException;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.dart.android.twitter.TwitterApi.ApiException;
import com.dart.android.twitter.TwitterApi.AuthException;
import com.google.android.photostream.UserTask;

import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ListView;
import android.widget.TextView;

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

    setContentView(R.layout.main);

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
          tweet = Tweet.create(jsonObject);
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

}