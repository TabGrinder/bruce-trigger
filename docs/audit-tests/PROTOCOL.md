# Graphics transport and validation

The owned RAM adapter sends `BT_<session nonce>_GAME|` records. The nonce scopes incoming records to a connection; it is not authentication. Only verified, ordered, complete drawing transactions are presented. Controls use the stock CLI, with one outstanding command and no replay of uncertain writes.

`R|sequence|length|operation_count|checksum|shard_id|hex|record_checksum` carries uncompressed drawing bytes. `D` uses the same envelope for PackBits-compressed XOR against the last complete nonempty drawing transaction. The adapter chooses `D` only when it reduces the number of data shards. Heartbeats use `R` and leave the previous drawing transaction unchanged. A connection reset discards the compression base.

PackBits control bytes 0–127 introduce 1–128 literal bytes; 128–255 introduce 3–130 repetitions of the following byte. Expansion must exactly match the base length, bounded to 4096 bytes. Truncation, overflow, missing bases, conflicting encoding metadata and skipped sequences fail validation. Compression does not omit drawing operations or predict game state.

Each group has up to eight 80-byte data shards and two GF(256) parity shards. Record and transaction Adler32 checksums detect corruption; parity repairs up to two erased shards in a group. Checksums are not cryptographic authentication. A lost transaction beyond recovery capacity cannot be skipped safely.

The USB worker requests one endpoint-sized packet per read. A bounded 12 ms read wait reduces the delay before an outbound control is considered; the main thread and device add their own latency. Game commands wait for the firmware prompt and a 20 ms cooldown. Native menus retain their 120 ms cooldown. A prompt acknowledges CLI completion, not a game action. Queued game inputs expire after 250 ms, and release/focus loss cancels unsent held-input samples. Firmware script uploads retain 64-byte chunks and 25 ms gaps.

`firmware-api.json` pins the stock 1.16.1 display and sprite exports independently. Sprite does not export triangle drawing. Megalodon uses immutable typed-array bitmap masks through the supported Sprite XBitmap API. Its Android asset drawing is compared with an independent native pixel reference. Generic JS games retain their original code and controls.

Validation includes an independent Python wire decoder, MicroQuickJS 0.0.6 execution with explicit native doubles, Java byte-equivalence and erasure tests, native API negative checks, actual game movement assertions, and Android Canvas/instrumentation tests. These do not establish physical LilyGO FPS or certify every third-party script.

## Runtime ownership and local resources

A pending stock interpreter/capture is checkpointed in private Android preferences before its launch is queued for USB execution. An unconfirmed close keeps the recovery fence across controller recreation. Acknowledged completion and worker drain, observed firmware boot output, or the disconnected physical-restart acknowledgement clear it. Preference writes are asynchronous; this is not a firmware authentication or crash-proof persistence guarantee. No startup, command or input is replayed after an uncertain result.

USB loss reports separate absent enumeration, changed Android identity, revoked permission and enumeration/query failure. These observations do not distinguish physical cable loss from power loss or a firmware reset without additional device evidence.

Native text and binary records share an unescaped stream: byte 0xAA is reserved for native drawing headers. Arbitrary UTF-8 containing that byte is not universally representable. Reviewed adapters use ASCII wire records; invalid or incomplete records remain bounded and cannot establish a verified screen.

The Android renderer keeps at most one spare RGB565 working image per supported sprite (eight sprites, each at most 320 by 170). Copy-on-first-mutation and atomic commit/rollback remain mandatory. Clearing the mirror releases these surfaces and derived transparency masks. Root-menu surfaces are reused only when geometry and presentation type match.
