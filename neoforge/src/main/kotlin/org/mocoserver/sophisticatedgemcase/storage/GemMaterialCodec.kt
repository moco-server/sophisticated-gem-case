package org.mocoserver.sophisticatedgemcase.storage

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

/** schema 1の6材料slotをregistry-aware ItemStack codecで永続化する。 */
internal object GemMaterialCodec {
    fun read(
        data: CustomData?,
        registries: HolderLookup.Provider,
    ): GemMaterialState {
        if (data == null) return GemMaterialState.Ready(GemMaterialContents.empty())
        return try {
            @Suppress("DEPRECATION")
            val tag = data.unsafe
            GemMaterialNbtBudget.check(tag)
            parse(tag, registries)
        } catch (invalid: InvalidGemMaterial) {
            GemMaterialState.Locked(invalid.message ?: "materials")
        }
    }

    private fun parse(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
    ): GemMaterialState {
        if (tag.allKeys == setOf("client_summary", "has_contents", "locked")) {
            requireTag(tag, "client_summary", Tag.TAG_BYTE)
            requireTag(tag, "has_contents", Tag.TAG_BYTE)
            requireTag(tag, "locked", Tag.TAG_BYTE)
            val summary = tag.getByte("client_summary").toInt()
            val hasContents = tag.getByte("has_contents").toInt()
            val locked = tag.getByte("locked").toInt()
            valid(summary == 1 && hasContents in 0..1 && locked in 0..1, "summary")
            return GemMaterialState.ClientSummary(hasContents == 1, locked == 1)
        }
        valid(tag.allKeys == setOf("schema_version", "entries"), "schema")
        requireTag(tag, "schema_version", Tag.TAG_INT)
        valid(tag.getInt("schema_version") == 1, "schema")
        requireTag(tag, "entries", Tag.TAG_LIST)
        val entries = tag.get("entries") as? ListTag ?: invalid("schema")
        valid(entries.size <= GemMaterialContents.SLOT_COUNT, "size")
        val stacks = MutableList(GemMaterialContents.SLOT_COUNT) { ItemStack.EMPTY }
        val seen = mutableSetOf<Int>()
        for (entryTag in entries) {
            val entry = entryTag as? CompoundTag ?: invalid("entry")
            valid(entry.allKeys == setOf("slot", "stack"), "entry")
            requireTag(entry, "slot", Tag.TAG_INT)
            requireTag(entry, "stack", Tag.TAG_COMPOUND)
            val slot = entry.getInt("slot")
            valid(slot in 0 until GemMaterialContents.SLOT_COUNT && seen.add(slot), "slot")
            val encodedStack = entry.getCompound("stack")
            valid(encodedStack.allKeys.all { it in STACK_FIELDS }, "stack")
            GemMaterialNbtBudget.checkStack(encodedStack)
            val stack =
                ItemStack.CODEC
                    .parse(registries.createSerializationContext(NbtOps.INSTANCE), encodedStack)
                    .result()
                    .orElse(null) ?: invalid("stack")
            valid(
                !stack.isEmpty && stack.count <= minOf(GemMaterialContents.MAX_STACK_SIZE, stack.maxStackSize),
                "stack",
            )
            stacks[slot] = stack
        }
        return GemMaterialState.Ready(GemMaterialContents.of(stacks))
    }

    fun write(
        contents: GemMaterialContents,
        registries: HolderLookup.Provider,
    ): CustomData {
        val entries = ListTag()
        for (slot in 0 until GemMaterialContents.SLOT_COUNT) {
            val stack = contents.stackAt(slot)
            if (stack.isEmpty) continue
            valid(stack.count in 1..minOf(GemMaterialContents.MAX_STACK_SIZE, stack.maxStackSize), "stack")
            val encodedStack =
                ItemStack.CODEC
                    .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
                    .result()
                    .orElse(null) as? CompoundTag ?: invalid("stack")
            GemMaterialNbtBudget.checkStack(encodedStack)
            entries.add(
                CompoundTag().apply {
                    putInt("slot", slot)
                    put("stack", encodedStack)
                },
            )
        }
        val root =
            CompoundTag().apply {
                putInt("schema_version", 1)
                put("entries", entries)
            }
        GemMaterialNbtBudget.check(root)
        return CustomData.of(root)
    }

    fun canWrite(
        contents: GemMaterialContents,
        registries: HolderLookup.Provider,
    ): Boolean =
        try {
            write(contents, registries)
            true
        } catch (_: InvalidGemMaterial) {
            false
        }

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
        if (!condition) invalid(reason)
    }

    private fun invalid(reason: String): Nothing = throw InvalidGemMaterial(reason)

    private val STACK_FIELDS = setOf("id", "count", "components")
}
