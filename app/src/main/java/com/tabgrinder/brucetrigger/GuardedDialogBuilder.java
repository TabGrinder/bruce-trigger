package com.tabgrinder.brucetrigger;

import android.app.AlertDialog;
import android.content.Context;
import android.view.*;

/** Covers dialog windows as well as Activity controls against obscured touch events. */
final class GuardedDialogBuilder extends AlertDialog.Builder {
  private final Context ownerContext;

  GuardedDialogBuilder(Context c) {
    super(c);
    ownerContext = c;
  }

  static boolean obscured(MotionEvent e) {
    return (e.getFlags() & (MotionEvent.FLAG_WINDOW_IS_OBSCURED | 2)) != 0;
  }

  static void protect(View v) {
    v.setFilterTouchesWhenObscured(true);
    if (v.isClickable()) v.setOnTouchListener((view, event) -> obscured(event));
    if (v instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) v;
      for (int i = 0; i < group.getChildCount(); i++) protect(group.getChildAt(i));
    }
  }

  @Override
  public AlertDialog create() {
    AlertDialog d = super.create();
    if (ownerContext instanceof MainActivity) ((MainActivity) ownerContext).trackDialog(d);
    Window window = d.getWindow();
    if (window != null) {
      View decor = window.getDecorView();
      decor.setFilterTouchesWhenObscured(true);
      decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> protect(decor));
    }
    return d;
  }
}
