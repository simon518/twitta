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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthSchemeRegistry;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

public class TwitterApi {
  private static final String TAG = "TwitterApi";

  private static final String UPDATE_URL =
    "http://twitter.com/statuses/update.json";
  private static final String VERIFY_CREDENTIALS_URL =
    "https://twitter.com/account/verify_credentials.json";
  private static final String FRIENDS_TIMELINE_URL =
    "http://twitter.com/statuses/friends_timeline.json";

  private static final String TWITTER_HOST = "twitter.com";

  private DefaultHttpClient mClient;
  private AuthScope mAuthScope;
  
  private static final String METHOD_GET = "GET";
  private static final String METHOD_POST = "POST";

  public class AuthException extends Exception {
    private static final long serialVersionUID = 1703735789572778599L;    
  }
    
  private static final int CONNECTION_TIMEOUT_MS = 30 * 1000;
  private static final int SOCKET_TIMEOUT_MS = 30 * 1000;
  
  public TwitterApi() {
    prepareHttpClient();
  }

  public static boolean isValidCredentials(String username, String password) {
    return !Utils.isEmpty(username) && !Utils.isEmpty(password);
  }
    
  private void prepareHttpClient() {
    mAuthScope = new AuthScope(TWITTER_HOST, AuthScope.ANY_PORT);
    mClient = new DefaultHttpClient();
    BasicScheme basicScheme = new BasicScheme(); 
    AuthSchemeRegistry authRegistry = new AuthSchemeRegistry(); 
    authRegistry.register(basicScheme.getSchemeName(),
        new BasicSchemeFactory()); 
    mClient.setAuthSchemes(authRegistry); 
    mClient.setCredentialsProvider(new BasicCredentialsProvider());     
  }
    
  public void setCredentials(String username, String password) {
    mClient.getCredentialsProvider().setCredentials( 
        mAuthScope, 
        new UsernamePasswordCredentials(username, password));         
  }
   
  private InputStream requestData(String url, String httpMethod,
      ArrayList<NameValuePair> params) throws IOException, AuthException {
    Log.i(TAG, "Sending " + httpMethod + " request to " + url);
    
    URI uri;
    
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      Log.e(TAG, e.getMessage(), e);
      throw new IOException("Invalid URL.");
    }
    
    HttpUriRequest method;

    if (METHOD_POST.equals(httpMethod)) {
      HttpPost post = new HttpPost(uri);
      // See this:
      // http://groups.google.com/group/twitter-development-talk/browse_thread/thread/e178b1d3d63d8e3b
      post.getParams().setBooleanParameter("http.protocol.expect-continue",
          false);                 
      post.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
      method = post;
    } else {
      method = new HttpGet(uri);            
    }
    
    HttpConnectionParams.setConnectionTimeout(method.getParams(),
        CONNECTION_TIMEOUT_MS);
    HttpConnectionParams.setSoTimeout(method.getParams(), SOCKET_TIMEOUT_MS);    
    
    HttpResponse response;
    
    try {
      response = mClient.execute(method);
    } catch (ClientProtocolException e) {
      Log.e(TAG, e.getMessage(), e);
      throw new IOException("HTTP protocol error.");
    }              
    
    int statusCode = response.getStatusLine().getStatusCode();
      
    if (statusCode == 401) {
      throw new AuthException();      
    } else if (statusCode != 200) {
      throw new IOException("Non OK response code: " + statusCode);
    }
    
    return response.getEntity().getContent();
  }
  
  public void login(String username, String password) throws
      IOException, AuthException {
    Log.i(TAG, "Login attempt for " + username);
    setCredentials(username, password);
    InputStream data = requestData(VERIFY_CREDENTIALS_URL, METHOD_GET, null);    
    data.close();
  }

  public void logout() {
    setCredentials("", "");
  }

  public JSONArray getTimeline() throws IOException, AuthException {
    Log.i(TAG, "Requesting friends timeline.");
    
    InputStream data = requestData(FRIENDS_TIMELINE_URL, METHOD_GET, null);        
    JSONArray json = null;
    
    try {
      json = new JSONArray(Utils.stringifyStream(data));
    } catch (JSONException e) {
      Log.e(TAG, e.getMessage(), e);
      throw new IOException("Could not parse JSON.");
    } finally {      
      data.close();
    }
        
    return json;
  }  

  public JSONArray getTimelineSinceId(int sinceId) throws
      IOException, AuthException {
    Log.i(TAG, "Requesting friends timeline since id.");

    String url = FRIENDS_TIMELINE_URL + "?since_id=" +
        URLEncoder.encode(sinceId + "", HTTP.UTF_8);
    
    InputStream data = requestData(url, METHOD_GET, null);        
    JSONArray json = null;
    
    try {
      json = new JSONArray(Utils.stringifyStream(data));
    } catch (JSONException e) {
      Log.e(TAG, e.getMessage(), e);
      throw new IOException("Could not parse JSON.");
    } finally {      
      data.close();
    }
        
    return json;
  }  
  
  public JSONObject update(String status) throws IOException, AuthException {
    Log.i(TAG, "Updating status.");
    
    ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
    params.add(new BasicNameValuePair("status", status));    
    params.add(new BasicNameValuePair("source", "Twitta"));
    
    InputStream data = requestData(UPDATE_URL, METHOD_POST, params);
    JSONObject json = null;
    
    try {
      json = new JSONObject(Utils.stringifyStream(data));
    } catch (JSONException e) {
      Log.e(TAG, e.getMessage(), e);
      throw new IOException("Could not parse JSON.");
    } finally {      
      data.close();
    }
        
    return json;
  }  
  
}
