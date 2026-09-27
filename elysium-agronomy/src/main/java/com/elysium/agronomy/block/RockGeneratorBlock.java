package com.elysium.agronomy.block;

import com.elysium.lib.item.ElysiumSlots;
import com.elysium.agronomy.registry.AgronomyBlockEntities;
import com.elysium.agronomy.rules.AgronomyRules;
import com.elysium.industry.block.GridBlock;
import com.elysium.industry.block.GridBlockEntity;
import com.elysium.industry.rules.IndustryRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * A rock generator: the oldest cheat in Minecraft, made explicit and priced.
 *
 * <h2>Why ship one at all</h2>
 *
 * Because everybody builds one anyway. A cobblestone generator made out of
 * water, lava and a piston has been in every pack since packs existed, it is
 * fiddly, it is ugly, and it teaches nothing after the first time. Shipping
 * the block is not adding a cheat, it is admitting one — and once it is a
 * block it can be <em>tiered</em>, which the lava-and-piston version never
 * could be.
 *
 * <p>The first rung needs no power at all, because that is the one a player
 * builds on day two and gating it behind a grid would just send them back to
 * the piston. Every rung above it is a machine on Age II's grid like anything
 * else: constant draw, share of full speed, slow rather than stopped.
 */
public class RockGeneratorBlock extends GridBlock {

    public static final MapCodec<RockGeneratorBlock> CODEC =
            simpleCodec(properties -> new RockGeneratorBlock(properties, 0));

    /**
     * What each vein actually drops into the slot.
     *
     * <p>Written down here because it was not, at first: every generator in
     * the table was built with a tier and nothing else, so the gold generator
     * produced cobblestone, the diamond generator produced cobblestone, and
     * all seven were the same block with different sides. Nothing would have
     * said so until somebody built one and waited.
     */
    public static final Map<String, Item> FROM_VEIN = Map.of(
            "stone", Items.COBBLESTONE,
            "copper", Items.RAW_COPPER,
            "iron", Items.RAW_IRON,
            "gold", Items.RAW_GOLD,
            "diamond", Items.DIAMOND,
            "emerald", Items.EMERALD,
            "netherite_scrap", Items.NETHERITE_SCRAP);

    private final int tier;

    /** The vein this one is tuned to, or null for a plain rock generator. */
    @Nullable
    private final AgronomyRules.Vein vein;

    public RockGeneratorBlock(Properties properties, int tier) {
        this(properties, tier, null);
    }

    public RockGeneratorBlock(Properties properties, AgronomyRules.Vein vein) {
        this(properties, vein.tier(), vein);
    }

    private RockGeneratorBlock(Properties properties, int tier,
                               @Nullable AgronomyRules.Vein vein) {
        super(properties);
        this.tier = Math.max(0, Math.min(AgronomyRules.ROCK_PER_HOUR.length - 1, tier));
        this.vein = vein;
        if (vein != null && !FROM_VEIN.containsKey(vein.material())) {
            throw new IllegalStateException("there is a vein of " + vein.material()
                    + " and nothing said what comes out of it");
        }
    }

    public int tier() {
        return tier;
    }

    /** The vein this one makes, or null if it just makes rock. */
    @Nullable
    public AgronomyRules.Vein vein() {
        return vein;
    }

    /** What comes out of this one. */
    public Item makes() {
        return vein == null ? Items.COBBLESTONE : FROM_VEIN.get(vein.material());
    }

    /** How much of it comes out in an hour, at a given share of the grid. */
    public float perHour(float share) {
        if (vein == null) {
            return AgronomyRules.rockPerHour(tier, share);
        }
        return vein.perHour() * Math.max(0.0F, Math.min(1.0F, share));
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RockGeneratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, AgronomyBlockEntities.ROCK_GENERATOR.get());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof RockGeneratorBlockEntity rock)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack out = rock.takeAll();
            if (!out.isEmpty()) {
                player.getInventory().placeItemBackInInventory(out);
            } else {
                player.displayClientMessage(rock.says(), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof RockGeneratorBlockEntity rock) {
            Containers.dropContents(level, pos, rock.spill());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    /** The working half: one output slot and a rate. */
    public static class RockGeneratorBlockEntity extends GridBlockEntity {

        public static final int OUT = 0;

        private final ItemStackHandler items = new ItemStackHandler(1) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return false;                  // it only ever gives
            }
        };

        private int worked;

        public RockGeneratorBlockEntity(BlockPos pos, BlockState state) {
            super(AgronomyBlockEntities.ROCK_GENERATOR.get(), pos, state);
        }

        public ItemStackHandler items() {
            return items;
        }

        /** What this one makes, asked of the block rather than assumed. */
        private Item makes() {
            return getBlockState().getBlock() instanceof RockGeneratorBlock rock
                    ? rock.makes() : Items.COBBLESTONE;
        }

        /** How much of it an hour, at whatever the grid can afford. */
        private float rate() {
            return getBlockState().getBlock() instanceof RockGeneratorBlock rock
                    ? rock.perHour(fed()) : 0.0F;
        }

        private int tier() {
            return getBlockState().getBlock() instanceof RockGeneratorBlock rock
                    ? rock.tier() : 0;
        }

        @Override
        public IndustryRules.Voltage voltage() {
            return AgronomyRules.rockDraw(tier()).rated();
        }

        /**
         * What it asks the grid for.
         *
         * Nothing at the first rung, which is the whole point of that rung,
         * and a constant above it.
         */
        @Nullable
        @Override
        public IndustryRules.Machine machine() {
            IndustryRules.Machine draw = AgronomyRules.rockDraw(tier());
            return draw.draw() <= 0.0F ? null : draw;
        }

        /** How fast it is running: everything at the first rung, a share above. */
        private float fed() {
            return tier() == 0 ? 1.0F : share();
        }

        public ItemStack takeAll() {
            return items.extractItem(OUT, items.getStackInSlot(OUT).getCount(), false);
        }

        public SimpleContainer spill() {
            return new SimpleContainer(items.getStackInSlot(OUT));
        }

        public Component says() {
            float rate = rate();
            if (rate <= 0.0F) {
                return Component.translatable("elysiumagronomy.rock.dark");
            }
            return Component.translatable("elysiumagronomy.rock.running",
                    String.format("%,.0f", rate));
        }

        @Override
        protected void serverTick(Level level, BlockPos pos, BlockState state) {
            float perHour = rate();
            if (perHour <= 0.0F) {
                return;
            }
            worked += Math.max(1, Math.round(perHour * AgronomyRules.WORK_FULL));
            int whole = AgronomyRules.TICKS_PER_HOUR * AgronomyRules.WORK_FULL;
            while (worked >= whole) {
                worked -= whole;
                ItemStack rock = new ItemStack(makes());
                if (!ElysiumSlots.roomFor(items, OUT, rock)) {
                    worked = 0;                // full: stop counting rather than pile up
                    return;
                }
                items.setStackInSlot(OUT, grow(items.getStackInSlot(OUT), rock));
            }
            setChanged();
        }

        private static ItemStack grow(ItemStack there, ItemStack added) {
            if (there.isEmpty()) {
                return added;
            }
            ItemStack more = there.copy();
            more.grow(added.getCount());
            return more;
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            items.deserializeNBT(registries, tag.getCompound("items"));
            worked = Math.max(0, tag.getInt("worked"));
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.put("items", items.serializeNBT(registries));
            tag.putInt("worked", worked);
        }
    }
}
