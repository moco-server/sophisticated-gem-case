package org.mocoserver.sophisticatedgemcase.storage

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.world.item.component.CustomData

/** schema 1の補充設定をraw保持可能なCustomDataとして読み書きする。 */
internal object GemProcessingSettingsCodec {
    fun read(data: CustomData?): GemProcessingSettingsState {
        if (data == null) return GemProcessingSettingsState.Ready(useBackpackMaterials = false)
        return try {
            @Suppress("DEPRECATION")
            val tag = data.unsafe
            GemMaterialNbtBudget.check(tag)
            parse(tag)
        } catch (invalid: InvalidGemMaterial) {
            GemProcessingSettingsState.Locked(invalid.message ?: "settings")
        }
    }

    private fun parse(tag: CompoundTag): GemProcessingSettingsState {
        if (tag.allKeys == setOf("client_summary", "use_backpack_materials", "locked")) {
            requireTag(tag, "client_summary", Tag.TAG_BYTE)
            requireTag(tag, "use_backpack_materials", Tag.TAG_BYTE)
            requireTag(tag, "locked", Tag.TAG_BYTE)
            val summary = tag.getByte("client_summary").toInt()
            val enabled = tag.getByte("use_backpack_materials").toInt()
            val locked = tag.getByte("locked").toInt()
            valid(summary == 1 && enabled in 0..1 && locked in 0..1, "summary")
            return GemProcessingSettingsState.ClientSummary(enabled == 1, locked == 1)
        }
        valid(tag.allKeys == setOf("schema_version", "use_backpack_materials"), "schema")
        requireTag(tag, "schema_version", Tag.TAG_INT)
        requireTag(tag, "use_backpack_materials", Tag.TAG_BYTE)
        valid(tag.getInt("schema_version") == 1, "schema")
        val enabled = tag.getByte("use_backpack_materials").toInt()
        valid(enabled == 0 || enabled == 1, "settings")
        return GemProcessingSettingsState.Ready(enabled == 1)
    }

    fun write(useBackpackMaterials: Boolean): CustomData =
        CustomData.of(
            CompoundTag().apply {
                putInt("schema_version", 1)
                putByte("use_backpack_materials", if (useBackpackMaterials) 1 else 0)
            },
        )

    private fun requireTag(
        tag: CompoundTag,
        key: String,
        type: Byte,
    ) {
        valid(tag.contains(key, type.toInt()), "schema")
    }

    private fun valid(
        condition: Boolean,
        reason: String,
    ) {
        if (!condition) throw InvalidGemMaterial(reason)
    }
}
