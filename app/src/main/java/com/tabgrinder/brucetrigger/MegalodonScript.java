package com.tabgrinder.brucetrigger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Fixed bundled RAM adaptation; never accepts device-supplied executable code. */
final class MegalodonScript {
  private static final String SHA256 = "df33d6c01893274a713952b5cc502c170b58b8c36254ffdf9dfadae4f2f583df";
  static String source(String template,String nonce,boolean capture) {
    if(template==null || template.length()>16000 || nonce==null || !nonce.matches("[0-9a-f]{16}"))
      throw new IllegalArgumentException("Megalodon bridge input");
    try {
      byte[] hash=MessageDigest.getInstance("SHA-256").digest(template.getBytes(StandardCharsets.US_ASCII));
      StringBuilder hex=new StringBuilder(64);
      for(byte b:hash)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
      if(!SHA256.contentEquals(hex))throw new IllegalArgumentException("Unrecognized Megalodon bridge");
    }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    String result=StockScriptText.lines(template.replace("@@NONCE@@",nonce).replace("@@CAPTURE@@",capture?"true":"false"));
    if(result.length()+4>PacedUsbWriter.MAX_WRITE_BYTES)throw new IllegalArgumentException("Megalodon bridge length");
    return result;
  }
  private MegalodonScript() {}
}
