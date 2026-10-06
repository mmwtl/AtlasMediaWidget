# Player buttons

Besides the usual previous / pause / next, many Android players publish their own buttons in their
media session: like, repeat, shuffle, playlist navigation and so on. Atlas Media Widget shows these
buttons on the card, both in the overlay and in the system widget.

The buttons use the player's own icons and behave as in the player's notification: the widget does
not know what each button does and simply passes the tap to the player. No per-app support is
needed; the player only has to publish such buttons.

> Screenshots were taken on an Android 11 emulator (1440×1920) with AIMP. The Russian screenshots
> show the Russian UI; labels are translated below.

## Where they appear

The buttons share one pill in the top-right corner of the card, opposite the source pill, in the
order the player publishes them.

<p align="center">
  <img src="images/player-buttons-all.webp" width="660"
       alt="Online card with five AIMP buttons: repeat, previous group, favorites, next group, shuffle">
</p>

After a tap the player changes the icon itself and the card shows the new state: for example, the
outlined heart becomes filled once the track is added to favorites.

There are no buttons:

- for radio, where the slot holds «Избранное» (Favorites);
- for stock OneOS sources: USB, Bluetooth and CarPlay do not publish them;
- for players that add a like button only to their notification, not to the media session.

## Settings

Open **Atlas Media Widget → «Карточка» (Card) → «Кнопки плеера» (Player buttons)**. Start
playback in the player you want to configure: the list always shows the player that is playing now.

<p align="center">
  <img src="images/player-buttons-settings.webp" width="660"
       alt="Player buttons block: «До 2» slider and the AIMP button list with both group buttons hidden">
</p>

### How many buttons

The **«До N»** (Up to N) slider sets how many buttons fit on the card: 0 to 5, 2 by default. 0
hides the pill. The slider applies to every player.

The card shows the first N visible buttons in the player's order. A visible button beyond the limit
is marked **«— не помещается»** (does not fit) in the list. A narrow card or a large top-row font can
fit fewer buttons than requested; the pill never covers the source pill.

### Which buttons to hide

Below the slider are the playing player's name and all of its buttons with icons:

- **checked**: the button is shown;
- **unchecked**: the button is hidden for this player.

Hidden buttons are remembered per app: hiding repeat for AIMP does not affect Yandex Music. New
buttons a player adds after an update appear right away and can be hidden the same way.

**«Показать все кнопки этого плеера»** (Show all buttons of this player) clears every hidden button
of the current player.

### Result

In the example above, AIMP's group buttons are hidden and the limit is 2. The card keeps repeat and
favorites, while shuffle does not fit:

<p align="center">
  <img src="images/player-buttons-result.webp" width="660"
       alt="Online card with two AIMP buttons: repeat and favorites">
</p>

To keep only the like button, also uncheck «Repeat All»: with the same limit the card shows
favorites and shuffle, and with a limit of 1 only favorites.

## Player examples

What the players tested on the emulator publish:

| Player | Buttons in order |
|---|---|
| AIMP | Repeat All, Previous group, Add to favorites (like), Next group, Normal (shuffle) |
| Yandex Music | Dislike, Like |

Button names come from the player and often include the state (for example, «Like Not selected»).

The full list of any player's buttons with their ids is in the diagnostics:
**«Система» (System) → «Открыть диагностику OneOS» → «Запустить проверку OneOS»**, section
«Active Media Sessions».

## Troubleshooting

- **No buttons at all.** Check that the slider is not at 0 and the source is Online, then check in
  the diagnostics whether the player publishes buttons. If the list is empty, the player does not.
- **A button is missing from the card.** It is either hidden (unchecked) or beyond the limit; the
  settings list shows which. Raise the limit or hide the unwanted buttons before it.
- **A button disappeared after a tap.** Some players replace a button with a new one after a tap
  (for example, «add» with «remove»). The new button is shown unless hidden; if it is not visible,
  check the limit.

Player button settings are part of the settings backup (System tab).
