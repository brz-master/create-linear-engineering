package io.github.brzmaster.createlinearengineering;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(CreateLinearEngineering.ID)
public final class CreateLinearEngineering {
    public static final String ID = "create_linear_engineering";
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, ID);
    public static final DeferredHolder<Block, LinearBlock> RAIL = BLOCKS.register("linear_rail", () -> new LinearBlock(false, properties()));
    public static final DeferredHolder<Block, LinearBlock> CARRIAGE = BLOCKS.register("linear_carriage", () -> new LinearBlock(true, properties()));
    public static final DeferredHolder<Block, LinearBlock> SCREW_RAIL = BLOCKS.register("screw_rail", () -> new LinearBlock(false, true, properties()));
    public static final DeferredHolder<Block, LinearBlock> SCREW_CARRIAGE = BLOCKS.register("screw_carriage", () -> new LinearBlock(true, true, properties()));
    public static final DeferredHolder<Item, RailSensorItem> SENSOR = ITEMS.register("rail_sensor", () -> new RailSensorItem(new Item.Properties()));
    public static final DeferredHolder<Item, GreaseItem> LOW_GREASE = ITEMS.register("low_viscosity_grease", () -> new GreaseItem(Grease.LOW, new Item.Properties()));
    public static final DeferredHolder<Item, GreaseItem> HIGH_GREASE = ITEMS.register("high_viscosity_grease", () -> new GreaseItem(Grease.HIGH, new Item.Properties()));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.create_linear_engineering"))
            .icon(() -> new net.minecraft.world.item.ItemStack(RAIL.get()))
            .displayItems((parameters, output) -> { output.accept(RAIL.get()); output.accept(CARRIAGE.get()); output.accept(SCREW_RAIL.get()); output.accept(SCREW_CARRIAGE.get()); output.accept(SENSOR.get()); output.accept(LOW_GREASE.get()); output.accept(HIGH_GREASE.get()); })
            .build());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CarriageEntity>> CARRIAGE_ENTITY = ENTITIES.register("linear_carriage",
            () -> BlockEntityType.Builder.of(CarriageEntity::new, CARRIAGE.get(), SCREW_CARRIAGE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RailEntity>> RAIL_ENTITY = ENTITIES.register("linear_rail",
            () -> BlockEntityType.Builder.of(RailEntity::new, RAIL.get(), SCREW_RAIL.get()).build(null));

    public CreateLinearEngineering(IEventBus bus) {
        dev.ryanhcode.sable.platform.SableEventPlatform.INSTANCE.onPostPhysicsTick(RailSensorMotion::afterPhysics);
        BLOCKS.register(bus);
        ITEMS.register("linear_rail", () -> new BlockItem(RAIL.get(), new Item.Properties()));
        ITEMS.register("linear_carriage", () -> new CarriageItem(CARRIAGE.get(), new Item.Properties()));
        ITEMS.register("screw_rail", () -> new BlockItem(SCREW_RAIL.get(), new Item.Properties()));
        ITEMS.register("screw_carriage", () -> new CarriageItem(SCREW_CARRIAGE.get(), new Item.Properties()));
        ITEMS.register(bus);
        ENTITIES.register(bus);
        TABS.register(bus);
        bus.addListener(CreateLinearEngineering::creativeTab);
    }

    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of().strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion();
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(RAIL.get());
            event.accept(CARRIAGE.get());
            event.accept(SCREW_RAIL.get());
            event.accept(SCREW_CARRIAGE.get());
            event.accept(SENSOR.get());
            event.accept(LOW_GREASE.get());
            event.accept(HIGH_GREASE.get());
        }
    }
}
