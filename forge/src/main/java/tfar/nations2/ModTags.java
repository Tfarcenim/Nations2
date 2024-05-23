package tfar.nations2;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class ModTags {

    public static final TagKey<Block> CLAIM_RESISTANT = new TagKey<>(Registries.BLOCK,new ResourceLocation(Nations2.MOD_ID,"claim_resistant"));

}
