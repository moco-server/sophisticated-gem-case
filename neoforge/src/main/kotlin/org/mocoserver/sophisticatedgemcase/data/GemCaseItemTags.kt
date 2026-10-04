package org.mocoserver.sophisticatedgemcase.data

import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.data.tags.ItemTagsProvider
import net.minecraft.data.tags.TagsProvider
import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.common.data.ExistingFileHelper
import org.mocoserver.sophisticatedgemcase.ModItems
import org.mocoserver.sophisticatedgemcase.ModTemplate
import java.util.concurrent.CompletableFuture
import net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems as BackpackItems

/** native装着判定が使う作者タグへ上下両版を追加する。block tagからのコピーは不要。 */
internal class GemCaseItemTags(
    output: PackOutput,
    lookup: CompletableFuture<HolderLookup.Provider>,
    helper: ExistingFileHelper,
) : ItemTagsProvider(
        output,
        lookup,
        CompletableFuture.completedFuture(TagsProvider.TagLookup.empty<Block>()),
        ModTemplate.ID,
        helper,
    ) {
    override fun addTags(provider: HolderLookup.Provider) {
        tag(BackpackItems.BACKPACK_UPGRADE_TAG)
            .add(ModItems.gemCaseUpgrade.get(), ModItems.advancedGemCaseUpgrade.get())
    }
}
