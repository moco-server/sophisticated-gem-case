package org.mocoserver.sophisticatedgemcase

import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.component.CustomData
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialCodec
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialContents
import org.mocoserver.sophisticatedgemcase.storage.GemMaterialState
import org.mocoserver.sophisticatedgemcase.storage.GemProcessingSettingsCodec
import org.mocoserver.sophisticatedgemcase.storage.GemProcessingSettingsState
import org.mocoserver.sophisticatedgemcase.storage.GemSettingsCodec
import org.mocoserver.sophisticatedgemcase.storage.GemSettingsState
import org.mocoserver.sophisticatedgemcase.storage.GemStorageCodec
import org.mocoserver.sophisticatedgemcase.storage.GemStorageState

/** 永続原文と標準slot同期を分離する。一覧は上限付きの専用ページで同期する。 */
internal object ModDataComponents {
    val registry: DeferredRegister.DataComponents =
        DeferredRegister.createDataComponents(
            Registries.DATA_COMPONENT_TYPE,
            ModTemplate.ID,
        )
    val gemStorage: DeferredHolder<DataComponentType<*>, DataComponentType<CustomData>> =
        registry.registerComponentType<CustomData>("gem_storage") { builder ->
            builder.persistent(CustomData.CODEC).networkSynchronized(summaryCodec)
        }
    val gemTransferSettings: DeferredHolder<DataComponentType<*>, DataComponentType<CustomData>> =
        registry.registerComponentType<CustomData>("gem_transfer_settings") { builder ->
            builder.persistent(CustomData.CODEC).networkSynchronized(settingsSummaryCodec)
        }
    val gemProcessingMaterials: DeferredHolder<DataComponentType<*>, DataComponentType<CustomData>> =
        registry.registerComponentType<CustomData>("gem_processing_materials") { builder ->
            builder.persistent(CustomData.CODEC).networkSynchronized(materialSummaryCodec)
        }
    val gemProcessingSettings: DeferredHolder<DataComponentType<*>, DataComponentType<CustomData>> =
        registry.registerComponentType<CustomData>("gem_processing_settings") { builder ->
            builder.persistent(CustomData.CODEC).networkSynchronized(processingSettingsSummaryCodec)
        }

    private val materialSummaryCodec =
        object : StreamCodec<RegistryFriendlyByteBuf, CustomData> {
            override fun encode(
                buffer: RegistryFriendlyByteBuf,
                value: CustomData,
            ) {
                when (val state = GemMaterialCodec.read(value, buffer.registryAccess())) {
                    is GemMaterialState.Ready -> {
                        buffer.writeBoolean(
                            (0 until GemMaterialContents.SLOT_COUNT).any { !state.contents.stackAt(it).isEmpty },
                        )
                        buffer.writeBoolean(false)
                    }
                    is GemMaterialState.ClientSummary -> {
                        buffer.writeBoolean(state.hasContents)
                        buffer.writeBoolean(state.locked)
                    }
                    is GemMaterialState.Locked -> {
                        buffer.writeBoolean(true)
                        buffer.writeBoolean(true)
                    }
                }
            }

            override fun decode(buffer: RegistryFriendlyByteBuf): CustomData =
                CustomData.of(
                    CompoundTag().apply {
                        putBoolean("client_summary", true)
                        putBoolean("has_contents", buffer.readBoolean())
                        putBoolean("locked", buffer.readBoolean())
                    },
                )
        }
    private val processingSettingsSummaryCodec =
        object : StreamCodec<RegistryFriendlyByteBuf, CustomData> {
            override fun encode(
                buffer: RegistryFriendlyByteBuf,
                value: CustomData,
            ) {
                val state = GemProcessingSettingsCodec.read(value)
                buffer.writeBoolean(
                    when (state) {
                        is GemProcessingSettingsState.Ready -> state.useBackpackMaterials
                        is GemProcessingSettingsState.ClientSummary -> state.useBackpackMaterials
                        is GemProcessingSettingsState.Locked -> false
                    },
                )
                buffer.writeBoolean(
                    when (state) {
                        is GemProcessingSettingsState.Ready -> false
                        is GemProcessingSettingsState.ClientSummary -> state.locked
                        is GemProcessingSettingsState.Locked -> true
                    },
                )
            }

            override fun decode(buffer: RegistryFriendlyByteBuf): CustomData =
                CustomData.of(
                    CompoundTag().apply {
                        putBoolean("client_summary", true)
                        putBoolean("use_backpack_materials", buffer.readBoolean())
                        putBoolean("locked", buffer.readBoolean())
                    },
                )
        }

    private val settingsSummaryCodec =
        object : StreamCodec<RegistryFriendlyByteBuf, CustomData> {
            override fun encode(
                buffer: RegistryFriendlyByteBuf,
                value: CustomData,
            ) {
                val state = GemSettingsCodec.read(value)
                buffer.writeBoolean(state !is GemSettingsState.Ready)
            }

            override fun decode(buffer: RegistryFriendlyByteBuf): CustomData =
                CustomData.of(
                    CompoundTag().apply {
                        putBoolean("client_summary", true)
                        putBoolean("locked", buffer.readBoolean())
                    },
                )
        }
    private val summaryCodec =
        object : StreamCodec<RegistryFriendlyByteBuf, CustomData> {
            override fun encode(
                buffer: RegistryFriendlyByteBuf,
                value: CustomData,
            ) {
                val state = GemStorageCodec.read(value)
                when (state) {
                    is GemStorageState.Ready -> {
                        buffer.writeBoolean(state.counts.entries().isNotEmpty())
                        buffer.writeBoolean(false)
                    }
                    is GemStorageState.ClientSummary -> {
                        buffer.writeBoolean(state.hasContents)
                        buffer.writeBoolean(state.locked)
                    }
                    is GemStorageState.Locked -> {
                        buffer.writeBoolean(true)
                        buffer.writeBoolean(true)
                    }
                }
            }

            override fun decode(buffer: RegistryFriendlyByteBuf): CustomData =
                CustomData.of(
                    CompoundTag().apply {
                        putBoolean("client_summary", true)
                        putBoolean("has_contents", buffer.readBoolean())
                        putBoolean("locked", buffer.readBoolean())
                    },
                )
        }
}
