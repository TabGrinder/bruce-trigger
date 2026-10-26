package com.tabgrinder.brucetrigger;

import java.util.Arrays;
import java.util.zip.Adler32;

/** v3: eight data shards plus two independent GF(256) parity shards per group.
 * Repairs erasures, never guesses/skips drawing deltas or replays device input.
 * Both the complete record (including its address) and reconstructed transaction
 * are checksummed. This is transport integrity, not device authentication. */
final class RecoverableGameStream {
  static final int SHARD = 80, DATA = 8, GROUP = 10, MAX_BYTES = 4096;
  private final byte[][] shards = new byte[70][];
  private final boolean[] decoded = new boolean[7];
  private final int[] separators = new int[6];
  private final WindowBudget budget = new WindowBudget(1048576, 1048576, 1000);
  private int length = -1, count, groups, completeGroups;
  private long checksum, sequence;
  private byte[] bytes;
  private byte[] previous;
  private boolean delta;
  long compressedFrames, wireBytes, decodedBytes;
  long repairedShards, repairedFrames, damagedRecords, redundantRecords;
  private int repaired;
  String failure = "None";

  static final class Frame {
    final byte[] bytes;
    final int count;
    final long checksum;
    Frame(byte[] bytes, int count, long checksum) {
      this.bytes = bytes; this.count = count; this.checksum = checksum;
    }
  }

  Frame accept(String record, long now) {
    if (!failure.equals("None")) return null;
    if (record == null || record.length() > 300 || !budget.allow(record.length() + 1, now))
      return fail("Recovery record length or rate exceeded");
    int end = record.lastIndexOf('|');
    // A broken line is an erasure. It cannot change the sequence/metadata or
    // freshness. More than two erasures in one group remain unrecoverable.
    if (end < 0) { damagedRecords++; return null; }
    try {
      long receivedCheck = number(record,end + 1,record.length());
      if (adlerAscii(record, end) != receivedCheck) {
        damagedRecords++; return null;
      }
      int fields=0;
      for(int i=0;i<end;i++)if(record.charAt(i)=='|') {
        if(fields==separators.length)return fail("Invalid recovery record");
        separators[fields++]=i;
      }
      if(fields!=6 || separators[0]!=1 || !(record.charAt(0)=='R' || record.charAt(0)=='D'))
        return fail("Invalid recovery record");
      boolean isDelta = record.charAt(0)=='D';
      long seq = field(record,1), len = field(record,2), ops = field(record,3), sum = field(record,4), id = field(record,5);
      int hexAt=separators[5]+1,hexLength=end-hexAt;
      if (len > MAX_BYTES || ops > 256 || sum > 0xffffffffL || id >= 70
          || hexLength != (len == 0 ? 0 : SHARD * 2)) return fail("Recovery metadata exceeds bounds");
      for(int i=hexAt;i<end;i++)if(hex(record.charAt(i))<0)return fail("Invalid recovery hex");
      int n = (int) ((len + SHARD - 1) / SHARD), g = (int) id / GROUP, index = (int) id % GROUP;
      int k = Math.min(DATA, n - g * DATA);
      if (len == 0 ? ops != 0 || sum != 1 || id != 0 : k < 1 || index < DATA && index >= k)
        return fail("Invalid recovery shard address");
      // Parity/duplicates that arrive after verified completion cannot draw twice.
      if (seq == sequence - 1) { redundantRecords++; return null; }
      if (seq != sequence) return fail("Graphics recovery capacity exceeded at sequence " + sequence);
      if (len == 0) {
        if (isDelta) return fail("Compressed heartbeat is invalid");
        if (length >= 0) return fail("Conflicting recovery heartbeat");
        sequence++; return new Frame(new byte[0], 0, 1);
      }
      if (length < 0) {
        length = (int) len; count = (int) ops; checksum = sum;
        delta = isDelta;
        groups = (n + DATA - 1) / DATA; bytes = new byte[length];
      } else if (length != len || count != ops || checksum != sum || delta != isDelta)
        return fail("Conflicting recovery metadata");
      byte[] shard = new byte[SHARD];
      for(int i=0;i<SHARD;i++)shard[i]=(byte)((hex(record.charAt(hexAt+i*2))<<4)|hex(record.charAt(hexAt+i*2+1)));
      if (shards[(int) id] != null) {
        if (!Arrays.equals(shards[(int) id], shard)) return fail("Conflicting recovery duplicate");
        redundantRecords++; return null;
      }
      shards[(int) id] = shard;
      if (!decoded[g] && recover(g, k)) {
        decoded[g] = true; completeGroups++;
        for (int i = 0; i < k; i++) {
          int offset = (g * DATA + i) * SHARD;
          System.arraycopy(shards[g * GROUP + i], 0, bytes, offset, Math.min(SHARD, length - offset));
        }
      }
      if (completeGroups != groups) return null;
      if (adler(bytes) != checksum) return fail("Recovered graphics checksum mismatch");
      byte[] complete = bytes;
      if (delta) {
        complete = expandDelta(bytes);
        if (complete == null) return fail("Invalid compressed graphics or missing base");
        compressedFrames++;
      }
      wireBytes += bytes.length; decodedBytes += complete.length;
      previous = complete;
      Frame frame = new Frame(complete, count, delta ? adler(complete) : checksum);
      repairedShards += repaired;
      if (repaired > 0) repairedFrames++;
      clearFrame(); sequence++;
      return frame;
    } catch (NumberFormatException e) {
      damagedRecords++; return null;
    }
  }

  /** Bounded PackBits XOR; the base is the last complete nonempty transaction.
   * Every transaction remains ordered and protected by the same erasure code.
   * A lost transaction cannot be skipped to create a plausible wrong image. */
  private byte[] expandDelta(byte[] packed) {
    if (previous == null) return null;
    byte[] out = new byte[previous.length];
    int at = 0, written = 0;
    while (at < packed.length) {
      int control = packed[at++] & 255;
      int size = control < 128 ? control + 1 : control - 128 + 3;
      if (size > out.length - written) return null;
      if (control < 128) {
        if (size > packed.length - at) return null;
        for (int i = 0; i < size; i++) out[written + i] = (byte) (packed[at++] ^ previous[written + i]);
      } else {
        if (at == packed.length) return null;
        byte value = packed[at++];
        for (int i = 0; i < size; i++) out[written + i] = (byte) (value ^ previous[written + i]);
      }
      written += size;
    }
    return written == out.length ? out : null;
  }

  private boolean recover(int group, int k) {
    int base = group * GROUP, a = -1, b = -1, missing = 0;
    for (int i = 0; i < k; i++) if (shards[base + i] == null) {
      if (++missing > 2) return false;
      if (a < 0) a = i; else b = i;
    }
    if (missing == 0) return true;
    byte[] p = shards[base + DATA], q = shards[base + DATA + 1];
    if (missing == 2 ? p == null || q == null : p == null && q == null) return false;
    byte[] first = new byte[SHARD], second = missing == 2 ? new byte[SHARD] : null;
    int ca = 1 << a, cb = b < 0 ? 0 : 1 << b;
    for (int j = 0; j < SHARD; j++) {
      int x = p == null ? 0 : p[j] & 255, y = q == null ? 0 : q[j] & 255;
      for (int i = 0; i < k; i++) if (i != a && i != b) {
        int v = shards[base + i][j] & 255;
        x ^= v; y ^= multiply(v, 1 << i);
      }
      int v = missing == 1 ? p != null ? x : divide(y, ca) : divide(y ^ multiply(cb, x), ca ^ cb);
      first[j] = (byte) v;
      if (second != null) second[j] = (byte) (x ^ v);
    }
    shards[base + a] = first;
    if (second != null) shards[base + b] = second;
    repaired += missing;
    return true;
  }

  private static int multiply(int a, int b) {
    int out = 0;
    while (b != 0) {
      if ((b & 1) != 0) out ^= a;
      b >>>= 1; a <<= 1;
      if ((a & 256) != 0) a ^= 0x11d;
    }
    return out;
  }
  private static int divide(int a, int b) {
    // All coefficients/divisors are nonzero distinct powers in GF(256).
    int inverse = 1, factor = b, power = 254;
    while (power != 0) {
      if ((power & 1) != 0) inverse = multiply(inverse, factor);
      factor = multiply(factor, factor); power >>>= 1;
    }
    return multiply(a, inverse);
  }
  private long field(String record,int field) {return number(record,separators[field-1]+1,separators[field]);}
  private static int hex(char c) {return c>='0'&&c<='9'?c-'0':c>='a'&&c<='f'?c-'a'+10:c>='A'&&c<='F'?c-'A'+10:-1;}
  private static long number(String text,int start,int end) {
    int length=end-start;
    if(length==0 || length>10 || length>1 && text.charAt(start)=='0')throw new NumberFormatException();
    long value=0;
    for(int i=start;i<end;i++) {
      char digit=text.charAt(i);
      if(digit<'0' || digit>'9')throw new NumberFormatException();
      value=value*10+digit-'0';
    }
    return value;
  }
  private static long adlerAscii(String text,int length) {
    int a=1,b=0;
    for(int i=0;i<length;i++) {
      char c=text.charAt(i);
      if(c>127)throw new NumberFormatException();
      a+=c;b+=a;
    }
    return ((long)(b%65521)<<16)|(a%65521);
  }
  private static long adler(byte[] bytes) { Adler32 a = new Adler32(); a.update(bytes); return a.getValue(); }
  private Frame fail(String reason) { failure = reason; clearFrame(); return null; }
  private void clearFrame() {
    Arrays.fill(shards, null); Arrays.fill(decoded, false);
    length = -1; count = groups = completeGroups = repaired = 0; bytes = null; delta = false;
  }
  void reset() {
    clearFrame(); budget.reset(); sequence = checksum = 0; previous = null;
    compressedFrames = wireBytes = decodedBytes = 0;
    repairedShards = repairedFrames = damagedRecords = redundantRecords = 0; failure = "None";
  }
}
