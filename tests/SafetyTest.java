import com.tabgrinder.brucetrigger.*;
public class SafetyTest {
 static int n; static void ok(boolean x){n++;if(!x)throw new AssertionError("case "+n);}
 public static void main(String[] x){
 WindowBudget b=new WindowBudget(2,5,1000);
 ok(!b.allow(-1,0));ok(!b.allow(6,0));ok(b.allow(3,0));ok(!b.allow(3,1));ok(b.allow(2,1));ok(!b.allow(0,2));ok(b.allow(5,1000));ok(!b.allow(1,1001));ok(b.allow(1,999));b.reset();ok(b.allow(5,1000));
 WindowBudget c=new WindowBudget(1,100,10);ok(c.allow(0,0));ok(!c.allow(0,1));ok(c.allow(0,10));
 boolean bad=false;try{new WindowBudget(0,1,1);}catch(IllegalArgumentException e){bad=true;}ok(bad);
 ConsoleBuffer console=new ConsoleBuffer();ok(console.snapshot().isEmpty());
 console.append("first\u202E line",0);String first=console.snapshot();
 ok(first.equals("first line\n"));ok(first==console.snapshot());
 console.append("second",1);ok(!console.snapshot().equals(first));
 for(int i=0;i<200;i++)console.append("bounded",2);
 String limited=console.snapshot();ok(limited.startsWith("[Output limited]\n"));ok(limited==console.snapshot());
 console.clear();ok(console.snapshot().isEmpty());console.append("new",3);ok(console.snapshot().equals("new\n"));
 System.out.println("PASS "+n+" traffic budget assertions.");
 }
}
