package com.tabgrinder.brucetrigger.audit;

import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** User-facing navigation, read-only support access and retained local data. */
final class ReleaseNavigationAudit {
  static AlertDialog dialog(AuditRunner a, Activity activity) throws Exception {
    for (Object d : (Set<?>) a.field(activity, "ownedDialogs"))
      if (((AlertDialog)d).isShowing()) return (AlertDialog)d;
    throw new AssertionError("Expected a visible dialog");
  }

  static List<String> items(AlertDialog d) {
    List<String> labels = new ArrayList<>();
    ListAdapter adapter = d.getListView().getAdapter();
    for (int i=0; i<adapter.getCount(); i++) labels.add(adapter.getItem(i).toString());
    return labels;
  }

  static void choose(AlertDialog d, String text) {
    int at = items(d).indexOf(text);
    if (at < 0) throw new AssertionError("Missing menu item: " + text);
    d.getListView().performItemClick(null, at, at);
  }

  static Button button(View v, String text) {
    if (v instanceof Button && text.equals(((Button)v).getText().toString())) return (Button)v;
    if (v instanceof ViewGroup) {
      ViewGroup group = (ViewGroup)v;
      for (int i=0;i<group.getChildCount();i++) {
        Button found=button(group.getChildAt(i),text);
        if (found!=null) return found;
      }
    }
    return null;
  }

  static String message(AlertDialog d) {
    TextView v=d.findViewById(android.R.id.message);
    return v==null ? "" : v.getText().toString();
  }

  static void run(AuditRunner a, Activity activity) throws Throwable {
    a.onUi(()->{
      a.method(activity.getClass(),"settings").invoke(activity);
      AlertDialog settings=dialog(a,activity);
      List<String> labels=items(settings);
      a.ok(labels.contains("Diagnostics") && labels.contains("Help & about"),"support tools accessible from Settings");
      a.ok(!labels.contains("Saved notes") && !labels.contains("Sessions") && !labels.contains("Console"),"fresh installation does not advertise notes or obsolete sections");
      a.ok(!labels.contains("Firmware update") && !labels.contains("Device Wi-Fi") && !labels.contains("Buttons & LEDs"),"unavailable functions are not offered as operational Settings controls");
      choose(settings,"Diagnostics");
      a.ok("Diagnostics".equals(a.field(activity,"active")),"Settings opens Diagnostics");
      a.ok(((ViewGroup)a.field(activity,"nav")).getChildAt(0).isSelected(),"secondary page retains Remote navigation context");
      View page=(View)a.field(activity,"page");
      for(String label:new String[]{"Connection report","Info","Memory","Storage","Clear view","Back to Remote"})
        a.ok(button(page,label)!=null,"Diagnostics exposes "+label);
      Object usb=a.field(activity,"usb"),serial=a.field(usb,"session");
      Object writes=a.field(serial,"writes");
      button(page,"Connection report").performClick();
      AlertDialog report=dialog(a,activity);
      a.ok(message(report).contains("Transport: native USB CDC"),"connection report available without a device");
      a.ok(report.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString().equals("Copy"),"report can be copied explicitly");
      report.dismiss();
      a.ok(writes.equals(a.field(serial,"writes")),"viewing diagnostics sends no device command");
      a.method(activity.getClass(),"appendConsole",String.class).invoke(activity,"retained diagnostic line");
      button(page,"Clear view").performClick();
      a.ok(((TextView)a.field(activity,"consoleText")).getText().toString().equals("No device response."),"Clear view clears displayed diagnostics");
      a.ok(writes.equals(a.field(serial,"writes")),"Clear view sends no device command");
      Object mirror=a.field(serial,"mirror");a.set(mirror,"active",true);
      for(String label:new String[]{"Info","Memory","Storage"}) {
        button((View)a.field(activity,"page"),label).performClick();
        AlertDialog blocked=dialog(a,activity);
        a.ok(message(blocked).contains("Stop capture"),label+" remains blocked during capture");
        blocked.dismiss();
      }
      a.ok(writes.equals(a.field(serial,"writes")),"blocked diagnostics preserve transport state");
      a.set(mirror,"active",false);
      activity.onBackPressed();
      a.ok("Remote".equals(a.field(activity,"active")),"Back from Diagnostics returns to Remote");
      ((ViewGroup)a.field(activity,"nav")).getChildAt(1).performClick();
      a.ok(button((View)a.field(activity,"page"),"Forget device")==null,"Device omits the ambiguous Forget action");
      FusionUiAudit.capture(a,activity,"release-device");
      button((View)a.field(activity,"page"),"Diagnostics").performClick();
      a.ok(button((View)a.field(activity,"page"),"Back to Device")!=null,"Device diagnostics preserves its return destination");
      FusionUiAudit.capture(a,activity,"release-diagnostics");
      activity.onBackPressed();
      a.ok("Device".equals(a.field(activity,"active")),"Back from Device diagnostics returns to Device");
      a.method(activity.getClass(),"settings").invoke(activity);
      choose(dialog(a,activity),"Additional controls");
      AlertDialog controls=dialog(a,activity);
      a.ok(items(controls).equals(Arrays.asList("Up","Down","Previous page","Next page")),"additional controls offer implemented navigation only");
      controls.dismiss();
      a.method(activity.getClass(),"help").invoke(activity);
      choose(dialog(a,activity),"Themes & compatibility");
      AlertDialog themes=dialog(a,activity);
      a.ok(message(themes).contains("works with custom themes") && message(themes).contains("default theme provides smoother performance"),"Help includes the maintainer's theme recommendation");
      themes.dismiss();
    });
    Class<?> sc=a.targetClass("SessionStore");Object notes=a.construct(sc);
    String id=UUID.randomUUID().toString();
    File file=new File(new File(a.getTargetContext().getFilesDir(),"sessions"),id+".json");
    SavedNoteFixtures.write(a.getTargetContext(),id,"Saved note fixture","Existing notes remain available.");
    try {
      a.onUi(()->{
        a.method(activity.getClass(),"settings").invoke(activity);
        AlertDialog settings=dialog(a,activity);
        a.ok(items(settings).contains("Saved notes"),"saved-note access appears only when data exists");
        choose(settings,"Saved notes");
        a.ok("Saved notes".equals(a.field(activity,"active")),"saved notes can be opened");
        a.ok(button((View)a.field(activity,"page"),"New session")==null,"saved-note view has no creation action");
      });
      long deadline=android.os.SystemClock.elapsedRealtime()+10000;
      boolean[] found={false};
      while(!found[0] && android.os.SystemClock.elapsedRealtime()<deadline) {
        a.onUi(()->found[0]=button((View)a.field(activity,"page"),"Saved note fixture")!=null);
        if(!found[0])Thread.sleep(30);
      }
      a.ok(found[0],"stored note is listed after asynchronous load");
      a.onUi(()->{
        button((View)a.field(activity,"page"),"Saved note fixture").performClick();
      });
      long dialogDeadline=android.os.SystemClock.elapsedRealtime()+10000;
      boolean[] noteReady={false};
      while(!noteReady[0] && android.os.SystemClock.elapsedRealtime()<dialogDeadline) {
        a.onUi(()->{
          for(Object d:(Set<?>)a.field(activity,"ownedDialogs"))
            if(((AlertDialog)d).isShowing())noteReady[0]=true;
        });
        if(!noteReady[0])Thread.sleep(30);
      }
      a.ok(noteReady[0],"stored note opens after asynchronous read");
      a.onUi(()->{
        AlertDialog note=dialog(a,activity);
        a.ok(message(note).equals("Existing notes remain available."),"existing note content is preserved");
        a.ok(note.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString().equals("Export JSON"),"existing notes retain explicit export");
        note.dismiss();
      });
      ExecutorService worker=(ExecutorService)a.field(activity,"notesIO");
      CountDownLatch started=new CountDownLatch(1), release=new CountDownLatch(1);
      worker.execute(()->{
        started.countDown();
        try { release.await(10,TimeUnit.SECONDS); }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); }
      });
      try {
        a.ok(started.await(10,TimeUnit.SECONDS),"notes worker paused before navigation race");
        a.onUi(()->{
          button((View)a.field(activity,"page"),"Saved note fixture").performClick();
          activity.onBackPressed();
        });
      } finally { release.countDown(); }
      worker.submit(()->{}).get(10,TimeUnit.SECONDS);
      a.onUi(()->{
        a.ok("Device".equals(a.field(activity,"active")),"Back leaves notes while a read is pending");
        for(Object d:(Set<?>)a.field(activity,"ownedDialogs"))
          a.ok(!((AlertDialog)d).isShowing(),"late note read cannot open a dialog on another screen");
      });
    } finally {a.method(sc,"delete",File.class).invoke(notes,file);}
    File damaged=new File(file.getParentFile(),UUID.randomUUID().toString()+".json");
    ExecutorService notesWorker=(ExecutorService)a.field(activity,"notesIO");
    try {
      try(FileOutputStream output=new FileOutputStream(damaged)){output.write("damaged note".getBytes("UTF-8"));}
      a.onUi(()->a.method(activity.getClass(),"openSection",String.class).invoke(activity,"Saved notes"));
      notesWorker.submit(()->{}).get(10,TimeUnit.SECONDS);
      a.onUi(()->{
        Button unreadable=button((View)a.field(activity,"page"),"Unreadable note");
        a.ok(unreadable!=null,"corrupt note is retained in the list");
        unreadable.performClick();
      });
      notesWorker.submit(()->{}).get(10,TimeUnit.SECONDS);
      a.onUi(()->{
        AlertDialog error=dialog(a,activity);
        a.ok(damaged.exists()&&message(error).contains("retained"),"reading corrupt data does not silently delete it");
        error.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
      });
      a.onUi(()->{
        AlertDialog confirm=dialog(a,activity);
        a.ok(damaged.exists()&&message(confirm).contains("only this saved note"),"corrupt note deletion still requires confirmation");
        confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      });
      notesWorker.submit(()->{}).get(10,TimeUnit.SECONDS);
      a.ok(!damaged.exists(),"explicitly confirmed corrupt note is deleted");
    } finally {damaged.delete();}
    a.onUi(()->{
      a.method(activity.getClass(),"settings").invoke(activity);
      AlertDialog settings=dialog(a,activity);
      a.ok(!items(settings).contains("Saved notes"),"saved-note entry disappears when no records remain");
      settings.dismiss();
      ((ViewGroup)a.field(activity,"nav")).getChildAt(0).performClick();
    });
  }
}
