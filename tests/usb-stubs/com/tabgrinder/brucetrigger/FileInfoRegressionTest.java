package com.tabgrinder.brucetrigger;

import java.util.*;
import java.io.*;
/** Stock layout with optional whitespace records, USB fragmentation and missing path rows. */
public final class FileInfoRegressionTest {
  static int checks;
  static void ok(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
  static byte[] text(String s,int x,int y)throws Exception{return FilesGameRegressionTest.text(s,17,x,y,1,0xf81f);}
  static byte[] sequence(String path,boolean blanks,boolean dropRow)throws Exception {
    ByteArrayOutputStream out=new ByteArrayOutputStream();
    out.write(text("FILE INFO\n",106,25));
    out.write(text("01:51:32",10,5));
    if(blanks)out.write(text("\n",10,41));
    String contents="Path: "+path;int row=0;
    for(int start=0;start<contents.length();start+=50,row++) {
      if(!dropRow||row!=1)out.write(text(contents.substring(start,Math.min(start+50,contents.length()))+"\n",10,49+row*8));
    }
    if(blanks)out.write(text("\n",10,49+row*8));
    out.write(text("Bytes: 23949\n",10,49+(row+1)*8));
    out.write(text("Size: 23.95 kB\n",10,49+(row+3)*8));
    return out.toByteArray();
  }
  public static void main(String[] args)throws Exception {
    for(String path:new String[]{"/GAMES/Dino.js","/GAMES/Flappy Bird.js","/GAMES/"+String.join("",Collections.nCopies(120,"x"))+" with spaces.js"}) {
      for(boolean blanks:new boolean[]{false,true}) {
        byte[] raw=sequence(path,blanks,false);
        for(int split=0;split<=raw.length;split++) {
          FilesScriptSelection selection=new FilesScriptSelection();selection.beginInfo();
          FrameDecoder decoder=new FrameDecoder(new FrameDecoder.Sink(){
            public void frame(byte[] b){selection.frame(b);}public void line(String s){}
            public void malformed(){throw new AssertionError("malformed fixture");}
          });
          decoder.accept(Arrays.copyOfRange(raw,0,split));decoder.accept(Arrays.copyOfRange(raw,split,raw.length));
          ok(selection.infoComplete&&path.equals(selection.infoPath),"path acquired with optional blank records at split "+split);
          if(split==0){String last=selection.lastAttempt;selection.reset();ok(last.equals(selection.lastAttempt),"reconnect retains last Files attempt");}
        }
      }
    }
    FilesScriptSelection missing=new FilesScriptSelection();missing.beginInfo();
    byte[] raw=sequence("/GAMES/"+String.join("",Collections.nCopies(120,"x"))+".js",false,true);
    new FrameDecoder(new FrameDecoder.Sink(){public void frame(byte[] b){missing.frame(b);}public void line(String s){}public void malformed(){throw new AssertionError();}}).accept(raw);
    ok(!missing.infoComplete,"missing middle path row cannot execute a truncated filename");
    FilesScriptSelection s=new FilesScriptSelection();s.beginInfo();s.frame(text("FILE INFO\n",106,25));s.frame(text("Path: /GAMES/Dino.js\n",10,49));
    s.frame(text("Bytes: 23949\n",10,57));ok(!s.infoComplete,"wrong field row cannot terminate a path");
    s.frame(new byte[]{(byte)170,5,0,0,0});s.frame(text("Bytes: 23949\n",10,65));ok(!s.infoComplete,"redraw invalidates stale path and title");
    s.abort("Metadata incomplete");String retained=s.lastAttempt;s.reset();ok(retained.equals(s.lastAttempt),"failure survives a subsequent USB reset");
    System.out.println("PASS "+checks+" File Info whitespace, field-row, reconnect and USB split regressions.");
  }
}
