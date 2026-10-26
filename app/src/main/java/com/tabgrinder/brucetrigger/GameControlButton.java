package com.tabgrinder.brucetrigger;

import android.content.Context;
import android.view.MotionEvent;
import android.view.HapticFeedbackConstants;
import android.view.ViewConfiguration;
import android.widget.Button;

/** Games react on DOWN; only held directions repeat. Menus keep normal clicks. */
final class GameControlButton extends Button {
  interface Input {
    boolean enabled();
    boolean press(String command,boolean repeat);
    void release(String command);
  }
  private Input input;
  private String command;
  private boolean repeatDirection,holding,gameGesture;
  private int pointer=-1;
  private final Runnable repeat = new Runnable(){public void run(){
    if(!holding||input==null||!input.enabled()||!isEnabled()||!hasWindowFocus()){cancelGamePress();return;}
    input.press(command,true);postDelayed(this,80);
  }};
  GameControlButton(Context context){super(context);}
  void configureGameInput(Input value,String key,boolean repeatKey){cancelGamePress();input=value;command=key;repeatDirection=repeatKey;}
  boolean gameMode(){return input!=null&&input.enabled();}
  private void cancelGamePress(){
    if(holding&&input!=null)input.release(command);
    holding=false;pointer=-1;removeCallbacks(repeat);setPressed(false);
    if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
  }
  @Override public boolean onFilterTouchEventForSecurity(MotionEvent event){
    if(GuardedDialogBuilder.obscured(event)){cancelGamePress();return false;}
    return super.onFilterTouchEventForSecurity(event);
  }
  @Override public boolean onTouchEvent(MotionEvent event){
    int action=event.getActionMasked();
    if(GuardedDialogBuilder.obscured(event)){cancelGamePress();return true;}
    if(action==MotionEvent.ACTION_DOWN)gameGesture=false;
    if(action==MotionEvent.ACTION_DOWN&&isEnabled()&&gameMode()){
      cancelGamePress();holding=gameGesture=true;pointer=event.getPointerId(0);setPressed(true);
      if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
      if(input.press(command,false))performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
      if(repeatDirection)postDelayed(repeat,120);
      return true;
    }
    if(!holding){
      if(gameGesture){if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)gameGesture=false;return true;}
      return super.onTouchEvent(event);
    }
    if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL
        ||action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_POINTER_UP
        ||!gameMode()||!isEnabled()){
      cancelGamePress();if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)gameGesture=false;return true;
    }
    if(action==MotionEvent.ACTION_MOVE){
      int index=event.findPointerIndex(pointer),slop=ViewConfiguration.get(getContext()).getScaledTouchSlop();
      if(index<0||event.getX(index)<-slop||event.getY(index)<-slop
          ||event.getX(index)>getWidth()+slop||event.getY(index)>getHeight()+slop)cancelGamePress();
    }
    return true;
  }
  @Override public void setEnabled(boolean enabled){if(!enabled)cancelGamePress();super.setEnabled(enabled);}
  @Override public void onCancelPendingInputEvents(){cancelGamePress();super.onCancelPendingInputEvents();}
  @Override public void onWindowFocusChanged(boolean focused){if(!focused)cancelGamePress();super.onWindowFocusChanged(focused);}
  @Override protected void onDetachedFromWindow(){cancelGamePress();super.onDetachedFromWindow();}
}
