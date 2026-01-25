package org.dreamfinity.dsgl.mc1710

import net.minecraft.item.ItemStack
import org.dreamfinity.dsgl.core.ItemStackRef

/**
 * Wrapper for Minecraft 1.7.10 [ItemStack] to satisfy [ItemStackRef].
 */
class McItemStackRef(val stack: ItemStack) : ItemStackRef
