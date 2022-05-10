package com.technicalitiesmc.scm.component.digital;

import com.mojang.math.Vector3f;
import com.technicalitiesmc.lib.circuit.component.*;
import com.technicalitiesmc.lib.circuit.interfaces.RedstoneSink;
import com.technicalitiesmc.lib.math.VecDirection;
import com.technicalitiesmc.scm.component.InterfaceLookup;
import com.technicalitiesmc.scm.init.SCMComponents;
import com.technicalitiesmc.scm.init.SCMItemTags;
import com.technicalitiesmc.scm.init.SCMItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class NoteComponent extends DigitalComponentBase<NoteComponent> {

    private static final AABB BOUNDS = new AABB(0, 0, 0, 1, 6 / 16D, 1);

    private static final Property<Instrument> PROP_INSTRUMENT = EnumProperty.create("instrument", Instrument.class);

    private static final InterfaceLookup<NoteComponent> INTERFACES = InterfaceLookup.<NoteComponent>builder()
            .with(RedstoneSink.class, DigitalComponentBase.DEFAULT_INPUT_SIDES, RedstoneSink::instance)
            .build();

    // Internal state
    private boolean previousInput = false;

    // External state
    private boolean state = false;
    private Instrument instrument = Instrument.HARP;
    private int note = 0;

    public NoteComponent(ComponentContext context) {
        super(SCMComponents.NOTE, context, INTERFACES);
    }

    @Override
    public ComponentState getState() {
        return super.getState().setValue(PROP_INSTRUMENT, instrument);
    }

    @Override
    public AABB getBoundingBox() {
        return BOUNDS;
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(SCMItems.NOTE.get());
    }

    @Override
    protected boolean beforeCheckInputs(ComponentEventMap events, boolean tick) {
        // If we're running a scheduled tick, play note
        if (tick) {
            var input = getInputs() != 0;
            if (input && !previousInput) {
                playNote();
            }
            previousInput = input;
        }
        return true;
    }

    @Override
    protected void onNewInputs(boolean tick, byte newInputs) {
        // If we're generating a new output, schedule an update
        if ((getInputs() == 0) != (newInputs == 0)) {
            scheduleTick(1);
        }
    }

    @Override
    public InteractionResult use(Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
        var stack = player.getItemInHand(hand);
        if (!stack.isEmpty() && stack.is(SCMItemTags.WRENCHES)) {
            var newNote = (note + 1) % 24;
            updateExternalState(false, () -> {
                note = newNote;
                playNote();
            });
        } else if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
            var newInstrument = Instrument.byState(blockItem.getBlock().defaultBlockState());
            if (newInstrument != instrument) {
                updateExternalState(true, () -> {
                    instrument = newInstrument;
                    playNote();
                });
            }
        } else {
            playNote();
        }
        return InteractionResult.sidedSuccess(false);
    }

    // Serialization

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag = super.save(tag);
        tag.putBoolean("previous_input", previousInput);
        tag.putBoolean("state", state);
        tag.putInt("instrument", instrument.ordinal());
        tag.putInt("note", note);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        previousInput = tag.getBoolean("previous_input");
        state = tag.getBoolean("state");
        instrument = Instrument.VALUES[tag.getInt("instrument")];
        note = tag.getInt("note");
    }

    // Helpers

    private void playNote() {
        var pitch = (float) Math.pow(2, (note - 12) / 12D);
        playSound(instrument.getInstrument().getSoundEvent(), SoundSource.RECORDS, 1.0f, pitch);
    }

    public static void createState(ComponentStateBuilder builder) {
        builder.add(PROP_INSTRUMENT);
    }

    public static class Client extends ClientComponent {

        @Override
        public AABB getBoundingBox(ComponentState state) {
            return BOUNDS;
        }

        @Override
        public ItemStack getPickedItem(ComponentState state) {
            return new ItemStack(SCMItems.NOTE.get());
        }

        @Override
        public InteractionResult use(ComponentState state, Player player, InteractionHand hand, VecDirection sideHit, Vector3f hit) {
            return InteractionResult.sidedSuccess(true);
        }

    }

    public enum Instrument implements StringRepresentable {
        HARP(NoteBlockInstrument.HARP, Blocks.AIR.defaultBlockState()),
        BASSDRUM(NoteBlockInstrument.BASEDRUM, Blocks.STONE.defaultBlockState()),
        SNARE(NoteBlockInstrument.SNARE, Blocks.SAND.defaultBlockState()),
        HAT(NoteBlockInstrument.HAT, Blocks.GLASS.defaultBlockState()),
        BASS(NoteBlockInstrument.BASS, Blocks.OAK_WOOD.defaultBlockState()),
        FLUTE(NoteBlockInstrument.FLUTE, Blocks.CLAY.defaultBlockState()),
        BELL(NoteBlockInstrument.BELL, Blocks.GOLD_BLOCK.defaultBlockState()),
        GUITAR(NoteBlockInstrument.GUITAR, Blocks.WHITE_WOOL.defaultBlockState()),
        CHIME(NoteBlockInstrument.CHIME, Blocks.PACKED_ICE.defaultBlockState()),
        XYLOPHONE(NoteBlockInstrument.XYLOPHONE, Blocks.BONE_BLOCK.defaultBlockState()),
        IRON_XYLOPHONE(NoteBlockInstrument.IRON_XYLOPHONE, Blocks.IRON_BLOCK.defaultBlockState()),
        COWBELL(NoteBlockInstrument.COW_BELL, Blocks.SOUL_SAND.defaultBlockState()),
        DIDGERIDOO(NoteBlockInstrument.DIDGERIDOO, Blocks.PUMPKIN.defaultBlockState()),
        BIT(NoteBlockInstrument.BIT, Blocks.EMERALD_BLOCK.defaultBlockState()),
        BANJO(NoteBlockInstrument.BANJO, Blocks.HAY_BLOCK.defaultBlockState()),
        PLING(NoteBlockInstrument.PLING, Blocks.GLOWSTONE.defaultBlockState());

        private static final Instrument[] VALUES = values();
        private static final Map<NoteBlockInstrument, Instrument> INSTRUMENT_MAP = Arrays.stream(VALUES)
                .collect(Collectors.toMap(i -> i.instrument, i -> i));

        private final NoteBlockInstrument instrument;
        private final BlockState blockState;

        Instrument(NoteBlockInstrument instrument, BlockState blockState) {
            this.instrument = instrument;
            this.blockState = blockState;
        }

        public NoteBlockInstrument getInstrument() {
            return instrument;
        }

        public BlockState getBlockState() {
            return blockState;
        }

        @Override
        public String getSerializedName() {
            return instrument.getSerializedName();
        }

        public static Instrument byState(BlockState state) {
            return INSTRUMENT_MAP.get(NoteBlockInstrument.byState(state));
        }

    }

}
