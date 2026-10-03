package org.mocoserver.sophisticatedgemcase.storage

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.component.CustomData
import org.mocoserver.sophisticatedgemcase.core.GemFilter
import org.mocoserver.sophisticatedgemcase.core.GemFilterMode
import org.mocoserver.sophisticatedgemcase.core.GemTransferRule
import org.mocoserver.sophisticatedgemcase.core.GemTransferSettings
import java.nio.charset.StandardCharsets

/** 設定のschema検証と固定順保存を行う。破損rawは呼出元がそのまま保持する。 */
internal object GemSettingsCodec {
    const val MAX_TEXT_BYTES = 256
    const val MAX_ENTRIES = 4096
    private val purityPattern = Regex("[a-z0-9_]+")

    fun read(data: CustomData?): GemSettingsState {
        if (data == null) return GemSettingsState.Ready(GemTransferSettings.DEFAULT)
        return try {
            @Suppress("DEPRECATION")
            val tag = data.unsafe
            GemNbtBudget.check(tag)
            parse(tag)
        } catch (invalid: InvalidGemStorage) {
            GemSettingsState.Locked(invalid.message ?: "settings")
        }
    }

    private fun parse(tag: CompoundTag): GemSettingsState {
        if (tag.allKeys == setOf("client_summary", "locked")) {
            valid(
                tag.contains("client_summary", Tag.TAG_BYTE.toInt()) && tag.contains("locked", Tag.TAG_BYTE.toInt()),
                "summary",
            )
            val summary = tag.getByte("client_summary").toInt()
            val locked = tag.getByte("locked").toInt()
            valid((summary == 0 || summary == 1) && (locked == 0 || locked == 1), "summary")
            if (summary == 1) return GemSettingsState.ClientSummary(locked == 1)
        }
        valid(
            tag.allKeys == setOf("schema_version", "deposit", "restock"),
            "schema",
        )
        valid(tag.contains("schema_version", Tag.TAG_INT.toInt()) && tag.getInt("schema_version") == 1, "schema")
        var entries = 0
        val deposit =
            parseRule(tag.get("deposit"), {
                entries += it
                entries <= MAX_ENTRIES
            })
        val restock =
            parseRule(tag.get("restock"), {
                entries += it
                entries <= MAX_ENTRIES
            })
        return GemSettingsState.Ready(GemTransferSettings(deposit, restock))
    }

    private fun parseRule(
        value: Tag?,
        addEntries: (Int) -> Boolean,
    ): GemTransferRule {
        valid(value is CompoundTag, "rule")
        val tag = value as CompoundTag
        valid(tag.allKeys == setOf("enabled", "mode", "gem_ids", "purities"), "rule")
        valid(tag.contains("enabled", Tag.TAG_BYTE.toInt()), "rule")
        val enabledByte = tag.getByte("enabled").toInt()
        valid(enabledByte == 0 || enabledByte == 1, "rule")
        valid(tag.contains("mode", Tag.TAG_STRING.toInt()), "rule")
        val mode =
            when (tag.getString("mode")) {
                "all" -> GemFilterMode.ALL
                "include" -> GemFilterMode.INCLUDE
                "exclude" -> GemFilterMode.EXCLUDE
                else -> throw InvalidGemStorage("mode")
            }
        val ids = parseStrings(tag.get("gem_ids")) { validTextValue(it, true) }
        val purities = parseStrings(tag.get("purities")) { validTextValue(it, false) }
        valid(addEntries(ids.size + purities.size), "size")
        return GemTransferRule(enabledByte == 1, GemFilter.of(mode, ids, purities))
    }

    private fun parseStrings(
        value: Tag?,
        validate: (String) -> Unit,
    ): Set<String> {
        valid(value is ListTag, "list")
        val list = value as ListTag
        val result = linkedSetOf<String>()
        for (entry in list) {
            valid(entry is StringTag, "list")
            val text = (entry as StringTag).asString
            validate(text)
            valid(result.add(text), "duplicate")
        }
        return result
    }

    fun write(settings: GemTransferSettings): CustomData = CustomData.of(encode(settings))

    fun canWrite(settings: GemTransferSettings): Boolean =
        try {
            encode(settings)
            true
        } catch (_: InvalidGemStorage) {
            false
        }

    private fun encode(settings: GemTransferSettings): CompoundTag {
        val total =
            settings.deposit.filter.gemIds.size + settings.deposit.filter.purities.size +
                settings.restock.filter.gemIds.size + settings.restock.filter.purities.size
        valid(total <= MAX_ENTRIES, "size")
        val root =
            CompoundTag().apply {
                putInt("schema_version", 1)
                put("deposit", encodeRule(settings.deposit))
                put("restock", encodeRule(settings.restock))
            }
        GemNbtBudget.check(root)
        return root
    }

    private fun encodeRule(rule: GemTransferRule): CompoundTag {
        val filter = rule.filter
        filter.gemIds.forEach { validTextValue(it, true) }
        filter.purities.forEach { validTextValue(it, false) }
        return CompoundTag().apply {
            putByte("enabled", if (rule.enabled) 1 else 0)
            putString("mode", filter.mode.name.lowercase())
            put("gem_ids", ListTag().apply { filter.gemIds.sorted().forEach { add(StringTag.valueOf(it)) } })
            put("purities", ListTag().apply { filter.purities.sorted().forEach { add(StringTag.valueOf(it)) } })
        }
    }

    private fun validTextValue(
        value: String,
        gemId: Boolean,
    ) {
        val validValue =
            validText(value) &&
                if (gemId) ResourceLocation.tryParse(value)?.toString() == value else purityPattern.matches(value)
        valid(validValue, if (gemId) "id" else "purity")
    }

    private fun validText(value: String): Boolean =
        value.isNotEmpty() && value.toByteArray(StandardCharsets.UTF_8).size <= MAX_TEXT_BYTES
}

private fun valid(
    condition: Boolean,
    reason: String,
) {
    if (!condition) throw InvalidGemStorage(reason)
}
