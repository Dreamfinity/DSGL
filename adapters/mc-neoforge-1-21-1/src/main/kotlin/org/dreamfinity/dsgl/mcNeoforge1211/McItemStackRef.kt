package org.dreamfinity.dsgl.mcNeoforge1211

import net.minecraft.world.item.ItemStack
import org.dreamfinity.dsgl.core.ItemStackRef

/**
 * Wrapper for Minecraft 1.21.1 [ItemStack] to satisfy [ItemStackRef].
 */
class McItemStackRef(val stack: ItemStack) : ItemStackRef
