package com.dart.android.twitter;

import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;

public class LessClickyLinkMovementMethod extends LinkMovementMethod {
  
  public static MovementMethod getInstance() {
    if (sInstance == null)
      sInstance = new LessClickyLinkMovementMethod();

    return sInstance;
  }

  private static LessClickyLinkMovementMethod sInstance;
  
}