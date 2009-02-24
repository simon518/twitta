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
    
    if (jsonObject.has("id")) {
      tweet.tweetId = jsonObject.getLong("id") + "";
    }
    
    if (jsonObject.has("text")) {
      tweet.message = Utils.decodeTwitterJson(
          jsonObject.getString("text"));
    } 

    if (jsonObject.has("user")) {
      JSONObject user = jsonObject.getJSONObject("user");
      if (user.has("screen_name")) {
        tweet.screenName = Utils.decodeTwitterJson(
            user.getString("screen_name"));
      }
      if (user.has("profile_image_url")) {
        tweet.imageUrl = user.getString("profile_image_url");
      }          
    }      
    
    return tweet;
  }
}
