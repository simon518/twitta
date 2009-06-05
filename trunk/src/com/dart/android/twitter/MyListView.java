package com.dart.android.twitter;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.AbsListView;
import android.widget.ListView;

public class MyListView extends ListView implements ListView.OnScrollListener {

  private int mScrollState = OnScrollListener.SCROLL_STATE_IDLE;

  public MyListView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setOnScrollListener(this);
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent event) {
    boolean result = super.onInterceptTouchEvent(event);

    if (mScrollState == OnScrollListener.SCROLL_STATE_FLING) {
      return true;
    }

    return result;
  }

  @Override
  public void onScroll(AbsListView view, int firstVisibleItem,
      int visibleItemCount, int totalItemCount) {
  }

  @Override
  public void onScrollStateChanged(AbsListView view, int scrollState) {
    mScrollState = scrollState;
  }

}
