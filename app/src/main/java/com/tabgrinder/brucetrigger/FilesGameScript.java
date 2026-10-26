package com.tabgrinder.brucetrigger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Only a bundled bridge plus an explicitly selected Files path enters the uploader. */
final class FilesGameScript {
  private static final String SHA256 = "ac57bd9a53755de0bef00f1190eab399464821cabc5aca118e13a070b1ae4edb";
  static String source(String template,String nonce,String path) {return source(template,nonce,path,false);}
  static String source(String template,String nonce,String path,boolean capture) {
    if(template==null || template.length()>16000 || nonce==null || !nonce.matches("[0-9a-f]{16}")
        || !FilesScriptSelection.valid(path))throw new IllegalArgumentException("Files bridge input");
    try {
      byte[] hash=MessageDigest.getInstance("SHA-256").digest(template.getBytes(StandardCharsets.US_ASCII));
      StringBuilder hex=new StringBuilder(64);
      for(byte b:hash)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
      if(!SHA256.contentEquals(hex))throw new IllegalArgumentException("Unrecognized Files bridge");
    }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    // Insert the selected filename last: a literal placeholder in its name is data.
    String result=StockScriptText.lines(template.replace("@@NONCE@@",nonce).replace("@@CAPTURE@@",capture?"true":"false").replace("@@PATH@@",path)).replace("\n\n","\n");
    if(result.length()+4>PacedUsbWriter.MAX_WRITE_BYTES)throw new IllegalArgumentException("Files bridge length");
    return result;
  }
  private FilesGameScript() {}
}
