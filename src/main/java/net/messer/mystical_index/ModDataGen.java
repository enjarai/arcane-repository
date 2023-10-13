package net.messer.mystical_index;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.TagKey;

import java.util.concurrent.CompletableFuture;

public class ModDataGen implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        fabricDataGenerator.createPack().addProvider(ModTagGenerator::new);
    }

    private static class ModTagGenerator extends FabricTagProvider<Item> {
        public ModTagGenerator(FabricDataOutput dataGenerator, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
            super(dataGenerator, RegistryKeys.ITEM, registriesFuture);
        }

        private static final TagKey<Item> BLOCK_ITEMS = TagKey.of(RegistryKeys.ITEM, MysticalIndex.id("block_items"));
        private static final TagKey<Item> FOOD_ITEMS = TagKey.of(RegistryKeys.ITEM, MysticalIndex.id("food_items"));

        @Override
        protected void configure(RegistryWrapper.WrapperLookup arg) {
            var blockItems = getOrCreateTagBuilder(BLOCK_ITEMS);
            var foodItems = getOrCreateTagBuilder(FOOD_ITEMS);

            Registries.ITEM.stream().filter(BlockItem.class::isInstance).forEach(blockItems::add);
            Registries.ITEM.stream().filter(Item::isFood).forEach(foodItems::add);
        }
    }
}
