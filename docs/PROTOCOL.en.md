# Nikon Z50II Wireless Control Protocol Notes (Findings)

[中文](PROTOCOL.md) | **English**

## Connection Modes

- Camera menu: "Connect to Smart Device" + Wi-Fi connect (STA mode); the camera joins the phone's hotspot as a client.
- "Connect to Computer" mode is supported as a fallback (different Init code).

## Device Discovery

- mDNS service type `_ptp._tcp.local`; instance name `<model>_<serial>` (e.g. `Z50_2_8095684`).
- SRV port: 15740; TXT records: `guid` (matches the Init ACK), `vid`, `pid`, `apps` (`PAIR`/`$DSC`).

## Frame Format (TCP 15740)

- 8-byte little-endian header: `length(4) + packet_type(4)`, length first.
- Types: 1/2 Init Command (request/ACK), 3/4 Init Event, 6/7 command (request/response), 8 event, 9/10/12 data phase (Start/Data/End), 13/14 keep-alive.

## Session Establishment

1. Command channel: `type1` Init Command Request → `type2` ACK (returns session=1, camera GUID, model + serial).
2. Event channel: a second TCP connection sends `type3` → `type4` ACK.
3. On the command channel, send GetDeviceInfo (0x1001), then OpenSession (0x1002, parameter 2).

### Init Command Request Payload

`client UUID(16B) + device name(UTF-16) + 00 00 + bufferSize(4LE=65536)`.

- Smart device mode: UUID bytes 5-6 are `61 1e`, device name UTF-16BE (e.g. `Android Device`).
- Computer mode: UUID bytes 5-6 are `0d cc`, device name UTF-16LE (e.g. `WTU-<hostname>`).
- The client UUID is persisted and reused; the command session ID is whatever the ACK returns (always 1 in practice).

## Pairing

1. Inside the session send GetPtpipPairingCode (0x952B).
2. Response data = `count(4LE) + count bytes`; the decimal digits of each byte concatenated form the code (e.g. `05 00 07 07` → `5077`).
3. Send CompletePairing (0x935A, parameter 0x2001) to finish pairing.
4. The camera ends the session by itself after pairing completes; paired clients skip the pairing-code flow when reconnecting.

## Common Commands (opcodes identical to USB MTP)

| Opcode | Command | Notes |
| --- | --- | --- |
| 0x1007 | GetObjectHandles | params: storage, format, association; response is a handle array |
| 0x1008 | GetObjectInfo | ObjectInfo dataset |
| 0x1009 / 0x100A | GetObject / GetThumb | download original / thumbnail |
| 0x90C4 | GetLargeThumb | large preview (spec 6.2.3.6, priority MPF class1 → VIEW → class2; for 3:2 stills this actually returns class 1 at ~640×424, 1620×1080 is the class 2 tier) |
| 0x100B | DeleteObject | delete |
| 0x1014 | GetDevicePropDesc | property enumeration (actual supported values) |
| 0x1015 / 0x1016 | Get/SetDevicePropValue | read/write of 2-byte property codes; data is the raw value bytes |
| 0x943C | SetDevicePropValueEx | 4-byte extended property codes |
| 0x9201 / 0x9202 | Start/EndLiveView | remote viewfinder on/off |
| 0x9205 | ChangeAfArea | tap-to-focus coordinates (see below) |
| 0x9207 | InitiateCaptureRecInMedia | params `0xFFFFFFFF, 0`; poll DeviceReady afterwards |
| 0x90C1 / 0x90C8 | AfDrive / DeviceReady | AF drive / operation-complete polling |
| 0x9428 | GetLiveViewImageEx | poll viewfinder frames (~10fps) |

## Data Phase (host → camera writes)

`type6 request → type9 Start Data(session+length8) → type12 End Data(session+data) → type7 response`.

## Device Properties

- ISO: 0x500F (UINT16, value is the ISO)
- Shutter: 0x500D (UINT32, 1/10000s)
- Aperture: 0x5007 (UINT16, aperture×100)
- Exposure compensation: 0x5010 (INT16, EV×1000)
- AF area mode: 0xD05D (UINT16; single point = 0x8010)
- DevicePropValue data is the raw value bytes (no DataType prefix)

## Live View

- Data: LiveViewObject (1024-byte header + JPEG); JPEG resolution 640×424.
- Header multi-byte fields are big-endian: whole size 5568×3712, image size 640×424, AF box (size/center, offset 48-55), focus status (offset 41/42).
- ChangeAfArea coordinates are sent in full-sensor coordinates; the local calibration factor is JPEG pixels × 8.0.
- 0x2019 (Device_Busy) needs backoff and retry; 0xA00B (Not_LiveView) needs StartLiveView again.

## Gallery / ObjectInfo

- ObjectInfo multi-byte fields are little-endian; the fixed area is 52 bytes (AssociationDesc occupies 4), followed by filename, capture date and other UTF-16 strings.
- Object format codes: JPEG=0x3801, NEF=0x3000 (when MTP-supported)/0x3800.
- Thumbnails: GetThumb; originals: GetObject (large files should be accumulated in a stream to avoid memory peaks).
- Listing flow: GetObjectHandles once unfiltered + 3 format-filtered queries (JPEG/NEF×2) to obtain the badges, saving one round trip per photo compared to per-object GetObjectInfo; thumbnails are sourced in the order "memory → disk cache (cacheDir/camera-thumbs/<camera>, keyed by handle) → GetThumb". On refresh the cache is validated against the handle set — if any cached handle disappears (a deletion happened on the card, handles may have shifted) the whole cache is invalidated.
- Full-screen viewing (two stages): show the grid thumbnail first → quick stage "disk full cache (camera-full, a hit is already final quality) → GetLargeThumb (~640×424 transitional image)" → sharp stage GetObject (NEF uses embedded JPEG) replaces it and is cached to disk; the second open hits the cache and is instantly sharp.

## Response Codes

- 0x2001 OK, 0x2002 General Error, 0x200A property not supported, 0x2019 Device_Busy, 0xA002 Out of Focus, 0xA00B Not_LiveView.
