package net.messer.mystical_index.item.custom.book;

import com.google.common.collect.ImmutableList;
import net.messer.mystical_index.MysticalIndex;
import net.messer.mystical_index.util.BigStack;
import net.messer.mystical_index.util.ContentsIndex;
import net.messer.mystical_index.util.request.ExtractionRequest;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ClickType;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public abstract class InventoryBookItem extends BookItem {
    public InventoryBookItem(Settings settings) {
        super(settings);
    }

    public static final String OCCUPIED_STACKS_TAG = "occupied_stacks";
    public static final String OCCUPIED_TYPES_TAG = "occupied_types";

    @Override
    public boolean onStackClicked(ItemStack book, Slot slot, ClickType clickType, PlayerEntity player) {
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
    public boolean onClicked(ItemStack book, ItemStack cursorStack, Slot slot, ClickType clickType, PlayerEntity player, StackReference cursorStackReference) {
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

    public abstract int getMaxTypes(ItemStack book);

    public abstract int getMaxStack(ItemStack book);

    public static ContentsIndex getContents(ItemStack book) {
        NbtCompound nbtCompound = book.getNbt();
        ContentsIndex result = new ContentsIndex();
        if (nbtCompound != null) {
            NbtList nbtList = nbtCompound.getList("Items", 10);
            Stream<NbtElement> nbtStream = nbtList.stream();
            Objects.requireNonNull(NbtCompound.class);
            nbtStream.map(NbtCompound.class::cast).forEach(
                    nbt -> result.add(ItemStack.fromNbt(nbt.getCompound("Item")), nbt.getInt("Count")));
        }
        return result;
    }

    protected static int getFullness(ItemStack book) {
        int result = 0;
        for (BigStack bigStack : getContents(book).getAll()) {
            result += bigStack.getAmount() * getItemOccupancy(bigStack.getItem());
        }
        return result;
    }

    private static int getItemOccupancy(Item item) {
        return 64 / item.getMaxCount();
    }

    private static Optional<NbtCompound> canMergeStack(ItemStack stack, NbtList items) {
        return items.stream()
                .filter(NbtCompound.class::isInstance)
                .map(NbtCompound.class::cast)
                .filter(item -> ItemStack.canCombine(ItemStack.fromNbt(item.getCompound("Item")), stack))
                .findFirst();
    }

    protected boolean canInsert(Item item) {
        return item.canBeNested();
    }

    public int tryAddItem(ItemStack book, ItemStack stack) {
        if (stack.isEmpty() || !canInsert(stack.getItem())) {
            return 0;
        }
        NbtCompound bookNbt = book.getOrCreateNbt();
        if (!bookNbt.contains("Items")) {
            bookNbt.put("Items", new NbtList());
        }

        int maxFullness = getMaxStack(book) * 64;
        int fullnessLeft = maxFullness - getFullness(book);
        int canBeTakenAmount = Math.min(stack.getCount(), fullnessLeft / getItemOccupancy(stack.getItem()));
        if (canBeTakenAmount == 0) {
            return 0;
        }

        NbtList itemsList = bookNbt.getList("Items", 10);
        Optional<NbtCompound> mergeAbleStack = canMergeStack(stack, itemsList);
        if (mergeAbleStack.isPresent()) {
            NbtCompound mergeStack = mergeAbleStack.get();
            mergeStack.putInt("Count", mergeStack.getInt("Count") + canBeTakenAmount);
            itemsList.remove(mergeStack);
            itemsList.add(0, mergeStack);
        } else {
            if (itemsList.size() >= getMaxTypes(book)) {
                return 0;
            }

            ItemStack insertStack = stack.copy();
            insertStack.setCount(1);
            NbtCompound insertNbt = new NbtCompound();
            insertNbt.put("Item", insertStack.writeNbt(new NbtCompound()));
            insertNbt.putInt("Count", canBeTakenAmount);
            itemsList.add(0, insertNbt);
        }

        saveOccupancy(bookNbt,
                maxFullness - fullnessLeft + canBeTakenAmount * getItemOccupancy(stack.getItem()),
                itemsList.size());

        return canBeTakenAmount;
    }

    public static Optional<ItemStack> removeFirstStack(ItemStack book) {
        return removeFirstStack(book, null);
    }

    public static Optional<ItemStack> removeFirstStack(ItemStack book, Integer maxAmount) {
        NbtCompound bookNbt = book.getOrCreateNbt();
        if (!bookNbt.contains("Items")) {
            return Optional.empty();
        }
        NbtList itemsList = bookNbt.getList("Items", 10);
        if (itemsList.isEmpty()) {
            return Optional.empty();
        }
        NbtCompound firstItem = itemsList.getCompound(0);
        ItemStack itemStack = ItemStack.fromNbt(firstItem.getCompound("Item"));
        int itemCount = firstItem.getInt("Count");
        int takeCount = Math.min(itemCount, itemStack.getMaxCount());
        if (maxAmount != null) {
            takeCount = Math.min(takeCount, maxAmount);
        }

        itemStack.setCount(takeCount);

        if (takeCount >= itemCount) {
            itemsList.remove(0);
            if (itemsList.isEmpty()) {
                book.removeSubNbt("Items");
            }
        } else {
            firstItem.putInt("Count", itemCount - takeCount);
        }

        saveOccupancy(bookNbt, getFullness(book), itemsList.size());

        return Optional.of(itemStack);
    }

    public static List<ItemStack> extractItems(ItemStack book, ExtractionRequest request, boolean apply) {
        if (request.isSatisfied())
            return Collections.emptyList();

        NbtCompound bookNbt = book.getOrCreateNbt();
        if (!bookNbt.contains("Items"))
            return Collections.emptyList();

        NbtList itemsList = bookNbt.getList("Items", 10);
        if (itemsList.isEmpty())
            return Collections.emptyList();

        ImmutableList.Builder<ItemStack> builder = ImmutableList.builder();

        for (int i = 0; i < itemsList.size(); i++) {
            NbtCompound nbtItem = itemsList.getCompound(i);
            ItemStack itemStack = ItemStack.fromNbt(nbtItem.getCompound("Item"));

            if (request.matches(itemStack.getItem())) {
                int itemCount = nbtItem.getInt("Count");
                int extractAmount = Math.min(itemCount, request.getAmountUnsatisfied());
                int stackSize = itemStack.getItem().getMaxCount();

                request.satisfy(extractAmount);
                if (apply) {
                    if (extractAmount >= itemCount) {
                        itemsList.remove(i);
                        i -= 1;
                    } else {
                        nbtItem.putInt("Count", itemCount - extractAmount);
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
        }

        if (itemsList.isEmpty()) {
            book.removeSubNbt("Items");
        }

        saveOccupancy(bookNbt, getFullness(book), itemsList.size());

        return builder.build();
    }

    public static void saveOccupancy(NbtCompound bookNbt, int stacks, int types) {
        bookNbt.putInt(OCCUPIED_STACKS_TAG, stacks);
        bookNbt.putInt(OCCUPIED_TYPES_TAG, types);
    }

    // TODO get better sounds and make them actually work on servers
    public static void playRemoveOneSound(Entity entity) {
        entity.playSound(SoundEvents.ITEM_BUNDLE_REMOVE_ONE, 0.8f, 0.8f + entity.getWorld().getRandom().nextFloat() * 0.4f);
    }

    public static void playInsertSound(Entity entity) {
        entity.playSound(SoundEvents.ITEM_BUNDLE_INSERT, 0.8f, 0.8f + entity.getWorld().getRandom().nextFloat() * 0.4f);
    }

    public boolean isEmpty(ItemStack book) {
        return !book.getOrCreateNbt().contains("Items");
    }

    @Override
    public void appendTooltip(ItemStack book, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        for (Text text : getContents(book).getTextList()) {
            tooltip.add(text.copy().formatted(Formatting.GRAY));
        }

        var nbt = book.getOrCreateNbt();

        var stacksOccupied = nbt.getInt(InventoryBookItem.OCCUPIED_STACKS_TAG);
        var stacksTotal = getMaxStack(book) * 64;
        double stacksFullRatio = (double) stacksOccupied / stacksTotal;
        var typesOccupied = nbt.getInt(InventoryBookItem.OCCUPIED_TYPES_TAG);
        var typesTotal = getMaxTypes(book);
        double typesFullRatio = (double) typesOccupied / typesTotal;

        super.appendTooltip(book, world, tooltip, context);
        tooltip.add(new LiteralText(""));
        tooltip.add(new TranslatableText("item.mystical_index.custom_book.tooltip.capacity")
                .formatted(Formatting.GRAY));
        tooltip.add(new TranslatableText("item.mystical_index.custom_book.tooltip.stacks",
                stacksOccupied, stacksTotal)
                .formatted(stacksFullRatio < 0.75 ? Formatting.GREEN :
                        stacksFullRatio == 1 ? Formatting.RED : Formatting.GOLD));
        tooltip.add(new TranslatableText("item.mystical_index.custom_book.tooltip.types",
                typesOccupied, typesTotal)
                .formatted(typesFullRatio < 0.75 ? Formatting.GREEN :
                        typesFullRatio == 1 ? Formatting.RED : Formatting.GOLD));

        super.appendTooltip(book, world, tooltip, context);
    }

    @Override
    public boolean hasGlint(ItemStack book) {
        return !isEmpty(book);
    }
}