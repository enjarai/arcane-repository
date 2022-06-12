package net.messer.mystical_index.item.custom.page.attribute;

import net.messer.mystical_index.item.custom.page.type.ItemStorageTypePage;
import net.minecraft.item.ItemStack;

public class StacksPage extends ItemStorageTypePage.ItemStorageAttributePage {
    @Override
    public double getStacksMultiplier(ItemStack page) {
        return 2;
    } // TODO make this percentages, and display as such

    @Override
    public int getColor() {
        return 0xffff00;
    }
}