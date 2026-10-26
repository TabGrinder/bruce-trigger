package com.tabgrinder.brucetrigger;
import java.nio.file.*;
import java.util.*;
/** Current production decoder consumes the complete engine-generated long Dino stream. */
public final class DinoWireReplay {
  public static void main(String[] args) throws Exception {
    GameScreenStream screen=new GameScreenStream();
    long now=0,delivered=0,draws=0;
    for(String line:Files.readAllLines(Paths.get(args[0]))) {
      if(!screen.line(line,now))throw new AssertionError(screen.failure);
      List<byte[]> frame;
      while((frame=screen.takeFrame())!=null){delivered++;draws+=frame.size();}
      now=Long.parseLong(line.split("\\|")[1])*80;
    }
    if(delivered<3000||!"None".equals(screen.failure)||screen.rejected!=0)
      throw new AssertionError(screen.failure+" delivered="+delivered);
    System.out.println("PASS current production decoder: "+screen.frames+" transactions, "+delivered+" drawing batches, "+draws+" records; rejected=0, peak queue="+screen.peakQueuedFrames());
    System.out.println("Synthetic timestamps and immediate consumer; no physical USB timing claim.");
  }
}
