package com.dart.android.twitter;

import android.content.Context;
import android.text.Layout;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.TextView;

public class MyTextView extends TextView {

  public MyTextView(Context context) {
    super(context);
  }

  public MyTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    CharSequence text = getText();
    int action = event.getAction();

    if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN)
        && text instanceof Spanned) {
      TextView widget = this;

      int x = (int) event.getX();
      int y = (int) event.getY();

      x -= widget.getTotalPaddingLeft();
      y -= widget.getTotalPaddingTop();

      x += widget.getScrollX();
      y += widget.getScrollY();

      Layout layout = widget.getLayout();
      int line = layout.getLineForVertical(y);
      int off = layout.getOffsetForHorizontal(line, x);

      Spanned buffer = (Spanned) text;
      URLSpan[] link = buffer.getSpans(off, off, URLSpan.class);

      if (link.length != 0) {
        if (action == MotionEvent.ACTION_UP) {
          link[0].onClick(widget);
        } else if (action == MotionEvent.ACTION_DOWN) {
          /*
           * Selection.setSelection(buffer, buffer.getSpanStart(link[0]),
           * buffer.getSpanEnd(link[0]));
           */
        }
        return true;
      } else {
        /*
         * Selection.removeSelection(buffer);
         */
      }
    }

    return super.onTouchEvent(event);
  }

}
