package android.os;
import java.util.*;
public class Handler {
 static class Task {Runnable r;long at,id;Task(Runnable r,long at,long id){this.r=r;this.at=at;this.id=id;}}
 static long seq;static PriorityQueue<Task> q=new PriorityQueue<>((a,b)->a.at==b.at?Long.compare(a.id,b.id):Long.compare(a.at,b.at));
 public Handler(Looper l){}
 public boolean post(Runnable r){return postDelayed(r,0);}
 public boolean postDelayed(Runnable r,long d){synchronized(q){q.add(new Task(r,SystemClock.now+d,seq++));}return true;}
 public void removeCallbacks(Runnable r){synchronized(q){q.removeIf(t->t.r==r);}}
 public static void reset(){synchronized(q){q.clear();SystemClock.now=1000;seq=0;}}
 public static void runCurrent(){advance(0);}
 // Model deep sleep/main-thread suspension: elapsed time advances without dispatch.
 public static void suspend(long millis){SystemClock.now+=millis;}
 public static void dispatchOverdue(){int guard=10000;while(true){Task t;synchronized(q){if(q.isEmpty()||q.peek().at>SystemClock.now)break;t=q.poll();}if(--guard==0)throw new AssertionError("overdue scheduler loop");t.r.run();}}
 public static void advance(long d){long end=SystemClock.now+d;int guard=10000;while(true){Task t;synchronized(q){if(q.isEmpty()||q.peek().at>end)break;t=q.poll();}if(--guard==0)throw new AssertionError("scheduler loop");SystemClock.now=t.at;t.r.run();}SystemClock.now=end;}
}
