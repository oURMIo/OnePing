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
  2. is added to that source's **silent card** (`service/SilentCards.kt`).

## The silent card

- **One card per source.** When a repeat is silenced, every original notification from that source
  is cancelled. The card then shows the conversation.
- **Its own history.** OnePing keeps the last 25 messages of each source, so the card shows the whole
  burst even if the app itself only shows its latest few messages.
- **Contents.** The card is a real conversation (`MessagingStyle`) with the person's name and avatar.
  The sub-text gives the app and the counts, e.g. "Telegram · 5 messages, 4 silenced".
- **Buttons:** **Reply** (if the app offers it), **Mute 1 h** and **Always ring**.
- **Grouping.** Cards from the same app are grouped in the shade.
- **When it goes away:**
  - Opening or dismissing the card, or reading the chat in the app, removes the card and its history.
  - When the source rings again after its quiet period, the card hides and the app's own notification
    takes over.

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
   | Mode "Everyone rings", or paused from the tile | ring |
   | Mode "Silence" (15 min) | silence |
   | The source's rule is "Always silent", or it is muted for 1 h | silence |
   | The source's rule is "Always ring" | ring |
   | Less time than the quiet period has passed since this source last rang | silence |
   | Otherwise | ring, and the source's timer restarts |

   A re-posted or edited notification during the quiet period is silenced too.
5. **Speed.** The engine is synchronous and works from memory, so a repeat is cancelled within
   milliseconds. State (timers, sources, log) is saved to DataStore in the background, so it survives
   restarts.

## Timer resets (optional)

With **Reset when I've seen it** turned on, the source's timer is cleared when you:

- open or swipe away its notification or card (`REASON_CLICK` / `REASON_CANCEL`);
- read the chat, so the app clears its own notification (`REASON_APP_CANCEL`).

The next message from that source then rings right away.

## Privacy

The app has no `INTERNET` permission and no analytics. Backups are disabled
(`android:allowBackup="false"`). Everything stays in the app's private storage on the device.
