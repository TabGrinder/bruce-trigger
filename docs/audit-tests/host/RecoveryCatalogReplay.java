package com.tabgrinder.brucetrigger;
import java.nio.file.*;
import java.util.*;
/** Complete real engine traces through the current production v3 decoder. */
public final class RecoveryCatalogReplay {
  public static void main(String[] paths)throws Exception {
    if(paths.length!=6)throw new AssertionError("six unchanged game traces required");
    for(String path:paths){
      GameScreenStream screen=new GameScreenStream();
      long now=0,drawings=0,records=0;int fragments=0;
      for(String line:Files.readAllLines(Paths.get(path))){
        if(!(line.startsWith("R|")||line.startsWith("D|")))throw new AssertionError("legacy/non-frame record in v3 trace");
        if(!screen.line(line,now))throw new AssertionError(path+": "+screen.failure);
        List<byte[]> frame;while((frame=screen.takeFrame())!=null){drawings++;records+=frame.size();}
        fragments++;now=Long.parseLong(line.split("\\|")[1])*80;
      }
      if(screen.frames<3||drawings<2||screen.rejected!=0||records!=screen.records)throw new AssertionError(path+": incomplete replay");
      System.out.println("PASS "+Paths.get(path).getFileName()+": "+screen.frames+" transactions, "+fragments+" recoverable shards, "+drawings+" drawings, "+records+" records, rejected=0");
    }
    System.out.println("Production Java decoder; engine source plus strict hardware doubles, synthetic timestamps and immediate consumer. No physical FPS/transport claim.");
  }
}
