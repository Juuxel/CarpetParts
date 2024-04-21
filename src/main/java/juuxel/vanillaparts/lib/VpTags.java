package juuxel.vanillaparts.lib;

import juuxel.vanillaparts.VanillaParts;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

public final class VpTags {
    public static final TagKey<Block> EXCLUDED = TagKey.of(RegistryKeys.BLOCK, VanillaParts.id("excluded"));

    public static void init() {
    }
}
