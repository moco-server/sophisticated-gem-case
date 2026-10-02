package org.mocoserver.sophisticatedgemcase

import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.component.CustomData
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
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
    private val summaryCodec =
        object : StreamCodec<RegistryFriendlyByteBuf, CustomData> {
            override fun encode(
                buffer: RegistryFriendlyByteBuf,
                value: CustomData,
            ) {
                val state = GemStorageCodec.read(value)
                buffer.writeBoolean(state !is GemStorageState.Ready || state.counts.entries().isNotEmpty())
                buffer.writeBoolean(state !is GemStorageState.Ready)
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
