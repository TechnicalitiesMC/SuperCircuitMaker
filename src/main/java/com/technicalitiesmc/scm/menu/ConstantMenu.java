package com.technicalitiesmc.scm.menu;

import com.technicalitiesmc.lib.menu.TKMenu;
import com.technicalitiesmc.lib.util.value.Reference;
import com.technicalitiesmc.lib.util.value.Value;
import com.technicalitiesmc.scm.init.SCMMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;

import java.util.function.Predicate;

public class ConstantMenu extends TKMenu {

    private final Predicate<Player> accessTester;
    private final Reference<Integer> output;

    public ConstantMenu(int id, Inventory playerInv, Predicate<Player> accessTester, Reference<Integer> output) {
        super(SCMMenus.CONSTANT, id, playerInv);
        this.accessTester = accessTester;
        this.output = output;
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return output.get();
            }

            @Override
            public void set(int value) {
                output.set(value);
            }
        });
    }

    public ConstantMenu(int id, Inventory playerInv) {
        this(id, playerInv, p -> true, new Value<>(0));
    }

    public int getOutput() {
        return output.get();
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        switch (button) {
            case 0 -> output.set(Math.max(0, output.get() - 20));
            case 1 -> output.set(Math.max(0, output.get() - 1));
            case 2 -> output.set(Math.min(output.get() + 1, 255));
            case 3 -> output.set(Math.min(output.get() + 20, 255));
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return accessTester.test(player);
    }

}
