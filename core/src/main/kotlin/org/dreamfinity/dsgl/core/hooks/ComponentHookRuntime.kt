package org.dreamfinity.dsgl.core.hooks

import kotlin.reflect.KType
import java.util.ArrayDeque

internal enum class HookEntryKind {
    Ref,
    State,
    Memo,
    CustomScope,
    Custom
}

enum class HookRenderSessionMode {
    Normal,
    HotReload
}

class HookHotReloadRemountException(
    message: String
) : RuntimeException(message)

internal class HookUsageException(message: String) : IllegalStateException(message)

@PublishedApi
internal sealed interface HookSignature {
    fun diagnosticLabel(): String
}

internal data class KindOnlyHookSignature(
    val kind: HookEntryKind
) : HookSignature {
    override fun diagnosticLabel(): String = "KindOnly($kind)"
}

internal data class StateHookSignature(
    val valueType: KType
) : HookSignature {
    override fun diagnosticLabel(): String = "State<$valueType>"
}

internal data class RefHookSignature(
    val valueType: KType
) : HookSignature {
    override fun diagnosticLabel(): String = "Ref<$valueType>"
}

internal data class MemoHookSignature(
    val valueType: KType
) : HookSignature {
    override fun diagnosticLabel(): String = "Memo<$valueType>"
}

@PublishedApi
internal object HookSignatures {
    @PublishedApi
    internal fun kindOnly(kind: HookEntryKind): HookSignature = KindOnlyHookSignature(kind)

    @PublishedApi
    internal fun state(valueType: KType): HookSignature = StateHookSignature(valueType)

    @PublishedApi
    internal fun ref(valueType: KType): HookSignature = RefHookSignature(valueType)

    @PublishedApi
    internal fun memo(valueType: KType): HookSignature = MemoHookSignature(valueType)
}

internal data class HookPath(
    val segments: List<String>
) {
    init {
        require(segments.isNotEmpty()) { "Hook path must contain at least one segment." }
    }

    override fun toString(): String = segments.joinToString(".")
}

internal class OwnerIdentity(
    private val owner: Any
) {
    override fun equals(other: Any?): Boolean {
        return other is OwnerIdentity && owner === other.owner
    }

    override fun hashCode(): Int = System.identityHashCode(owner)
}

internal sealed interface ComponentDiscriminator {
    fun debugLabel(): String
}

private data class ExplicitKeyDiscriminator(
    val key: Any
) : ComponentDiscriminator {
    override fun debugLabel(): String = "key=$key"
}

private data class PositionalIndexDiscriminator(
    val index: Int
) : ComponentDiscriminator {
    override fun debugLabel(): String = "#$index"
}

internal data class ComponentIdentitySegment(
    val name: String,
    private val discriminator: ComponentDiscriminator
) {
    fun debugLabel(): String = "$name[${discriminator.debugLabel()}]"
}

internal data class ComponentInstanceId(
    private val ownerIdentity: OwnerIdentity,
    val segments: List<ComponentIdentitySegment>
) {
    companion object {
        fun root(owner: Any): ComponentInstanceId {
            return ComponentInstanceId(
                ownerIdentity = OwnerIdentity(owner),
                segments = emptyList()
            )
        }
    }

    fun child(name: String, explicitKey: Any?, positionalIndex: Int): ComponentInstanceId {
        val discriminator = if (explicitKey != null) {
            ExplicitKeyDiscriminator(explicitKey)
        } else {
            PositionalIndexDiscriminator(positionalIndex)
        }
        return ComponentInstanceId(
            ownerIdentity = ownerIdentity,
            segments = segments + ComponentIdentitySegment(name, discriminator)
        )
    }

    fun isSameOrDescendantOf(ancestor: ComponentInstanceId): Boolean {
        if (ownerIdentity != ancestor.ownerIdentity) {
            return false
        }
        if (segments.size < ancestor.segments.size) {
            return false
        }
        return segments.subList(0, ancestor.segments.size) == ancestor.segments
    }

    fun debugPath(): String {
        if (segments.isEmpty()) return "root"
        return segments.joinToString(".") { segment -> segment.debugLabel() }
    }
}

internal data class HookEntry(
    val kind: HookEntryKind,
    val signature: HookSignature,
    var value: Any?,
    val synthetic: Boolean,
    val createdRenderEpoch: Long
) {
    var lastVisitedRenderEpoch: Long = createdRenderEpoch
}

internal data class ResolvedHookEntry(
    val path: HookPath,
    val entry: HookEntry,
    val created: Boolean,
    val synthetic: Boolean
)

internal data class ResolvedTypedHookEntry<T : Any>(
    val path: HookPath,
    val value: T,
    val created: Boolean,
    val synthetic: Boolean
)

private data class SyntheticCounterKey(
    val scopeSegments: List<String>,
    val hookName: String
)

private enum class HookCompatibilityMismatchReason {
    Kind,
    Signature
}

private data class HookCompatibilityMismatch(
    val componentId: ComponentInstanceId,
    val path: HookPath,
    val existingKind: HookEntryKind,
    val requestedKind: HookEntryKind,
    val existingSignature: HookSignature,
    val requestedSignature: HookSignature,
    val reason: HookCompatibilityMismatchReason
) {
    fun runtimeErrorMessage(): String {
        val guidance = "Use distinct delegated property names/scopes for semantically different hooks."
        return when (reason) {
            HookCompatibilityMismatchReason.Kind -> {
                "Hook kind mismatch at path '$path' in component '${componentId.debugPath()}': " +
                    "existing=$existingKind (${existingSignature.diagnosticLabel()}), " +
                    "requested=$requestedKind (${requestedSignature.diagnosticLabel()}). $guidance"
            }

            HookCompatibilityMismatchReason.Signature -> {
                "Hook signature mismatch at path '$path' in component '${componentId.debugPath()}': " +
                    "existing=${existingSignature.diagnosticLabel()}, " +
                    "requested=${requestedSignature.diagnosticLabel()}. $guidance"
            }
        }
    }

    fun hotReloadWarningMessage(): String {
        return "[DSGL][Hooks] Hot-reload remount/reset for component '${componentId.debugPath()}' due to " +
            "incompatible hook at path '$path': " +
            "previous=${existingSignature.diagnosticLabel()} ($existingKind), " +
            "next=${requestedSignature.diagnosticLabel()} ($requestedKind). " +
            "Local hook state for this component subtree was reset. " +
            "Use distinct delegated property names/scopes for semantically different hooks."
    }
}

private class HookCompatibilityMismatchException(
    val mismatch: HookCompatibilityMismatch
) : RuntimeException()

internal class ComponentHookContext(
    val componentId: ComponentInstanceId
) {
    private val entriesByPath: MutableMap<HookPath, HookEntry> = linkedMapOf()
    private val claimedPathsThisRender: MutableSet<HookPath> = linkedSetOf()
    private val syntheticCounterByScope: MutableMap<SyntheticCounterKey, Int> = linkedMapOf()

    var lastVisitedRenderEpoch: Long = -1L
        private set

    fun beginRender(renderEpoch: Long) {
        lastVisitedRenderEpoch = renderEpoch
        claimedPathsThisRender.clear()
        syntheticCounterByScope.clear()
    }

    fun resolveNamedEntry(
        scopeSegments: List<String>,
        delegateName: String,
        kind: HookEntryKind,
        signature: HookSignature,
        renderEpoch: Long,
        initializer: () -> Any?
    ): ResolvedHookEntry {
        val path = HookPath(scopeSegments + delegateName)
        return resolvePath(
            path = path,
            kind = kind,
            signature = signature,
            synthetic = false,
            renderEpoch = renderEpoch,
            initializer = initializer
        )
    }

    fun resolveUnnamedEntry(
        scopeSegments: List<String>,
        hookName: String,
        kind: HookEntryKind,
        signature: HookSignature,
        renderEpoch: Long,
        initializer: () -> Any?
    ): ResolvedHookEntry {
        val counterKey = SyntheticCounterKey(scopeSegments = scopeSegments, hookName = hookName)
        val index = syntheticCounterByScope[counterKey] ?: 0
        syntheticCounterByScope[counterKey] = index + 1
        val syntheticSegment = "$hookName#$index"
        val path = HookPath(scopeSegments + syntheticSegment)
        return resolvePath(
            path = path,
            kind = kind,
            signature = signature,
            synthetic = true,
            renderEpoch = renderEpoch,
            initializer = initializer
        )
    }

    fun cleanupUnvisitedEntries(renderEpoch: Long) {
        val iterator = entriesByPath.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.lastVisitedRenderEpoch != renderEpoch) {
                iterator.remove()
            }
        }
    }

    fun entryCount(): Int = entriesByPath.size

    private fun resolvePath(
        path: HookPath,
        kind: HookEntryKind,
        signature: HookSignature,
        synthetic: Boolean,
        renderEpoch: Long,
        initializer: () -> Any?
    ): ResolvedHookEntry {
        if (!claimedPathsThisRender.add(path)) {
            if (synthetic) {
                throw HookUsageException(
                    "Synthetic hook key collision at path '$path' in component '${componentId.debugPath()}'."
                )
            }
            throw HookUsageException(
                "Duplicate hook path '$path' in component '${componentId.debugPath()}'."
            )
        }

        val existing = entriesByPath[path]
        if (existing != null) {
            if (existing.synthetic != synthetic) {
                throw HookUsageException(
                    "Synthetic hook key collision at path '$path' in component '${componentId.debugPath()}'."
                )
            }
            if (existing.kind != kind) {
                throw HookCompatibilityMismatchException(
                    HookCompatibilityMismatch(
                        componentId = componentId,
                        path = path,
                        existingKind = existing.kind,
                        requestedKind = kind,
                        existingSignature = existing.signature,
                        requestedSignature = signature,
                        reason = HookCompatibilityMismatchReason.Kind
                    )
                )
            }
            if (existing.signature != signature) {
                throw HookCompatibilityMismatchException(
                    HookCompatibilityMismatch(
                        componentId = componentId,
                        path = path,
                        existingKind = existing.kind,
                        requestedKind = kind,
                        existingSignature = existing.signature,
                        requestedSignature = signature,
                        reason = HookCompatibilityMismatchReason.Signature
                    )
                )
            }
            existing.lastVisitedRenderEpoch = renderEpoch
            return ResolvedHookEntry(path = path, entry = existing, created = false, synthetic = synthetic)
        }

        val created = HookEntry(
            kind = kind,
            signature = signature,
            value = initializer(),
            synthetic = synthetic,
            createdRenderEpoch = renderEpoch
        )
        created.lastVisitedRenderEpoch = renderEpoch
        entriesByPath[path] = created
        return ResolvedHookEntry(path = path, entry = created, created = true, synthetic = synthetic)
    }
}

internal class ComponentHookRuntime {
    internal data class StorageHookBindingToken(
        val hookName: String,
        val renderEpoch: Long,
        var bound: Boolean = false
    )

    private data class ComponentFrame(
        val componentId: ComponentInstanceId,
        var context: ComponentHookContext,
        val scopeSegments: ArrayDeque<String> = ArrayDeque(),
        val positionalChildCounters: MutableMap<String, Int> = linkedMapOf()
    )

    private val contextsByComponent: MutableMap<ComponentInstanceId, ComponentHookContext> = linkedMapOf()
    private val enteredComponentIdsThisRender: MutableSet<ComponentInstanceId> = linkedSetOf()
    private val componentStack: ArrayDeque<ComponentFrame> = ArrayDeque()
    private val pendingStorageHookBindings: MutableList<StorageHookBindingToken> = arrayListOf()

    private var renderEpoch: Long = 0L
    private var renderActive: Boolean = false
    private var renderSessionMode: HookRenderSessionMode = HookRenderSessionMode.Normal
    private var renderAbortedByHotReloadRemount: Boolean = false

    fun beginRender(owner: Any, sessionMode: HookRenderSessionMode = HookRenderSessionMode.Normal) {
        ensureNotActiveRender()
        renderActive = true
        renderSessionMode = sessionMode
        renderAbortedByHotReloadRemount = false
        renderEpoch += 1L
        enteredComponentIdsThisRender.clear()
        componentStack.clear()
        pendingStorageHookBindings.clear()

        val rootId = ComponentInstanceId.root(owner)
        val rootContext = contextsByComponent.getOrPut(rootId) { ComponentHookContext(rootId) }
        rootContext.beginRender(renderEpoch)
        enteredComponentIdsThisRender.add(rootId)
        componentStack.addLast(ComponentFrame(componentId = rootId, context = rootContext))
    }

    fun endRender() {
        ensureActiveRender()
        if (renderAbortedByHotReloadRemount) {
            clearRenderSessionStateOnly()
            return
        }

        val unboundStorageHook = pendingStorageHookBindings.firstOrNull { token ->
            token.renderEpoch == renderEpoch && !token.bound
        }
        if (unboundStorageHook != null) {
            failStorageBackedHookWithoutDelegate(unboundStorageHook.hookName)
        }
        val current = componentStack.lastOrNull()
        if (componentStack.size != 1 || current == null) {
            throw HookUsageException(
                "Invalid nested component scope behavior: render ended with unbalanced component scopes."
            )
        }
        if (current.scopeSegments.isNotEmpty()) {
            throw HookUsageException(
                "Invalid nested hook scope behavior: render ended with unclosed custom hook scope " +
                    "'${current.scopeSegments.last()}'."
            )
        }

        contextsByComponent.values.forEach { context ->
            if (context.lastVisitedRenderEpoch == renderEpoch) {
                context.cleanupUnvisitedEntries(renderEpoch)
            }
        }

        val iterator = contextsByComponent.entries.iterator()
        while (iterator.hasNext()) {
            val contextEntry = iterator.next()
            if (contextEntry.value.lastVisitedRenderEpoch != renderEpoch) {
                iterator.remove()
            }
        }

        clearRenderSessionStateOnly()
    }

    fun enterComponentInstance(componentName: String, key: Any? = null) {
        val parent = currentFrame()
        val normalizedName = validateSegment(componentName, "component name")
        val positionalIndex = if (key == null) {
            val current = parent.positionalChildCounters[normalizedName] ?: 0
            parent.positionalChildCounters[normalizedName] = current + 1
            current
        } else {
            -1
        }

        val childId = parent.componentId.child(
            name = normalizedName,
            explicitKey = key,
            positionalIndex = positionalIndex
        )
        if (!enteredComponentIdsThisRender.add(childId)) {
            throw HookUsageException(
                "Duplicate component identity '${childId.debugPath()}' in a single render pass."
            )
        }

        val childContext = contextsByComponent.getOrPut(childId) { ComponentHookContext(childId) }
        childContext.beginRender(renderEpoch)
        componentStack.addLast(ComponentFrame(componentId = childId, context = childContext))
    }

    fun leaveComponentInstance() {
        ensureActiveRender()
        if (componentStack.size <= 1) {
            throw HookUsageException("Cannot leave root component hook scope.")
        }
        val frame = componentStack.last()
        if (frame.scopeSegments.isNotEmpty()) {
            throw HookUsageException(
                "Invalid nested hook scope behavior: component '${frame.componentId.debugPath()}' " +
                    "ended with unclosed custom hook scope '${frame.scopeSegments.last()}'."
            )
        }
        componentStack.removeLast()
    }

    inline fun <T> withComponentInstance(componentName: String, key: Any? = null, block: () -> T): T {
        enterComponentInstance(componentName = componentName, key = key)
        return try {
            block()
        } finally {
            leaveComponentInstance()
        }
    }

    fun enterCustomHookScope(delegateName: String) {
        val frame = currentFrame()
        val normalizedName = validateSegment(delegateName, "custom hook delegated property")
        frame.context.resolveNamedEntry(
            scopeSegments = frame.scopeSegments.toList(),
            delegateName = normalizedName,
            kind = HookEntryKind.CustomScope,
            signature = HookSignatures.kindOnly(HookEntryKind.CustomScope),
            renderEpoch = renderEpoch
        ) { Unit }
        frame.scopeSegments.addLast(normalizedName)
    }

    fun leaveCustomHookScope() {
        val frame = currentFrame()
        if (frame.scopeSegments.isEmpty()) {
            throw HookUsageException(
                "Invalid nested hook scope behavior: no active custom hook scope to leave."
            )
        }
        frame.scopeSegments.removeLast()
    }

    inline fun <T> withCustomHookScope(delegateName: String, block: () -> T): T {
        enterCustomHookScope(delegateName)
        return try {
            block()
        } finally {
            leaveCustomHookScope()
        }
    }

    fun resolveNamedEntry(
        kind: HookEntryKind,
        delegateName: String,
        initializer: () -> Any?
    ): ResolvedHookEntry {
        val frame = currentFrame()
        val normalizedName = validateSegment(delegateName, "delegated property")
        val signature = HookSignatures.kindOnly(kind)
        return resolveNamedEntryWithSignature(frame, kind, signature, normalizedName, initializer)
    }

    fun resolveUnnamedEntry(
        kind: HookEntryKind,
        hookName: String,
        initializer: () -> Any?
    ): ResolvedHookEntry {
        val frame = currentFrame()
        val normalizedHookName = validateSegment(hookName, "hook name")
        val signature = HookSignatures.kindOnly(kind)
        return resolveUnnamedEntryWithSignature(frame, kind, signature, normalizedHookName, initializer)
    }

    fun <T : Any> resolveNamedTypedEntry(
        kind: HookEntryKind,
        delegateName: String,
        signature: HookSignature,
        expectedRawType: Class<*>,
        initializer: () -> T
    ): ResolvedTypedHookEntry<T> {
        val frame = currentFrame()
        val normalizedName = validateSegment(delegateName, "delegated property")
        val resolved = resolveNamedEntryWithSignature(frame, kind, signature, normalizedName, initializer)
        val typedValue: T = castResolvedEntryValue(
            value = resolved.entry.value,
            path = resolved.path,
            componentLabel = frame.componentId.debugPath(),
            expectedRawType = expectedRawType
        )
        return ResolvedTypedHookEntry(
            path = resolved.path,
            value = typedValue,
            created = resolved.created,
            synthetic = resolved.synthetic
        )
    }

    inline fun <reified T : Any> resolveNamedTypedEntry(
        kind: HookEntryKind,
        delegateName: String,
        signature: HookSignature,
        noinline initializer: () -> T
    ): ResolvedTypedHookEntry<T> {
        return resolveNamedTypedEntry(
            kind = kind,
            delegateName = delegateName,
            signature = signature,
            expectedRawType = T::class.java,
            initializer = initializer
        )
    }

    fun <T : Any> resolveUnnamedTypedEntry(
        kind: HookEntryKind,
        hookName: String,
        signature: HookSignature,
        expectedRawType: Class<*>,
        initializer: () -> T
    ): ResolvedTypedHookEntry<T> {
        val frame = currentFrame()
        val normalizedHookName = validateSegment(hookName, "hook name")
        val resolved = resolveUnnamedEntryWithSignature(frame, kind, signature, normalizedHookName, initializer)
        val typedValue: T = castResolvedEntryValue(
            value = resolved.entry.value,
            path = resolved.path,
            componentLabel = frame.componentId.debugPath(),
            expectedRawType = expectedRawType
        )
        return ResolvedTypedHookEntry(
            path = resolved.path,
            value = typedValue,
            created = resolved.created,
            synthetic = resolved.synthetic
        )
    }

    inline fun <reified T : Any> resolveUnnamedTypedEntry(
        kind: HookEntryKind,
        hookName: String,
        signature: HookSignature,
        noinline initializer: () -> T
    ): ResolvedTypedHookEntry<T> {
        return resolveUnnamedTypedEntry(
            kind = kind,
            hookName = hookName,
            signature = signature,
            expectedRawType = T::class.java,
            initializer = initializer
        )
    }

    fun failStorageBackedHookWithoutDelegate(hookName: String): Nothing {
        throw HookUsageException(
            "Storage-backed hook '$hookName' must be bound via delegated property syntax (`by $hookName(...)`). " +
                "Direct assignment is not supported."
        )
    }

    fun registerStorageBackedHookCandidate(hookName: String): StorageHookBindingToken {
        ensureActiveRender()
        val token = StorageHookBindingToken(
            hookName = hookName,
            renderEpoch = renderEpoch
        )
        pendingStorageHookBindings.add(token)
        return token
    }

    fun markStorageBackedHookBound(token: StorageHookBindingToken) {
        ensureActiveRender()
        if (token.renderEpoch != renderEpoch) {
            throw HookUsageException(
                "Storage-backed hook '${token.hookName}' binding token does not belong to current render session."
            )
        }
        token.bound = true
    }

    fun debugComponentContextCount(): Int = contextsByComponent.size

    fun debugTotalEntryCount(): Int = contextsByComponent.values.sumOf { context -> context.entryCount() }

    private fun resolveNamedEntryWithSignature(
        frame: ComponentFrame,
        kind: HookEntryKind,
        signature: HookSignature,
        delegateName: String,
        initializer: () -> Any?
    ): ResolvedHookEntry {
        return try {
            frame.context.resolveNamedEntry(
                scopeSegments = frame.scopeSegments.toList(),
                delegateName = delegateName,
                kind = kind,
                signature = signature,
                renderEpoch = renderEpoch,
                initializer = initializer
            )
        } catch (mismatch: HookCompatibilityMismatchException) {
            handleCompatibilityMismatch(mismatch.mismatch)
        }
    }

    private fun resolveUnnamedEntryWithSignature(
        frame: ComponentFrame,
        kind: HookEntryKind,
        signature: HookSignature,
        hookName: String,
        initializer: () -> Any?
    ): ResolvedHookEntry {
        return try {
            frame.context.resolveUnnamedEntry(
                scopeSegments = frame.scopeSegments.toList(),
                hookName = hookName,
                kind = kind,
                signature = signature,
                renderEpoch = renderEpoch,
                initializer = initializer
            )
        } catch (mismatch: HookCompatibilityMismatchException) {
            handleCompatibilityMismatch(mismatch.mismatch)
        }
    }

    private fun handleCompatibilityMismatch(mismatch: HookCompatibilityMismatch): Nothing {
        if (renderSessionMode != HookRenderSessionMode.HotReload) {
            throw HookUsageException(mismatch.runtimeErrorMessage())
        }
        resetComponentSubtree(mismatch.componentId)
        renderAbortedByHotReloadRemount = true
        throw HookHotReloadRemountException(mismatch.hotReloadWarningMessage())
    }

    private fun resetComponentSubtree(root: ComponentInstanceId) {
        val idsToRemove = contextsByComponent.keys
            .filter { componentId -> componentId.isSameOrDescendantOf(root) }
            .toSet()
        if (idsToRemove.isEmpty()) return
        val iterator = contextsByComponent.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key in idsToRemove) {
                iterator.remove()
            }
        }
    }

    private fun clearRenderSessionStateOnly() {
        enteredComponentIdsThisRender.clear()
        componentStack.clear()
        pendingStorageHookBindings.clear()
        renderActive = false
        renderSessionMode = HookRenderSessionMode.Normal
        renderAbortedByHotReloadRemount = false
    }

    private fun currentFrame(): ComponentFrame {
        ensureActiveRender()
        return componentStack.lastOrNull()
            ?: throw HookUsageException("Hook runtime has no active component frame.")
    }

    private fun ensureActiveRender() {
        if (!renderActive) {
            throw HookUsageException("Hook usage outside active component render is not allowed.")
        }
    }

    private fun ensureNotActiveRender() {
        if (renderActive) {
            throw HookUsageException("Hook runtime render session is already active.")
        }
    }

    private fun validateSegment(value: String, label: String): String {
        val normalized = value.trim()
        if (normalized.isEmpty()) {
            throw HookUsageException("$label cannot be blank.")
        }
        if (normalized.contains(".")) {
            throw HookUsageException("$label '$value' cannot contain '.'.")
        }
        return normalized
    }

    private fun <T : Any> castResolvedEntryValue(
        value: Any?,
        path: HookPath,
        componentLabel: String,
        expectedRawType: Class<*>
    ): T {
        if (value == null || !expectedRawType.isInstance(value)) {
            val actualType = if (value == null) "null" else value.javaClass.name
            throw HookUsageException(
                "Hook value type mismatch at path '$path' in component '$componentLabel': " +
                    "expected=${expectedRawType.name}, actual=$actualType."
            )
        }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }
}
