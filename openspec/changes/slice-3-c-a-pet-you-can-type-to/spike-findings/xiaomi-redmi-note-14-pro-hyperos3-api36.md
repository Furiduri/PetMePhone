# IME viability spike — findings

## Run: 2026-08-12T11:11:29.058406 — Mode: Full IME
- Device: Xiaomi 24090RA29G, Android 16 (API 36)
- Keyboard appeared: false
- Keyboard covers field: false
- IME inset callback fired (without relying on imePadding()): true
- Window ever received focus (observed via onWindowFocusChanged): true
- Window removed cleanly (no leaked focusable state): true
- Video paused when window took focus (human): Not tested
- Focus returned to the app underneath after dismissal (human): No

## Run: 2026-08-12T11:13:29.565073 — Mode: Focus-only
- Device: Xiaomi 24090RA29G, Android 16 (API 36)
- Keyboard appeared: N/A (Focus-only mode raises no keyboard)
- Keyboard covers field: N/A (Focus-only mode raises no keyboard)
- IME inset callback fired (without relying on imePadding()): N/A (Focus-only mode raises no keyboard)
- Window ever received focus (observed via onWindowFocusChanged): true
- Window removed cleanly (no leaked focusable state): true
- Video paused when window took focus (human): No
- Focus returned to the app underneath after dismissal (human): No

## Run: 2026-08-12T11:18:42.43919 — Mode: Full IME
- Device: Xiaomi 24090RA29G, Android 16 (API 36)
- Keyboard appeared: false
- Keyboard covers field: false
- IME inset callback fired (without relying on imePadding()): true
- Window ever received focus (observed via onWindowFocusChanged): true
- Window removed cleanly (no leaked focusable state): true
- Video paused when window took focus (human): No
- Focus returned to the app underneath after dismissal (human): No

## Run: 2026-08-12T13:05:56.013402 — Mode: Focus-only
- Device: Xiaomi 24090RA29G, Android 16 (API 36)
- Keyboard appeared: N/A (Focus-only mode raises no keyboard)
- Keyboard covers field: N/A (Focus-only mode raises no keyboard)
- IME inset callback fired (without relying on imePadding()): N/A (Focus-only mode raises no keyboard)
- Window ever received focus (observed via onWindowFocusChanged): true
- Window removed cleanly (no leaked focusable state): true
- Video paused when window took focus (human): No
- Focus returned to the app underneath after dismissal (human): Yes

## Run: 2026-08-12T13:06:34.775994 — Mode: Full IME
- Device: Xiaomi 24090RA29G, Android 16 (API 36)
- Keyboard appeared: false
- Keyboard covers field: false
- IME inset callback fired (without relying on imePadding()): true
- Window ever received focus (observed via onWindowFocusChanged): true
- Window removed cleanly (no leaked focusable state): true
- Video paused when window took focus (human): No
- Focus returned to the app underneath after dismissal (human): Yes

## Run: 2026-08-12T13:14:23.98902 — Mode: Focus-only
- Device: Xiaomi 24090RA29G, Android 16 (API 36)
- Keyboard appeared: N/A (Focus-only mode raises no keyboard)
- Keyboard covers field: N/A (Focus-only mode raises no keyboard)
- IME inset callback fired (without relying on imePadding()): N/A (Focus-only mode raises no keyboard)
- Window ever received focus (observed via onWindowFocusChanged): true
- Window removed cleanly (no leaked focusable state): true
- Video paused when window took focus (human): No
- Focus returned to the app underneath after dismissal (human): Yes

## Run: 2026-08-12T13:15:02.443197 — Mode: Full IME
- Device: Xiaomi 24090RA29G, Android 16 (API 36)
- Keyboard appeared: false
- Keyboard covers field: false
- IME inset callback fired (without relying on imePadding()): true
- Window ever received focus (observed via onWindowFocusChanged): true
- Window removed cleanly (no leaked focusable state): true
- Video paused when window took focus (human): No
- Focus returned to the app underneath after dismissal (human): Yes

## Run: 2026-08-13T09:20:45.917458 — Mode: Focus-only
- Device: Xiaomi 24090RA29G, Android 16 (API 36)
- Strategy: softInputMode none applied, repositions on focus: false
- Start LayoutParams.y: 1952
- Keyboard geometry signal: not measured (fewer than two comparable samples)
- CONTROL — any inset dispatch reached the window: false (known-bad ime() signal, recorded only to confirm it reproduces; gates nothing)
- Window ever received focus (observed via onWindowFocusChanged): true
- Window removed cleanly (no leaked focusable state): true
- Keyboard appeared at all (human): Not tested
- Text field visibility while typing (human): Fully visible
- Card visibly jumped or moved when the field was focused (human): Not tested
- Resulting placement acceptable to use (human): Not tested
- Video paused when window took focus (human): No
- Focus returned to the app underneath after dismissal (human): Not tested
- Raw samples:
  | Point | +ms | visibleDisplayFrame | windowBounds | fieldBoundsOnScreen | LayoutParams.y | CONTROL ime() inset bottom |
  |---|---|---|---|---|---|---|
  | before focus | 25 | [0,130,1220,2660] | [0,0,1220,2712] | not measured | 1952 | not measured |
  | after dismissal | 21582 | [0,130,1220,2660] | [0,0,1220,2712] | not measured | 1952 | not measured |

## Run: 2026-08-13T09:21:41.994206 — Mode: Full IME (no strategy control)
- Device: Xiaomi 24090RA29G, Android 16 (API 36)
- Strategy: softInputMode none applied, repositions on focus: false
- Start LayoutParams.y: 1952
- Keyboard geometry signal: no keyboard geometry signal available on this window class (visible display frame identical before focus and with the keyboard up)
- CONTROL — any inset dispatch reached the window: true (known-bad ime() signal, recorded only to confirm it reproduces; gates nothing)
- Window ever received focus (observed via onWindowFocusChanged): true
- Window removed cleanly (no leaked focusable state): true
- Keyboard appeared at all (human): Yes
- Text field visibility while typing (human): Partially covered
- Card visibly jumped or moved when the field was focused (human): No
- Resulting placement acceptable to use (human): No
- Video paused when window took focus (human): No
- Focus returned to the app underneath after dismissal (human): Yes
- Raw samples:
  | Point | +ms | visibleDisplayFrame | windowBounds | fieldBoundsOnScreen | LayoutParams.y | CONTROL ime() inset bottom |
  |---|---|---|---|---|---|---|
  | before focus | 27 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2391,1220,2541] | 1952 | 0 |
  | after field gained focus | 33 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2391,1220,2541] | 1952 | 0 |
  | after showSoftInput returned | 930 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2391,1220,2541] | 1952 | 0 |
  | after dismissal | 12606 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2391,1220,2614] | 1952 | 0 |

## Run: 2026-08-13T09:22:21.622548 — Mode: Pan
- Device: Xiaomi 24090RA29G, Android 16 (API 36)
- Strategy: softInputMode SOFT_INPUT_ADJUST_PAN (0x20), repositions on focus: false
- Start LayoutParams.y: 1952
- Keyboard geometry signal: no keyboard geometry signal available on this window class (visible display frame identical before focus and with the keyboard up)
- CONTROL — any inset dispatch reached the window: true (known-bad ime() signal, recorded only to confirm it reproduces; gates nothing)
- Window ever received focus (observed via onWindowFocusChanged): true
- Window removed cleanly (no leaked focusable state): true
- Keyboard appeared at all (human): Yes
- Text field visibility while typing (human): Partially covered
- Card visibly jumped or moved when the field was focused (human): No
- Resulting placement acceptable to use (human): No
- Video paused when window took focus (human): No
- Focus returned to the app underneath after dismissal (human): Yes
- Raw samples:
  | Point | +ms | visibleDisplayFrame | windowBounds | fieldBoundsOnScreen | LayoutParams.y | CONTROL ime() inset bottom |
  |---|---|---|---|---|---|---|
  | before focus | 30 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2450,1220,2600] | 1952 | 0 |
  | after field gained focus | 37 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2450,1220,2600] | 1952 | 0 |
  | after showSoftInput returned | 933 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2450,1220,2600] | 1952 | 0 |
  | after dismissal | 11836 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2437,1220,2660] | 1952 | 0 |

## Run: 2026-08-13T09:23:17.270707 — Mode: Resize
- Device: Xiaomi 24090RA29G, Android 16 (API 36)
- Strategy: softInputMode SOFT_INPUT_ADJUST_RESIZE (0x10), repositions on focus: false
- Start LayoutParams.y: 1952
- Keyboard geometry signal: no keyboard geometry signal available on this window class (visible display frame identical before focus and with the keyboard up)
- CONTROL — any inset dispatch reached the window: true (known-bad ime() signal, recorded only to confirm it reproduces; gates nothing)
- Window ever received focus (observed via onWindowFocusChanged): true
- Window removed cleanly (no leaked focusable state): true
- Keyboard appeared at all (human): Yes
- Text field visibility while typing (human): Fully visible
- Card visibly jumped or moved when the field was focused (human): Yes
- Resulting placement acceptable to use (human): Yes
- Video paused when window took focus (human): No
- Focus returned to the app underneath after dismissal (human): Yes
- Raw samples:
  | Point | +ms | visibleDisplayFrame | windowBounds | fieldBoundsOnScreen | LayoutParams.y | CONTROL ime() inset bottom |
  |---|---|---|---|---|---|---|
  | before focus | 37 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2450,1220,2600] | 1952 | 0 |
  | after field gained focus | 43 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2450,1220,2600] | 1952 | 0 |
  | after showSoftInput returned | 938 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2450,1220,2600] | 1952 | 0 |
  | after dismissal | 13609 | [0,130,1220,1727] | [0,0,1220,2712] | [0,2437,1220,2660] | 1952 | 0 |

## Run: 2026-08-13T09:24:04.813172 — Mode: Anchor top on focus
- Device: Xiaomi 24090RA29G, Android 16 (API 36)
- Strategy: softInputMode none applied, repositions on focus: true
- Start LayoutParams.y: 1952
- Keyboard geometry signal: no keyboard geometry signal available on this window class (visible display frame identical before focus and with the keyboard up)
- CONTROL — any inset dispatch reached the window: true (known-bad ime() signal, recorded only to confirm it reproduces; gates nothing)
- Window ever received focus (observed via onWindowFocusChanged): true
- Window removed cleanly (no leaked focusable state): true
- Keyboard appeared at all (human): Yes
- Text field visibility while typing (human): Fully visible
- Card visibly jumped or moved when the field was focused (human): No
- Resulting placement acceptable to use (human): Yes
- Video paused when window took focus (human): No
- Focus returned to the app underneath after dismissal (human): Not tested
- Raw samples:
  | Point | +ms | visibleDisplayFrame | windowBounds | fieldBoundsOnScreen | LayoutParams.y | CONTROL ime() inset bottom |
  |---|---|---|---|---|---|---|
  | before focus | 40 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2450,1220,2600] | 1952 | 0 |
  | after field gained focus | 54 | [0,130,1220,2660] | [0,0,1220,2712] | [0,618,1220,768] | 120 | 0 |
  | after showSoftInput returned | 943 | [0,130,1220,2660] | [0,0,1220,2712] | [0,618,1220,768] | 120 | 0 |
  | after dismissal | 18593 | [0,130,1220,2660] | [0,0,1220,2712] | [0,618,1220,768] | 120 | 0 |

## Run: 2026-08-13T09:25:00.511517 — Mode: Resize
- Device: Xiaomi 24090RA29G, Android 16 (API 36)
- Strategy: softInputMode SOFT_INPUT_ADJUST_RESIZE (0x10), repositions on focus: false
- Start LayoutParams.y: 878
- Keyboard geometry signal: no keyboard geometry signal available on this window class (visible display frame identical before focus and with the keyboard up)
- CONTROL — any inset dispatch reached the window: true (known-bad ime() signal, recorded only to confirm it reproduces; gates nothing)
- Window ever received focus (observed via onWindowFocusChanged): true
- Window removed cleanly (no leaked focusable state): true
- Keyboard appeared at all (human): Yes
- Text field visibility while typing (human): Fully visible
- Card visibly jumped or moved when the field was focused (human): Yes
- Resulting placement acceptable to use (human): Yes
- Video paused when window took focus (human): No
- Focus returned to the app underneath after dismissal (human): Yes
- Raw samples:
  | Point | +ms | visibleDisplayFrame | windowBounds | fieldBoundsOnScreen | LayoutParams.y | CONTROL ime() inset bottom |
  |---|---|---|---|---|---|---|
  | before focus | 34 | [130,130,2712,1168] | [0,0,2712,1220] | [130,1018,2712,1168] | 878 | 0 |
  | after field gained focus | 42 | [130,130,2712,1168] | [0,0,2712,1220] | [130,1018,2712,1168] | 878 | 0 |
  | after showSoftInput returned | 938 | [130,130,2712,1168] | [0,0,2712,1220] | [130,1018,2712,1168] | 878 | 0 |
  | after dismissal | 12035 | [130,130,2712,1168] | [0,0,2712,1220] | [130,1018,2712,1168] | 878 | 0 |

## Run: 2026-08-13T10:25:40.613008 — Mode: Two windows: card resizes, pet follows
- Device: Xiaomi 24090RA29G, Android 16 (API 36)
- Strategy: softInputMode SOFT_INPUT_ADJUST_RESIZE (0x10), repositions on focus: false
- Start LayoutParams.y: 1952
- Keyboard geometry signal: no keyboard geometry signal available on this window class (visible display frame identical before focus and with the keyboard up)
- CONTROL — any inset dispatch reached the window: true (known-bad ime() signal, recorded only to confirm it reproduces; gates nothing)
- Window ever received focus (observed via onWindowFocusChanged): true
- Window removed cleanly (no leaked focusable state): true
- Keyboard appeared at all (human): Yes
- Text field visibility while typing (human): Fully visible
- Card visibly jumped or moved when the field was focused (human): Yes
- Resulting placement acceptable to use (human): Yes
- Video paused when window took focus (human): No
- Focus returned to the app underneath after dismissal (human): Yes
- Pet visible above the keyboard while typing (human): Yes
- Pet movement quality (human): Did not move at all
- Pet returned to its original position after the keyboard closed (human): Not tested
- Pet-follow: no reduction ever observed (the card's visible display frame was readable throughout and never shrank, so no keyboard height was derivable and the pet was not moved)
- Pet start LayoutParams.y: 2332
- Pet restored to its original y (observed): not measured (the pet never moved, so there was nothing to restore)
- Pet moves: none (no move was made)
- Layout-driven sampling hit its cap: false (true means the series below is TRUNCATED, not that it stopped changing)
- Raw samples:
  | Point | +ms | visibleDisplayFrame | windowBounds | fieldBoundsOnScreen | LayoutParams.y | CONTROL ime() inset bottom |
  |---|---|---|---|---|---|---|
  | on layout change | 41 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | before focus | 76 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | on layout change | 80 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | after field gained focus | 86 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | after showSoftInput returned | 980 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | after late settle window | 2580 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | on layout change | 3991 | [0,130,1220,2660] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 4007 | [0,130,1220,2660] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 34626 | [0,130,1220,2660] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 34649 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | after dismissal | 36887 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |

## Run: 2026-08-13T10:26:58.657215 — Mode: Two windows: card resizes, pet follows
- Device: Xiaomi 24090RA29G, Android 16 (API 36)
- Strategy: softInputMode SOFT_INPUT_ADJUST_RESIZE (0x10), repositions on focus: false
- Start LayoutParams.y: 1952
- Keyboard geometry signal: no keyboard geometry signal available on this window class (visible display frame identical before focus and with the keyboard up)
- CONTROL — any inset dispatch reached the window: true (known-bad ime() signal, recorded only to confirm it reproduces; gates nothing)
- Window ever received focus (observed via onWindowFocusChanged): true
- Window removed cleanly (no leaked focusable state): true
- Keyboard appeared at all (human): Yes
- Text field visibility while typing (human): Fully visible
- Card visibly jumped or moved when the field was focused (human): Yes
- Resulting placement acceptable to use (human): Yes
- Video paused when window took focus (human): No
- Focus returned to the app underneath after dismissal (human): Yes
- Pet visible above the keyboard while typing (human): Yes
- Pet movement quality (human): Jumped or lagged
- Pet returned to its original position after the keyboard closed (human): No
- Pet-follow: keyboard height derived from a measured frame reduction of up to 1746 px
- Pet start LayoutParams.y: 2332
- Pet restored to its original y (observed): false
- Pet moves:
  | +ms | measured reduction px | moved to y |
  |---|---|---|
  | 3128 | 933 | 1399 |
  | 9040 | 1746 | 586 |
  | 9049 | 933 | 1399 |
- Layout-driven sampling hit its cap: true (true means the series below is TRUNCATED, not that it stopped changing)
- Raw samples:
  | Point | +ms | visibleDisplayFrame | windowBounds | fieldBoundsOnScreen | LayoutParams.y | CONTROL ime() inset bottom |
  |---|---|---|---|---|---|---|
  | on layout change | 59 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | before focus | 113 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | on layout change | 115 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | after field gained focus | 123 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | after showSoftInput returned | 1015 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | after late settle window | 2615 | [0,130,1220,2660] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | on layout change | 3128 | [0,130,1220,1727] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 3144 | [0,130,1220,1727] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 8150 | [0,130,1220,1727] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 8158 | [0,130,1220,1727] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | on layout change | 9040 | [0,130,1220,914] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 813 |
  | on layout change | 9049 | [0,130,1220,1727] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 9958 | [0,130,1220,1727] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 9966 | [0,130,1220,1727] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | on layout change | 11165 | [0,130,1220,1727] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 11168 | [0,130,1220,1727] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 11962 | [0,130,1220,1727] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 11970 | [0,130,1220,1727] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | on layout change | 15066 | [0,130,1220,1727] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 15075 | [0,130,1220,1727] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 15961 | [0,130,1220,1727] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 15977 | [0,130,1220,1727] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | on layout change | 23945 | [0,130,1220,1727] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 27797 | [0,130,1220,1727] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | after dismissal | 31738 | [0,130,1220,1727] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |

## Run: 2026-08-13T11:16:02.24352 — Mode: Two windows: card resizes, pet follows
- Device: Xiaomi 24090RA29G, Android 16 (API 36)
- Strategy: softInputMode SOFT_INPUT_ADJUST_RESIZE (0x10), repositions on focus: false
- Start LayoutParams.y: 1952
- CONTROL — visible display frame signal: no keyboard geometry signal available on this window class (visible display frame identical before focus and with the keyboard up) (round 2 showed this frame reports the resize on some runs and not others, so it is recorded as evidence only and drives nothing)
- CONTROL — any inset dispatch reached the window: true (known-bad ime() signal, recorded only to confirm it reproduces; gates nothing)
- Window ever received focus (observed via onWindowFocusChanged): true
- Window removed cleanly (no leaked focusable state): true
- Keyboard appeared at all (human): Yes
- Text field visibility while typing (human): Fully visible
- Card visibly jumped or moved when the field was focused (human): Yes
- Resulting placement acceptable to use (human): Yes
- Video paused when window took focus (human): No
- Focus returned to the app underneath after dismissal (human): Yes
- Pet visible above the keyboard while typing (human): Yes
- Pet movement quality (human): Jumped or lagged
- Pet returned to its original position after the keyboard closed (human): No
- Pet-follow: keyboard height derived from a content displacement of up to 933 px, confirmed by two consecutive samples
- Pet start LayoutParams.y: 2332
- Pet restored to its original y (observed): true
- Pet moves:
  | +ms | measured displacement px | moved to y |
  |---|---|---|
  | 1867 | 933 | 1399 |
  | 16509 | 0 | 2332 |
- Displacement baseline resets:
  | +ms | cause |
  |---|---|
  | 16485 | service onConfigurationChanged |
  | 16485 | window bounds orientation changed from portrait to landscape |
  | 27008 | service onConfigurationChanged |
  | 27008 | window bounds orientation changed from landscape to portrait |
- Content displacement observations (EVERY observation, including ones that never agreed with the previous sample and therefore never caused a move):
  | +ms | displacement px | agreed with previous | caused a pet move |
  |---|---|---|---|
  | 34 | 0 | false | false |
  | 78 | 0 | true | false |
  | 83 | 0 | true | false |
  | 89 | 0 | true | false |
  | 983 | 0 | true | false |
  | 1853 | 933 | false | false |
  | 1867 | 933 | true | true |
  | 2582 | 933 | true | false |
  | 8044 | 933 | true | false |
  | 8052 | 0 | false | false |
  | 9513 | 933 | false | false |
  | 9525 | 933 | true | false |
  | 10515 | 933 | true | false |
  | 10532 | 0 | false | false |
  | 12812 | 933 | false | false |
  | 12821 | 933 | true | false |
  | 13645 | 933 | true | false |
  | 13653 | 0 | false | false |
  | 16485 | 0 | false | false |
  | 16509 | 0 | true | true |
  | 16524 | 0 | true | false |
  | 19871 | 0 | true | false |
  | 21636 | 0 | true | false |
  | 22893 | 0 | true | false |
  | 24259 | 0 | true | false |
  | 27008 | 0 | false | false |
  | 27071 | 0 | true | false |
  | 27092 | 0 | true | false |
  | 31493 | 0 | true | false |
- Displacement observation recording hit its cap: false (true means the observation table above is TRUNCATED; the follow behaviour kept running regardless)
- Layout-driven sampling hit its cap: true (true means the raw series below is TRUNCATED, not that it stopped changing, and not that the pet stopped following — the cap bounds recording only)
- Raw samples:
  | Point | +ms | orientation | contentTopOnScreen | CONTROL visibleDisplayFrame | windowBounds | fieldBoundsOnScreen | LayoutParams.y | CONTROL ime() inset bottom |
  |---|---|---|---|---|---|---|---|---|
  | on layout change | 34 | portrait | 2510 (field) | [0,130,1220,2660] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | before focus | 78 | portrait | 2510 (field) | [0,130,1220,2660] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | on layout change | 83 | portrait | 2510 (field) | [0,130,1220,2660] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | after field gained focus | 89 | portrait | 2510 (field) | [0,130,1220,2660] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | after showSoftInput returned | 983 | portrait | 2510 (field) | [0,130,1220,2660] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | on layout change | 1853 | portrait | 1577 (field) | [0,130,1220,2660] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 1867 | portrait | 1577 (field) | [0,130,1220,2660] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | after late settle window | 2582 | portrait | 1577 (field) | [0,130,1220,2660] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 8044 | portrait | 1577 (field) | [0,130,1220,2660] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 8052 | portrait | 2510 (field) | [0,130,1220,2660] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | on layout change | 9513 | portrait | 1577 (field) | [0,130,1220,2660] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 9525 | portrait | 1577 (field) | [0,130,1220,2660] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 10515 | portrait | 1577 (field) | [0,130,1220,2660] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 10532 | portrait | 2510 (field) | [0,130,1220,2660] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | on layout change | 12812 | portrait | 1577 (field) | [0,130,1220,2660] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 12821 | portrait | 1577 (field) | [0,130,1220,2660] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 13645 | portrait | 1577 (field) | [0,130,1220,2660] | [0,0,1220,2712] | [0,1577,1220,1727] | 1952 | 0 |
  | on layout change | 13653 | portrait | 2510 (field) | [0,130,1220,2660] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |
  | on layout change | 16485 | landscape | 1018 (field) | [130,130,2712,1168] | [0,0,2712,1220] | [130,1018,2712,1168] | 1952 | 0 |
  | on layout change | 16509 | landscape | 1018 (field) | [130,130,2712,1168] | [0,0,2712,1220] | [130,1018,2712,1168] | 1952 | 0 |
  | on layout change | 16524 | landscape | 1018 (field) | [130,130,2712,1168] | [0,0,2712,1220] | [130,1018,2712,1168] | 1952 | 0 |
  | on layout change | 19871 | landscape | 1018 (field) | [130,130,2712,1168] | [0,0,2712,1220] | [130,1018,2712,1168] | 1952 | 0 |
  | on layout change | 21636 | landscape | 1018 (field) | [130,130,2712,1168] | [0,0,2712,1220] | [130,1018,2712,1168] | 1952 | 0 |
  | on layout change | 22893 | landscape | 1018 (field) | [130,130,2712,1168] | [0,0,2712,1220] | [130,1018,2712,1168] | 1952 | 0 |
  | after dismissal | 31493 | portrait | 2510 (field) | [0,130,1220,2660] | [0,0,1220,2712] | [0,2510,1220,2660] | 1952 | 0 |

