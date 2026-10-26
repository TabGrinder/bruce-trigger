package com.tabgrinder.brucetrigger.audit;
import java.util.*;
import java.util.zip.Adler32;
import java.nio.charset.StandardCharsets;
final class RecoveryWire {
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
  static String encode(String prefix,long seq,byte[] bytes,int ops){StringBuilder out=new StringBuilder();for(String line:encode(seq,bytes,ops))out.append(prefix).append(line).append('\n');return out.toString();}
}
