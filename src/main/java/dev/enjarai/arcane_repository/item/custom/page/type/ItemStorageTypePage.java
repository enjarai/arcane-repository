package dev.enjarai.arcane_repository.item.custom.page.type;

import com.google.common.collect.ImmutableList;
import dev.enjarai.arcane_repository.client.tooltip.ItemStorageTooltipData;
import dev.enjarai.arcane_repository.item.ItemSettings;
import dev.enjarai.arcane_repository.item.ModDataComponentTypes;
import dev.enjarai.arcane_repository.item.component.StorageFilterComponent;
import dev.enjarai.arcane_repository.util.request.ExtractionRequest;
import dev.enjarai.arcane_repository.ArcaneRepository;
import dev.enjarai.arcane_repository.block.entity.MysticalLecternBlockEntity;
import dev.enjarai.arcane_repository.item.custom.book.MysticalBookItem;
import dev.enjarai.arcane_repository.item.custom.page.AttributePageItem;
import dev.enjarai.arcane_repository.item.custom.page.TypePageItem;
import dev.enjarai.arcane_repository.util.BigStack;
import dev.enjarai.arcane_repository.util.ContentsIndex;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

import static dev.enjarai.arcane_repository.item.ModItems.*;

public class ItemStorageTypePage extends TypePageItem implements ItemInsertableTypePage {
    private static final ComponentType<StorageFilterComponent> COMPONENT_TYPE = ModDataComponentTypes.STORAGE_FILTERS;
    public static final String MAX_STACKS_TAG = "max_stacks";
    public static final String MAX_TYPES_TAG = "max_types";

    public ItemStorageTypePage(String id) {
        super(new ItemSettings().component(COMPONENT_TYPE, new StorageFilterComponent(List.of(), 0, 0)), id);
    }

    private StorageFilterComponent getComponent(ItemStack stack) {
        return stack.get(COMPONENT_TYPE);
    }

    @Override
    public int getColor() {
        return 0x88ff88;
    }

    @Override
    public MutableText getTypeDisplayName() {
        return super.getTypeDisplayName().formatted(Formatting.DARK_AQUA);
    }

    @Override
    public void onCraftToBook(ItemStack page, ItemStack book) {
        super.onCraftToBook(page, book);

        NbtCompound attributes = getAttributes(book);

        attributes.putInt(MAX_STACKS_TAG, 2);
        attributes.putInt(MAX_TYPES_TAG, 4);
    }

    public int getMaxTypes(ItemStack book) {
        return getAttributes(book).getInt(MAX_TYPES_TAG);
    }

    public int getMaxStack(ItemStack book) {
        return getAttributes(book).getInt(MAX_STACKS_TAG);
    }

    public ContentsIndex getContents(ItemStack book) {
        ContentsIndex result = new ContentsIndex();
        ContainerComponent containerComponent = book.get(DataComponentTypes.CONTAINER);
        if (containerComponent != null) {
            containerComponent.iterateNonEmptyCopy().forEach(result::add);
        }
        return result;
    }

    protected int getFullness(ItemStack book) {
        int result = 0;
        for (BigStack bigStack : getContents(book).getAll()) {
            result += bigStack.getAmount() * getItemOccupancy(bigStack.getItem());
        }
        return result;
    }

    private int getItemOccupancy(Item item) {
        if (item.getMaxCount() == 1) {
            return 8;
        }
        return 1;
    }

    private Optional<ItemStack> canMergeStack(ItemStack stack, List<ItemStack> items) {
        return items.stream()
          .filter(item -> ItemStack.areItemsAndComponentsEqual(item, stack))
          .findFirst();
    }

    public boolean isFiltered(ItemStack book) {
        var filters = getComponent(book).items();

        return filters != null && !filters.isEmpty();
    }

    public boolean isFilteredTo(ItemStack book, ItemStack stack) {
        var filters = getComponent(book).items();

        return filters != null && filters.contains(Registries.ITEM.getId(stack.getItem()));
    }

    public List<Item> getFilteredItems(ItemStack book) {
        var filters = getComponent(book).items();

        return filters.stream()
          .filter(Objects::nonNull)
          .map(Registries.ITEM::get)
          .collect(ImmutableList.toImmutableList());
    }

    public void addFilteredItem(ItemStack book, Item item) {
        var component = getComponent(book);
        var filters = component.items();

        var newFilters = new ArrayList<>(filters);
        filters.add(Registries.ITEM.getId(item));

        book.set(COMPONENT_TYPE, component.withItems(newFilters));
    }

    public void removeFilteredItem(ItemStack book, int i) {
        var component = getComponent(book);
        var filters = component.items();

        var newFilters = new ArrayList<>(filters);
        filters.remove(i);

        book.set(COMPONENT_TYPE, component.withItems(newFilters));
    }

    public void clearFilteredItems(ItemStack book) {
        book.set(COMPONENT_TYPE, getComponent(book).withItems(List.of()));
    }

    public void setFilteredItems(ItemStack book, List<Item> items) {
        var newFilters = new ArrayList<Identifier>();
        items.forEach(item -> newFilters.add(Registries.ITEM.getId(item)));

        book.set(COMPONENT_TYPE, getComponent(book).withItems(newFilters));
    }

    protected boolean canInsert(ItemStack book, ItemStack itemStack) {
        return itemStack.getItem().canBeNested();
    }

    private boolean canInsertFiltered(ItemStack book, ItemStack itemStack) {
        if (!canInsert(book, itemStack)) return false;

        if (!isFiltered(book)) return true;
        return isFilteredTo(book, itemStack);
    }

    protected int getBaseInsertPriority(ItemStack book) {
        return 0;
    }

    public int getInsertPriority(ItemStack book, ItemStack stack) {
        if (!canInsertFiltered(book, stack)) return -1;
        var base = getBaseInsertPriority(book);
        if (isFilteredTo(book, stack)) return base + 100;
        return base;
    }

    public int tryAddItem(ItemStack book, ItemStack stack) {
        if (stack.isEmpty() || !canInsertFiltered(book, stack)) {
            return 0;
        }

        ContainerComponent containerComponent = book.get(DataComponentTypes.CONTAINER);

        var itemsList = DefaultedList.<ItemStack>of();
        if (containerComponent != null) {
            containerComponent.copyTo(itemsList);
        }

        int maxFullness = getMaxStack(book) * 64;
        int fullnessLeft = maxFullness - getFullness(book);
        int canBeTakenAmount = Math.min(stack.getCount(), fullnessLeft / getItemOccupancy(stack.getItem()));
        if (canBeTakenAmount == 0) {
            return 0;
        }

        Optional<ItemStack> mergeAbleStack = canMergeStack(stack, itemsList);
        if (mergeAbleStack.isPresent()) {
            ItemStack mergeStack = mergeAbleStack.get();
            mergeStack.setCount(mergeStack.getCount() + canBeTakenAmount);
            itemsList.remove(mergeStack);
            itemsList.addFirst(mergeStack);
        } else {
            if (itemsList.size() >= getMaxTypes(book)) {
                return 0;
            }

            ItemStack insertStack = stack.copy();
            insertStack.setCount(canBeTakenAmount);

            itemsList.addFirst(insertStack);
        }

        book.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(itemsList));

        saveOccupancy(book,
          maxFullness - fullnessLeft + canBeTakenAmount * getItemOccupancy(stack.getItem()),
          itemsList.size());

        return canBeTakenAmount;
    }

    @Override
    public int book$tryInsertItemStack(ItemStack book, PlayerEntity player, ItemStack insert) {
        var affected = tryAddItem(book, insert);
        insert.decrement(affected);
        return affected;
    }

    @Override
    public int lectern$tryInsertItemStack(MysticalLecternBlockEntity lectern, ItemStack insert) {
        var affected = tryAddItem(lectern.getBook(), insert);
        insert.decrement(affected);
        return affected;
    }

    public Optional<ItemStack> removeFirstStack(ItemStack book) {
        return removeFirstStack(book, null);
    }

    public Optional<ItemStack> removeFirstStack(ItemStack book, Integer maxAmount) {
        var containerComponent = book.get(DataComponentTypes.CONTAINER);
        if (containerComponent == null) {
            return Optional.empty();
        }

        ItemStack firstItem = containerComponent.copyFirstStack();
        if (firstItem.isEmpty()) {
            return Optional.empty();
        }

        int itemCount = firstItem.getCount();
        int takeCount = Math.min(itemCount, firstItem.getMaxCount());
        if (maxAmount != null) {
            takeCount = Math.min(takeCount, maxAmount);
        }

        firstItem.setCount(takeCount);

        if (takeCount >= itemCount) {
            var newStacks = DefaultedList.<ItemStack>of();
            containerComponent.copyTo(newStacks);
            newStacks.removeFirst();

            firstItem.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(newStacks));
        } else {
            firstItem.setCount(itemCount - takeCount);
        }

        saveOccupancy(book, getFullness(book), Math.toIntExact(containerComponent.stream().filter(stack -> !stack.isEmpty()).count()));

        return Optional.of(firstItem);
    }

    public Optional<ItemStack> tryRemoveFirstStack(ItemStack book, int amount, Predicate<ItemStack> condition) {
        var stack = removeFirstStack(book, amount);
        if (stack.isPresent() && !condition.test(stack.get())) {
            tryAddItem(book, stack.get());
            return Optional.empty();
        }
        return stack;
    }

    public List<ItemStack> extractItems(ItemStack book, ExtractionRequest request, boolean apply) {
        if (request.isSatisfied()) {
            return Collections.emptyList();
        }

        var containerComponent = book.get(DataComponentTypes.CONTAINER);

        if (containerComponent == null) {
            return Collections.emptyList();
        }

        var itemsList = DefaultedList.<ItemStack>of();
        containerComponent.copyTo(itemsList);

        if (itemsList.isEmpty())
            return Collections.emptyList();

        ImmutableList.Builder<ItemStack> builder = ImmutableList.builder();

        containerComponent.iterateNonEmptyCopy().forEach(itemStack -> {
            if (request.matches(itemStack.getItem())) {
                int itemCount = itemStack.getCount();
                int extractAmount = Math.min(itemCount, request.getAmountUnsatisfied());
                int stackSize = itemStack.getItem().getMaxCount();

                request.satisfy(extractAmount);
                if (apply) {
                    if (extractAmount >= itemCount) {
                        itemsList.remove(itemStack);
                    } else {
                        itemStack.setCount(itemCount - extractAmount);
                    }
                }

                while (extractAmount > 0) {
                    int extractAmountStack = Math.min(extractAmount, stackSize);

                    ItemStack extractStack = itemStack.copy();
                    extractStack.setCount(extractAmountStack);
                    builder.add(extractStack);

                    extractAmount -= extractAmountStack;
                }
            }
        });

        saveOccupancy(book, getFullness(book), itemsList.size());

        return builder.build();
    }

    public void saveOccupancy(ItemStack book, int stacks, int types) {
        book.set(COMPONENT_TYPE, getComponent(book).withOccupiedStacks(stacks).withOccupiedTypes(types));
    }

    public void playRemoveOneSound(PlayerEntity player) {
        ArcaneRepository.playUISound(
          player, SoundEvents.ITEM_BUNDLE_REMOVE_ONE, SoundCategory.PLAYERS, player.getEyePos());
        ArcaneRepository.playUISound(
          player, SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, player.getEyePos(), 0.4f);
    }

    public void playInsertSound(PlayerEntity player) {
        ArcaneRepository.playUISound(
          player, SoundEvents.ITEM_BUNDLE_INSERT, SoundCategory.PLAYERS, player.getEyePos());
        ArcaneRepository.playUISound(
          player, SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, player.getEyePos(), 0.4f);
    }

    public boolean isEmpty(ItemStack book) {
        var containerComponent = book.get(DataComponentTypes.CONTAINER);
        return containerComponent == null || containerComponent.copyFirstStack().isEmpty();
    }

    @Override
    public boolean book$onStackClicked(ItemStack book, Slot slot, ClickType clickType, PlayerEntity player) {
        if (clickType != ClickType.RIGHT) {
            return false;
        }
        ItemStack itemStack = slot.getStack();
        if (itemStack.isEmpty()) {
            playRemoveOneSound(player);
            removeFirstStack(book).ifPresent(removedStack -> tryAddItem(book, slot.insertStack(removedStack)));
        } else {
            int amount = tryAddItem(book, itemStack);
            if (amount > 0) {
                playInsertSound(player);
                itemStack.decrement(amount);
            }
        }
        return true;
    }

    @Override
    public boolean book$onClicked(ItemStack book, ItemStack cursorStack, Slot slot, ClickType clickType, PlayerEntity player, StackReference cursorStackReference) {
        if (clickType != ClickType.RIGHT || !slot.canTakePartial(player)) {
            return false;
        }
        if (cursorStack.isEmpty()) {
            removeFirstStack(book).ifPresent(itemStack -> {
                playRemoveOneSound(player);
                cursorStackReference.set(itemStack);
            });
        } else {
            int amount = tryAddItem(book, cursorStack);
            if (amount > 0) {
                playInsertSound(player);
                cursorStack.decrement(amount);
            }
        }
        return true;
    }

    @Override
    public void book$appendTooltip(ItemStack book, @Nullable TooltipContext world, List<Text> tooltip, TooltipType type) {
        if (isFiltered(book)) {
            tooltip.add(Text.literal(""));
            tooltip.add(Text.translatable("item.arcane_repository.repository_book.tooltip.type.item_storage.filtered")
              .formatted(Formatting.GRAY));

            tooltip.addAll(getFilteredItems(book).stream()
              .map(Item::getName)
              .map(text -> Text.literal(" ").append(text).formatted(Formatting.GRAY))
              .toList()
            );
        }
    }

    @Override
    public void book$appendPropertiesTooltip(ItemStack book, @Nullable TooltipContext context, List<Text> tooltip, TooltipType type) {
        var component = getComponent(book);

        var stacksOccupied = component.occupiedStacks();
        var stacksTotal = getMaxStack(book) * 64;
        double stacksFullRatio = (double) stacksOccupied / stacksTotal;
        var typesOccupied = component.occupiedTypes();
        var typesTotal = getMaxTypes(book);
        double typesFullRatio = (double) typesOccupied / typesTotal;

        tooltip.add(Text.translatable("item.arcane_repository.repository_book.tooltip.type.item_storage.stacks",
            stacksOccupied, stacksTotal)
          .formatted(stacksFullRatio < 0.75 ? Formatting.GREEN :
            stacksFullRatio == 1 ? Formatting.RED : Formatting.GOLD));
        tooltip.add(Text.translatable("item.arcane_repository.repository_book.tooltip.type.item_storage.types",
            typesOccupied, typesTotal)
          .formatted(typesFullRatio < 0.75 ? Formatting.GREEN :
            typesFullRatio == 1 ? Formatting.RED : Formatting.GOLD));
    }

    @Override
    public boolean book$onInventoryScroll(ItemStack book, PlayerEntity player, byte scrollDirection) {
        var containerComponent = book.get(DataComponentTypes.CONTAINER);

        if (containerComponent == null) return false;

        var itemsList = DefaultedList.<ItemStack>of();
        containerComponent.copyTo(itemsList);

        if (itemsList.isEmpty()) return false;

        Collections.rotate(itemsList, -scrollDirection);
        if (player.getWorld().isClient()) {
            player.playSoundToPlayer(SoundEvents.ITEM_BUNDLE_INSERT, SoundCategory.PLAYERS, 0.4f, 0.8f);
        }

        return true;
    }

    @Override
    public Optional<TooltipData> book$getTooltipData(ItemStack book) {
        return Optional.of(new ItemStorageTooltipData(getContents(book)));
    }

    @Override
    public ItemActionResult lectern$onUseWithItem(MysticalLecternBlockEntity lectern, ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        var book = lectern.getBook();

        if (stack.getItem() instanceof MysticalBookItem bookItem) {
            if (bookItem.getTypePage(stack) instanceof ItemStorageTypePage handPage) {
                var ownFilters = getFilteredItems(book);
                handPage.setFilteredItems(stack, ownFilters);
                player.sendMessage(Text.translatable("chat.arcane_repository.copied_filters"), true);
                return ItemActionResult.SUCCESS;
            }
        } else {
            if (!(canInsert(book, stack) || stack.isEmpty())) return ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;

            var filters = getFilteredItems(book);
            var i = stack.isEmpty() ? filters.size() - 1 : filters.indexOf(stack.getItem());

            if (i == -1) {
                if (stack.isEmpty()) return ItemActionResult.CONSUME;

                addFilteredItem(book, stack.getItem());
                lectern.items.add(stack.getItem().getDefaultStack());
            } else {
                removeFilteredItem(book, i);
                lectern.items.remove(i);
            }

            lectern.markDirty();
            world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);

            return ItemActionResult.success(world.isClient());
        }

        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void lectern$onPlaced(MysticalLecternBlockEntity lectern) {
        lectern.items = getFilteredItems(lectern.getBook()).stream()
          .map(Item::getDefaultStack)
          .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    public static abstract class ItemStorageAttributePage extends AttributePageItem {
        public ItemStorageAttributePage(String id) {
            super(id);
        }

        @Override
        public @Nullable MutableText getAttributeDisplayName() {
            return null;
        }

        @Override
        public List<TypePageItem> getCompatibleTypes(ItemStack page) {
            return List.of(ITEM_STORAGE_TYPE_PAGE, FOOD_STORAGE_TYPE_PAGE, BLOCK_STORAGE_TYPE_PAGE);
        }

        public double getStacksMultiplier(ItemStack page) {
            return 1;
        }

        public double getTypesMultiplier(ItemStack page) {
            return 1;
        }

        @Override
        public void appendAttributes(ItemStack page, NbtCompound nbt) {
            multiplyIntAttribute(nbt, MAX_STACKS_TAG, getStacksMultiplier(page));
            multiplyIntAttribute(nbt, MAX_TYPES_TAG, getTypesMultiplier(page));
        }

        @Override
        public void appendTooltip(ItemStack stack, @Nullable TooltipContext context, List<Text> tooltip, TooltipType type) {
            super.appendTooltip(stack, context, tooltip, type);

            var stacks = getStacksMultiplier(stack);
            var types = getTypesMultiplier(stack);

            if (stacks != 1)
                tooltip.add(Text.translatable("item.arcane_repository.page.tooltip.type.item_storage.stacks", stacks)
                  .formatted(Formatting.DARK_GREEN));
            if (types != 1)
                tooltip.add(Text.translatable("item.arcane_repository.page.tooltip.type.item_storage.types", types)
                  .formatted(Formatting.DARK_GREEN));
        }
    }
}
