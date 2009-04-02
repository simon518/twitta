package com.dart.android.twitter;

import java.io.File;
import java.io.IOException;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.dart.android.twitter.TwitterApi.ApiException;
import com.dart.android.twitter.TwitterApi.AuthException;
import com.google.android.photostream.UserTask;

public class PictureActivity extends BaseActivity {

  private static final String TAG = "PictureActivity";

  private ImageView mPreview;
  private TweetEdit mTweetEdit;
  private ImageButton mSendButton;

  private TextView mProgressText;

  private Uri mImageUri;
  private File mFile;

  private UserTask<Void, Void, TaskResult> mSendTask;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.picture);

    mPreview = (ImageView) findViewById(R.id.preview);

    mTweetEdit = new TweetEdit((EditText) findViewById(R.id.tweet_edit),
        (TextView) findViewById(R.id.chars_text));

    mTweetEdit.setOnKeyListener(editEnterHandler);

    mProgressText = (TextView) findViewById(R.id.progress_text);

    mSendButton = (ImageButton) findViewById(R.id.send_button);
    mSendButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        doSend();
      }
    });

    Intent intent = getIntent();
    Bundle extras = intent.getExtras();

    mFile = null;

    if (Intent.ACTION_SEND.equals(intent.getAction()) && (extras != null)
        && extras.containsKey(Intent.EXTRA_STREAM)) {
      mImageUri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);
      if (mImageUri != null) {
        mPreview.setImageURI(mImageUri);

        Cursor cursor = getContentResolver().query(mImageUri, null, null, null,
            null);

        if (cursor.moveToFirst()) {
          String filename = cursor.getString(cursor
              .getColumnIndexOrThrow(ImageColumns.DATA));
          mFile = new File(filename);
        }
      }
    }

    if (mFile == null) {
      updateProgress("Could not locate picture file. Sorry!");
      disableEntry();
    }
  }

  @Override
  protected void onDestroy() {
    Log.i(TAG, "onDestroy.");

    if (mSendTask != null && mSendTask.getStatus() == UserTask.Status.RUNNING) {
      // Doesn't really cancel execution (we let it continue running).
      // See the SendTask code for more details.
      mSendTask.cancel(true);
    }

    super.onDestroy();
  }

  // UI helpers.

  private void updateProgress(String progress) {
    mProgressText.setText(progress);
  }

  private void enableEntry() {
    mTweetEdit.setEnabled(true);
    mSendButton.setEnabled(true);
  }

  private void disableEntry() {
    mTweetEdit.setEnabled(false);
    mSendButton.setEnabled(false);
  }

  private enum TaskResult {
    OK, IO_ERROR, AUTH_ERROR, CANCELLED, API_ERROR
  }

  private void doSend() {
    if (mSendTask != null && mSendTask.getStatus() == UserTask.Status.RUNNING) {
      Log.w(TAG, "Already sending.");
    } else {
      mSendTask = new SendTask().execute();
    }
  }

  private class SendTask extends UserTask<Void, Void, TaskResult> {
    private String apiErrorMessage;
    
    @Override
    public void onPreExecute() {
      disableEntry();
      updateProgress("Posting pic...");
    }

    @Override
    public TaskResult doInBackground(Void... params) {
      try {
        String status = mTweetEdit.getText().toString();
        mApi.postTwitPic(mFile, status);
      } catch (IOException e) {
        Log.e(TAG, e.getMessage(), e);
        return TaskResult.IO_ERROR;
      } catch (AuthException e) {
        Log.i(TAG, "Invalid authorization.");
        return TaskResult.AUTH_ERROR;
      } catch (ApiException e) {
        Log.e(TAG, e.getMessage(), e);
        apiErrorMessage = e.getMessage();
        return TaskResult.API_ERROR;
      }

      return TaskResult.OK;
    }

    @Override
    public void onPostExecute(TaskResult result) {
      if (isCancelled()) {
        // Canceled doesn't really mean "canceled" in this task.
        // We want the request to complete, but don't want to update the
        // activity (it's probably dead).
        return;
      }

      if (result == TaskResult.AUTH_ERROR) {
        onAuthFailure();
      } else if (result == TaskResult.API_ERROR) {
        updateProgress(apiErrorMessage);        
      } else if (result == TaskResult.OK) {
        updateProgress("Picture has been posted");
      } else if (result == TaskResult.IO_ERROR) {
        updateProgress("Unable to post pic");
        enableEntry();
      }
    }
  }
  
  private void onAuthFailure() {
    logout();
  }

  private View.OnKeyListener editEnterHandler = new View.OnKeyListener() {
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

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);

    // What a hack!
    menu.clear();

    MenuItem item = menu.add(0, OPTIONS_MENU_ID_TWEETS, 0, R.string.tweets);
    item.setIcon(android.R.drawable.ic_menu_view);    
        
    item = menu.add(0, OPTIONS_MENU_ID_ABOUT, 0, R.string.about);
    item.setIcon(android.R.drawable.ic_menu_info_details);
    
    return true;
  }
  
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case OPTIONS_MENU_ID_TWEETS:
      Intent intent = TwitterActivity.createIntent(this);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(intent);
      return true;                  
    }

    return super.onOptionsItemSelected(item);
  }
  
}
