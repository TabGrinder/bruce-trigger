import com.tabgrinder.brucetrigger.*;
public final class PresentationSafetyTest {
 static int n;static void ok(boolean v,String label){n++;if(!v)throw new AssertionError(label);}
 public static void main(String[] args){
 ok(DisplayText.deviceName("bruce\n\r\u202Eoff\u0000").equals("bruceoff"),"radio label removes controls and bidi");
 ok(DisplayText.deviceName(null).equals("Bruce candidate"),"null label");
 ok(DisplayText.deviceName("\u202e\n").equals("Bruce candidate"),"empty label fallback");
 StringBuilder emoji=new StringBuilder();for(int i=0;i<100;i++)emoji.appendCodePoint(0x1f600);
 String name=DisplayText.deviceName(emoji.toString());ok(name.codePointCount(0,name.length())==64,"codepoint bound");ok(!Character.isHighSurrogate(name.charAt(name.length()-1)),"not split emoji");
 ConsoleBuffer c=new ConsoleBuffer();for(int i=0;i<128;i++)ok(c.append("x",100),"accepted bounded line");ok(!c.append("overflow",101),"line flood rejected");ok(c.snapshot().startsWith("[Output limited]"),"visible limiter");ok(c.append("new window",1100),"window recovery");c.clear();ok(c.snapshot().isEmpty(),"clear resets content and limiter");ok(c.append("literal <script>\u202e\u0000\n",1200),"literal text accepted");ok(c.snapshot().equals("literal <script>\n"),"control and format sanitation");
 StringBuilder huge=new StringBuilder();for(int i=0;i<100000;i++)huge.append('a');c.clear();for(int i=0;i<100;i++)c.append(huge.toString(),2000+i*1000);ok(c.snapshot().length()<=8192,"bounded retained output under huge inputs");
 c.clear();ok(!c.append(null,0),"null output safe");for(int i=0;i<8;i++)ok(c.append(huge.toString(),0),"bytes budget allowed");ok(!c.append("x",1),"char budget limit");
 System.out.println("PASS "+n+" display and diagnostic flood assertions.");
 }
}
