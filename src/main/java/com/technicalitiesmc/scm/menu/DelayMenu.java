package com.technicalitiesmc.scm.menu;

import com.technicalitiesmc.lib.util.value.Reference;
import com.technicalitiesmc.lib.util.value.Value;
import com.technicalitiesmc.scm.init.SCMMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import java.util.function.Predicate;

public class DelayMenu extends TimingMenu {

    public DelayMenu(int id, Inventory playerInv, Predicate<Player> accessTester, Reference<Integer> delay) {
        super(SCMMenus.DELAY, id, playerInv, accessTester, delay);
    }

    public DelayMenu(int id, Inventory playerInv) {
        this(id, playerInv, p -> true, new Value<>(0));
    }

    @Override
    protected int getMin() {
        return 1;
    }

}
