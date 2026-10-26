package com.tabgrinder.brucetrigger.audit;

import android.app.Activity;
import android.graphics.*;
import android.os.SystemClock;
import java.lang.reflect.Method;
import java.util.*;

final class GamePresentationAudit {
  static void run(AuditRunner a,Activity activity)throws Throwable {a.onUi(()->{
    Class<?> mc=a.targetClass("MirrorView");Object view=a.construct(mc);
    Method apply=a.method(mc,"applyGameFrame",List.class);
    a.ok(Boolean.TRUE.equals(apply.invoke(view,Arrays.asList(a.hex("AA 08 63 01 40 00 AA 01"),a.hex("AA 05 00 F8 00")))),"complete initial game frame published");
    Bitmap first=(Bitmap)a.field(view,"bitmap");a.ok(first.getPixel(40,40)==Color.RED,"initial game frame pixel visible");
    android.view.View target=(android.view.View)view;target.layout(0,0,320,170);
    Bitmap surface=Bitmap.createBitmap(320,170,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(surface);
    target.draw(canvas);a.ok((Long)a.method(mc,"presentedGameFrame").invoke(view)==1,"first actual game draw advances the game presentation counter");
    a.ok(Boolean.FALSE.equals(apply.invoke(view,Arrays.asList(a.hex("AA 05 00 07 E0"),a.hex("AA 03 19")))) ,"partially composed invalid game frame rejected");
    a.ok(a.field(view,"bitmap")==first&&first.getPixel(40,40)==Color.RED,"failed game frame preserves prior complete front buffer");
    a.ok((Long)a.method(mc,"gameFramesComposed").invoke(view)==1,"rejected batch cannot increment composed frame counter");
    a.ok(Boolean.TRUE.equals(apply.invoke(view,Arrays.asList(a.hex("AA 05 00 00 1F")))) ,"next complete game frame can be composed");
    a.ok(((Bitmap)a.field(view,"bitmap")).getPixel(40,40)==Color.BLUE,"complete game frame swaps to blue atomically");
    a.method(mc,"clearScreen").invoke(view);a.ok(a.field(view,"bitmap")==null&&a.field(view,"gameBack")==null,"closing releases both game buffers");
    a.method(mc,"apply",byte[].class).invoke(view,(Object)a.hex("AA 08 63 01 40 00 AA 01"));
    a.method(mc,"apply",byte[].class).invoke(view,(Object)a.hex("AA 05 00 07 E0"));
    target.draw(canvas);
    a.ok((Long)a.method(mc,"presentedGameFrame").invoke(view)==1,"native redraw cannot falsely present an undisplayed last game frame");
    a.ok((Long)a.method(mc,"gameFramesComposed").invoke(view)==2,"native redraw preserves composed game count independently");
    surface.recycle();a.method(mc,"clearScreen").invoke(view);

    Object stream=a.targetClass("NativeMirrorStream").getDeclaredConstructor().newInstance();
    Method accept=a.method(stream.getClass(),"accept",byte[].class,long.class);
    byte[] geo=a.hex("AA 08 63 01 40 00 AA 01"),clear=a.hex("AA 05 00 00 00");
    accept.invoke(stream,geo,1L);accept.invoke(stream,clear,2L);
    accept.invoke(stream,MirrorTextAudit.text(14,160,85,1,0xffff,0,"Not Supported"),3L);
    a.ok(Boolean.TRUE.equals(a.method(stream.getClass(),"unsupportedSprites").invoke(stream)),"exact stock Megalodon marker recognized");
    accept.invoke(stream,clear,4L);
    a.ok(Boolean.FALSE.equals(a.method(stream.getClass(),"unsupportedSprites").invoke(stream)),"next actual full-screen clear resets unsupported marker");
    accept.invoke(stream,MirrorTextAudit.text(16,0,0,1,0xffff,0,"Not Supported"),5L);
    a.ok(Boolean.FALSE.equals(a.method(stream.getClass(),"unsupportedSprites").invoke(stream)),"arbitrary unrelated text cannot classify a native game");

    Object real=a.field(activity,"mirrorStream");a.method(real.getClass(),"reset").invoke(real);
    a.method(real.getClass(),"accept",byte[].class,long.class).invoke(real,geo,SystemClock.elapsedRealtime());
    a.method(real.getClass(),"accept",byte[].class,long.class).invoke(real,clear,SystemClock.elapsedRealtime());
    a.method(real.getClass(),"accept",byte[].class,long.class).invoke(real,MirrorTextAudit.text(14,160,85,1,0xffff,0,"Not Supported"),SystemClock.elapsedRealtime());
    a.ok(a.method(activity.getClass(),"inputStatus").invoke(activity).toString().contains("Megalodon"),"screen status names the missing sprite path instead of claiming ready");
    a.ok(a.method(activity.getClass(),"presentationStatus").invoke(activity).toString().contains("cannot capture this compiled game"),"game profile cannot be misrepresented as native Megalodon mirroring");
    a.method(real.getClass(),"reset").invoke(real);
  });}
}
