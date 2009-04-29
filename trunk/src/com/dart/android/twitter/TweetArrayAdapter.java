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
  private static final String TAG = "TweetArrayAdapter";
  
  private ArrayList<Tweet> mTweets = new ArrayList<Tweet>();
  private Context mContext;
  private LayoutInflater mInflater;
  private StringBuilder mMetaBuilder;
      
  public TweetArrayAdapter(Context context) {
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
    public TextView tweetUserText;
    public TextView tweetText;
    public ImageView profileImage;
    public TextView metaText;
  }
  
  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    View view;
    
    if (convertView == null) {
      view = mInflater.inflate(R.layout.tweet, parent, false);
      
      ViewHolder holder = new ViewHolder();
      holder.tweetUserText = (TextView) view.findViewById(R.id.tweet_user_text);
      holder.tweetText = (TextView) view.findViewById(R.id.tweet_text);
      holder.profileImage = (ImageView) view.findViewById(R.id.profile_image);
      holder.metaText = (TextView) view.findViewById(R.id.tweet_meta_text);
      view.setTag(holder);
    } else {
      view = convertView;
    }

    ViewHolder holder = (ViewHolder) view.getTag();

    Tweet tweet = mTweets.get(position);
    
    holder.tweetUserText.setText(tweet.screenName);

    holder.tweetText.setText(tweet.text);
    Linkify.addLinks(holder.tweetText, Linkify.WEB_URLS);
    /*
    Linkify.addLinks(holder.tweetText, NAME_MATCHER, PROFILE_URL, null,
        NAME_MATCHER_TRANFORM);
        */
    // TODO: replace with custom movement method that is less 'clicky'.
    /*
    holder.tweetText.setMovementMethod(
        TestMovementMethod.getInstance());
        */

    String profileImageUrl = tweet.profileImageUrl;

    if (!Utils.isEmpty(profileImageUrl)) {
      holder.profileImage.setImageBitmap(TwitterApplication.mImageManager.get(
          profileImageUrl));
    }

    holder.metaText.setText(Tweet.buildMetaText(mMetaBuilder,
        tweet.createdAt, tweet.source));        
    
    return view;    
  }
        
  public void refresh(ArrayList<Tweet> tweets) {
    mTweets = tweets;
    notifyDataSetChanged();
  }
  
}    

