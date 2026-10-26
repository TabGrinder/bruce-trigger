package android.os; public class SystemClock { public static volatile long now=1000; public static long elapsedRealtime(){return now;} }
