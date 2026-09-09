# UniRemote

An Android app that turns your phone into a TV remote. The phone registers itself as a
Bluetooth Classic HID peripheral — a keyboard + mouse + consumer-control composite device —
so the TV discovers it the way it discovers any BT keyboard: no app on the TV, no 6-digit
pairing code. A network transport (Roku ECP, Samsung Tizen, LG webOS, Wake-on-LAN) covers
the TVs Bluetooth cannot, and cold power-on.

Design and architecture live in [PLAN.md](PLAN.md).

## Building

Requires JDK 21 and the Android SDK (compileSdk 35).

```sh
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # JVM unit tests (report encoding, transport, session)
./gradlew lintDebug            # static analysis
```

## Gradle daemon JVM

`gradle/gradle-daemon-jvm.properties` pins the Gradle daemon to Java 21. Daemon JVM
criteria take precedence over `JAVA_HOME` and `org.gradle.java.home`, so a global
`~/.gradle/gradle.properties` entry cannot drag the daemon onto another JVM for this
project.

After changing the file, stop the running daemon first:

```sh
./gradlew --stop
```

One-off override for a build (skip the checked-in pin):

```sh
./gradlew -Dorg.gradle.java.home=/path/to/a/jdk-21
```

Regenerate the file instead of hand-editing:

```sh
./gradlew updateDaemonJvm --jvm-version=21
```

Notes:

- The **launcher** JVM (the `./gradlew` process itself) still comes from `JAVA_HOME` /
  `PATH`; only the daemon is pinned. Gradle 9.x runs fine on newer launchers.
- Android Studio sets the build JVM via its own "Gradle JDK" setting
  (Settings → Build, Execution, Deployment → Build Tools → Gradle). Keep it on a JDK 21
  for this project.
