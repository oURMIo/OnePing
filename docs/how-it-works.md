# How it works

[← README](../README.md) · [Usage](usage.md) · [Building](building.md) · [Limitations](limitations.md)

## Silencing a repeat

A normal Android app cannot change the sound of another app's notification before it rings.
OnePing works around this with a `NotificationListenerService` (`service/ListenerService.kt`), which
gets every notification the moment it is posted.

- **The first notification** from a source is left alone. It rings as usual, with the app's own sound.
- **A repeat** inside the quiet period:
  1. is **cancelled** immediately. The system stops its sound and vibration at that moment. Measured
     on a Pixel 8a, the sound is cut off about **30 ms** after it starts;
  2. is re-posted as a **silent copy** on OnePing's own silent channel (`service/MirrorNotifier.kt`).
     The copy keeps the title, the text or messages, the avatar, the tap action and the action buttons,
     including inline reply.

## Decision logic (`engine/`)

1. **Filtering** (`NotificationParser`). OnePing never touches:
   - group summaries, ongoing and foreground-service notifications;
   - media notifications;
   - the categories call, alarm, navigation, transport, progress, service, system, stopwatch and
     location sharing;
   - its own notifications.
2. **Source key**: `"<package>|<id>"`, so the same name in two apps gives two separate sources.
   `<id>` is:
   - the conversation **shortcut id** if the app sets one (Telegram, WhatsApp and Signal do, and it
     is exact per chat);
   - otherwise a name: the conversation title (groups), the sender of the newest incoming
     `MessagingStyle` message, or `android.title`.
3. **New message or just an update?** Apps often re-post the same notification.
   - It counts as a new message only if the newest `MessagingStyle` message has a later timestamp, or
     the message count went up.
   - Without `MessagingStyle`, a change in the text or in `Notification.when` counts.
   - Your own replies never count.
4. **Verdict** for a new message on an audible channel (importance ≥ DEFAULT):

   | Condition | Result |
   |---|---|
   | The source's rule is "Always silent" | silence |
   | The source's rule is "Always alert" | ring |
   | Less time than the quiet period has passed since this source last rang | silence |
   | Otherwise | ring, and the source's timer restarts |

   A re-posted or edited notification during the quiet period is silenced too.
5. **Speed.** The engine is synchronous and works from memory, so a repeat is cancelled within
   milliseconds. State (timers, sources, log) is saved to DataStore in the background, so it survives
   restarts.

## Timer resets (optional)

With **Reset the timer when I've seen it** turned on, opening or swiping away a notification
(`REASON_CLICK` / `REASON_CANCEL`) clears that source's timer, so the next message rings right away.
A second switch also counts the app clearing its own notification (`REASON_APP_CANCEL`, for example
when the chat was read on another device).

## Privacy

The app has no `INTERNET` permission and no analytics. Backups are disabled
(`android:allowBackup="false"`). Everything stays in the app's private storage on the device.
