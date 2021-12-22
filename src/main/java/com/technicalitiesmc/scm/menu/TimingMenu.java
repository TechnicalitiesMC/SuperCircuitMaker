package com.technicalitiesmc.scm.menu;

import com.technicalitiesmc.lib.menu.TKMenu;
import com.technicalitiesmc.lib.util.value.Reference;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Predicate;

public abstract class TimingMenu extends TKMenu {

    private final Predicate<Player> accessTester;
    private final Reference<Integer> delay;

    public TimingMenu(RegistryObject<? extends MenuType<? extends TimingMenu>> type, int id, Inventory playerInv,
                      Predicate<Player> accessTester, Reference<Integer> delay) {
        super(type, id, playerInv);
        this.accessTester = accessTester;
        this.delay = delay;
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return delay.get();
            }

            @Override
            public void set(int value) {
                delay.set(value);
            }
        });
    }

    protected abstract int getMin();

    public int getDelay() {
        return delay.get();
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        switch (button) {
            case 0 -> delay.set(Math.max(getMin(), delay.get() - 200));
            case 1 -> delay.set(Math.max(getMin(), delay.get() - 20));
            case 2 -> delay.set(delay.get() + 20);
            case 3 -> delay.set(delay.get() + 200);
            case 4 -> delay.set(Math.max(getMin(), delay.get() - 10));
            case 5 -> delay.set(Math.max(getMin(), delay.get() - 1));
            case 6 -> delay.set(delay.get() + 1);
            case 7 -> delay.set(delay.get() + 10);
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return accessTester.test(player);
    }

}
