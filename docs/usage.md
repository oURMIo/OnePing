# Usage

[← README](../README.md) · [How it works](how-it-works.md) · [Building](building.md) · [Limitations](limitations.md)

## Tabs

- **Setup**: whether the service is running, plus buttons to grant notification access, notifications
  and unrestricted battery.
- **Settings**:
  - **Quiet period**: 30 s – 60 min (default 5 min). Set it with the slider or type an exact number
    of seconds.
  - **Reset the timer when I've seen it**: see [How it works → Timer resets](how-it-works.md#timer-resets-optional).
  - **Apps**: an on/off switch for each app that has posted a notification, and whether new apps are
    included automatically. System UI, phone and clock are off by default.
- **Sources**: every person or chat seen, grouped by app. Tap one to choose:
  - *Use global quiet period*;
  - *Custom quiet period*;
  - *Always alert (no cooldown)*;
  - *Always silent*.
- **Log**: the last 50 decisions. Each entry shows who it was, which app, and whether it rang, was
  silenced (and why), or reset the timer.

## Testing

1. Set the quiet period to **1 minute**.
2. Ask friend **A** to send 5 messages in a row. You should hear one sound. The Log shows "Alerted"
   once and "Silenced (cooldown)" four times, and the shade shows a silent OnePing copy of A's chat.
3. While A's minute is running, ask friend **B** to write. B rings.
4. After the minute is over, a new message from A rings again.
5. Set A to **Always silent** on the Sources tab. A's next message is silent.

Without a second person, you can simulate a chat over adb. These notifications come from "Shell":

```bash
adb shell "cmd notification post -S messaging --conversation 'A' --message 'A:one' chatA m1"
adb shell "cmd notification post -S messaging --conversation 'A' --message 'A:one' --message 'A:two' chatA m2"
adb shell "cmd notification post -S messaging --conversation 'B' --message 'B:hey' chatB m3"
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| Setup shows "Not working yet" | Grant the missing item on the Setup tab |
| "Notification access" switch is greyed out | App info → ⋮ → **Allow restricted settings** |
| Stopped working after an update or a reboot | Open the app. Setup → **Reconnect** |
| Log says "can't post copies, NOT silenced" | Allow notifications for OnePing |
| Repeats still buzz on a fitness band | Turn off OnePing notifications in the band's app |

Debug output: `adb logcat -s OnePing`.
