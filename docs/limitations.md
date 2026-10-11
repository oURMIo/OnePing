# Limitations

[← README](../README.md) · [How it works](how-it-works.md) · [Usage](usage.md) · [Building](building.md)

## Known trade-offs

- **The start of a repeat's sound can be heard.** A repeat is cancelled about 30 ms after its sound
  starts. That is almost inaudible, but not zero.
- **Silenced messages live in OnePing's card**, not in the app's own notification.
  - The card can't show the source app's small icon (it shows the app name and the avatar instead).
  - After you reply from the card, the card is not updated.
  - The card history is kept in memory, so it is lost if Android restarts OnePing.
- **Fitness bands and watches** that mirror notifications (Mi Fitness, Zepp, …) may receive the
  original before it is cancelled, and they receive the cards too.
- **Same name, same timer.** If an app sets no conversation shortcut id, the source is identified by
  its display name. Two contacts with the same name in that app then share a timer.
- **Service unbinding.** After an app update Android sometimes unbinds notification listeners. Open
  the app and tap **Reconnect**.

## Why not the system notification assistant?

Android's *notification assistant* (`NotificationAssistantService`) sees notifications **before** they
ring and could make them silent cleanly. It cannot be used by a normal app:

- the assistant must hold `REQUEST_NOTIFICATION_ASSISTANT_SERVICE`, a
  `signature|privileged|role` permission;
- that permission comes with the `SYSTEM_NOTIFICATION_INTELLIGENCE` role, which only preinstalled
  system apps qualify for;
- `cmd notification allow_assistant` is silently ignored, and `cmd role add-role-holder` fails with
  "Package does not qualify".

This was verified on a Pixel 8a with Android 17. Without root, cancel + silent card is the only way.
