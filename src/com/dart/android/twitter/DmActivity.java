package com.dart.android.twitter;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
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

public class DmActivity extends BaseActivity {
  
  private static final String TAG = "DmActivity";

  // Views.
  private ListView mTweetList;
  private Adapter mAdapter;

  private EditText mTweetEdit;
  private ImageButton mSendButton;

  private TextView mCharsRemainText;
  private TextView mProgressText;

  // Tasks.
  private UserTask<Void, Void, RetrieveResult> mRetrieveTask;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  
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

    // Nice optimization which can preserve objects in an Activity
    // that is going to be destroyed and recreated immediately by the system.
    // See Activity doc for more.
    Object data = getLastNonConfigurationInstance();

    if (data != null) {
      // Check to see if it was running a send or retrieve task.
      // It makes no sense to resend the send request (don't want dupes)
      // so we instead retrieve (refresh) to see if the message has posted.
      /*
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
      */      
    } else {
      // Mark all as read.
      mDb.markAllDmsRead();     
      // We want to refresh.     
      doRetrieve();
    }
                
    setupAdapter();
    
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

    super.onDestroy();
  }
  
  private void setupAdapter() {
    Cursor cursor = mDb.fetchAllDms();
    startManagingCursor(cursor);

    mAdapter = new Adapter(this, cursor);
    mTweetList.setAdapter(mAdapter);
    registerForContextMenu(mTweetList);
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
        jsonArray = mApi.getDirectMessages();
      } catch (IOException e) {
        Log.e(TAG, e.getMessage(), e);
        return RetrieveResult.IO_ERROR;
      } catch (AuthException e) {
        Log.i(TAG, "Invalid authorization.");
        return RetrieveResult.AUTH_ERROR;
      }

      ArrayList<Dm> dms = new ArrayList<Dm>();

      for (int i = 0; i < jsonArray.length(); ++i) {
        if (isCancelled()) {
          return RetrieveResult.CANCELLED;
        }

        Dm dm;

        try {
          JSONObject jsonObject = jsonArray.getJSONObject(i);
          dm = Dm.create(jsonObject);
          dms.add(dm);
        } catch (JSONException e) {
          Log.e(TAG, e.getMessage(), e);
          return RetrieveResult.IO_ERROR;
        }

        if (isCancelled()) {
          return RetrieveResult.CANCELLED;
        }

        if (!Utils.isEmpty(dm.profileImageUrl)
            && !sImageManager.contains(dm.profileImageUrl)) {
          // Fetch image to cache.
          try {
            sImageManager.put(dm.profileImageUrl);
          } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
          }
        }
      }

      if (isCancelled()) {
        return RetrieveResult.CANCELLED;
      }

      mDb.syncDms(dms);

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

  private class Adapter extends CursorAdapter {

    public Adapter(Context context, Cursor cursor) {
      super(context, cursor);
      
      mInflater = LayoutInflater.from(context);
      
      mUserTextColumn = cursor
          .getColumnIndexOrThrow(TwitterDbAdapter.KEY_USER);
      mTextColumn = cursor.getColumnIndexOrThrow(TwitterDbAdapter.KEY_TEXT);
      mProfileImageUrlColumn = cursor
          .getColumnIndexOrThrow(TwitterDbAdapter.KEY_PROFILE_IMAGE_URL);
      mCreatedAtColumn = cursor
          .getColumnIndexOrThrow(TwitterDbAdapter.KEY_CREATED_AT);      
    }

    private LayoutInflater mInflater;
    
    private int mUserTextColumn; 
    private int mTextColumn;
    private int mProfileImageUrlColumn;
    private int mCreatedAtColumn;
    
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
      View view = mInflater.inflate(R.layout.tweet, parent, false);
      
      ViewHolder holder = new ViewHolder();      
      holder.userText = (TextView) view.findViewById(R.id.tweet_user_text);
      holder.tweetText = (TextView) view.findViewById(R.id.tweet_text);
      holder.profileImage = (ImageView) view.findViewById(R.id.profile_image);
      holder.metaText = (TextView) view.findViewById(R.id.tweet_meta_text);      
      view.setTag(holder);
      
      return view;
    }

    class ViewHolder {
      public TextView userText;
      public TextView tweetText;
      public ImageView profileImage;
      public TextView metaText;      
    }
    
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
      ViewHolder holder = (ViewHolder) view.getTag();

      holder.userText.setText(cursor.getString(mUserTextColumn));
      holder.tweetText.setText(cursor.getString(mTextColumn));

      String profileImageUrl = cursor.getString(mProfileImageUrlColumn);

      if (!Utils.isEmpty(profileImageUrl)) {
        holder.profileImage.setImageBitmap(sImageManager.get(profileImageUrl));
      }

      try {
        holder.metaText.setText(Utils.getRelativeDate(
            TwitterDbAdapter.DB_DATE_FORMATTER.parse(
                cursor.getString(mCreatedAtColumn))));
      } catch (ParseException e) {
        Log.w(TAG, "Invalid created at data.");
      }
    }

    public void refresh() {
      getCursor().requery();
    }

  }

  // Menu.
  
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);

    MenuItem item = menu.add(0, OPTIONS_MENU_ID_REFRESH, 0, R.string.refresh);
    item.setIcon(R.drawable.refresh);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case OPTIONS_MENU_ID_REFRESH:
      sImageManager.clear();
      doRetrieve();
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

}
