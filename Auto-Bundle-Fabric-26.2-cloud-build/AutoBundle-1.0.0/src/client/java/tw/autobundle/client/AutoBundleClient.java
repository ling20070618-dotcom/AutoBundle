package tw.autobundle.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-only helper. It uses the normal vanilla container-click path;
 * it does not write item components or send custom packets.
 */
public final class AutoBundleClient implements ClientModInitializer {
    private static final int FIRST_PLAYER_SLOT = 9;
    private static final int LAST_PLAYER_SLOT = 45; // includes offhand
    private static final int RETRY_DELAY_TICKS = 20;

    private final ItemStack[] previous = new ItemStack[LAST_PLAYER_SLOT + 1];
    private final ArrayDeque<PendingMove> pending = new ArrayDeque<>();
    private boolean initialized;
    private int retryTicks;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        if (client.player == null || client.gameMode == null) {
            resetTracking();
            return;
        }

        AbstractContainerMenu menu = client.player.containerMenu;

        // Only automate the normal player inventory. Do not interfere with
        // chests, crafting tables, merchants, or other server containers.
        if (!(menu instanceof InventoryMenu)) {
            return;
        }

        if (!initialized) {
            snapshot(menu);
            initialized = true;
            return;
        }

        detectNewItems(menu);

        if (pending.isEmpty()) {
            return;
        }

        if (retryTicks > 0) {
            retryTicks--;
            return;
        }

        PendingMove move = pending.peekFirst();
        if (move == null) return;

        if (move.slot < FIRST_PLAYER_SLOT || move.slot > LAST_PLAYER_SLOT) {
            pending.removeFirst();
            return;
        }

        Slot sourceSlot = menu.slots.get(move.slot);
        ItemStack source = sourceSlot.getItem();

        // If the player moved/replaced the stack while it was queued, discard
        // the stale request rather than risk moving a different item.
        if (source.isEmpty()
                || !ItemStack.isSameItemSameComponents(source, move.identity)) {
            pending.removeFirst();
            return;
        }

        // Never take over an item already on the cursor.
        if (!menu.getCarried().isEmpty()) {
            return;
        }

        int beforeCount = source.getCount();
        boolean moved = tryInsertIntoAnyBundle(client, menu, move.slot);

        ItemStack afterSource = menu.slots.get(move.slot).getItem();
        int afterCount = afterSource.isEmpty() ? 0 : afterSource.getCount();
        int transferred = Math.max(0, beforeCount - afterCount);

        if (moved && transferred > 0) {
            move.remaining -= transferred;
            if (move.remaining <= 0 || afterSource.isEmpty()) {
                pending.removeFirst();
            }
            snapshot(menu);
        } else {
            // A full bundle or an item rejected by every bundle: retry slowly.
            retryTicks = RETRY_DELAY_TICKS;
        }
    }

    private boolean tryInsertIntoAnyBundle(Minecraft client, AbstractContainerMenu menu, int sourceSlot) {
        List<Integer> bundleSlots = findBundleSlots(menu);
        if (bundleSlots.isEmpty()) return false;

        // Pick up the source stack, click each bundle using vanilla's normal
        // BundleItem click behavior, then return any remainder to its slot.
        for (int bundleSlot : bundleSlots) {
            if (bundleSlot == sourceSlot) continue;

            ItemStack beforeBundle = menu.slots.get(bundleSlot).getItem().copy();

            click(client, menu, sourceSlot);
            click(client, menu, bundleSlot);
            click(client, menu, sourceSlot);

            ItemStack afterBundle = menu.slots.get(bundleSlot).getItem();
            if (!ItemStack.matches(beforeBundle, afterBundle)) {
                return true;
            }

            // If the click was rejected, the cursor should have been returned
            // to the original source slot by the third click.
            if (!menu.getCarried().isEmpty()) {
                // Safety recovery: put the carried stack back into its source.
                click(client, menu, sourceSlot);
            }
        }
        return false;
    }

    private void click(Minecraft client, AbstractContainerMenu menu, int slot) {
        client.gameMode.handleContainerInput(
                menu.containerId,
                slot,
                0,
                ContainerInput.PICKUP,
                client.player
        );
    }

    private List<Integer> findBundleSlots(AbstractContainerMenu menu) {
        List<Integer> result = new ArrayList<>();

        // Prefer hotbar, then main inventory, then offhand.
        for (int slot = 36; slot <= 44; slot++) {
            if (menu.slots.get(slot).getItem().is(Items.BUNDLE)) result.add(slot);
        }
        for (int slot = 9; slot <= 35; slot++) {
            if (menu.slots.get(slot).getItem().is(Items.BUNDLE)) result.add(slot);
        }
        if (menu.slots.get(45).getItem().is(Items.BUNDLE)) result.add(45);

        return result;
    }

    private void detectNewItems(AbstractContainerMenu menu) {
        for (int slot = FIRST_PLAYER_SLOT; slot <= LAST_PLAYER_SLOT; slot++) {
            ItemStack current = menu.slots.get(slot).getItem();
            ItemStack old = previous[slot];

            if (current.isEmpty() || current.is(Items.BUNDLE)) continue;

            int oldCount = 0;
            if (!old.isEmpty() && ItemStack.isSameItemSameComponents(old, current)) {
                oldCount = old.getCount();
            }

            int added = current.getCount() - oldCount;
            if (added > 0) {
                pending.addLast(new PendingMove(slot, current.copyWithCount(1), added));
            }
        }

        snapshot(menu);
    }

    private void snapshot(AbstractContainerMenu menu) {
        for (int slot = FIRST_PLAYER_SLOT; slot <= LAST_PLAYER_SLOT; slot++) {
            previous[slot] = menu.slots.get(slot).getItem().copy();
        }
    }

    private void resetTracking() {
        initialized = false;
        pending.clear();
        retryTicks = 0;
    }

    private static final class PendingMove {
        private final int slot;
        private final ItemStack identity;
        private int remaining;

        private PendingMove(int slot, ItemStack identity, int remaining) {
            this.slot = slot;
            this.identity = identity;
            this.remaining = remaining;
        }
    }
}
