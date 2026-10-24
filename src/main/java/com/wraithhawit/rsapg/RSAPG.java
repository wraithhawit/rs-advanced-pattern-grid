package com.wraithhawit.rsapg;

import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.content.BlockEntities;
import com.refinedmods.refinedstorage.common.content.ContentIds;
import com.refinedmods.refinedstorage.common.content.Items;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Refined Storage - Advanced Pattern Grid.
 *
 * <p>The advanced grid is Refined Storage's own pattern grid block entity and menu, placed by our
 * block. Everything RS and its addons hook on the pattern grid (EMI/JEI recipe transfer, Refined
 * Fluid Substitution, rstweaks) keeps working. Our mixins switch on only when the block is ours:
 * blanks come from the network instead of a slot, an Autocrafting Upgrade gets blanks crafted, a
 * recipe that qualifies for fluid substitution gets an RFS pattern, and fuzzy mode is left off a
 * pattern it would not change.
 */
@Mod(RSAPG.MOD_ID)
public final class RSAPG {
    public static final String MOD_ID = "rsapg";
    public static final Logger LOG = LoggerFactory.getLogger("rsapg");

    public RSAPG(final IEventBus modBus) {
        ModBlocks.register(modBus);
        modBus.addListener(RSAPG::onBlockEntityValidBlocks);
        modBus.addListener(RSAPG::onCreativeTab);
        modBus.addListener(ModNetwork::register);
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(RSAPG::registerUpgrades));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> SelfTest.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> RecipeDiagnostics.register(event.getDispatcher()));
    }

    public static ResourceLocation id(final String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Lets RS's pattern grid block entity type live in our blocks. */
    private static void onBlockEntityValidBlocks(final BlockEntityTypeAddBlocksEvent event) {
        final Block[] blocks = ModBlocks.ADVANCED_PATTERN_GRID.toArray();
        event.modify(BlockEntities.INSTANCE.getPatternGrid(), blocks);
        LOG.info("added {} advanced pattern grid blocks to refinedstorage:pattern_grid", blocks.length);
    }

    private static void registerUpgrades() {
        RefinedStorageApi.INSTANCE.getUpgradeRegistry()
            .forDestination(GridUpgradeDestination.ADVANCED_PATTERN_GRID)
            .add(Items.INSTANCE.getAutocraftingUpgrade(), 1);
        LOG.info("registered the advanced pattern grid as an RS upgrade destination");
    }

    private static void onCreativeTab(final BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ResourceKey.create(Registries.CREATIVE_MODE_TAB, ContentIds.CREATIVE_MODE_TAB))) {
            ModBlocks.ITEMS.getEntries().forEach(item -> event.accept(item.get()));
        }
    }
}
