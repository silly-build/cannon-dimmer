package com.cannonafkdim;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup("cannonafkdim")
public interface CannonAfkDimConfig extends Config
{
    @ConfigItem(
            keyName = "startEnabled",
            name = "Start enabled",
            description = "Starts the dimmer enabled when the plugin starts.",
            position = 1
    )
    default boolean startEnabled()
    {
        return true;
    }

    @ConfigItem(
            keyName = "toggleHotkey",
            name = "Toggle hotkey",
            description = "Hotkey to quickly turn the dimmer on or off.",
            position = 2
    )
    default Keybind toggleHotkey()
    {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "dimMode",
            name = "Dim mode",
            description = "Controls how dimming changes as cannonballs run down.",
            position = 3
    )
    default CannonAfkDimMode dimMode()
    {
        return CannonAfkDimMode.GRADUAL_TO_PARTIAL;
    }

    @Range(
            min = 0,
            max = 255
    )
    @ConfigItem(
            keyName = "fullDimOpacity",
            name = "Full dim opacity",
            description = "Maximum dim strength. 0 is invisible. 255 is fully black.",
            position = 4
    )
    default int fullDimOpacity()
    {
        return 190;
    }

    @Range(
            min = 0,
            max = 255
    )
    @ConfigItem(
            keyName = "partialDimOpacity",
            name = "Partial dim opacity",
            description = "Dim strength reached at the zero dim cannonball value in gradual mode.",
            position = 5
    )
    default int partialDimOpacity()
    {
        return 80;
    }

    @Range(
            min = 0,
            max = 60
    )
    @ConfigItem(
            keyName = "fullDimAtCannonballs",
            name = "Full dim cannonballs",
            description = "Cannonball count where dimming is at full strength. Use 30, 45, or 60 depending on cannon capacity.",
            position = 6
    )
    default int fullDimAtCannonballs()
    {
        return 30;
    }

    @Range(
            min = 0,
            max = 59
    )
    @ConfigItem(
            keyName = "zeroDimAtCannonballs",
            name = "Zero dim cannonballs",
            description = "Mode 1: dim turns off at this value. Mode 2: dim reaches partial opacity here, then fades to zero at 0 cannonballs.",
            position = 7
    )
    default int zeroDimAtCannonballs()
    {
        return 0;
    }

    @Range(
            min = 0,
            max = 100
    )
    @ConfigItem(
            keyName = "pauseAfterReloadTicks",
            name = "Reload pause",
            description = "Number of game ticks to avoid dimming after cannonballs are reloaded. Set to 0 to disable.",
            position = 8
    )
    default int pauseAfterReloadTicks()
    {
        return 0;
    }

    @ConfigItem(
            keyName = "hideWhileMoving",
            name = "Hide while moving",
            description = "Stops dimming while your character is moving.",
            position = 9
    )
    default boolean hideWhileMoving()
    {
        return false;
    }

    @Range(
            min = 0,
            max = 100
    )
    @ConfigItem(
            keyName = "movementBufferTicks",
            name = "Movement buffer",
            description = "Extra game ticks to keep dimming hidden after your character stops moving. Only applies when Hide while moving is enabled.",
            position = 10
    )
    default int movementBufferTicks()
    {
        return 0;
    }

    @ConfigItem(
            keyName = "hideAfterHit",
            name = "Hide after hit",
            description = "Stops dimming after a damage hitsplat is applied to your character.",
            position = 11
    )
    default boolean hideAfterHit()
    {
        return false;
    }

    @Range(
            min = 1,
            max = 100
    )
    @ConfigItem(
            keyName = "hitCooldownTicks",
            name = "Hit cooldown",
            description = "Number of game ticks to avoid dimming after your character takes damage.",
            position = 12
    )
    default int hitCooldownTicks()
    {
        return 5;
    }

    @ConfigItem(
            keyName = "hideOnInteraction",
            name = "Hide on interaction",
            description = "Stops dimming after a manual game interaction, such as clicking to move, eat, attack, or use an object.",
            position = 13
    )
    default boolean hideOnInteraction()
    {
        return false;
    }

    @Range(
            min = 1,
            max = 100
    )
    @ConfigItem(
            keyName = "interactionBufferTicks",
            name = "Interaction buffer",
            description = "Number of game ticks to keep dimming hidden after a manual game interaction.",
            position = 14
    )
    default int interactionBufferTicks()
    {
        return 3;
    }

    @ConfigItem(
            keyName = "showCannonballText",
            name = "Show cannonballs",
            description = "Shows the number of cannonballs remaining in white text.",
            position = 15
    )
    default boolean showCannonballText()
    {
        return true;
    }

    @ConfigItem(
            keyName = "showEstimatedTime",
            name = "Show ETA",
            description = "Shows an estimated time until the zero dim cannonball value is reached. The average resets when the cannon is reloaded.",
            position = 16
    )
    default boolean showEstimatedTime()
    {
        return true;
    }

    @Range(
            min = 12,
            max = 64
    )
    @ConfigItem(
            keyName = "textSize",
            name = "Text size",
            description = "Size of the cannonball count and estimated time text.",
            position = 17
    )
    default int textSize()
    {
        return 28;
    }

    @ConfigItem(
            keyName = "textPrefix",
            name = "Text prefix",
            description = "Text shown before the cannonball count.",
            position = 18
    )
    default String textPrefix()
    {
        return "Cannonballs";
    }
}