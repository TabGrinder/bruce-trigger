package com.tabgrinder.brucetrigger;
import java.util.*;
/** Every shortened final group, including single-data and parity-only recovery. */
public final class ShortRecoveryProof {
 public static void main(String[] args){int cases=0;
  for(int k=1;k<=8;k++){
   byte[] expected=RecoveryGameRegressionTest.data((k-1)*16);
   List<String> records=RecoveryGameRegressionTest.encode(0,expected,2+(k-1)*16);
   if(records.size()!=k+2)throw new AssertionError("expected shortened group");
   for(int a=0;a<records.size();a++)for(int b=a+1;b<records.size();b++){
    GameScreenStream screen=RecoveryGameRegressionTest.screen();
    for(int i=0;i<records.size();i++)if(i!=a&&i!=b&&!screen.line(records.get(i),1000))throw new AssertionError(screen.failure);
    if(screen.frames!=1||!Arrays.equals(expected,RecoveryGameRegressionTest.take(screen)))throw new AssertionError(k+":"+a+":"+b);
    cases++;
   }
  }
  System.out.println("PASS "+cases+" shortened-group loss pairs across 1..8 data shards: exact bytes, parity-only and single-parity recovery included.");
 }
}
