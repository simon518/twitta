package com.dart.android.twitter;

import java.io.IOException;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
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
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.dart.android.twitter.TwitterApi.ApiException;
import com.dart.android.twitter.TwitterApi.AuthException;
import com.google.android.photostream.UserTask;

public class UserActivity extends BaseActivity {

  private static final String TAG = "UserActivity";

  // State.
  private String mUsername;
  private String mMe;
  private ArrayList<Tweet> mTweets;
  private User mUser;
  private Boolean mIsFollowing;
  private Boolean mIsFollower = false;

  private static class State {
    State(UserActivity activity) {
      mTweets = activity.mTweets;
      mUser = activity.mUser;
      mIsFollowing = activity.mIsFollowing;
      mIsFollower = activity.mIsFollower;
    }

    public ArrayList<Tweet> mTweets;
    public User mUser;
    public boolean mIsFollowing;
    public boolean mIsFollower;
  }

  // Views.
  private ListView mTweetList;
  private TextView mProgressText;
  private TextView mUserText;
  private TextView mNameText;
  private ImageView mProfileImage;
  private Button mFollowButton;

  private TweetArrayAdapter mAdapter;

  // Tasks.
  private UserTask<Void, Void, TaskResult> mRetrieveTask;
  private UserTask<Void, Void, TaskResult> mFriendshipTask;

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
    mNameText = (TextView) findViewById(R.id.realname_text);
    mProfileImage = (ImageView) findViewById(R.id.profile_image);

    mFollowButton = (Button) findViewById(R.id.follow_button);
    mFollowButton.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        confirmFollow();
      }
    });

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

    State state = (State) getLastNonConfigurationInstance();

    boolean wasRunning = savedInstanceState != null
        && savedInstanceState.containsKey(SIS_RUNNING_KEY)
        && savedInstanceState.getBoolean(SIS_RUNNING_KEY);

    if (state != null && !wasRunning) {
      mTweets = state.mTweets;
      mUser = state.mUser;
      mIsFollowing = state.mIsFollowing;
      mIsFollower = state.mIsFollower;
      draw();
    } else {
      doRetrieve();
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

    if (mFriendshipTask != null
        && mFriendshipTask.getStatus() == UserTask.Status.RUNNING) {
      mFriendshipTask.cancel(true);
    }

    super.onDestroy();
  }


  // UI helpers.

  private void updateProgress(String progress) {
    mProgressText.setText(progress);
  }

  private void draw() {
    if (mTweets.size() > 0) {
      String imageUrl = mTweets.get(0).profileImageUrl;

      if (!TextUtils.isEmpty(imageUrl)) {
        mProfileImage.setImageBitmap(getImageManager().get(imageUrl));
      }
    }

    mAdapter.refresh(mTweets);

    if (mUser != null) {
      mNameText.setText(mUser.name);
    }

    if (mUsername.equalsIgnoreCase(mMe)) {
      mFollowButton.setVisibility(View.GONE);
    } else if (mIsFollowing != null) {
      mFollowButton.setVisibility(View.VISIBLE);

      if (mIsFollowing) {
        mFollowButton.setText(R.string.unfollow);
      } else {
        mFollowButton.setText(R.string.follow);
      }
    }
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

            if (!Utils.isEmpty(mUser.profileImageUrl)) {
              // Fetch image to cache.
              try {
                imageManager.put(mUser.profileImageUrl);
              } catch (IOException e) {
                Log.e(TAG, e.getMessage(), e);
              }
            }
          }
        } catch (JSONException e) {
          Log.e(TAG, e.getMessage(), e);
          return TaskResult.IO_ERROR;
        }

        if (isCancelled()) {
          return TaskResult.CANCELLED;
        }
      }

      // Bad style! But learned something.
      UserActivity.this.mTweets = mTweets;

      if (isCancelled()) {
        return TaskResult.CANCELLED;
      }

      publishProgress();

      if (isCancelled()) {
        return TaskResult.CANCELLED;
      }

      try {
        mIsFollowing = api.isFollows(mMe, mUsername);
        mIsFollower = api.isFollows(mUsername, mMe);
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

      if (isCancelled()) {
        return TaskResult.CANCELLED;
      }

      return TaskResult.OK;
    }

    @Override
    public void onProgressUpdate(Void... progress) {
      draw();
    }

    @Override
    public void onPostExecute(TaskResult result) {
      if (result == TaskResult.AUTH_ERROR) {
        onAuthFailure();
      } else if (result == TaskResult.OK) {
        draw();
      } else {
        // Do nothing.
      }

      updateProgress("");
    }
  }


  private class FriendshipTask extends UserTask<Void, Void, TaskResult> {

    private boolean mIsDestroy;

    public FriendshipTask(boolean isDestroy) {
      mIsDestroy = isDestroy;
    }

    @Override
    public void onPreExecute() {
      mFollowButton.setEnabled(false);

      if (mIsDestroy) {
        updateProgress("Unfollowing...");
      } else {
        updateProgress("Following...");
      }
    }

    @Override
    public TaskResult doInBackground(Void... params) {
      JSONObject jsonObject;

      int id = Integer.parseInt(mUser.id);

      TwitterApi api = getApi();

      try {
        if (mIsDestroy) {
          jsonObject = api.destroyFriendship(id);
        } else {
          jsonObject = api.createFriendship(id);
        }
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

      if (isCancelled()) {
        return TaskResult.CANCELLED;
      }

      try {
        User.create(jsonObject);
      } catch (JSONException e) {
        Log.e(TAG, e.getMessage(), e);
        return TaskResult.IO_ERROR;
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
        mIsFollowing = !mIsFollowing;
        draw();
      } else {
        // Do nothing.
      }

      mFollowButton.setEnabled(true);
      updateProgress("");
    }
  }


  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuItem item = menu.add(0, OPTIONS_MENU_ID_REFRESH, 0, R.string.refresh);
    item.setIcon(R.drawable.refresh);

    item = menu.add(0, OPTIONS_MENU_ID_DM, 0, R.string.dm);
    item.setIcon(android.R.drawable.ic_menu_send);

    item = menu.add(0, OPTIONS_MENU_ID_FOLLOW, 0, R.string.follow);
    item.setIcon(android.R.drawable.ic_menu_add);

    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuItem item = menu.findItem(OPTIONS_MENU_ID_DM);
    item.setEnabled(mIsFollower);

    item = menu.findItem(OPTIONS_MENU_ID_FOLLOW);

    if (mIsFollowing == null) {
      item.setEnabled(false);
      item.setTitle(R.string.follow);
      item.setIcon(android.R.drawable.ic_menu_add);
    } else if (mIsFollowing) {
      item.setTitle(R.string.unfollow);
      item.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
    } else {
      item.setTitle(R.string.follow);
      item.setIcon(android.R.drawable.ic_menu_add);
    }

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


  private static final int DIALOG_CONFIRM = 0;

  private void confirmFollow() {
    showDialog(DIALOG_CONFIRM);
  }

  @Override
  protected Dialog onCreateDialog(int id) {
    AlertDialog dialog = new AlertDialog.Builder(this).create();

    dialog.setTitle(R.string.friendship);
    dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Doesn't matter", mConfirmListener);
    dialog.setButton(AlertDialog.BUTTON_NEUTRAL,
        getString(R.string.cancel), mCancelListener);
    dialog.setMessage("FOO");

    return dialog;
  }

  @Override
  protected void onPrepareDialog(int id, Dialog dialog) {
    super.onPrepareDialog(id, dialog);

    AlertDialog confirmDialog = (AlertDialog) dialog;

    String action = mIsFollowing ? getString(R.string.unfollow) :
        getString(R.string.follow);
    String message = action + " " + mUsername + "?";

    (confirmDialog.getButton(AlertDialog.BUTTON_POSITIVE)).setText(action);
    confirmDialog.setMessage(message);
  }

  private DialogInterface.OnClickListener mConfirmListener = new DialogInterface.OnClickListener() {
    public void onClick(DialogInterface dialog, int whichButton) {
      toggleFollow();
    }
  };

  private DialogInterface.OnClickListener mCancelListener = new DialogInterface.OnClickListener() {
    public void onClick(DialogInterface dialog, int whichButton) {
    }
  };

  private void toggleFollow() {
    if (mFriendshipTask != null
        && mFriendshipTask.getStatus() == UserTask.Status.RUNNING) {
      Log.w(TAG, "Already updating friendship.");
      return;
    }

    mFriendshipTask = new FriendshipTask(mIsFollowing).execute();

    // TODO: should we do a timeline refresh here?
  }

}
