package com.dart.android.twitter;

import org.json.JSONException;
import org.json.JSONObject;

public class Tweet {
  public String id;
  public String screenName;
  public String text;
  public String profileImageUrl;
  
  public static Tweet create(JSONObject jsonObject) throws JSONException {
    Tweet tweet = new Tweet();
    
    tweet.id = jsonObject.getLong("id") + "";    
    tweet.text = Utils.decodeTwitterJson(jsonObject.getString("text"));

    JSONObject user = jsonObject.getJSONObject("user");
    tweet.screenName = Utils.decodeTwitterJson(user.getString("screen_name"));
    tweet.profileImageUrl = user.getString("profile_image_url");
    
    return tweet;
  }
}
