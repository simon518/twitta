package com.dart.android.twitter;

import java.io.IOException;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import com.dart.android.twitter.TwitterApi.AuthException;
import com.google.android.photostream.UserTask;

public class DmActivity extends Activity {
  
  private static final String TAG = "DmActivity";

  // Views.
  private ListView mTweetList;

  private EditText mTweetEdit;
  private ImageButton mSendButton;

  private TextView mCharsRemainText;
  private TextView mProgressText;

  // Sources.
  private TwitterApi mApi;
  // private TwitterDbAdapter mDb;

  private SharedPreferences mPreferences;

  // Tasks.
  private UserTask<Void, Void, RetrieveResult> mRetrieveTask;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  
    mApi = new TwitterApi();
    mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        
    setContentView(R.layout.main);
    
    mTweetList = (ListView) findViewById(R.id.tweet_list);
    mTweetEdit = (EditText) findViewById(R.id.tweet_edit);
    /*
    mTweetEdit.addTextChangedListener(mTextWatcher);
    mTweetEdit.setFilters(new InputFilter[] { new InputFilter.LengthFilter(
        MAX_TWEET_INPUT_LENGTH) });
    mTweetEdit.setOnKeyListener(tweetEnterHandler);
    */

    mCharsRemainText = (TextView) findViewById(R.id.chars_text);
    mProgressText = (TextView) findViewById(R.id.progress_text);

    mSendButton = (ImageButton) findViewById(R.id.send_button);
    mSendButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        // doSend();
      }
    });

    String username = mPreferences.getString(Preferences.USERNAME_KEY, "");
    String password = mPreferences.getString(Preferences.PASSWORD_KEY, "");
    mApi.setCredentials(username, password);
    
    doRetrieve();
    
    // Want to be able to focus on the items with the trackball.
    // That way, we can navigate up and down by changing item focus.
    mTweetList.setItemsCanFocus(true);    
  }
  
  @Override
  protected void onDestroy() {
    Log.i(TAG, "onDestroy.");

//    if (mSendTask != null && mSendTask.getStatus() == UserTask.Status.RUNNING) {
//      // Doesn't really cancel execution (we let it continue running).
//      // See the SendTask code for more details.
//      mSendTask.cancel(true);
//    }

    if (mRetrieveTask != null
        && mRetrieveTask.getStatus() == UserTask.Status.RUNNING) {
      mRetrieveTask.cancel(true);
    }

//    mDb.close();
//
//    mImageManager.cleanup();

    super.onDestroy();
  }
  
  private void logout() {
    TwitterService.unschedule(this);

    /*
    mDb.deleteAllTweets();
    */

    // It is very important to clear preferences,
    // in particular the username and password, or else
    // LoginActivity may launch TwitterActivity again because
    // it thinks there are valid credentials.
    SharedPreferences.Editor editor = mPreferences.edit();
    editor.clear();
    editor.commit();

    // Let's cleanup files while we're at it.
    // mImageManager.clear();

    Intent intent = new Intent();
    intent.setClass(this, LoginActivity.class);
    startActivity(intent);
    finish();
  }
  
  public void update() {
    
  }
  
  private void updateProgress(String progress) {
    mProgressText.setText(progress);
  }
  
  private enum RetrieveResult {
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

        /*
        if (!Utils.isEmpty(tweet.profileImageUrl)
            && !mImageManager.contains(tweet.profileImageUrl)) {
          // Fetch image to cache.
          try {
            mImageManager.put(tweet.profileImageUrl);
          } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
          }
        }
        */
      }

      if (isCancelled()) {
        return RetrieveResult.CANCELLED;
      }

      // mDb.syncTweets(tweets);

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
  
}
