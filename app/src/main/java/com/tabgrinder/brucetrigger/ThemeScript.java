package com.tabgrinder.brucetrigger;

/** Read-only script for the compatible Bruce JavaScript interpreter. No firmware or filesystem writes. */
final class ThemeScript {
  static String source(String nonce) {
    if(nonce==null || !nonce.matches("[0-9a-f]{16}")) throw new IllegalArgumentException("nonce");
    // Metadata checks precede every read. Configuration stays on Bruce: only allowlisted
    // theme images are emitted. Never evaluate theme data or print error/file contents.
    return StockScriptText.lines("(function(){var st=require('storage'),p='BT_"+nonce+"_IMG|',total=0,count=0;"
        + "function emit(s){serial.println(p+s);}function ends(s,e){return s.substring(s.length-e.length)===e;}"
        + "function path(s){if(typeof s!=='string'||s.length>115||s.length<3||s.charAt(0)!=='/')return false;"
        + "if(s.indexOf('//')>=0||s.indexOf('\\\\')>=0||s.indexOf('|')>=0)return false;"
        + "var a=s.split('/');for(var i=0;i<a.length;i++)if(a[i]==='.'||a[i]==='..')return false;"
        + "for(var i=0;i<s.length;i++){var c=s.charCodeAt(i);if(c<32||c>126)return false;}return true;}"
        + "function col(v,d){if(typeof v!=='string')return d;var s=v.toLowerCase();if(s.substring(0,2)==='0x')s=s.substring(2);"
        + "if(s.length<1||s.length>4)return d;for(var i=0;i<s.length;i++)if('0123456789abcdef'.indexOf(s.charAt(i))<0)return d;return s;}"
        + "function read(fs,f,max,bin){var cut=f.lastIndexOf('/'),dir=cut===0?'/':f.substring(0,cut),name=f.substring(cut+1);"
        + "var list=st.readdir({fs:fs,path:dir},{withFileTypes:true});if(list.length>256)throw 1;"
        + "var size=-1;for(var i=0;i<list.length;i++)if(list[i].name===name&&!list[i].isDirectory)size=list[i].size;"
        + "if(size<1||size>max)throw 1;var data=st.read({fs:fs,path:f},bin);if(data.length>max)throw 1;return data;}"
        + "try{var cfg;try{cfg=JSON.parse(read('sd','/bruce.conf',16384,false));}"
        + "catch(configError){cfg=JSON.parse(read('littlefs','/bruce.conf',16384,false));}"
        + "var f=cfg.themeFile,fs=cfg.themeOnSd===2?'sd':'littlefs',id=cfg.themeOnSd===2?0:2;"
        + "if(path(f)&&ends(f.toLowerCase(),'.json')&&(cfg.themeOnSd===1||cfg.themeOnSd===2)){"
        + "var th=JSON.parse(read(fs,f,16384,false)),base=f.substring(0,f.lastIndexOf('/')+1);"
        + "emit('C|'+col(cfg.priColor,col(th.priColor,'f81f'))+'|'+col(cfg.bgColor,col(th.bgColor,'0000')));"
        + "var keys=['wifi','ble','ethernet','rf','rfid','fm','ir','files','gps','nrf','interpreter','clock','others','connect','config','boot_img','lora'];"
        + "var seen=[];for(var k=0;k<keys.length;k++){var name=th[keys[k]];if(typeof name!=='string')continue;"
        + "var file=base+name,low=file.toLowerCase();if(!path(file)||!(ends(low,'.jpg')||ends(low,'.jpeg')||ends(low,'.png')||ends(low,'.bmp')||ends(low,'.gif'))){emit('S');continue;}"
        + "emit('M|'+keys[k]+'|'+id+'|'+file);if(seen.indexOf(file)>=0)continue;seen.push(file);"
        + "try{var data=read(fs,file,131072,true);if(total+data.length>1048576||count>=18)throw 1;total+=data.length;count++;"
        + "emit('B|'+id+'|'+file+'|'+data.length);var a=1,b=0,hex='0123456789abcdef';"
        + "for(var i=0;i<data.length;i+=128){var s='';for(var j=i;j<data.length&&j<i+128;j++){var v=data[j];s+=hex.charAt(v>>4)+hex.charAt(v&15);a=(a+v)%65521;b=(b+a)%65521;}emit('D|'+i+'|'+s);delay(1);}"
        + "emit('E|'+(b*65536+a));data=null;}catch(imageError){emit('S');}}}}catch(themeError){emit('S');}"
        + "emit('Z');serial.println('BT_"+nonce+"_THEME_DONE');})();\n");
  }
  private ThemeScript() {}
}
