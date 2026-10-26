package com.tabgrinder.brucetrigger;

import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;

/** Touch-operated encoder. Rim motion is local input, never physical-position/LED telemetry. */
final class EncoderControl extends FrameLayout {
  interface Step {
    boolean turn(int direction);
  }

  private final EncoderArtwork artwork = new EncoderArtwork();
  private final float density;
  private final RotaryGesture gesture = new RotaryGesture();
  private final Step step;
  private float angle, lastAngle;
  private boolean dragging;
  private int pointer = -1;
  private int pendingSteps;
  private long lastMovement;
  private boolean suspended;
  private boolean available = true;
  private final Runnable flushInput = () -> flushPending();

  EncoderControl(Context c, Runnable select, Step step) {
    super(c);
    this.step = step;
    density = getResources().getDisplayMetrics().density;
    setWillNotDraw(false);
    setMinimumHeight((int) (160 * density));
    setFocusable(true);
    setContentDescription("Rotary control. Turn outer ring to navigate; centre button selects.");
    setFilterTouchesWhenObscured(true);
    Button b = new GameControlButton(c);
    b.setText("SELECT");
    b.setTextSize(14);
    b.setLetterSpacing(.12f);
    b.setTypeface(Typeface.MONOSPACE);
    b.setAllCaps(false);
    b.setTextColor(FusionTheme.TEXT);
    b.setFilterTouchesWhenObscured(true);
    b.setBackground(EncoderArtwork.centre(density));
    b.setOnClickListener(v -> select.run());
    b.setContentDescription("Select current menu item");
    addView(b, new LayoutParams((int) (88 * density), (int) (88 * density), Gravity.CENTER));
  }

  void configureGameInput(GameControlButton.Input input) {
    ((GameControlButton)getChildAt(0)).configureGameInput(input,"nav select",false);
  }
  private boolean gameMode(){return ((GameControlButton)getChildAt(0)).gameMode();}

  void setAvailable(boolean value) {
    if (available == value) return;
    available = value;
    if (!value) end();
    getChildAt(0).setEnabled(value);
    setAlpha(value ? 1f : .45f);
  }

  private double radius(float x, float y) {
    double scale=Math.max(.5, Math.min(getWidth(),getHeight())/(160.0*density));
    return Math.hypot(x - getWidth() / 2f, y - getHeight() / 2f) / (density*scale);
  }

  private boolean ring(float x, float y) {
    double r = radius(x, y);
    return r >= 48 && r <= 80;
  }

  private float degrees(float x, float y) {
    return (float) Math.toDegrees(Math.atan2(y - getHeight() / 2f, x - getWidth() / 2f));
  }

  @Override
  public boolean onFilterTouchEventForSecurity(MotionEvent event) {
    // Android may discard a covered touch before onTouchEvent can cancel our
    // deferred detents. Cancel here as well, including partially covered taps.
    if (GuardedDialogBuilder.obscured(event)) {
      end();
      return false;
    }
    return super.onFilterTouchEventForSecurity(event);
  }

  @Override
  public void onCancelPendingInputEvents() {
    end();
    super.onCancelPendingInputEvents();
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent e) {
    if (!available) return false;
    if (GuardedDialogBuilder.obscured(e)) return false;
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN && ring(e.getX(), e.getY())) {
      dragging = true;
      pointer = e.getPointerId(0);
      lastAngle = degrees(e.getX(), e.getY());
      gesture.begin(lastAngle);
      pendingSteps = 0;
      suspended = false;
      if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
      return true;
    }
    return dragging;
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    if (!available) return false;
    if (GuardedDialogBuilder.obscured(e)) {
      end();
      return true;
    }
    int action = e.getActionMasked();
    if (action == MotionEvent.ACTION_POINTER_DOWN
        || action == MotionEvent.ACTION_POINTER_UP
        || action == MotionEvent.ACTION_CANCEL
        || action == MotionEvent.ACTION_UP) {
      boolean handled = dragging;
      end();
      return handled;
    }
    if (!dragging) return super.onTouchEvent(e);
    if (action == MotionEvent.ACTION_MOVE) {
      int i = e.findPointerIndex(pointer);
      if (i < 0) {
        end();
        return true;
      }
      for (int n = 0; n < e.getHistorySize(); n++)
        move(e.getHistoricalX(i, n), e.getHistoricalY(i, n));
      move(e.getX(i), e.getY(i));
      flushPending();
    }
    return true;
  }

  private void move(float x, float y) {
    double r = radius(x, y);
    // Keep ownership during ordinary finger drift; crossing the centre never selects.
    if (r < 28 || r > 126) {
      suspended = true;
      pendingSteps = 0;
      gesture.cancel();
      removeCallbacks(flushInput);
      return;
    }
    float next = degrees(x, y);
    if (suspended) {
      suspended = false;
      lastAngle = next;
      gesture.begin(next);
      return;
    }
    float delta = next - lastAngle;
    while (delta > 180) delta -= 360;
    while (delta < -180) delta += 360;
    lastAngle = next;
    if (Math.abs(delta) <= 90) {
      angle = (angle + delta) % 360;
      postInvalidateOnAnimation();
    }
    int detents = gesture.move(next);
    if (detents != 0) {
      if (Integer.signum(detents) != Integer.signum(pendingSteps)) pendingSteps = 0;
      int limit=gameMode()?8:2;
      pendingSteps = Math.max(-limit, Math.min(limit, pendingSteps + detents));
      lastMovement = SystemClock.uptimeMillis();
    }
  }

  private void flushPending() {
    removeCallbacks(flushInput);
    if (!dragging || suspended || SystemClock.uptimeMillis() - lastMovement > 220) {
      pendingSteps = 0;
      return;
    }
    if (pendingSteps == 0) return;
    int limit=gameMode()?8:1;
    for(int i=0;i<limit && pendingSteps!=0;i++) {
      int direction=Integer.signum(pendingSteps);
      if(!step.turn(direction))break;
      pendingSteps-=direction;performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }
    if (pendingSteps != 0) postDelayed(flushInput, 32);
  }

  private void end() {
    dragging = false;
    pointer = -1;
    pendingSteps = 0;
    removeCallbacks(flushInput);
    gesture.cancel();
    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
  }

  @Override
  protected void onDetachedFromWindow() {
    end();
    super.onDetachedFromWindow();
  }

  @Override
  public void onWindowFocusChanged(boolean focused) {
    if (!focused) end();
    super.onWindowFocusChanged(focused);
  }

  @Override
  public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
    super.onInitializeAccessibilityNodeInfo(info);
    info.setScrollable(true);
    info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
    info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
  }

  @Override
  public boolean performAccessibilityAction(int action, Bundle args) {
    if (!available) return false;
    if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
      int d = action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 1 : -1;
      if (step.turn(d)) {
        angle = (angle + d * 15) % 360;
        invalidate();
        return true;
      }
      return false;
    }
    return super.performAccessibilityAction(action, args);
  }

  @Override
  protected void onMeasure(int width,int height) {
    int side=Math.min(MeasureSpec.getSize(width),MeasureSpec.getSize(height));
    int centre=Math.max((int)(48*density),Math.round(side*.55f));
    View child=getChildAt(0);child.getLayoutParams().width=centre;child.getLayoutParams().height=centre;
    super.onMeasure(width,height);
  }

  @Override
  protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight) {
    super.onSizeChanged(width,height,oldWidth,oldHeight);artwork.size(width,height,density);
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);artwork.draw(c,angle);
  }
}
