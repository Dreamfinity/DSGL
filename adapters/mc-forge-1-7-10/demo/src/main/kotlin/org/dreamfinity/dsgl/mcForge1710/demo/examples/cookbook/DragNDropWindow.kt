package org.dreamfinity.dsgl.mcForge1710.demo.examples.cookbook

import cpw.mods.fml.common.registry.GameRegistry
import net.minecraft.item.ItemStack
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.dnd.applyDraggable
import org.dreamfinity.dsgl.core.dnd.applyDroppable
import org.dreamfinity.dsgl.core.dnd.useDraggable
import org.dreamfinity.dsgl.core.dnd.useDroppable
import org.dreamfinity.dsgl.core.dsl.UiScope
import org.dreamfinity.dsgl.core.dsl.div
import org.dreamfinity.dsgl.core.dsl.itemStack
import org.dreamfinity.dsgl.core.dsl.text
import org.dreamfinity.dsgl.core.hooks.useState
import org.dreamfinity.dsgl.core.style.AlignItems
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.mcForge1710.McItemStackRef
import org.dreamfinity.dsgl.mcForge1710.demo.examples.containers.centeredFlexWrapper

class DragNDropWindow : DsglWindow() {
    override fun render() =
        ui {
            centeredFlexWrapper {
                dragBucketRecipe()
            }
        }
}

private data class Card(
    val id: String,
    val label: String,
)

fun UiScope.dragBucketRecipe() {
    var lane by useState(listOf(Card("apple", "Apple"), Card("bread", "Bread")))
    var done by useState(emptyList<Card>())

    val doneDrop =
        useDroppable(
            id = "bucket.done",
            nodeKey = "bucket.done",
            accepts = { active -> !active.id.isNullOrBlank() },
            onDrop = { _, active ->
                val movedId = active?.id ?: return@useDroppable
                val moved = lane.firstOrNull { it.id == movedId } ?: return@useDroppable
                lane = lane.filterNot { it.id == movedId }
                done = done + moved
            },
        )

    div({
        key = "recipe.done.bucket"
        style = {
            display = Display.Flex
            alignItems = AlignItems.Center
        }
        applyDroppable(doneDrop)
    }) {
        div({
            style = {
                display = Display.Flex
                alignItems = AlignItems.Center
            }
        }) {
            text("Done (${done.size})")
        }
        done.forEach { card ->
            div({
                style = {
                    display = Display.Flex
                    alignItems = AlignItems.Center
                }
            }) {
                GameRegistry.findItem("minecraft", card.id)?.let { item ->
                    itemStack(McItemStackRef(ItemStack(item, 1, 0)), { size = 32 })
                } ?: text("?")
                text(card.label)
            }
        }
    }

    lane.forEach { card ->
        val drag = useDraggable(id = card.id, nodeKey = "lane.card.${card.id}")
        div({
            key = "lane.card.${card.id}"
            style = {
                display = Display.Flex
                alignItems = AlignItems.Center
            }
            applyDraggable(drag)
        }) {
            GameRegistry.findItem("minecraft", card.id)?.let { item ->
                itemStack(McItemStackRef(ItemStack(item, 1, 0)), { size = 32 })
            } ?: text("?")
            text(card.label)
        }
    }
}
