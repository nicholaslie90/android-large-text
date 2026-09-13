# BigSign

A phone-sized signboard. Type something, and it fills the whole screen — for
airport pickups, "BACK IN 5 MIN" on a shop door, or holding a name up across a
crowded room. Works in portrait and landscape.

<!-- Screens: display mode is just the text; tapping it reveals the controls. -->

## Using it

- **Tap the sign** to open the controls. Tap it again, or press **SHOW SIGN**,
  to go back to the fullscreen sign.
- **Display mode** hides the status and navigation bars and keeps the screen
  awake. Brightness is left alone — turn it up yourself if you are outdoors.
- **Rotating the phone** just re-lays-out the sign — nothing is lost, and the
  text re-fits itself to the new shape.

### Controls

| Control | What it does |
| --- | --- |
| Text field | What the sign says. Newlines are kept. |
| Recent | The last 20 messages, newest first. Tap to reuse, long-press to delete. Each chip previews the colours it was last shown in. |
| Size | `AUTO` (far left) fills the screen; drag right for a fixed 15–312sp. |
| Text / Background | Ten preset colours each, plus **INVERT** to swap them. |
| BOLD / SANS / CENTER | Weight, typeface (sans, serif, mono) and alignment. |
| Scroll across screen | Turns the sign into an LED-style ticker, with a speed slider in dp per second. |

Everything is saved automatically — the app reopens showing exactly what it
showed last, or, if that has been cleared, the newest message in Recent.

## Building

Needs a JDK and the Android SDK build-tools. No Gradle, no AndroidX, no
dependencies, nothing downloaded at build time.

```sh
brew install openjdk@17     # if you don't have a JDK
./build.sh                  # -> dist/BigSign.apk
```

`build.sh` runs the tests, then `aapt2` → `javac` → `d8` → `zipalign` →
`apksigner`. It picks the newest installed build-tools and platform. Override
`ANDROID_HOME`, `JAVA_HOME` or `BIGSIGN_KEYSTORE` if yours live elsewhere.

The signing key is generated on first build at `~/.config/bigsign/bigsign.jks`
and is deliberately **not** in this repo. Keep it: rebuilding on a machine
without it produces a differently-signed APK, which Android will refuse to
install over the existing one until you uninstall first.

### Tests

```sh
./test.sh
```

`SignStyle`, `History` and `TextFitter` carry no Android imports, so they run on
the host JVM with no test framework — see `test/`. The drawing, the immersive
flags and the rotation behaviour are not covered; those need a device.

## Installing

Over USB or wireless debugging:

```sh
adb install -r dist/BigSign.apk
```

Or send it to the phone:

```sh
tailscale file cp dist/BigSign.apk <device-name>:
```

Then open it from the notification. On vivo/OriginOS you will have to allow
"install unknown apps" for whichever app opens the file, and dismiss a warning
about the developer not being verified.

## How it fits together

```
SignStyle    one sign's text + styling; serializes to a single string
History      the recents list: dedupe, cap at 20, newest first
TextFitter   wrapping, and the binary search for the largest size that fits
SignView     measures glyphs with Paint, draws the text or scrolls the ticker
Prefs        SharedPreferences storage
MainActivity the two modes, the control panel, the recents chips
```

The first three hold no Android types, which is what makes them testable on the
host. `TextFitter` takes its measurements through an interface that `SignView`
backs with a real `Paint`, and the tests back with a stub where every glyph is
half an em wide.

Auto-fit keeps words whole. Splitting one would often allow a larger size —
`0819` as `08` over `19` fills far more of a narrow portrait screen — but a sign
that does that is unreadable, so the size gives way instead. Text wraps at
spaces, and a single word too long to fit its own line even at the smallest size
is the one case that still gets broken between characters.
