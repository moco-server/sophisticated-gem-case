package org.mocoserver.sophisticatedgemcase.storage

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.component.CustomData
import org.mocoserver.sophisticatedgemcase.core.GemCounts
import org.mocoserver.sophisticatedgemcase.core.GemKey
import java.nio.charset.StandardCharsets

/** schemaの意味だけを検査し、未知・破損の原文を変更しない。 */
internal object GemStorageCodec {
    const val MAX_ENTRIES = 4096
    const val MAX_TEXT_BYTES = 256
    const val MAX_BYTES = 1_048_576
    private val purityPattern = Regex("[a-z0-9_]+")

    fun read(data: CustomData?): GemStorageState {
        if (data == null) return GemStorageState.Ready(GemCounts.of(emptyMap()))
        return try {
            val tag = validationView(data)
            GemNbtBudget.check(tag)
            parse(tag)
        } catch (invalid: InvalidGemStorage) {
            GemStorageState.Locked(invalid.message ?: "entry")
        }
    }

    private fun parse(tag: CompoundTag): GemStorageState {
        if (tag.allKeys == setOf("client_summary", "has_contents", "locked")) {
            val fields = listOf("client_summary", "has_contents", "locked")
            valid(
                fields.all { tag.contains(it, Tag.TAG_BYTE.toInt()) && tag.getByte(it).toInt() in 0..1 },
                "summary",
            )
            valid(tag.getByte("client_summary").toInt() == 1, "summary")
            return GemStorageState.ClientSummary(tag.getBoolean("has_contents"), tag.getBoolean("locked"))
        }
        valid(tag.allKeys == setOf("schema_version", "entries"), "schema")
        valid(tag.contains("schema_version", Tag.TAG_INT.toInt()) && tag.getInt("schema_version") == 1, "schema")
        valid(tag.contains("entries", Tag.TAG_LIST.toInt()), "schema")
        val list = requireNotNull(tag.get("entries") as? ListTag)
        valid(list.size <= MAX_ENTRIES, "size")
        val entries = linkedMapOf<GemKey, Int>()
        for (value in list) {
            val (key, count) = parseEntry(value)
            valid(entries.put(key, count) == null, "duplicate")
        }
        return GemStorageState.Ready(GemCounts.of(entries))
    }

    private fun parseEntry(value: Tag): Pair<GemKey, Int> {
        valid(value is CompoundTag, "entry")
        val entry = value as CompoundTag
        valid(entry.allKeys == setOf("gem_id", "purity", "count"), "entry")
        valid(entry.contains("gem_id", Tag.TAG_STRING.toInt()), "entry")
        valid(entry.contains("purity", Tag.TAG_STRING.toInt()) && entry.contains("count", Tag.TAG_INT.toInt()), "entry")
        val key = GemKey(entry.getString("gem_id"), entry.getString("purity"))
        validKey(key)
        val count = entry.getInt("count")
        valid(count in 1..GemCounts.CAPACITY, "entry")
        return key to count
    }

    fun write(counts: GemCounts): CustomData {
        valid(counts.entries().size <= MAX_ENTRIES, "size")
        val list = ListTag()
        counts.entries().entries.sortedWith(compareBy({ it.key.gemId }, { it.key.purity })).forEach { (key, count) ->
            validKey(key)
            list.add(
                CompoundTag().apply {
                    putString("gem_id", key.gemId)
                    putString("purity", key.purity)
                    putInt("count", count)
                },
            )
        }
        val tag =
            CompoundTag().apply {
                putInt("schema_version", 1)
                put("entries", list)
            }
        GemNbtBudget.check(tag)
        return CustomData.of(tag)
    }

    fun canWrite(counts: GemCounts): Boolean =
        try {
            write(counts)
            true
        } catch (_: InvalidGemStorage) {
            false
        }

    fun validText(value: String): Boolean =
        value.isNotEmpty() &&
            value.length <= MAX_TEXT_BYTES &&
            value.toByteArray(StandardCharsets.UTF_8).size <= MAX_TEXT_BYTES

    private fun validKey(key: GemKey) {
        valid(validText(key.gemId) && ResourceLocation.tryParse(key.gemId)?.toString() == key.gemId, "entry")
        valid(validText(key.purity) && purityPattern.matches(key.purity), "entry")
    }

    private fun valid(
        condition: Boolean,
        reason: String,
    ) {
        if (!condition) throw InvalidGemStorage(reason)
    }

    // copyTag前に上限を調べる非変更view。1.21.1の代替がなく、外部へ返さず原文には書かない。
    @Suppress("DEPRECATION")
    private fun validationView(data: CustomData): CompoundTag = data.unsafe
}

/** 想定済みの入力形式エラーだけをLockedへ変換するための例外。 */
internal class InvalidGemStorage(
    reason: String,
) : IllegalArgumentException(reason)
