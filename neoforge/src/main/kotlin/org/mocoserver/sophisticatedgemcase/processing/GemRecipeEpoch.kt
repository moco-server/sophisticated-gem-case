package org.mocoserver.sophisticatedgemcase.processing

import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.neoforged.neoforge.event.AddReloadListenerEvent
import java.util.concurrent.atomic.AtomicLong

/** datapack reload後のpreview・計画を古い世代から区別する。 */
internal object GemRecipeEpoch {
    private val epoch: AtomicLong = AtomicLong(0L)
    private val listener = ResourceManagerReloadListener { advance() }

    fun current(): Long = epoch.get()

    fun advance(): Long = epoch.incrementAndGet()

    fun install(event: AddReloadListenerEvent) {
        event.addListener(listener)
    }
}
