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

import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;

public class Tweet {
  @SuppressWarnings("unused")
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
    tweet.createdAt = Utils.parseDateTime(jsonObject.getString("created_at"));
    
    JSONObject user = jsonObject.getJSONObject("user");
    tweet.screenName = Utils.decodeTwitterJson(user.getString("screen_name"));
    tweet.profileImageUrl = user.getString("profile_image_url");
    tweet.source = Utils.decodeTwitterJson(jsonObject.getString("source")).
        replaceAll("\\<.*?>", "");
    
    return tweet;
  }
    
}
