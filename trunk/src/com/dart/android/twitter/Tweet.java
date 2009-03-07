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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

public class Tweet {
  private static final String TAG = "Tweet";
  
  public String id;
  public String screenName;
  public String text;
  public String profileImageUrl;
  public Date createdAt;
  public String source;
  
  public static Tweet create(JSONObject jsonObject) throws JSONException {
    Tweet tweet = new Tweet();
    
    tweet.id = jsonObject.getLong("id") + "";    
    tweet.text = Utils.decodeTwitterJson(jsonObject.getString("text"));
    tweet.createdAt = parseDateTime(jsonObject.getString("created_at"));
    
    JSONObject user = jsonObject.getJSONObject("user");
    tweet.screenName = Utils.decodeTwitterJson(user.getString("screen_name"));
    tweet.profileImageUrl = user.getString("profile_image_url");
    tweet.source = Utils.decodeTwitterJson(jsonObject.getString("source")).
        replaceAll("\\<.*?>", "");
    
    return tweet;
  }
  
  public static final DateFormat TWITTER_DATE_FORMATTER =
      new SimpleDateFormat("E MMM d HH:mm:ss Z yyyy");
     
  public static final Date parseDateTime(String dateString) {
    try {
      return TWITTER_DATE_FORMATTER.parse(dateString);
    } catch (ParseException e) {
      Log.w(TAG, "Could not parse Twitter date string: " + dateString);
      return null;
    }
  }  

  public static final DateFormat AGO_FULL_DATE_FORMATTER =
      new SimpleDateFormat("h:mm a MMM d");
  
  public static String getRelativeDate(Date date) {
    Date now = new Date();
    
    // Seconds.
    long diff = (now.getTime() - date.getTime()) / 1000;

    if (diff < 0) {
      diff = 0;
    }
    
    if (diff < 60) {
      return diff + " seconds ago";
    } 
    
    // Minutes.
    diff /= 60;
    
    if (diff <= 1) {
      return "about a minute ago";
    } else if (diff < 60) {
      return "about " + diff + " minutes ago";
    }
    
    // Hours.
    diff /= 60;
    
    if (diff <= 1) {
      return "about an hour ago";
    } else if (diff < 24) {
      return "about " + diff + " hours ago";
    }
    
    return AGO_FULL_DATE_FORMATTER.format(date);        
  }
  
}
