<p align="center"><img src="docs/logo.png" width="160" alt="OnePing logo"></p>

# OnePing

One ping per burst. When someone sends you several messages in a row, **only the first notification
rings**. The rest arrive silently until that person's quiet period runs out, and **other people still
ring normally**.

- Works with any app (Telegram, WhatsApp, Signal, SMS, mail…). You don't change any app's settings.
- A separate timer for each person or chat in each app, 30 s – 60 min (default 5 min).
- Silenced messages are collected in **one silent card per person**. Pull it down to read the whole
  burst, reply, mute the person for an hour or make them always ring.
- Big mode buttons: **One ping** / **Everyone rings** / **Silence for 15 min**. There is also a
  **Quick Settings tile** that pauses OnePing for an hour.
- No internet permission, no analytics. All data stays on the phone.
- Android 10+ (`minSdk 29`). Tested on a Google Pixel 8a with Android 17.

## Install on your phone

1. **Build the APK** (JDK 17+ and the Android SDK are required; details in [Building](docs/building.md)):
   ```bash
   ./gradlew assembleDebug          # Windows: .\gradlew.bat assembleDebug
   ```
2. **Turn on USB debugging** on the phone: Settings → About phone → tap "Build number" 7 times, then
   Settings → System → Developer options → **USB debugging**. Connect the cable and allow debugging.
3. **Install it:**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
4. **Open OnePing.** The **Finish setup** card on the home screen asks for:
   - **Notification access.** If it is greyed out ("Restricted setting"), go to
     App info → ⋮ → **Allow restricted settings** and try again.
   - **Notifications.** Silenced messages are collected in a silent card.
   - **Unrestricted battery.** This keeps Android from killing the service.

Then ask a friend to send you 5 messages in a row: you should hear only one.

## Documentation

| | |
|---|---|
| [How it works](docs/how-it-works.md) | How repeats are silenced, how sources and new messages are detected |
| [Usage](docs/usage.md) | Home and People screens, the silent card, the tile, testing, troubleshooting |
| [Building](docs/building.md) | Requirements, build commands, source layout |
| [Limitations](docs/limitations.md) | Known trade-offs and why the system "assistant" approach isn't used |
