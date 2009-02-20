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

  // Twitter JSON encodes < and > to prevent XSS attacks on websites.
  public static String decodeTwitterJson(String s) {
    return s.replace("&lt;", "<").replace("&gt;", ">");
  }
  
}
