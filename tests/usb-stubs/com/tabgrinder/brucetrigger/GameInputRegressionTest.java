package com.tabgrinder.brucetrigger;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public final class GameInputRegressionTest {
  static int checks;
  static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
  public static void main(String[] args)throws Exception {
    GameInputQueue q=new GameInputQueue();String scope="session-A";
    ok(q.offer("nav select",false,scope,1000),"first tap accepted");
    ok(q.offer("nav select",false,scope,1010),"second tap retained while first write pending");
    ok("nav select".equals(q.peek(scope,1040))&&q.size()==2,"peeking never submits/replays input");
    q.submitted(1040);q.submitted(1050);
    ok(q.size()==0&&q.submitted==2&&q.longestWait==40,"exactly two taps submitted once");
    ok(q.peek(scope,1060)==null,"no replay after completed submission");
    q.offer("nav next",false,scope,1100);q.offer("nav next",true,scope,1110);q.offer("nav next",true,scope,1120);
    ok(q.size()==2&&q.coalesced==1,"busy held direction has only one pending repeat");
    q.release("nav next");ok(q.size()==1,"release cancels repeats but keeps actual initial tap");
    q.clear();ok(q.size()==0,"focus/lifecycle cancellation clears pending taps");
    for(int i=0;i<GameInputQueue.CAPACITY;i++)ok(q.offer("nav select",false,scope,2000+i),"bounded distinct taps accepted");
    ok(!q.offer("nav select",false,scope,2010),"capacity cannot grow unbounded");
    ok(q.peek(scope,2249)!=null,"fresh queue entry retained at 249 ms");
    ok(q.peek(scope,2257)==null&&q.expired==8,"expired taps are never played later");
    q.offer("nav prev",true,scope,3000);ok(q.peek("session-B",3001)==null,"new game cannot inherit pending input");
    for(String c:new String[]{"js exit","nav up","nav select 100","nav esc\nreboot","nav next;info"})ok(!q.offer(c,false,scope,4000),"queue admits only four exact controls");
    ok(!q.offer("nav select",true,scope,4000)&&!q.offer("nav esc",true,scope,4000),"SELECT/EXIT can never auto-repeat");
    ok(!q.offer("nav next",false,null,4000),"no unscoped input");
    q.reset();ok(q.offered==0&&q.expired==0&&q.cancelled==0&&q.size()==0,"new run clears metrics");
    String template=new String(Files.readAllBytes(Paths.get(System.getProperty("project.root"),"app/src/main/res/raw/files_bridge.txt")),StandardCharsets.US_ASCII);
    UsbSerialSession s=FilesGameRegressionTest.running(template);String prefix=s.game.inputScope().replace("_GAME|","_KEY|");
    byte[] b=s.begin("nav select",2000);s.written(b.length,b.length,2001);
    s.receive(UsbRegressionTest.bytes("BT_ffffffffffffffff_KEY|select|1\n"),2002);
    ok(s.game.keyReads==0,"different session cannot claim a JS key read");
    s.receive(UsbRegressionTest.bytes(prefix+"select|1\n"),2003);
    ok(s.game.keyReads==1&&"select".equals(s.game.lastKeyRead),"owned key read observed independently of CLI receipt");
    ok(s.stage==UsbSerialSession.Stage.WAITING,"key telemetry cannot acknowledge or replay command");
    ok(s.game.screen.frames==0,"key read is not a fresh image or drawing frame");
    s.receive(UsbRegressionTest.bytes(prefix+"select|1\n"+prefix+"next|3\n"),2004);
    ok(s.game.keyReads==3&&"next".equals(s.game.lastKeyRead),"duplicate read ignored; lost telemetry does not terminate game");
    ok(s.game.screen.rejected==0,"valid key telemetry does not enter graphics decoder");
    for(String malformed:new String[]{"select|", "s", "select|-1", "next|99999999999999999", "select|2|extra"})s.receive(UsbRegressionTest.bytes(prefix+malformed+"\n"),2005);
    ok(s.game.keyReads==3&&s.game.screen.rejected==0&&s.stage==UsbSerialSession.Stage.WAITING,"damaged optional telemetry cannot stop graphics, change input or acknowledge CLI");
    s.receive(UsbRegressionTest.bytes("# "),2006);ok(s.stage==UsbSerialSession.Stage.OPEN,"only actual prompt completes CLI request");
    s.game.reset();ok(s.game.keyReads==0&&s.game.inputScope()==null,"reset removes old key evidence and scope");
    System.out.println("PASS "+checks+" game input freshness, bounded FIFO, held cancellation, no write replay and owned JS-read checks.");
  }
}
