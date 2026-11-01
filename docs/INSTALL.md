# Installing Home Studio on your phone

Home Studio is signed with a private release key and installed over Wi-Fi with `adb`,
run from the build box ("jw"). Your phone and jw must be on the same Wi-Fi network.

## 1. Remove the old app (one time)

The app was previously called AlarmAPI (`com.brainwash.alarm`). Home Studio is a
different app ID (`com.jamilcpu.homestudio`), so it installs alongside the old one.
Uninstall the old one so you do not end up with two:

- Long-press the old AlarmAPI icon, then Uninstall, or
- from jw: `D:\android-sdk\platform-tools\adb.exe uninstall com.brainwash.alarm`

## 2. Enable wireless debugging on the phone (one time)

1. Settings, About phone, tap **Build number** 7 times to unlock Developer options.
2. Settings, System, Developer options, turn on **Wireless debugging**.
3. Open Wireless debugging and tap **Pair device with pairing code**. Keep that
   screen open: it shows a pairing IP:port and a 6-digit code.

## 3. Pair and connect from jw

Pairing and connecting use two different ports.

```
D:\android-sdk\platform-tools\adb.exe pair <pairing-ip:pairing-port>
```

Enter the 6-digit code when asked. Then go back to the main Wireless debugging
screen, which shows the connect address (a different port), and run:

```
D:\android-sdk\platform-tools\adb.exe connect <ip:port>
D:\android-sdk\platform-tools\adb.exe devices
```

The phone should be listed as `device`.

## 4. Build and install the signed release APK

On jw, in the repo clone (`D:\dev\home-studio`):

```
gradlew.bat assembleRelease
D:\android-sdk\platform-tools\adb.exe install -r app\build\outputs\apk\release\app-release.apk
```

`-r` keeps the app's data, so updates install over the top as long as they are
signed with the same key.

Optionally check the signature first:

```
D:\android-sdk\build-tools\35.0.0\apksigner.bat verify --print-certs app\build\outputs\apk\release\app-release.apk
```

## 5. Back up the signing key (important)

The release key lives only on jw:

- Keystore: `%USERPROFILE%\.android\home-studio.jks` (alias `homestudio`)
- Passwords: `%USERPROFILE%\.gradle\gradle.properties`
  (`HOME_STUDIO_STORE_FILE`, `HOME_STUDIO_STORE_PASSWORD`, `HOME_STUDIO_KEY_ALIAS`,
  `HOME_STUDIO_KEY_PASSWORD`)

**If you lose the keystore or its passwords, future versions cannot be installed over
the existing app.** Android refuses an update signed by a different key, so you would
have to uninstall and lose the app's data (server URL, settings).

Copy both the `.jks` file and the four property lines to a safe place that is not this
repo and not public, for example a password manager attachment or an encrypted drive.
Never commit either to git.

## Without the release key

If the `HOME_STUDIO_*` properties are not set (for example in CI), `assembleRelease`
prints a warning and signs with the debug key. Such an APK installs fine, but it cannot
be updated over a release-signed install, and vice versa.
