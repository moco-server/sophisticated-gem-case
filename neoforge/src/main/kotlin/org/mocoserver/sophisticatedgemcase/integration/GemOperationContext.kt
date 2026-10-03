package org.mocoserver.sophisticatedgemcase.integration

import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper
import java.util.UUID

/** 同一バッグ内のhandler再入だけを識別する、スレッド局所の操作文脈。 */
internal object GemOperationContext {
    private data class Frame(
        val storage: IStorageWrapper,
        val contentsUuid: UUID?,
        val suppressRouting: Boolean,
        val permit: GemTransferPermit?,
    ) {
        fun matches(candidate: IStorageWrapper): Boolean {
            val candidateUuid = candidate.contentsUuid.orElse(null)
            return if (contentsUuid !=
                null
            ) {
                contentsUuid == candidateUuid
            } else {
                candidateUuid == null && storage === candidate
            }
        }
    }

    private val frames: ThreadLocal<MutableList<Frame>> = ThreadLocal.withInitial { mutableListOf() }

    fun routingSuppressed(storage: IStorageWrapper): Boolean {
        val stack = frames.get()
        val suppressed = stack.any { it.suppressRouting && it.matches(storage) }
        if (stack.isEmpty()) frames.remove()
        return suppressed
    }

    fun isExclusive(storage: IStorageWrapper): Boolean {
        val stack = frames.get()
        val exclusive = stack.any { it.permit != null && it.matches(storage) }
        if (stack.isEmpty()) frames.remove()
        return exclusive
    }

    fun mayMutate(
        storage: IStorageWrapper,
        permit: GemTransferPermit?,
    ): Boolean {
        val stack = frames.get()
        val exclusive = stack.lastOrNull { it.permit != null && it.matches(storage) }
        if (stack.isEmpty()) frames.remove()
        return if (exclusive == null) permit == null else exclusive.permit === permit
    }

    fun <T> withoutRouting(
        storage: IStorageWrapper,
        action: () -> T,
    ): T {
        val stack = frames.get()
        stack.add(Frame(storage, storage.contentsUuid.orElse(null), true, null))
        return try {
            action()
        } finally {
            stack.removeAt(stack.lastIndex)
            if (stack.isEmpty()) frames.remove()
        }
    }

    fun <T> exclusive(
        storage: IStorageWrapper,
        action: (GemTransferPermit) -> T,
    ): T? {
        val stack = frames.get()
        if (stack.any { it.permit != null && it.matches(storage) }) {
            if (stack.isEmpty()) frames.remove()
            return null
        }
        val permit = GemTransferPermit()
        stack.add(Frame(storage, storage.contentsUuid.orElse(null), false, permit))
        return try {
            action(permit)
        } finally {
            stack.removeAt(stack.lastIndex)
            if (stack.isEmpty()) frames.remove()
        }
    }
}

/** 専用転送中にだけ保存更新を許可する、文脈identity付きの一時token。 */
internal class GemTransferPermit
