package com.dart.android.twitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Utils {
  public static boolean isEmpty(String s) {
    return s == null || s.length() == 0;      
  }
   
  public static String stringifyStream(InputStream is) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
    StringBuilder sb = new StringBuilder();
    String line;
    
    while ((line = reader.readLine()) != null) {
      sb.append(line + "\n");
    }
     
    return sb.toString();
  }
   
  /*
  public static final String METHOD_GET = "GET";
  public static final String METHOD_POST = "POST";
    
  public static Bitmap requestImage(URI uri) throws IOException {
    InputStream is = requestData(uri, METHOD_GET);
    BufferedInputStream bis = new BufferedInputStream(is); 
    Bitmap bitmap = BitmapFactory.decodeStream(bis); 
    bis.close(); 
    is.close();
     
    return bitmap;
  }          
  */
  
  /*
  public static InputStream requestData(URI uri, String httpMethod)
      throws IOException {
    DefaultHttpClient client = new DefaultHttpClient();
    HttpUriRequest method;
     
    BasicScheme basicScheme = new BasicScheme(); 
    AuthSchemeRegistry authRegistry = new AuthSchemeRegistry(); 
    authRegistry.register(basicScheme.getSchemeName(), new 
        BasicSchemeFactory()); 
    client.setAuthSchemes(authRegistry); 
    client.setCredentialsProvider(new BasicCredentialsProvider()); 
     
    if (METHOD_POST.equals(httpMethod)) {
      method = new HttpPost(uri);
    } else if (METHOD_GET.equals(httpMethod)) {
      method = new HttpGet(uri);       
    } else {
      throw new IllegalArgumentException("Invalid method.");
    }
     
    HttpResponse response = client.execute(method);          
     
    if (response.getStatusLine().getStatusCode() != 200) {
      throw new IOException("Non OK response code: " +
          response.getStatusLine().getStatusCode());
    }
     
    InputStream data = response.getEntity().getContent();         
     
    return data;
  }    
  */      
}
