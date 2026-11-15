# Build and verification guide

## Requirements

- An Apple Silicon Mac. The setup script downloads macOS Android tools; other
  host platforms need their own SDK tool paths and are not verified here.
- Python 3 and an existing JDK whose `javac` supports `--release 8`.
- `curl` for development-tool downloads and Git for obtaining the source.
- Network access during setup. The Android app itself has no Internet permission.

The default JDK location is the Homebrew OpenJDK installation. If yours differs,
set `SNAP_JAVA_HOME` to the JDK home before running the commands. The build uses
Android API 35, Java 8 bytecode, and an Android API 26 minimum. No Android Studio,
Gradle, or NDK installation is required by these scripts.

## Obtain the source and tools

Clone the repository:

```sh
git clone https://github.com/Akil-0x1/snap-explorer-android.git
cd snap-explorer-android
python3 restore_toolchain.py
```

Tool downloads are stored in `android-mobile/.tools/`, which is excluded from
Git. The setup checks SDK archives against pinned checksums and Java libraries
against checksums obtained from Maven Central over HTTPS. Maven checksums are
fetched at setup time rather than pinned independently in the repository; this
is a build-supply-chain limitation, not a cryptographic provenance guarantee.

The committed fixtures are sufficient to run the tests. To regenerate the
synthetic ZIP and image fixtures, optionally run `python3 restore_fixtures.py`.
ZIP timestamps can change during regeneration, producing harmless fixture diffs.

## Host tests

```sh
python3 android-mobile/test.py
```

This compiles and runs Java fixtures and production SQL checks under Python's
SQLite. It reads only committed synthetic fixtures. Large-file regression tests
include a restricted Java heap. No device or signing key is required.

## Compile an unsigned verification APK

```sh
python3 android-mobile/build.py
```

The historical output filename is:
`android-mobile/artifacts/Snap-Explorer-Recovered-0.4.2-UNSIGNED.apk`.
It is generated from the current source despite that filename. The script uses
the base manifest's original package identity; it is not the signed Recovery
release. Do not install it as an update. It exists to verify compilation,
resource packaging, DEX generation, alignment, and the absence of permissions.
The generated `.build/` directory is replaced on each build. Source and signing
keys are not removed. Do not run `build.py` and `test.py` concurrently because
they share generated paths.

## Signed Recovery builds

```sh
python3 android-mobile/build_recovery.py
```

This packages the current Recovery identity and version and requires the
established `recovery.p12` and `password` files in
`android-mobile/.keys-recovery/`. These files are private, not included in GitHub.
The script can restore the maintainer's existing local backup, but refuses to
create a replacement key. It produces a signed APK and verification/checksum
files under `android-mobile/artifacts/`.

A fresh clone without those private files can run host tests and compile the
unsigned artifact; it cannot sign an update for installed Recovery users.
A contributor's independently signed build needs an independent installation
identity and cannot replace an app signed with another key. Do not uninstall
an existing app to bypass a signature mismatch or commit private signing files.

## Device checks and screenshots

Instrumentation source is in `android-mobile/tests/device/`. Its build script
requires compiled app classes and the same signing identity as the Recovery app.
Existing maintainers can build it with:

```sh
python3 android-mobile/tests/device/build_smoke.py
```

Run only on a disposable emulator containing synthetic data. Several suites
replace the app's entire imported archive. Use an explicit emulator serial for
every device command. These tests must not target a personal phone.

`DemoScreens` is an optional screenshot fixture. It requires an emulator and the
explicit instrumentation argument `-e demo synthetic-only`. It replaces the
emulator app's data with the bundled sample and fictional project messages, then
captures three real screens to `/data/local/tmp/snap-demo-*.png`. It does not
modify the distributed APK or read external exports.

## Validation scope

A clean tracked-source checkout was compiled and its host tests were run using
already-installed, project-local tools. That check reused the existing SDK and
JAR downloads rather than testing a fresh network download. Native screenshots
were captured on an Android 11 ARM64 emulator using the signed release APK.
Device behaviour and codec support still need validation on the intended phone.

There is no continuous-integration or automated dependency scanning configured.
The optional Mac inspector and host tests use `org.json:json:20260719`, which is
listed as patched for the [numeric resource-exhaustion advisory](https://github.com/advisories/GHSA-ghc9-756g-vrcv).
That JAR is not bundled into the Android APK. Re-run toolchain setup and rebuild
the inspector to use it locally. Rebuilding retires older generated JSON JARs
from the inspector's wildcard classpath; merely changing the source checkout
does not update an existing inspector binary. Older source tags and archived
tool copies may still reference the previous dependency.
