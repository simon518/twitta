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
