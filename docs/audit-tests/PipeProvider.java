package com.tabgrinder.brucetrigger.audit;
import android.content.*;import android.database.Cursor;import android.net.Uri;import android.os.*;import java.io.*;
/** Only in the separate QA APK; never shipped with the client. */
public final class PipeProvider extends ContentProvider {
 private ParcelFileDescriptor reader;private int opens;
 public boolean onCreate(){return true;}
 public synchronized ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{opens++;if(uri.getPath().contains("failure"))throw new FileNotFoundException("Intentional QA provider failure");try{ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();reader=pipe[0];return pipe[1];}catch(IOException e){throw new FileNotFoundException(e.toString());}}
 public synchronized Bundle call(String method,String arg,Bundle extras){Bundle out=new Bundle();if("release".equals(method)&&reader!=null){try{reader.close();}catch(IOException ignored){}reader=null;}out.putInt("opens",opens);out.putBoolean("blocked",reader!=null);return out;}
 public String getType(Uri u){return "application/json";}public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int delete(Uri u,String s,String[] a){return 0;}public int update(Uri u,ContentValues v,String s,String[] a){return 0;}
}
