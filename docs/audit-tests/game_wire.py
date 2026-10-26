"""Independent no-loss encoder audit: verify all systematic and parity shards.

This reference does NOT reconstruct missing shards. Java recovery tests compare
their repaired bytes to these original engine-produced systematic bytes.
"""
import zlib


def multiply(a, b):
    result = 0
    for bit in range(8):
        if b & (1 << bit):
            value = a << bit
            for shift in range(14, 7, -1):
                if value & (1 << shift):
                    value ^= 0x11d << (shift - 8)
            result ^= value
    return result


def decode(lines, allow_partial=False):
    frames, group, metadata, previous = [], {}, None, None

    def finish():
        nonlocal previous
        if metadata is None:
            return
        seq, length, operations, checksum, mode = metadata
        n = (length + 79) // 80
        required = {g * 10 + i for g in range((n + 7) // 8)
                    for i in list(range(min(8, n - g * 8))) + [8, 9]} if n else {0}
        if set(group) != required:
            assert allow_partial and seq == len(frames), ('incomplete transaction', seq, set(group), required)
            return
        data = b''.join(group[(i // 8) * 10 + i % 8] for i in range(n))[:length]
        assert zlib.adler32(data) == checksum
        for g in range((n + 7) // 8):
            k = min(8, n - g * 8)
            p, q = bytearray(80), bytearray(80)
            for i in range(k):
                shard = group[g * 10 + i]
                for j, byte in enumerate(shard):
                    p[j] ^= byte
                    q[j] ^= multiply(byte, 1 << i)
            assert bytes(p) == group[g * 10 + 8] and bytes(q) == group[g * 10 + 9]
        if mode == 'D':
            assert previous is not None and length > 0
            expanded = bytearray(); cursor = 0
            while cursor < len(data):
                control = data[cursor]; cursor += 1
                size = control + 1 if control < 128 else control - 128 + 3
                assert len(expanded) + size <= len(previous)
                if control < 128:
                    assert cursor + size <= len(data)
                    expanded.extend(data[cursor:cursor + size]); cursor += size
                else:
                    assert cursor < len(data)
                    expanded.extend([data[cursor]] * size); cursor += 1
            assert len(expanded) == len(previous)
            data = bytes(a ^ b for a, b in zip(expanded, previous))
        if data: previous = data
        assert seq == len(frames)
        at, packets = 0, []
        while at < len(data):
            size = data[at + 1]
            assert 3 <= size <= 96 and at + size <= len(data)
            packets.append(data[at:at + size].hex())
            at += size
        assert len(packets) == operations
        frames.append(packets)

    for line in lines:
        fields = line.split('|')
        if fields[0] in ('R','D'):
            assert len(fields) == 8 and zlib.adler32(line.rsplit('|', 1)[0].encode('ascii')) == int(fields[7])
            meta = tuple(map(int, fields[1:5])) + (fields[0],)
            if metadata is not None and meta[0] != metadata[0]:
                finish(); group = {}; metadata = None
            if metadata is None:
                metadata = meta
            assert metadata == meta
            index, shard = int(fields[5]), bytes.fromhex(fields[6])
            assert len(shard) == (80 if meta[1] else 0)
            assert index not in group or group[index] == shard
            group[index] = shard
    finish()
    return frames
