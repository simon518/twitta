package com.dart.android.twitter;

import org.json.JSONException;
import org.json.JSONObject;

public class Tweet {
  public String tweetId;
  public String screenName;
  public String message;
  public String imageUrl;
  
  public static Tweet parse(JSONObject jsonObject) throws JSONException {
    Tweet tweet = new Tweet();
    
    tweet.tweetId = jsonObject.getLong("id") + "";    
    tweet.message = Utils.decodeTwitterJson(
          jsonObject.getString("text"));

    JSONObject user = jsonObject.getJSONObject("user");
    tweet.screenName = Utils.decodeTwitterJson(user.getString("screen_name"));
    tweet.imageUrl = user.getString("profile_image_url");
    
    return tweet;
  }
}
