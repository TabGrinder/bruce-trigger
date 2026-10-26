package com.tabgrinder.brucetrigger;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.Adler32;

/** Erasures and corrupted records are injected before the production receiver.
 * Expected bytes are the original draw stream, not the decoder's reconstruction. */
public final class RecoveryGameRegressionTest {
  static int checks;
  static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
  static long sum(byte[] b){Adler32 a=new Adler32();a.update(b);return a.getValue();}
  static String protect(String s){return s+"|"+sum(s.getBytes(StandardCharsets.US_ASCII));}
  static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();}
  static int weight(int value,int shift){for(int i=0;i<shift;i++)value=(value<<1)^((value&128)!=0?285:0);return value;}
  static List<String> encode(long seq,byte[] bytes,int ops){
    List<String> lines=new ArrayList<>();String head="R|"+seq+"|"+bytes.length+"|"+ops+"|"+sum(bytes)+"|";
    if(bytes.length==0){for(int i=0;i<3;i++)lines.add(protect(head+"0|"));return lines;}
    for(int base=0,group=0;base<bytes.length;base+=640,group++){
      byte[] p=new byte[80],q=new byte[80];
      for(int k=0;k<8&&base+k*80<bytes.length;k++){
        byte[] shard=new byte[80];int n=Math.min(80,bytes.length-base-k*80);System.arraycopy(bytes,base+k*80,shard,0,n);
        for(int j=0;j<80;j++){p[j]^=shard[j];q[j]^=(byte)weight(shard[j]&255,k);}
        lines.add(protect(head+(group*10+k)+"|"+hex(shard)));
      }
      lines.add(protect(head+(group*10+8)+"|"+hex(p)));lines.add(protect(head+(group*10+9)+"|"+hex(q)));
    }
    return lines;
  }
  static byte[] data(int clears){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] initial=GameRegressionTest.initial();b.write(initial,0,initial.length);for(int i=0;i<clears;i++)b.write(UsbMirrorRegressionTest.CLEAR,0,5);return b.toByteArray();}
  static GameScreenStream screen(){GameScreenStream s=new GameScreenStream();return s;}
  static byte[] take(GameScreenStream s){ByteArrayOutputStream b=new ByteArrayOutputStream();List<byte[]> frame;while((frame=s.takeFrame())!=null)for(byte[] p:frame)b.write(p,0,p.length);return b.toByteArray();}
  static void feed(GameScreenStream s,List<String> records,long now){for(String line:records)ok(s.line(line,now),s.failure);}
  static String damage(String record){int i=record.lastIndexOf('|')-1;return record.substring(0,i)+(record.charAt(i)=='0'?'1':'0')+record.substring(i+1);}
  static String tag(String record){return "BT_0123456789abcdef_GAME|"+record+"\n";}

  public static void main(String[] args)throws Exception{
    byte[] original=data(240);List<String> records=encode(0,original,242);
    // Every pair (data/data, data/parity, parity/parity) in each group, also
    // simultaneous losses in the other groups. No missing operations permitted.
    for(int first=0;first<10;first++)for(int second=first+1;second<10;second++){
      GameScreenStream s=screen();int removed=0;
      for(String record:records){int id=Integer.parseInt(record.split("\\|")[5])%10;
        if(id==first||id==second){removed++;continue;}
        ok(s.line(record,1000),s.failure);
      }
      ok(s.frames==1&&s.rejected==0&&Arrays.equals(original,take(s)),"any two erased shards per group recover exact bytes: "+first+","+second);
      ok(removed>=4,"faults were actually injected");
    }
    for(int first=0;first<10;first++)for(int second=first+1;second<10;second++){
      GameScreenStream s=screen();
      for(String record:records){int id=Integer.parseInt(record.split("\\|")[5])%10;ok(s.line(id==first||id==second?damage(record):record,1000),s.failure);}
      ok(s.frames==1&&Arrays.equals(original,take(s))&&s.recovery.damagedRecords>0,"corrupted records become erasures, never pixels");
    }
    GameScreenStream s=screen();for(String record:records){int id=Integer.parseInt(record.split("\\|")[5])%10;if(id>2)ok(s.line(record,1000),s.failure);}
    ok(s.frames==0&&s.takeFrame()==null&&!s.usable(1001),"three missing data shards cannot publish partial state");
    ok(!s.line(encode(1,UsbMirrorRegressionTest.CLEAR,1).get(0),1100)&&s.failure.contains("capacity exceeded"),"unrecoverable delta is never silently skipped");
    s=screen();List<String> reordered=new ArrayList<>(records);Collections.shuffle(reordered,new Random(25));feed(s,reordered,1000);
    ok(Arrays.equals(original,take(s)),"bounded out-of-order shards reconstructed without drawing twice");
    feed(s,records,1100);ok(s.frames==1&&s.takeFrame()==null&&s.lastFrame==1000,"duplicate complete transaction never refreshes freshness or redraws");
    feed(s,encode(1,new byte[0],0),1200);ok(s.frames==2&&s.heartbeatFrames==1,"triplicated heartbeat is applied exactly once");
    s.reset();ok(s.recovery.repairedShards==0&&s.recovery.damagedRecords==0&&s.frames==0,"new connection clears recovery state");
    // Valid record checksum with altered payload still fails the full-transaction checksum.
    s=screen();for(int i=0;i<records.size()&&s.failure.equals("None");i++){
      String record=records.get(i);if(i==0){record=damage(record);record=protect(record.substring(0,record.lastIndexOf('|')));}s.line(record,1000);
    }
    ok(s.failure.contains("checksum")&&s.takeFrame()==null,"conflicting content cannot bypass complete transaction integrity");
    for(String bad:new String[]{"R|0|4097|2|1|0|00","R|0|13|257|1|0|00","R|0|13|2|1|70|00","R|0|0|1|1|0|","R|0|0|0|1|1|"}){
      s=screen();ok(!s.line(protect(bad),0)&&s.takeFrame()==null,"bounded metadata rejection");
    }
    // The user's exact logical failures at 24/300: the first data chunk vanishes.
    s=screen();for(int seq=0;seq<=300;seq++){
      byte[] expected=seq==0?GameRegressionTest.initial():data(30);List<String> lines=encode(seq,expected,seq==0?2:32);
      // Subsequent test data must not repeat geometry.
      if(seq>0){expected=Arrays.copyOfRange(expected,8,expected.length);lines=encode(seq,expected,31);}
      for(int i=0;i<lines.size();i++)if(!(i==0&&(seq==24||seq==300)))ok(s.line(lines.get(i),seq*80),s.failure);
      ok(Arrays.equals(expected,take(s)),"full transaction retained at "+seq);
    }
    ok(s.frames==301&&s.recovery.repairedFrames==2&&s.recovery.repairedShards==2,"both reported first-shard losses recover without resetting the stream");
    String template=new String(Files.readAllBytes(Paths.get(System.getProperty("project.root"),"app/src/main/res/raw/files_bridge.txt")),StandardCharsets.US_ASCII);
    StringBuilder wire=new StringBuilder();for(int i=1;i<records.size();i++)wire.append(tag(records.get(i)));
    byte[] raw=wire.toString().getBytes(StandardCharsets.US_ASCII);
    // Every byte split with a host SELECT write between reads. No parser reset,
    // no replay, and CLI text/prompts following the graphics remain independent.
    for(int split=0;split<=raw.length;split++){
      UsbSerialSession session=FilesGameRegressionTest.running(template);
      long initialWrites=session.writes;session.receive(Arrays.copyOfRange(raw,0,split),1100);
      byte[] nav=session.begin("nav select",1101);ok(nav!=null,"SELECT ready during recovery");session.written(nav.length,nav.length,1102);
      session.receive(Arrays.copyOfRange(raw,split,raw.length),1103);
      session.receive(UsbRegressionTest.bytes("COMMAND: nav select\nSelect Pressed\nActual Menu: Files\n# "),1104);
      ok(session.stage==UsbSerialSession.Stage.OPEN&&session.game.active&&session.game.screen.rejected==0&&session.game.screen.frames==1,"split/read/write retains owned game");
      ok(Arrays.equals(original,take(session.game.screen))&&session.writes==initialWrites+1&&!session.game.stopRequested,"identical graphics, one SELECT, no exit/replay");
    }
    System.out.println("PASS "+checks+" recovery assertions: all two-erasure combinations, corruption, duplicate/order, bounds, 24/300 first-shard loss, every USB split with SELECT; exact graphics, no input replay. Hardware is simulated.");
  }
}
