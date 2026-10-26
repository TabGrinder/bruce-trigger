package com.tabgrinder.brucetrigger;

import java.util.ArrayDeque;
import java.util.Iterator;

/** Only fresh, not-yet-submitted user inputs. Never retries a USB write. */
final class GameInputQueue {
  static final int CAPACITY = 8;
  static final long MAX_AGE_MS = 250;
  private static final class Press {
    final String command;
    final long at;
    final boolean repeat;
    Press(String c, boolean r, long t) { command=c; repeat=r; at=t; }
  }
  private final ArrayDeque<Press> pending = new ArrayDeque<>();
  private String scope;
  long offered, submitted, expired, rejected, cancelled, coalesced, longestWait;

  static boolean supports(String command){return "nav prev".equals(command)||"nav next".equals(command)||"nav select".equals(command)||"nav esc".equals(command);}

  boolean offer(String command, boolean repeat, String currentScope, long now) {
    offered++;
    if(currentScope==null || !supports(command)
        || repeat && !("nav prev".equals(command)||"nav next".equals(command))) { rejected++; return false; }
    if(!currentScope.equals(scope)){clear();scope=currentScope;}
    prune(now);
    // EXIT is an explicit cancellation intent. Stale movement must not delay it.
    if("nav esc".equals(command)) { cancelled+=pending.size();pending.clear(); }
    if(repeat)for(Press p:pending)if(p.repeat&&p.command.equals(command)){coalesced++;return true;}
    if(pending.size()>=CAPACITY){rejected++;return false;}
    pending.addLast(new Press(command,repeat,now));return true;
  }
  String peek(String currentScope,long now) {
    if(scope==null||!scope.equals(currentScope)){clear();return null;}
    prune(now);return pending.isEmpty()?null:pending.peekFirst().command;
  }
  void submitted(long now) {
    Press p=pending.pollFirst();if(p!=null){submitted++;longestWait=Math.max(longestWait,now-p.at);}
  }
  void release(String command) {
    for(Iterator<Press> i=pending.iterator();i.hasNext();) {
      Press p=i.next();if(p.repeat&&p.command.equals(command)){i.remove();cancelled++;}
    }
  }
  private void prune(long now) {
    while(!pending.isEmpty()&&now-pending.peekFirst().at>=MAX_AGE_MS){pending.removeFirst();expired++;}
  }
  int size(){return pending.size();}
  void clear(){cancelled+=pending.size();pending.clear();scope=null;}
  void reset(){clear();offered=submitted=expired=rejected=cancelled=coalesced=longestWait=0;}
  String summary(){return "queued="+pending.size()+"/"+CAPACITY+"; offered="+offered+"; submitted="+submitted
      +"; expired="+expired+"; rejected="+rejected+"; cancelled="+cancelled+"; hold samples merged="+coalesced
      +"; longest pre-write wait="+longestWait+" ms; submitted does not confirm game action";}
}
