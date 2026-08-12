# Spike findings — IME viability (issue #18, #17's back-gesture deviation)

This folder holds the `:spike:ime-viability` app's committed output — pulled or shared verbatim
from a real device, never hand-transcribed. One file per device tested; each file may contain
multiple runs (focus-only and full-IME, and repeats across sessions) because the app appends
rather than overwrites.

## How to add a device's findings here

1. Run the spike per `spike/ime-viability/README.md` on the device, in both modes.
2. Pull or share `ime-viability-findings.md` from the device.
3. Commit it into this folder as `<manufacturer>-<model>.md` (e.g. `xiaomi-23049rad8c.md`), with
   its content unchanged from what the app produced.

## Expected format (produced by the app, not written by hand)

Each run is one section:

```
## Run: <ISO timestamp> — Mode: <Focus-only | Full IME>
- Device: <manufacturer> <model>, Android <release> (API <level>)
- Keyboard appeared: <true|false|N/A>
- Keyboard covers field: <true|false|N/A>
- IME inset callback fired (without relying on imePadding()): <true|false|N/A>
- Window removed cleanly (no leaked focusable state): <true|false>
- Video paused when window took focus (human): <Yes|No|Not tested>
- Focus returned to the app underneath after dismissal (human): <Yes|No|Not tested>
```

## Status

No device findings are committed yet — this spike is maintainer-run on real hardware and cannot
be closed by this pipeline (adb-injected input does not reach the overlay on the maintainer's
HyperOS device; see `spike/ime-viability/README.md`).
