package com.dart.android.twitter;

import java.util.HashSet;

import android.app.Application;
import android.database.Cursor;
import android.util.Log;

public class TwitterApplication extends Application {
  
  public static final String TAG = "TwitterApplication";
  
  public static ImageManager mImageManager;
  public static TwitterDbAdapter mDb; 

  @Override
  public void onCreate() {
    super.onCreate();
    mImageManager = new ImageManager(this);
    mDb = new TwitterDbAdapter(this);
    mDb.open();
  }
  
  @Override
  public void onTerminate() {
    Log.i(TAG, "onTerminate.");    
    cleanupImages();
    mDb.close();
    
    super.onTerminate();
  }
  
  protected static void cleanupImages() {
    HashSet<String> keepers = new HashSet<String>();
    
    Cursor cursor = mDb.fetchAllTweets();
    
    if (cursor.moveToFirst()) {
      int imageIndex = cursor.getColumnIndexOrThrow(
          TwitterDbAdapter.KEY_PROFILE_IMAGE_URL);
      do {
        keepers.add(cursor.getString(imageIndex));
      } while (cursor.moveToNext());
    }
    
    cursor.close();
    
    cursor = mDb.fetchAllDms();
    
    if (cursor.moveToFirst()) {
      int imageIndex = cursor.getColumnIndexOrThrow(
          TwitterDbAdapter.KEY_PROFILE_IMAGE_URL);
      do {
        keepers.add(cursor.getString(imageIndex));
      } while (cursor.moveToNext());
    }
    
    cursor.close();
    
    mImageManager.cleanup(keepers);
  }
    
}
