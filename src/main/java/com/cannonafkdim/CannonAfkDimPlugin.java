package com.cannonafkdim;

import com.google.inject.Provides;
import java.time.Duration;
import java.util.Locale;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.Hitsplat;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.Text;

@PluginDescriptor(
        name = "Cannon Dimmer",
        description = "Dims the game screen while your dwarf multicannon is placed and loaded",
        tags = {"cannon", "combat", "overlay", "dim", "timer", "slayer"}
)
public class CannonAfkDimPlugin extends Plugin
{
    private static final int FULLY_BUILT_CANNON_VARP_VALUE = 4;

    private static final long CANNON_BREAK_DURATION_MILLIS = Duration.ofMinutes(25).toMillis();
    private static final long CANNON_ALMOST_DECAYED_REMAINING_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final long CANNON_WARNING_THRESHOLD_MILLIS = Duration.ofSeconds(60).toMillis();

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private KeyManager keyManager;

    @Inject
    private CannonAfkDimOverlay overlay;

    @Inject
    private CannonAfkDimConfig config;

    private boolean cannonPlaced;
    private int cannonballsLeft;
    private boolean shouldRenderOverlay;
    private boolean dimmerToggledOn;

    private int reloadPauseTicksRemaining;
    private int movementBufferTicksRemaining;
    private int hitCooldownTicksRemaining;
    private int interactionBufferTicksRemaining;
    private boolean playerMovingOrBuffered;
    private WorldPoint lastPlayerWorldPoint;

    private boolean slayerBraceletEquipped;
    private boolean hasSlayerTask;

    private long cannonBreakAtMillis;
    private boolean cannonBroken;

    private int lastObservedCannonballs = -1;
    private int etaStartCannonballs = -1;
    private long etaStartTimeMillis;
    private String estimatedTimeText = "ETA: calculating...";

    private final HotkeyListener toggleHotkeyListener = new HotkeyListener(() -> config.toggleHotkey())
    {
        @Override
        public void hotkeyPressed()
        {
            dimmerToggledOn = !dimmerToggledOn;
            updateOverlayState();
        }
    };

    @Provides
    CannonAfkDimConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(CannonAfkDimConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        keyManager.registerKeyListener(toggleHotkeyListener);

        dimmerToggledOn = config.startEnabled();

        clientThread.invokeLater(() ->
        {
            refreshCannonState();
            refreshMovementState();
            refreshCombatState();
            refreshSlayerBraceletState();
            refreshSlayerTaskState();
            refreshCannonBreakState();
            updateEstimatedTimeText();
            updateOverlayState();
        });
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
        keyManager.unregisterKeyListener(toggleHotkeyListener);

        cannonPlaced = false;
        cannonballsLeft = 0;
        shouldRenderOverlay = false;
        dimmerToggledOn = false;

        reloadPauseTicksRemaining = 0;
        movementBufferTicksRemaining = 0;
        hitCooldownTicksRemaining = 0;
        interactionBufferTicksRemaining = 0;
        playerMovingOrBuffered = false;
        lastPlayerWorldPoint = null;

        slayerBraceletEquipped = false;
        hasSlayerTask = false;

        resetCannonBreakTimer();
        resetEtaCalculation();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            refreshCannonState();
            refreshMovementState();
            refreshCombatState();
            refreshSlayerBraceletState();
            refreshSlayerTaskState();
            refreshCannonBreakState();
            updateEstimatedTimeText();
            updateOverlayState();
            return;
        }

        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
        {
            cannonPlaced = false;
            cannonballsLeft = 0;
            shouldRenderOverlay = false;

            reloadPauseTicksRemaining = 0;
            movementBufferTicksRemaining = 0;
            hitCooldownTicksRemaining = 0;
            interactionBufferTicksRemaining = 0;
            playerMovingOrBuffered = false;
            lastPlayerWorldPoint = null;

            slayerBraceletEquipped = false;
            hasSlayerTask = false;

            resetEtaCalculation();
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        if (event.getVarpId() == VarPlayerID.DROPCANNON)
        {
            updateCannonPlacedState(event.getValue());
        }
        else if (event.getVarpId() == VarPlayerID.ROCKTHROWER)
        {
            handleCannonballCountChanged(event.getValue());
        }

        if (event.getVarpId() == VarPlayerID.SLAYER_COUNT)
        {
            refreshSlayerTaskState();
        }

        refreshCannonBreakState();
        updateEstimatedTimeText();
        updateOverlayState();
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (event.getContainerId() != InventoryID.WORN)
        {
            return;
        }

        refreshSlayerBraceletState();
        updateOverlayState();
    }


    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event)
    {
        if (!config.hideAfterHit())
        {
            return;
        }

        Player localPlayer = client.getLocalPlayer();

        /*
         * This is the same incoming-hit rule used by RuneLite's built-in
         * Idle Notifier:
         *
         * 1. The actor receiving the hitsplat must be the local player.
         * 2. Hitsplat#isMine() must be true.
         *
         * On the local player, isMine() includes both BLOCK_ME and DAMAGE_ME
         * hitsplats. That covers incoming zeroes and incoming damage. Cannon
         * hits fail the first check because their receiving actor is the NPC.
         */
        if (localPlayer == null || event.getActor() != localPlayer)
        {
            return;
        }

        Hitsplat hitsplat = event.getHitsplat();

        if (hitsplat == null || !hitsplat.isMine())
        {
            return;
        }

        triggerAttackCooldown();
        updateOverlayState();
    }

    @Subscribe
    public void onGraphicChanged(GraphicChanged event)
    {
        if (!config.hideAfterHit())
        {
            return;
        }

        Player localPlayer = client.getLocalPlayer();
        Actor actor = event.getActor();

        if (localPlayer == null
                || actor != localPlayer
                || !actor.hasSpotAnim(SpotanimID.FAILEDSPELL_IMPACT))
        {
            return;
        }

        /*
         * RuneLite's Idle Notifier treats a failed incoming spell impact as
         * combat even when no normal damage hitsplat is produced.
         */
        triggerAttackCooldown();
        updateOverlayState();
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (!config.hideOnInteraction())
        {
            return;
        }

        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        interactionBufferTicksRemaining = Math.max(1, config.interactionBufferTicks());
        updateOverlayState();
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
        {
            return;
        }

        String message = Text.removeTags(event.getMessage()).toLowerCase(Locale.ENGLISH);

        if (message.equals("you add the furnace."))
        {
            restartCannonBreakTimer();
        }
        else if (isCannonDecayWarningMessage(message))
        {
            cannonBreakAtMillis = System.currentTimeMillis() + CANNON_ALMOST_DECAYED_REMAINING_MILLIS;
            cannonBroken = false;
        }
        else if (isCannonBrokenMessage(message))
        {
            cannonBroken = true;
            cannonBreakAtMillis = System.currentTimeMillis();
        }
        else if (isCannonRepairMessage(message))
        {
            restartCannonBreakTimer();
        }
        else if (message.contains("you pick up the cannon"))
        {
            resetCannonBreakTimer();
        }

        updateOverlayState();
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        refreshCannonState();
        refreshMovementState();
        refreshCombatState();
        refreshSlayerTaskState();
        refreshCannonBreakState();
        updateEstimatedTimeText();
        updateOverlayState();

        tickDownReloadPause();
        tickDownHitCooldown();
        tickDownInteractionBuffer();
    }

    private void refreshCannonState()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        updateCannonPlacedState(client.getVarpValue(VarPlayerID.DROPCANNON));

        if (!cannonPlaced)
        {
            return;
        }

        handleCannonballCountChanged(client.getVarpValue(VarPlayerID.ROCKTHROWER));
    }

    private void updateCannonPlacedState(int cannonState)
    {
        boolean wasCannonPlaced = cannonPlaced;
        cannonPlaced = cannonState == FULLY_BUILT_CANNON_VARP_VALUE;

        if (cannonPlaced)
        {
            if (!wasCannonPlaced && cannonBreakAtMillis <= 0L)
            {
                restartCannonBreakTimer();
            }

            return;
        }

        cannonballsLeft = 0;
        shouldRenderOverlay = false;
        reloadPauseTicksRemaining = 0;
        resetCannonBreakTimer();
        resetEtaCalculation();
    }

    private void handleCannonballCountChanged(int newCannonballCount)
    {
        if (newCannonballCount < 0)
        {
            newCannonballCount = 0;
        }

        int previousCannonballCount = lastObservedCannonballs;
        cannonballsLeft = newCannonballCount;

        if (!cannonPlaced || cannonballsLeft <= 0)
        {
            lastObservedCannonballs = newCannonballCount;
            resetEtaCalculation();
            return;
        }

        boolean firstObservation = previousCannonballCount < 0;
        boolean reloaded = !firstObservation && cannonballsLeft > previousCannonballCount;
        boolean noEtaSession = etaStartCannonballs < 0;

        if (reloaded)
        {
            reloadPauseTicksRemaining = Math.max(0, config.pauseAfterReloadTicks());
        }

        if (firstObservation || reloaded || noEtaSession)
        {
            startEtaCalculation(cannonballsLeft);
        }

        lastObservedCannonballs = newCannonballCount;
    }

    private void tickDownReloadPause()
    {
        if (reloadPauseTicksRemaining > 0)
        {
            reloadPauseTicksRemaining--;
        }
    }

    private void refreshMovementState()
    {
        playerMovingOrBuffered = false;

        if (client.getGameState() != GameState.LOGGED_IN)
        {
            lastPlayerWorldPoint = null;
            movementBufferTicksRemaining = 0;
            return;
        }

        Player localPlayer = client.getLocalPlayer();

        if (localPlayer == null)
        {
            lastPlayerWorldPoint = null;
            movementBufferTicksRemaining = 0;
            return;
        }

        WorldPoint currentWorldPoint = localPlayer.getWorldLocation();

        if (currentWorldPoint == null)
        {
            lastPlayerWorldPoint = null;
            movementBufferTicksRemaining = 0;
            return;
        }

        boolean movedThisTick = lastPlayerWorldPoint != null && !currentWorldPoint.equals(lastPlayerWorldPoint);

        if (movedThisTick)
        {
            movementBufferTicksRemaining = Math.max(0, config.movementBufferTicks());
        }

        playerMovingOrBuffered = movedThisTick || movementBufferTicksRemaining > 0;

        if (!movedThisTick && movementBufferTicksRemaining > 0)
        {
            movementBufferTicksRemaining--;
        }

        lastPlayerWorldPoint = currentWorldPoint;
    }

    private void refreshCombatState()
    {
        if (!config.hideAfterHit() || client.getGameState() != GameState.LOGGED_IN)
        {
            hitCooldownTicksRemaining = 0;
        }
    }

    private void triggerAttackCooldown()
    {
        hitCooldownTicksRemaining = Math.max(
                hitCooldownTicksRemaining,
                Math.max(1, config.hitCooldownTicks())
        );
    }

    private void tickDownHitCooldown()
    {
        if (hitCooldownTicksRemaining > 0)
        {
            hitCooldownTicksRemaining--;
        }
    }

    private void tickDownInteractionBuffer()
    {
        if (interactionBufferTicksRemaining > 0)
        {
            interactionBufferTicksRemaining--;
        }
    }

    private void refreshSlayerBraceletState()
    {
        slayerBraceletEquipped = false;

        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        ItemContainer equipment = client.getItemContainer(InventoryID.WORN);

        if (equipment == null)
        {
            return;
        }

        Item[] equippedItems = equipment.getItems();
        int glovesSlot = EquipmentInventorySlot.GLOVES.getSlotIdx();

        if (glovesSlot < 0 || glovesSlot >= equippedItems.length)
        {
            return;
        }

        Item equippedItem = equippedItems[glovesSlot];

        if (equippedItem == null || equippedItem.getId() <= 0)
        {
            return;
        }

        int equippedItemId = equippedItem.getId();

        slayerBraceletEquipped = equippedItemId == ItemID.BRACELET_OF_SLAUGHTER
                || equippedItemId == ItemID.EXPEDITIOUS_BRACELET;
    }

    private boolean isCannonDecayWarningMessage(String message)
    {
        return message.contains("cannon has almost decayed")
                || message.contains("cannon is almost decayed")
                || message.contains("cannon is about to decay")
                || message.contains("cannon has nearly decayed");
    }

    private boolean isCannonBrokenMessage(String message)
    {
        return message.contains("cannon has broken")
                || message.contains("cannon has decayed")
                || message.contains("cannon has degraded");
    }

    private boolean isCannonRepairMessage(String message)
    {
        return message.contains("you repair your cannon")
                || message.contains("you fix your cannon");
    }

    private void refreshSlayerTaskState()
    {
        hasSlayerTask = client.getGameState() == GameState.LOGGED_IN
                && client.getVarpValue(VarPlayerID.SLAYER_COUNT) > 0;
    }

    private void restartCannonBreakTimer()
    {
        cannonBreakAtMillis = System.currentTimeMillis() + CANNON_BREAK_DURATION_MILLIS;
        cannonBroken = false;
    }

    private void resetCannonBreakTimer()
    {
        cannonBreakAtMillis = 0L;
        cannonBroken = false;
    }

    private void refreshCannonBreakState()
    {
        if (!cannonPlaced)
        {
            return;
        }

        if (cannonBreakAtMillis <= 0L)
        {
            restartCannonBreakTimer();
            return;
        }

        if (System.currentTimeMillis() >= cannonBreakAtMillis)
        {
            cannonBroken = true;
        }
    }

    private void startEtaCalculation(int startingCannonballs)
    {
        etaStartCannonballs = startingCannonballs;
        etaStartTimeMillis = System.currentTimeMillis();
        estimatedTimeText = "ETA: calculating...";
    }

    private void resetEtaCalculation()
    {
        lastObservedCannonballs = -1;
        etaStartCannonballs = -1;
        etaStartTimeMillis = 0L;
        estimatedTimeText = "ETA: calculating...";
    }

    private void updateEstimatedTimeText()
    {
        if (!config.showEstimatedTime())
        {
            estimatedTimeText = "";
            return;
        }

        if (!cannonPlaced || cannonballsLeft <= 0)
        {
            estimatedTimeText = "ETA: calculating...";
            return;
        }

        int targetCannonballs = getEffectiveZeroDimAt();

        if (cannonballsLeft <= targetCannonballs)
        {
            estimatedTimeText = "ETA: now";
            return;
        }

        if (etaStartCannonballs <= cannonballsLeft || etaStartTimeMillis <= 0L)
        {
            estimatedTimeText = "ETA: calculating...";
            return;
        }

        int cannonballsUsed = etaStartCannonballs - cannonballsLeft;
        long elapsedMillis = System.currentTimeMillis() - etaStartTimeMillis;

        if (cannonballsUsed <= 0 || elapsedMillis < 1000L)
        {
            estimatedTimeText = "ETA: calculating...";
            return;
        }

        int cannonballsUntilTarget = cannonballsLeft - targetCannonballs;
        double millisPerCannonball = elapsedMillis / (double) cannonballsUsed;
        long etaMillis = Math.round(millisPerCannonball * cannonballsUntilTarget);

        estimatedTimeText = "ETA: " + formatDuration(etaMillis);
    }

    private String formatDuration(long millis)
    {
        if (millis <= 0L)
        {
            return "now";
        }

        long totalSeconds = Math.max(0L, millis / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (hours > 0L)
        {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }

        return String.format("%d:%02d", minutes, seconds);
    }

    private void updateOverlayState()
    {
        shouldRenderOverlay = shouldDimNow();
    }

    private boolean shouldDimNow()
    {
        if (!dimmerToggledOn)
        {
            return false;
        }

        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return false;
        }

        if (!cannonPlaced)
        {
            return false;
        }

        if (cannonballsLeft <= 0)
        {
            return false;
        }

        if (reloadPauseTicksRemaining > 0)
        {
            return false;
        }

        if (config.hideWhileMoving() && playerMovingOrBuffered)
        {
            return false;
        }

        if (config.hideAfterHit() && hitCooldownTicksRemaining > 0)
        {
            return false;
        }

        if (config.hideOnInteraction() && interactionBufferTicksRemaining > 0)
        {
            return false;
        }

        return getCurrentDimOpacity() > 0;
    }

    boolean shouldRenderOverlay()
    {
        return shouldRenderOverlay;
    }

    int getCannonballsLeft()
    {
        return cannonballsLeft;
    }

    String getEstimatedTimeText()
    {
        return estimatedTimeText;
    }

    boolean shouldShowNoBraceletWarning()
    {
        return config.warnWhenNoBracelet() && !slayerBraceletEquipped;
    }

    boolean shouldShowTaskCompletedWarning()
    {
        return config.showTaskCompletedWarning() && !hasSlayerTask;
    }

    String getCannonBreakWarningText()
    {
        if (!config.showCannonBreakWarning() || !cannonPlaced || cannonBreakAtMillis <= 0L)
        {
            return "";
        }

        long remainingMillis = cannonBreakAtMillis - System.currentTimeMillis();

        if (cannonBroken || remainingMillis <= 0L)
        {
            return "CANNON BROKEN";
        }

        if (remainingMillis > CANNON_WARNING_THRESHOLD_MILLIS)
        {
            return "";
        }

        long totalSeconds = Math.max(0L, (remainingMillis + 999L) / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;

        return String.format("%d:%02d until cannon breaks", minutes, seconds);
    }

    boolean shouldShowCannonInactiveWarning()
    {
        return config.showCannonInactiveWarning()
                && dimmerToggledOn
                && client.getGameState() == GameState.LOGGED_IN
                && cannonPlaced
                && (cannonballsLeft <= 0 || cannonBroken);
    }

    boolean shouldRenderWarningOnly()
    {
        return shouldShowCannonInactiveWarning();
    }

    boolean isDimmerToggledOn()
    {
        return dimmerToggledOn;
    }

    int getCurrentDimOpacity()
    {
        if (!dimmerToggledOn || !cannonPlaced || cannonballsLeft <= 0)
        {
            return 0;
        }

        int fullOpacity = clamp(config.fullDimOpacity(), 0, 255);
        int partialOpacity = clamp(config.partialDimOpacity(), 0, 255);

        if (partialOpacity > fullOpacity)
        {
            partialOpacity = fullOpacity;
        }

        int fullDimAt = getEffectiveFullDimAt();
        int zeroDimAt = getEffectiveZeroDimAt();

        if (config.dimMode() == CannonAfkDimMode.FULL_UNTIL_ZERO_DIM)
        {
            return getModeOneOpacity(cannonballsLeft, fullOpacity, zeroDimAt);
        }

        return getModeTwoOpacity(cannonballsLeft, fullOpacity, partialOpacity, fullDimAt, zeroDimAt);
    }

    private int getModeOneOpacity(int cannonballs, int fullOpacity, int zeroDimAt)
    {
        if (cannonballs <= 0)
        {
            return 0;
        }

        if (cannonballs <= zeroDimAt)
        {
            return 0;
        }

        return fullOpacity;
    }

    private int getModeTwoOpacity(int cannonballs, int fullOpacity, int partialOpacity, int fullDimAt, int zeroDimAt)
    {
        if (cannonballs <= 0)
        {
            return 0;
        }

        if (cannonballs >= fullDimAt)
        {
            return fullOpacity;
        }

        if (zeroDimAt <= 0)
        {
            double ratio = cannonballs / (double) fullDimAt;
            return clamp((int) Math.round(fullOpacity * ratio), 0, fullOpacity);
        }

        if (cannonballs >= zeroDimAt)
        {
            double ratio = (cannonballs - zeroDimAt) / (double) (fullDimAt - zeroDimAt);
            int opacity = partialOpacity + (int) Math.round((fullOpacity - partialOpacity) * ratio);
            return clamp(opacity, partialOpacity, fullOpacity);
        }

        double ratio = cannonballs / (double) zeroDimAt;
        int opacity = (int) Math.round(partialOpacity * ratio);
        return clamp(opacity, 0, partialOpacity);
    }

    private int getEffectiveFullDimAt()
    {
        return clamp(Math.max(1, config.fullDimAtCannonballs()), 1, 60);
    }

    private int getEffectiveZeroDimAt()
    {
        int fullDimAt = getEffectiveFullDimAt();
        int zeroDimAt = clamp(config.zeroDimAtCannonballs(), 0, 59);

        if (zeroDimAt >= fullDimAt)
        {
            zeroDimAt = Math.max(0, fullDimAt - 1);
        }

        return zeroDimAt;
    }

    private int clamp(int value, int min, int max)
    {
        return Math.max(min, Math.min(max, value));
    }
}