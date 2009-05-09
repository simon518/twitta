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
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Pattern;

import android.text.util.Linkify;
import android.util.Log;
import android.widget.TextView;

public class Utils {

  private static final String TAG = "Utils";

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

  public static final DateFormat TWITTER_DATE_FORMATTER = new SimpleDateFormat(
      "E MMM d HH:mm:ss Z yyyy");

  public static final Date parseDateTime(String dateString) {
    try {
      return TWITTER_DATE_FORMATTER.parse(dateString);
    } catch (ParseException e) {
      Log.w(TAG, "Could not parse Twitter date string: " + dateString);
      return null;
    }
  }

  public static final DateFormat AGO_FULL_DATE_FORMATTER = new SimpleDateFormat(
      "h:mm a MMM d");

  public static String getRelativeDate(Date date) {
    Date now = new Date();

    // Seconds.
    long diff = (now.getTime() - date.getTime()) / 1000;

    if (diff < 0) {
      diff = 0;
    }

    if (diff < 60) {
      return diff + " seconds ago";
    }

    // Minutes.
    diff /= 60;

    if (diff <= 1) {
      return "about a minute ago";
    } else if (diff < 60) {
      return "about " + diff + " minutes ago";
    }

    // Hours.
    diff /= 60;

    if (diff <= 1) {
      return "about an hour ago";
    } else if (diff < 24) {
      return "about " + diff + " hours ago";
    }

    return AGO_FULL_DATE_FORMATTER.format(date);
  }

  public static long getNowTime() {
    return Calendar.getInstance().getTime().getTime();
  }

  private static final Pattern NAME_MATCHER = Pattern.compile("\\b\\w+\\b");
  private static final Linkify.MatchFilter NAME_MATCHER_MATCH_FILER = new Linkify.MatchFilter() {
    @Override
    public final boolean acceptMatch(final CharSequence s, final int start, final int end) {
      if (start == 0) {
        return false;
      }

      if (start > 1 && !Character.isWhitespace(s.charAt(start - 2))) {
        return false;
      }

      return s.charAt(start - 1) == '@';
    }
  };


  private static final String TWITTA_USER_URL = "twitta://users/";

  public static void linkifyUsers(TextView view) {
    Linkify.addLinks(view, NAME_MATCHER, TWITTA_USER_URL, NAME_MATCHER_MATCH_FILER, null);
  }

}
