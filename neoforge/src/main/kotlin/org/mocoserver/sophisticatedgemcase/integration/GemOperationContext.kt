package org.mocoserver.sophisticatedgemcase.integration

import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper
import java.util.UUID

/** 同一バッグ内のhandler再入だけを識別する、スレッド局所の操作文脈。 */
internal object GemOperationContext {
    private data class Frame(
        val storage: IStorageWrapper,
        val contentsUuid: UUID?,
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
        val suppressed = stack.any { it.matches(storage) }
        if (stack.isEmpty()) frames.remove()
        return suppressed
    }

    fun <T> withoutRouting(
        storage: IStorageWrapper,
        action: () -> T,
    ): T {
        val stack = frames.get()
        stack.add(Frame(storage, storage.contentsUuid.orElse(null)))
        return try {
            action()
        } finally {
            stack.removeAt(stack.lastIndex)
            if (stack.isEmpty()) frames.remove()
        }
    }
}
