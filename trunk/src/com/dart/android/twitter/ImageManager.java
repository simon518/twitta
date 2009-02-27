package com.dart.android.twitter;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class ImageManager {
  private static final String TAG = "ImageManager";

  private Context mContext;  
  private Map<String, Bitmap> mCache;
  private HttpClient mClient;
  private MessageDigest mDigest;
    
  private static final int CONNECTION_TIMEOUT_MS = 5 * 1000;
  private static final int SOCKET_TIMEOUT_MS = 5 * 1000;
  
  ImageManager(Context context) {
    mContext = context;
    mCache = new HashMap<String, Bitmap>();
    mClient = new DefaultHttpClient();
    
    try {
      mDigest = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      // This shouldn't happen.
      throw new RuntimeException("No MD5 algorithm.");
    }    
  }

  private String getHash(MessageDigest digest) {
    StringBuilder builder = new StringBuilder();
    
    for (byte b : digest.digest()) {
      builder.append(Integer.toHexString((b >> 4) & 0xf));
      builder.append(Integer.toHexString(b & 0xf));
    }
    
    return builder.toString();
  }

  private String getMd5(String url) {
    mDigest.update(url.getBytes());
    
    return getHash(mDigest);
  }

  private Bitmap lookupFile(String url) {
    String hashedUrl = getMd5(url);    
    FileInputStream fis = null;
    
    try {
      fis = mContext.openFileInput(hashedUrl);
      return BitmapFactory.decodeStream(fis);
    } catch (FileNotFoundException e) {
      // Not there.
      return null;
    } finally {
      if (fis != null) {
        try {
          fis.close();
        } catch (IOException e) {
          // Ignore.
        }
      }
    }           
  }
  
  public Bitmap put(String url) throws IOException {
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
    bis.close();

    synchronized(this) {            
      mCache.put(url, bitmap);
    }
    
    writeFile(url, bitmap);    
    
    return bitmap;    
  }

  private void writeFile(String url, Bitmap bitmap) {
    String hashedUrl = getMd5(url);
    
    FileOutputStream fos;
    
    try {
      fos = mContext.openFileOutput(hashedUrl,
          Context.MODE_PRIVATE);
    } catch (FileNotFoundException e) {
      Log.w(TAG, "Error creating file.");
      return;
    }
    
    Log.i(TAG, "Writing file: " + hashedUrl);
    bitmap.compress(Bitmap.CompressFormat.PNG, 0, fos);
    
    try {
      fos.close();
    } catch (IOException e) {
      // Ignore.
    }                
  }
  
  public Bitmap get(String url) {
    Bitmap bitmap;
    
    synchronized(this) {            
      bitmap = mCache.get(url);
    }

    if (bitmap != null) {
      return bitmap;
    }
    
    // Try file.
    bitmap = lookupFile(url);
    
    if (bitmap != null) {
      Log.i(TAG, "Image is in file cache.");
      
      synchronized(this) {            
        mCache.put(url, bitmap);
      }
      
      return bitmap;          
    }

    return null;    
  }
  
  public boolean contains(String url) {
    return get(url) != null;
  }

  public void cleanupFiles() {
    // Remove stored files that aren't in the cache. 
  }
  
}
