package com.dart.android.twitter;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

/**
 * <p>
 * Retrieves and caches images in memory. Does not persist or store the images.
 * TODO: persist and remove old images.
 * </p> 
 */
public class ImageManager {
  private static final String TAG = "ImageManager";
  
  private Map<String, Bitmap> mCache;
  private HttpClient mClient;
  
  private static final int CONNECTION_TIMEOUT_MS = 5 * 1000;
  private static final int SOCKET_TIMEOUT_MS = 5 * 1000;
  
  ImageManager() {
    mCache = new HashMap<String, Bitmap>();
    mClient = new DefaultHttpClient();
  }

  public Bitmap get(String url) throws IOException {
    if (lookup(url) == null) {
      fetch(url);
    }
    
    return lookup(url);
  }
  
  public void fetch(String url) throws IOException {
    Log.i(TAG, "Fetching image: " + url);    
    
    HttpGet get = new HttpGet(url);
    HttpConnectionParams.setConnectionTimeout(get.getParams(),
        CONNECTION_TIMEOUT_MS);
    HttpConnectionParams.setSoTimeout(get.getParams(),
        SOCKET_TIMEOUT_MS);    
    
    HttpResponse response;
    
    try {
      response = mClient.execute(get);
    } catch (ClientProtocolException e) {
      e.printStackTrace();
      throw new IOException("Invalid client protocol.");
    }
    
    if (response.getStatusLine().getStatusCode() != 200) {
      throw new IOException("Non OK response: " +
          response.getStatusLine().getStatusCode());
    }
    
    HttpEntity entity = response.getEntity();        
    BufferedInputStream bis = new BufferedInputStream(entity.getContent(), 
        8 * 1024); 
    Bitmap bitmap = BitmapFactory.decodeStream(bis);
    
    synchronized(this) {        
      mCache.put(url, bitmap);
    }
    
    bis.close();    
  }

  public Bitmap lookup(String url) {
    synchronized(this) {            
      return mCache.get(url);
    }
  }

  public boolean contains(String url) {
    return lookup(url) != null;
  }
  
}
