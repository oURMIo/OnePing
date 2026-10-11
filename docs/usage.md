# Usage

[← README](../README.md) · [How it works](how-it-works.md) · [Building](building.md) · [Limitations](limitations.md)

## Home

- **Mode**: three big buttons at the top.
  - **One ping**: normal mode. Only the first message of a burst rings.
  - **Everyone rings**: OnePing steps aside and every notification rings, until you switch back.
  - **Silence**: everything OnePing handles is silent for 15 minutes, then it switches back to
    One ping by itself. Tapping it again restarts the 15 minutes. Silenced messages still go to
    the silent cards.
- **Finish setup**: shown only while something is missing (notification access, notifications,
  unrestricted battery, or the service needs a **Reconnect**).
- **Quiet period**: chips for 1 / 2 / 5 / 15 / 30 min, or **Other…** for any value from 30 s to 60 min.
- **Reset when I've seen it**: opening, dismissing or reading a chat makes its next message ring.
- **Today**: how many repeats were silenced, plus each person's message count and how often they rang.
- **Recent activity** (collapsed): the last 50 decisions, for troubleshooting.

## People

- Everyone seen, with the app they wrote from. Tap someone to choose:
  - *Default quiet period*;
  - *Own quiet period*;
  - *Always ring*;
  - *Always silent*;
  - **Mute for 1 hour**.
- **Apps** (collapsed, at the bottom): a switch for each app, and whether new apps are handled
  automatically.

## Silent card

Silenced messages from a person are collected in one card in the shade. Pull it down to see the whole
burst. The buttons are **Reply**, **Mute 1 h** and **Always ring**. Details are in
[How it works → The silent card](how-it-works.md#the-silent-card).

## Quick Settings tile

To add the tile, pull down the shade, tap ✏️ and drag **OnePing** into your tiles. Tapping it pauses
OnePing for 1 hour, and tapping again resumes it.

## Testing

1. Set the quiet period to **1 minute**.
2. Ask friend **A** to send 5 messages in a row. You should hear one sound. Recent activity on Home shows "Rang"
   once and "Silenced" four times. The shade shows A's silent card with all 5 messages.
3. While A's minute is running, ask friend **B** to write. B rings.
4. After the minute is over, a new message from A rings again.
5. Set A to **Always silent** on the People screen. A's next message is silent.

Without a second person, you can simulate a chat over adb. These notifications come from "Shell":

```bash
adb shell "cmd notification post -S messaging --conversation 'A' --message 'A:one' chatA m1"
adb shell "cmd notification post -S messaging --conversation 'A' --message 'A:one' --message 'A:two' chatA m2"
adb shell "cmd notification post -S messaging --conversation 'B' --message 'B:hey' chatB m3"
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| Home shows "Finish setup" | Grant the missing item right there |
| "Notification access" switch is greyed out | App info → ⋮ → **Allow restricted settings** |
| Stopped working after an update or a reboot | Open the app and tap **Reconnect** |
| Activity says "can't show cards, NOT silenced" | Allow notifications for OnePing |
| Cards buzz on a fitness band | Turn off OnePing notifications in the band's app |

Debug output: `adb logcat -s OnePing`.
