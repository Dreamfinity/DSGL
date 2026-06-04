package org.dreamfinity.dsgl.mcForge1122

import net.minecraft.item.ItemStack
import org.dreamfinity.dsgl.core.ItemStackRef

/**
 * Wrapper for Minecraft 1.12.2 [ItemStack] to satisfy [ItemStackRef].
 */
class McItemStackRef(val stack: ItemStack) : ItemStackRef
