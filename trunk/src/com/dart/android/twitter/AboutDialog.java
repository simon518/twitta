package com.dart.android.twitter;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;

public class AboutDialog {

  static void show(Activity activity) {
    View view = LayoutInflater.from(activity).inflate(R.layout.about, null);
    
    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
    builder.setView(view);
    builder.setCancelable(true);
    builder.setTitle(R.string.about_title);
    builder.setPositiveButton(R.string.cool, null);
    builder.create().show();
  }
  
}
