package com.dart.android.twitter;

import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class TwitterDbAdapter {
  private static final String TAG = "TwitterDbAdapter";
    
  public static final String KEY_ID = "_id";
  public static final String KEY_USER = "user";
  public static final String KEY_TEXT = "text";
  public static final String KEY_PROFILE_IMAGE_URL = "profile_image_url";
  
  public static final String [] COLUMNS = new String [] {
    KEY_ID,
    KEY_USER,
    KEY_TEXT,
    KEY_PROFILE_IMAGE_URL
  };
  
  private DatabaseHelper mDbHelper;
  private SQLiteDatabase mDb;

  private static final String DATABASE_NAME = "data";
  private static final String DATABASE_TABLE = "tweets";
  private static final int DATABASE_VERSION = 1;
  
  private static final String DATABASE_CREATE =
      "create table tweets (" +
          KEY_ID + " integer primary key on conflict replace, " +
          KEY_USER + " text not null, " +
          KEY_TEXT + " text not null, " +
          KEY_PROFILE_IMAGE_URL + " text not null);";

  private final Context mContext;

  private static class DatabaseHelper extends SQLiteOpenHelper {
    DatabaseHelper(Context context) {
      super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      db.execSQL(DATABASE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      Log.w(TAG, "Upgrading database from version " + oldVersion + " to " +
          newVersion + " which destroys all old data");
      db.execSQL("DROP TABLE IF EXISTS " + DATABASE_TABLE);
      onCreate(db);
    }
  }

  public TwitterDbAdapter(Context context) {
    this.mContext = context;
  }

  public TwitterDbAdapter open() throws SQLException {
    mDbHelper = new DatabaseHelper(mContext);
    mDb = mDbHelper.getWritableDatabase();
    
    return this;
  }
  
  public void close() {
    mDbHelper.close();
  }

  public long createTweet(String id, String user, String text,
      String imageUrl) {
    ContentValues initialValues = new ContentValues();
    initialValues.put(KEY_ID, id);
    initialValues.put(KEY_USER, user);      
    initialValues.put(KEY_TEXT, text);
    initialValues.put(KEY_PROFILE_IMAGE_URL, imageUrl);

    return mDb.insert(DATABASE_TABLE, null, initialValues);
  }

  public void syncTweets(List<Tweet> tweets) {
    try {
      mDb.beginTransaction();
      
      deleteAllTweets();
      
      for (Tweet tweet : tweets) {
        createTweet(tweet.id, tweet.screenName, tweet.text,
            tweet.profileImageUrl);
      }
      
      mDb.setTransactionSuccessful();
    } finally {      
      mDb.endTransaction();
    }
  }
  
  public Cursor fetchAllTweets() {
    return mDb.query(DATABASE_TABLE, COLUMNS, null, null, null, null,
        KEY_ID + " DESC");
  }
  
  public boolean deleteAllTweets() {
    return mDb.delete(DATABASE_TABLE, null, null) > 0;
  }  

  public int fetchMaxId() {
    Cursor mCursor = mDb.rawQuery("SELECT MAX(" + KEY_ID + ") FROM tweets",
        null);

    int result = 0;
        
    if (mCursor == null) {
      return result;
    } 
    
    mCursor.moveToFirst();    
    result = mCursor.getInt(0);
    mCursor.close();
    
    return result;
  }  

}
