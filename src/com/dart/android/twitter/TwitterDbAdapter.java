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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
  public static final String KEY_IS_UNREAD = "is_unread";
  public static final String KEY_CREATED_AT = "created_at";
  public static final String KEY_SOURCE = "source";
  public static final String KEY_IS_SENT = "is_sent";  
  
  public static final String [] TWEET_COLUMNS = new String [] {
    KEY_ID,
    KEY_USER,
    KEY_TEXT,
    KEY_PROFILE_IMAGE_URL,
    KEY_IS_UNREAD,
    KEY_CREATED_AT,
    KEY_SOURCE
  };

  public static final String [] DM_COLUMNS = new String [] {
    KEY_ID,
    KEY_USER,
    KEY_TEXT,
    KEY_PROFILE_IMAGE_URL,
    KEY_IS_UNREAD,
    KEY_IS_SENT,    
    KEY_CREATED_AT
  };
  
  private DatabaseHelper mDbHelper;
  private SQLiteDatabase mDb;

  private static final String DATABASE_NAME = "data";
  private static final String TWEET_TABLE = "tweets";
  private static final String DM_TABLE = "dms";  	
  
  private static final int DATABASE_VERSION = 4;
  
  // NOTE: the twitter ID is used as the row ID.
  // Furthermore, if a row already exists, an insert will replace
  // the old row upon conflict.
  private static final String TWEET_TABLE_CREATE =
      "create table tweets (" +
          KEY_ID + " integer primary key on conflict replace, " +
          KEY_USER + " text not null, " +
          KEY_TEXT + " text not null, " +
          KEY_PROFILE_IMAGE_URL + " text not null, " +
          KEY_IS_UNREAD + " boolean not null, " +
          KEY_CREATED_AT + " date not null, " +
          KEY_SOURCE + " text not null)";

  private static final String DM_TABLE_CREATE =
    "create table dms (" +
        KEY_ID + " integer primary key on conflict replace, " +
        KEY_USER + " text not null, " +
        KEY_TEXT + " text not null, " +
        KEY_PROFILE_IMAGE_URL + " text not null, " +
        KEY_IS_UNREAD + " boolean not null, " +
        KEY_IS_SENT + " boolean not null, " +        
        KEY_CREATED_AT + " date not null)";
  
  private final Context mContext;

  private static class DatabaseHelper extends SQLiteOpenHelper {
    DatabaseHelper(Context context) {
      super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      db.execSQL(TWEET_TABLE_CREATE);
      db.execSQL(DM_TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      Log.w(TAG, "Upgrading database from version " + oldVersion + " to " +
          newVersion + " which destroys all old data");
      db.execSQL("DROP TABLE IF EXISTS " + TWEET_TABLE);
      db.execSQL("DROP TABLE IF EXISTS " + DM_TABLE);
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

  public final static DateFormat DB_DATE_FORMATTER =
      new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
  
  // TODO: move all these to the model.  
  public long createTweet(Tweet tweet, boolean isUnread) {
    ContentValues initialValues = new ContentValues();
    initialValues.put(KEY_ID, tweet.id);
    initialValues.put(KEY_USER, tweet.screenName);      
    initialValues.put(KEY_TEXT, tweet.text);
    initialValues.put(KEY_PROFILE_IMAGE_URL, tweet.profileImageUrl);
    initialValues.put(KEY_IS_UNREAD, isUnread);
    initialValues.put(KEY_CREATED_AT,
        DB_DATE_FORMATTER.format(tweet.createdAt));
    initialValues.put(KEY_SOURCE, tweet.source);
    
    return mDb.insert(TWEET_TABLE, null, initialValues);
  }

  public long createDm(Dm dm, boolean isUnread) {
    ContentValues initialValues = new ContentValues();
    initialValues.put(KEY_ID, dm.id);
    initialValues.put(KEY_USER, dm.screenName);      
    initialValues.put(KEY_TEXT, dm.text);
    initialValues.put(KEY_PROFILE_IMAGE_URL, dm.profileImageUrl);
    initialValues.put(KEY_IS_UNREAD, isUnread);
    initialValues.put(KEY_IS_SENT, dm.isSent);
    initialValues.put(KEY_CREATED_AT,
        DB_DATE_FORMATTER.format(dm.createdAt));
    
    return mDb.insert(DM_TABLE, null, initialValues);
  }
  
  public void syncTweets(List<Tweet> tweets) {
    try {
      mDb.beginTransaction();
      
      deleteAllTweets();
      
      for (Tweet tweet : tweets) {
        createTweet(tweet, false);
      }
      
      mDb.setTransactionSuccessful();
    } finally {      
      mDb.endTransaction();
    }
  }

  public void syncDms(List<Dm> dms) {
    try {
      mDb.beginTransaction();
      
      deleteAllDms();
      
      for (Dm dm : dms) {
        createDm(dm, false);
      }
      
      mDb.setTransactionSuccessful();
    } finally {      
      mDb.endTransaction();
    }
  }
  
  public int addNewTweetsAndCountUnread(List<Tweet> tweets) {
    int unreadCount = 0;
    
    try {
      mDb.beginTransaction();
      
      for (Tweet tweet : tweets) {
        createTweet(tweet, true);
      }
      
      unreadCount = fetchUnreadCount();      
      mDb.setTransactionSuccessful();
    } finally {      
      mDb.endTransaction();
    }
    
    return unreadCount;
  }
  
  public Cursor fetchAllTweets() {
    return mDb.query(TWEET_TABLE, TWEET_COLUMNS, null, null, null, null,
        KEY_ID + " DESC");
  }

  public Cursor fetchAllDms() {
    return mDb.query(DM_TABLE, DM_COLUMNS, null, null, null, null,
        KEY_ID + " DESC");
  }

  // TODO: this seems a bit messy.
  public Cursor getRecentRecipients(String filter) {
    String likeFilter = '%' + filter + '%';
    
    return mDb.rawQuery("SELECT " + KEY_USER + " as _id, " + KEY_USER + " "
        + "FROM " + DM_TABLE + " " + "WHERE " + KEY_USER + " LIKE ? "
        + "AND " + KEY_IS_SENT + " = 1 GROUP BY " + KEY_USER        
        ,
        new String[] { likeFilter });    
  }
  
  public void clearData() {
    deleteAllTweets();
    deleteAllDms();
  }
  
  public boolean deleteAllTweets() {
    return mDb.delete(TWEET_TABLE, null, null) > 0;
  }  

  public boolean deleteAllDms() {
    return mDb.delete(DM_TABLE, null, null) > 0;
  }  

  public boolean deleteDm(int id) {
    return mDb.delete(DM_TABLE, KEY_ID + "=" + id, null) > 0;
  }  
  
  public void markAllTweetsRead() {
    ContentValues values = new ContentValues();
    values.put(KEY_IS_UNREAD, 0);    
    mDb.update(TWEET_TABLE, values, null, null);
  }

  public void markAllDmsRead() {
    ContentValues values = new ContentValues();
    values.put(KEY_IS_UNREAD, 0);    
    mDb.update(DM_TABLE, values, null, null);
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

  public int fetchUnreadCount() {
    Cursor mCursor = mDb.rawQuery("SELECT COUNT(" + KEY_ID + ") FROM tweets " +
        "WHERE is_unread = 1", null);

    int result = 0;
        
    if (mCursor == null) {
      return result;
    } 
    
    mCursor.moveToFirst();    
    result = mCursor.getInt(0);
    mCursor.close();
    
    return result;
  }

  public int fetchMaxDmId() {
    Cursor mCursor = mDb.rawQuery("SELECT MAX(" + KEY_ID + ") FROM dm",
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

  public int addNewDmsAndCountUnread(ArrayList<Dm> dms) {
    int unreadCount = 0;
    
    try {
      mDb.beginTransaction();
      
      for (Dm dm : dms) {
        createDm(dm, true);
      }
      
      unreadCount = fetchUnreadDmCount();      
      mDb.setTransactionSuccessful();
    } finally {      
      mDb.endTransaction();
    }
    
    return unreadCount;
  }

  private int fetchUnreadDmCount() {
    Cursor mCursor = mDb.rawQuery("SELECT COUNT(" + KEY_ID + ") FROM dms " +
        "WHERE is_unread = 1", null);

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
