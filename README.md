<p align="center"><img src="docs/logo.png" width="160" alt="OnePing logo"></p>

# OnePing

An Android app (Kotlin + Jetpack Compose) that stops any app from ringing for every message in a burst.
When one person (or chat) sends several messages in a row, **only the first notification makes a
sound or vibration**. Later notifications from that **same source** arrive silently until that source's
quiet period runs out. **Other sources still ring normally.** If A sends 5 messages in a minute you
hear one, and if B writes during that minute you hear B.

- You don't change any app's notification settings. OnePing works at the system level.
- Works for any app: Telegram, WhatsApp, Signal, SMS, mail, etc.
- Target device: Google Pixel 8a (Android 15/16). `minSdk 29`, `targetSdk 35`.
- No internet permission, no analytics. All data stays on the device (DataStore, backups disabled).

## How it silences notifications

A normal Android app cannot change the sound of another app's notification before it rings.
OnePing works around this.

`ListenerService` (a `NotificationListenerService`) gets every notification as soon as it is posted.
For a repeat inside the quiet period it does two things:

1. It **cancels** the notification immediately. The system stops the sound and vibration at that
   moment. Measured on a Pixel 8a: the sound is cut off about **30 ms** after it starts.
2. It re-posts a **silent copy** on OnePing's own silent channel. The copy keeps the title, the text
   or messages, the avatar, the tap action and the action buttons, including inline reply.

The first notification from a source is left alone and rings as usual, with the app's own sound.

> Why not the system "notification assistant", which could silence before ringing? That role needs
> `REQUEST_NOTIFICATION_ASSISTANT_SERVICE` (`signature|privileged|role`) and the
> `SYSTEM_NOTIFICATION_INTELLIGENCE` role, which only preinstalled system apps qualify for.
> It cannot be granted to a sideloaded app without root. This was verified on a Pixel 8a with Android 17.

## Decision logic (`engine/`)

1. **Parsing** (`NotificationParser`): the following are ignored:
   - group summaries, ongoing and foreground-service notifications,
   - media notifications and the categories call, alarm, navigation, transport, progress, service,
     system, stopwatch and location sharing,
   - OnePing itself.
2. **Source key**: `"<package>|<id>"`, so the same name in two apps gives two separate sources. `<id>` is:
   - the conversation **shortcut id** if the app sets one (Telegram, WhatsApp and Signal do; it is exact per chat),
   - otherwise the name: the conversation title (groups), the sender of the newest incoming
     `MessagingStyle` message, or `android.title`.
3. **New message or just an update?** Apps often re-post the same notification. An update counts as a
   new message only if the newest `MessagingStyle` message has a later timestamp, or the message count
   went up. Without MessagingStyle it falls back to text or `Notification.when` changes. Your own replies
   never count.
4. **Verdict** for a new message on an audible channel (importance ≥ DEFAULT):
   - the source's rule is "always silent" → silence,
   - the source's rule is "always alert" → ring,
   - less time than the quiet period has passed since this source last rang → silence,
   - otherwise → ring, and the timer for this source starts.

   A re-posted or edited notification during the quiet period is also silenced.
5. The engine is synchronous and works from memory, so a repeat is cancelled within milliseconds.
   State is saved to DataStore in the background.

**Resets (optional).** When you open or swipe away a notification (`REASON_CLICK` / `REASON_CANCEL`),
that source's timer is cleared, and the next message rings right away. Optionally this also happens when
the app clears the notification itself (`REASON_APP_CANCEL`, e.g. the chat was read).

## Source layout

```
app/src/main/java/
└── com/oneping/
    ├── OnePingApp.kt                  Application: engine, service status
    ├── data/                          models, JSON codec, DataStore repository
    ├── engine/
    │   ├── NotificationParser.kt      notification → source key + message state
    │   └── CooldownEngine.kt          new-message detection, per-source cooldown, log
    ├── service/
    │   ├── ListenerService.kt         judging, cancel + copy, resets, seeding after restart
    │   └── MirrorNotifier.kt          silent copies
    └── ui/                            Setup / Settings / Sources / Log (Compose)
```

## Building

You need:
- JDK 17 or newer,
- Android SDK with platform `android-37` (compileSdk),
- internet access for the first build only, to download dependencies.

```bash
# Windows (PowerShell)
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
.\gradlew.bat assembleDebug

# macOS / Linux / Git Bash
./gradlew assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/app-debug.apk`.

## Installing and setup

1. On the phone, enable Developer options, then **USB debugging**. Connect the phone and accept the prompt.
2. Install the app:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
3. Open OnePing, go to the **Setup** tab, then **Notification access** → Grant.
   If the switch is greyed out ("Restricted setting"): App info → ⋮ → **Allow restricted settings**.
4. Allow **post notifications** (needed for the silent copies) and **unrestricted battery**.

## Settings

- **Quiet period**: 30 s to 60 min (default 5 min), set with a slider or an exact number of seconds.
- **Sources** tab: every person or chat seen, per app. Each can use the default, its own quiet period,
  always alert, or always silent.
- **Apps**: a per-app on/off switch, and whether newly seen apps are included automatically.
  System UI, phone and clock are off by default.
- **Reset on open/dismiss**: described above.
- **Log**: the last 50 decisions (source, app, rang or silenced and why, which mechanism).

## Testing

1. Set the quiet period to 1 minute.
2. Ask friend **A** to send 5 messages in a row. Expected: one sound. The Log shows "Alerted" once and
   "Silenced (cooldown)" four times, and the shade shows a silent OnePing copy of A's chat.
3. While A's minute is running, ask friend **B** to write. B rings.
4. After 1 minute, a new message from A rings again.
5. Set A to "Always silent" on the Sources tab. A's next message is silent.

Debug logs: `adb logcat -s OnePing`.

## Limitations

- The very beginning of a repeat's sound (about 30 ms) can still be heard.
- Copies can't show the original app's small icon or action icons. After an inline reply
  from a copy, the copy is not updated.
- Fitness bands and watches that mirror notifications (Mi Fitness, Zepp, etc.) may receive the
  original before it is cancelled, and will receive the copies too. Turn OnePing off in the band
  app if the copies buzz there.
- Without a shortcut id the key is the display name, so two contacts with the same name in one app
  share a timer.
- After an app update Android sometimes unbinds the services. Open the app: the Setup tab shows the
  status and has a "Reconnect" button.
