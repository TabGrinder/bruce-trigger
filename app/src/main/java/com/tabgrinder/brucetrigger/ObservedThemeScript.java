package com.tabgrinder.brucetrigger;

/** Read-only recovery from an actual DRAWIMAGE key, independent of saved theme configuration. */
final class ObservedThemeScript {
  static boolean validKey(String key) {
    return key != null && (key.startsWith("0:") || key.startsWith("2:"))
        && ThemeTransfer.validPath(key.substring(2));
  }
  static String folder(String key) {
    return validKey(key) ? key.substring(0, key.lastIndexOf('/') + 1) : null;
  }
  static String source(String nonce, String key) {
    if (nonce == null || !nonce.matches("[0-9a-f]{16}") || !validKey(key))
      throw new IllegalArgumentException("Invalid image request");
    // The path is a quoted data literal, never executable JS. Backslashes/control
    // characters are excluded by validPath; quotes are escaped independently.
    String literal = key.substring(2).replace("\"", "\\\"");
    return StockScriptText.lines("(function(){var st=require('storage'),p='BT_"+nonce+"_IMG|';"
        + "var wanted=\n\""+literal+"\";var fs='"+(key.charAt(0)=='0'?"sd":"littlefs")+"',id="+key.charAt(0)+";"
        + "var total=0,count=0,seen=[];function emit(s){serial.println(p+s);}function ends(s,e){return s.substring(s.length-e.length)===e;}"
        + "function image(s){if(typeof s!=='string'||s.length<3||s.length>115||s.charAt(0)!=='/'||s.indexOf('//')>=0||s.indexOf('\\\\')>=0||s.indexOf('|')>=0)return false;"
        + "var a=s.split('/');for(var i=0;i<a.length;i++)if(a[i]==='.'||a[i]==='..')return false;"
        + "for(var i=0;i<s.length;i++){var c=s.charCodeAt(i);if(c<32||c>126)return false;}"
        + "var t=s.toLowerCase();return ends(t,'.jpg')||ends(t,'.jpeg')||ends(t,'.png')||ends(t,'.bmp')||ends(t,'.gif');}"
        + "try{var cut=wanted.lastIndexOf('/'),dir=cut===0?'/':wanted.substring(0,cut),base=dir==='/'?'/':dir+'/';"
        + "var list=st.readdir({fs:fs,path:dir},{withFileTypes:true});if(list.length>256)throw 1;"
        + "var send=function(e){var f=base+e.name;if(e.isDirectory||!image(f)||e.name.indexOf('/')>=0)return;"
        + "if(seen.indexOf(f)>=0)return;seen.push(f);"
        + "if(e.size<1||e.size>131072||total+e.size>1048576||count>=18){emit('S');return;}"
        + "total+=e.size;count++;try{var data=st.read({fs:fs,path:f},true);if(data.length!==e.size)throw 1;"
        + "emit('B|'+id+'|'+f+'|'+data.length);var a=1,b=0,hex='0123456789abcdef';"
        + "for(var i=0;i<data.length;i+=128){var s='';for(var j=i;j<data.length&&j<i+128;j++){var v=data[j];s+=hex.charAt(v>>4)+hex.charAt(v&15);a=(a+v)%65521;b=(b+a)%65521;}emit('D|'+i+'|'+s);delay(1);}"
        + "emit('E|'+(b*65536+a));}catch(imageError){emit('S');}};"
        + "for(var i=0;i<list.length;i++)if(base+list[i].name===wanted)send(list[i]);"
        + "for(var i=0;i<list.length;i++)if(base+list[i].name!==wanted)send(list[i]);"
        + "}catch(folderError){emit('S');}emit('Z');serial.println('BT_"+nonce+"_THEME_DONE');})();\n");
  }
  private ObservedThemeScript() {}
}
