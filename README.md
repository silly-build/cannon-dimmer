# Cannon Dimmer

*A beautiful QOL plugin for those niche players or moments that require hours of reloading a cannon on a second monitor with one simple objective; keep your cannon loaded with minimal distraction.*

Cannon Dimmer is a RuneLite plugin that dims the game screen while your dwarf multicannon is placed and loaded.

It is designed as a visual overlay to make cannonball status easier to monitor at a glance. Depending upon your use case, many different delays and rules can be set to keep your screen dimmed exactly when you want.


## Features

* Dims the game screen while your cannon is placed and loaded
* Shows cannonballs remaining on screen
* Optional ETA until your configured cannonball threshold is reached
* Toggle hotkey to quickly enable or disable the dimmer
* Configurable full and partial dim opacity
* Supports 30, 45, and 60 cannonball capacity setups depending on your Combat Achievement tier
* Two dimming modes (gradual and constant)
* Multiple adjustable buffers




---

### Dimming Mode 1: Full until zero dim

The screen stays at full dim opacity until the configured zero dim cannonball value is reached.

Example:

* Full dim opacity: 190
* Zero dim cannonballs: 10

Result:

* 11 or more cannonballs: full dim
* 10 or fewer cannonballs: no dim


### Dimming Mode 2: Gradual to partial

The screen is fully dimmed at the configured full dim cannonball value, then gradually fades down to the configured partial dim opacity at the zero dim cannonball value. From there, it fades to zero as cannonballs reach 0.

Example:

* Full dim opacity: 190
* Partial dim opacity: 80
* Full dim cannonballs: 60
* Zero dim cannonballs: 15

Result:

* 60 or more cannonballs: full dim
* 60 to 15 cannonballs: gradually fades from full opacity to partial opacity
* 15 to 0 cannonballs: gradually fades from partial opacity to no dim
* 0 cannonballs: no dim


## Configuration

* Start enabled
* Toggle hotkey
* Dim mode
* Full dim opacity
* Partial dim opacity
* Full dim cannonballs
* Zero dim cannonballs
* Reload pause
* Hide while moving
* Movement buffer
* Hide after hit
* Hit cooldown
* Hide on interaction
* Interaction buffer
* Show cannonballs
* Show ETA
* Text size
* Text prefix


## Notes

Cannon Dimmer is visual only. It does not click, move, interact with the game, automate gameplay, or perform actions for the player.

