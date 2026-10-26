import com.tabgrinder.brucetrigger.FrameDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Rejected serial lines must not manufacture prompts or reset/bridge markers from their suffix. */
public final class LineBoundaryTest {
  private static int checks;
  private static void ok(boolean value, String why) {
    checks++;
    if (!value) throw new AssertionError(why);
  }
  private static final class Sink implements FrameDecoder.Sink {
    final List<String> lines = new ArrayList<>();
    int malformed, frames;
    public void frame(byte[] bytes) { frames++; }
    public void line(String line) { lines.add(line); }
    public void malformed() { malformed++; }
  }
  private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
  public static void main(String[] args) {
    for (int length : new int[]{2049, 4098, 1000000}) {
      for (String suffix : new String[]{"# ", "ESP-ROM:esp32s3", "Entering interpreter...",
          "BT_0123456789abcdef_GAME|DONE"}) {
        Sink sink = new Sink();
        FrameDecoder parser = new FrameDecoder(sink);
        byte[] oversized = new byte[length]; Arrays.fill(oversized, (byte)'x');
        for (int at=0; at<oversized.length; at+=64)
          parser.accept(Arrays.copyOfRange(oversized, at, Math.min(at+64, oversized.length)));
        parser.accept(bytes(suffix));
        ok(sink.lines.isEmpty(), "oversized line cannot emit a meaningful suffix: " + suffix);
        ok(sink.malformed == 1, "one overflow report per line, regardless of length");
        parser.discardText();
        parser.accept(bytes("# "));
        ok(sink.lines.isEmpty(), "discardText cannot promote the rejected suffix into a prompt");
        parser.accept(new byte[]{(byte)0xaa, 5, 0, 0, 0});
        ok(sink.frames == 1 && !parser.isPartial(), "binary drawing remains framed during line discard");
        parser.accept(bytes("\r\nvalid\n# "));
        ok(sink.lines.equals(Arrays.asList("valid", "# ")), "newline restores normal text and prompt parsing");
      }
    }
    Sink sink = new Sink(); FrameDecoder parser = new FrameDecoder(sink);
    byte[] oversized = new byte[2049]; Arrays.fill(oversized, (byte)'x');
    parser.accept(oversized); parser.reset(); parser.accept(bytes("# "));
    ok(sink.lines.equals(Arrays.asList("# ")), "a new connection resets line quarantine");
    System.out.println("PASS " + checks + " serial line-boundary assertions.");
  }
}
