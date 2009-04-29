package com.dart.android.twitter;

import java.util.ArrayList;

import android.content.Context;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class TweetArrayAdapter extends BaseAdapter {
  @SuppressWarnings("unused")
  private static final String TAG = "TweetArrayAdapter";
  
  private ArrayList<Tweet> mTweets;
  private Context mContext;
  private LayoutInflater mInflater;
  private StringBuilder mMetaBuilder;
      
  public TweetArrayAdapter(Context context) {
    mTweets = new ArrayList<Tweet>();
    mContext = context;
    mInflater = LayoutInflater.from(mContext);
    mMetaBuilder = new StringBuilder();
  }
  
  @Override
  public int getCount() {
    return mTweets.size();
  }

  @Override
  public Object getItem(int position) {
    return mTweets.get(position);
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  private static class ViewHolder {
    public TextView tweetText;
    public TextView metaText;
  }
  
  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    View view;
    
    if (convertView == null) {
      view = mInflater.inflate(R.layout.uniform_tweet, parent, false);
      
      ViewHolder holder = new ViewHolder();
      holder.tweetText = (TextView) view.findViewById(R.id.tweet_text);
      holder.metaText = (TextView) view.findViewById(R.id.tweet_meta_text);
      view.setTag(holder);
    } else {
      view = convertView;
    }

    ViewHolder holder = (ViewHolder) view.getTag();

    Tweet tweet = mTweets.get(position);
    
    holder.tweetText.setText(tweet.text);
    Linkify.addLinks(holder.tweetText, Linkify.WEB_URLS);
    // TODO: @ matcher.
    /*
    Linkify.addLinks(holder.tweetText, NAME_MATCHER, PROFILE_URL, null,
        NAME_MATCHER_TRANFORM);
        */
    holder.tweetText.setMovementMethod(
        LessClickyLinkMovementMethod.getInstance());

    String profileImageUrl = tweet.profileImageUrl;

    holder.metaText.setText(Tweet.buildMetaText(mMetaBuilder,
        tweet.createdAt, tweet.source));        
    
    return view;    
  }
        
  public void refresh(ArrayList<Tweet> tweets) {
    mTweets = tweets;
    notifyDataSetChanged();
  }
  
}    

