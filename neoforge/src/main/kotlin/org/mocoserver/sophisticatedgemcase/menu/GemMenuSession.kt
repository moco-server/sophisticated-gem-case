package org.mocoserver.sophisticatedgemcase.menu

import net.minecraft.core.HolderLookup
import net.minecraft.world.item.ItemStack
import org.mocoserver.sophisticatedgemcase.core.GemFilterMode
import org.mocoserver.sophisticatedgemcase.core.GemKey
import org.mocoserver.sophisticatedgemcase.core.GemTransferSettings
import org.mocoserver.sophisticatedgemcase.network.GemPage
import org.mocoserver.sophisticatedgemcase.network.GemPages
import org.mocoserver.sophisticatedgemcase.network.GemSettingsPage
import org.mocoserver.sophisticatedgemcase.network.GemSettingsPages
import org.mocoserver.sophisticatedgemcase.processing.GemRecipeEpoch
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialState
import org.mocoserver.sophisticatedgemcase.storage.GemProcessingSettingsState
import org.mocoserver.sophisticatedgemcase.storage.GemSettingsState
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState
import org.mocoserver.sophisticatedgemcase.upgrade.GemCaseUpgradeWrapper
import java.util.UUID

/** menuの生存期間だけ持つ世代。upgrade差し替えでは別sessionを発行する。 */
internal class GemMenuSession(
    val storageId: UUID?,
    val upgradeStack: ItemStack,
    initial: GemStorageState,
    initialSettings: GemSettingsState,
) {
    val id: UUID = UUID.randomUUID()
    var revision: Long = 0
        private set
    private var state = initial
    private var pages = GemPages.partition(initial)
    private var settingsState = initialSettings
    private var settingsPages = GemSettingsPages.partition(initialSettings)
    var sentRevision: Long = -1
    private var requestTick = Long.MIN_VALUE
    private var requests = 0
    private var materialsState: GemMaterialState? = null
    private var processingSettingsState: GemProcessingSettingsState? = null
    private var recipeEpoch: Long = GemRecipeEpoch.current()
    var processingOutput: GemKey? = null
    var previewRevision: Long = -1
    var processingGemId: String? = null
    var processingSelectionId: Long = -1
    var batchPreviews: Boolean = false
    var processingPreviewOutputs: Set<GemKey> = emptySet()

    fun refresh(
        wrapper: GemCaseUpgradeWrapper,
        registries: HolderLookup.Provider,
    ) {
        val current = wrapper.snapshot()
        val currentSettings = wrapper.settingsSnapshot()
        val materials = wrapper.materialsSnapshot(registries)
        val processingSettings = wrapper.processingSettingsSnapshot()
        val epoch = GemRecipeEpoch.current()
        val processingChanged =
            materialsState != null &&
                (materialsState != materials || processingSettingsState != processingSettings || recipeEpoch != epoch)
        if (state != current || settingsState != currentSettings || processingChanged) {
            state = current
            settingsState = currentSettings
            revision++
            pages = GemPages.partition(current)
            settingsPages = GemSettingsPages.partition(currentSettings)
            sentRevision = -1
            previewRevision = -1
            processingPreviewOutputs = emptySet()
        }
        materialsState = materials
        processingSettingsState = processingSettings
        recipeEpoch = epoch
    }

    fun settingsSnapshot(): GemSettingsState = settingsState

    fun matchesStorage(
        storageId: UUID?,
        stack: ItemStack,
    ): Boolean = this.storageId == storageId && upgradeStack === stack

    fun validPage(index: Int): Boolean = index in pages.indices

    fun page(
        menuId: Int,
        slot: Int,
        index: Int,
    ): GemPage = GemPage(menuId, slot, id, revision, index, pages.size, pages[index], state !is GemStorageState.Ready)

    fun settingsPage(
        menuId: Int,
        slot: Int,
        index: Int,
    ): GemSettingsPage {
        val ready = settingsState as? GemSettingsState.Ready
        val settings = ready?.settings ?: GemTransferSettings.DEFAULT
        return GemSettingsPage(
            menuId,
            slot,
            id,
            revision,
            index,
            settingsPages.size,
            settingsState !is GemSettingsState.Ready,
            settings.deposit.enabled,
            settings.restock.enabled,
            settings.deposit.filter.mode
                .takeUnless { settingsState !is GemSettingsState.Ready } ?: GemFilterMode.ALL,
            settings.restock.filter.mode
                .takeUnless { settingsState !is GemSettingsState.Ready } ?: GemFilterMode.ALL,
            settingsPages[index],
        )
    }

    fun validSettingsPage(index: Int): Boolean = index in settingsPages.indices

    fun allowRequest(tick: Long): Boolean {
        if (requestTick != tick) {
            requestTick = tick
            requests = 0
        }
        requests++
        return requests <= MAX_REQUESTS_PER_TICK
    }

    companion object {
        private const val MAX_REQUESTS_PER_TICK = 16
    }
}
