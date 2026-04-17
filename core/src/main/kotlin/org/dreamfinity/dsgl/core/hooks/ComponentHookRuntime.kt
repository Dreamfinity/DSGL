package org.dreamfinity.dsgl.core.hooks

import org.dreamfinity.dsgl.core.DsglWindow
import kotlin.reflect.KType
import kotlin.reflect.KClass
import java.util.ArrayDeque

internal enum class HookEntryKind {
    Ref,
    State,
    Reducer,
    Memo,
    Effect,
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

internal data class ReducerHookSignature(
    val stateClass: KClass<*>?,
    val initialWasNull: Boolean
) : HookSignature {
    override fun diagnosticLabel(): String {
        val classLabel = stateClass?.qualifiedName ?: "unknown"
        val nullabilityLabel = if (initialWasNull) "null-initial" else "non-null-initial"
        return "Reducer<state=$classLabel,$nullabilityLabel>"
    }
}

internal enum class EffectHookRunMode {
    OnDependencyChange,
    EveryCommit
}

internal data class EffectHookSignature(
    val runMode: EffectHookRunMode
) : HookSignature {
    override fun diagnosticLabel(): String = "Effect<$runMode>"
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

    @PublishedApi
    internal fun reducer(stateClass: KClass<*>?, initialWasNull: Boolean): HookSignature {
        return ReducerHookSignature(
            stateClass = stateClass,
            initialWasNull = initialWasNull
        )
    }

    @PublishedApi
    internal fun effect(runMode: EffectHookRunMode): HookSignature = EffectHookSignature(runMode)
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

    private enum class ComponentFrameOrigin {
        Root,
        Explicit,
        Inferred
    }

    private data class InferredComponentDescriptor(
        val ownerClassName: String,
        val methodName: String,
        val componentName: String
    )

    private data class HookCallSite(
        val className: String,
        val methodName: String,
        val lineNumber: Int
    )

    private data class InferenceSnapshot(
        val descriptors: List<InferredComponentDescriptor>,
        val leafCallSite: HookCallSite?
    )

    private data class ComponentFrame(
        val componentId: ComponentInstanceId,
        var context: ComponentHookContext,
        val scopeSegments: ArrayDeque<String> = ArrayDeque(),
        val positionalChildCounters: MutableMap<String, Int> = linkedMapOf(),
        val origin: ComponentFrameOrigin,
        val inferredDescriptor: InferredComponentDescriptor? = null,
        val seenHookCallSitesThisRender: MutableSet<HookCallSite> = linkedSetOf()
    )

    private data class EffectRegistrationKey(
        val componentId: ComponentInstanceId,
        val path: HookPath
    )

    private data class EffectRenderRegistration(
        val componentId: ComponentInstanceId,
        val path: HookPath,
        val runMode: EffectHookRunMode,
        val deps: List<Any?>,
        val effect: (registerCleanup: (() -> Unit) -> Unit) -> Unit
    )

    private data class CommittedEffectState(
        val runMode: EffectHookRunMode,
        val deps: List<Any?>,
        val cleanup: (() -> Unit)?
    )

    private data class PendingEffectCommitBatch(
        val visitedComponents: Set<ComponentInstanceId>,
        val registrationsInOrder: List<EffectRenderRegistration>
    )

    private val contextsByComponent: MutableMap<ComponentInstanceId, ComponentHookContext> = linkedMapOf()
    private val enteredComponentIdsThisRender: MutableSet<ComponentInstanceId> = linkedSetOf()
    private val componentStack: ArrayDeque<ComponentFrame> = ArrayDeque()
    private val pendingStorageHookBindings: MutableList<StorageHookBindingToken> = arrayListOf()
    private val renderEffectRegistrations: MutableMap<EffectRegistrationKey, EffectRenderRegistration> = linkedMapOf()
    private val committedEffectsByComponent: MutableMap<ComponentInstanceId, MutableMap<HookPath, CommittedEffectState>> =
        linkedMapOf()
    private var pendingEffectCommitBatch: PendingEffectCommitBatch? = null

    private var renderEpoch: Long = 0L
    private var renderActive: Boolean = false
    private var renderSessionMode: HookRenderSessionMode = HookRenderSessionMode.Normal
    private var renderAbortedByHotReloadRemount: Boolean = false
    private val windowSubclassCacheByClassName: MutableMap<String, Boolean> = linkedMapOf()
    private var currentInferenceLeafCallSite: HookCallSite? = null

    fun beginRender(owner: Any, sessionMode: HookRenderSessionMode = HookRenderSessionMode.Normal) {
        ensureNotActiveRender()
        pendingEffectCommitBatch = null
        renderActive = true
        renderSessionMode = sessionMode
        renderAbortedByHotReloadRemount = false
        renderEpoch += 1L
        enteredComponentIdsThisRender.clear()
        componentStack.clear()
        pendingStorageHookBindings.clear()
        renderEffectRegistrations.clear()

        val rootId = ComponentInstanceId.root(owner)
        val rootContext = contextsByComponent.getOrPut(rootId) { ComponentHookContext(rootId) }
        rootContext.beginRender(renderEpoch)
        enteredComponentIdsThisRender.add(rootId)
        componentStack.addLast(
            ComponentFrame(
                componentId = rootId,
                context = rootContext,
                origin = ComponentFrameOrigin.Root
            )
        )
    }

    fun endRender() {
        ensureActiveRender()
        if (renderAbortedByHotReloadRemount) {
            clearRenderSessionStateOnly()
            return
        }

        unwindInferredFramesForRenderEnd()

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

        pendingEffectCommitBatch = PendingEffectCommitBatch(
            visitedComponents = enteredComponentIdsThisRender.toSet(),
            registrationsInOrder = renderEffectRegistrations.values.toList()
        )

        clearRenderSessionStateOnly()
    }

    fun registerEffectOnDependencyChange(
        deps: List<Any?>,
        effect: (registerCleanup: (() -> Unit) -> Unit) -> Unit
    ) {
        registerEffectInvocation(
            runMode = EffectHookRunMode.OnDependencyChange,
            deps = deps,
            effect = effect
        )
    }

    fun registerEffectEveryCommit(
        effect: (registerCleanup: (() -> Unit) -> Unit) -> Unit
    ) {
        registerEffectInvocation(
            runMode = EffectHookRunMode.EveryCommit,
            deps = emptyList(),
            effect = effect
        )
    }

    fun commitPostRenderEffects() {
        ensureNotActiveRender()
        val pending = pendingEffectCommitBatch ?: return
        pendingEffectCommitBatch = null
        applyEffectCommitBatch(pending)
    }

    fun discardPostRenderEffects() {
        ensureNotActiveRender()
        pendingEffectCommitBatch = null
    }

    fun disposeAll() {
        if (renderActive) {
            throw HookUsageException("Cannot dispose hook runtime during an active render session.")
        }
        pendingEffectCommitBatch = null
        runCommittedEffectCleanupForSubtree(
            root = null,
            reason = "component runtime disposal"
        )
        committedEffectsByComponent.clear()
        contextsByComponent.clear()
        enteredComponentIdsThisRender.clear()
        componentStack.clear()
        pendingStorageHookBindings.clear()
        renderEffectRegistrations.clear()
    }

    fun enterComponentInstance(componentName: String, key: Any? = null) {
        val parent = currentFrame()
        val normalizedName = validateSegment(componentName, "component name")
        pushComponentFrame(
            parent = parent,
            componentName = normalizedName,
            key = key,
            origin = ComponentFrameOrigin.Explicit,
            inferredDescriptor = null
        )
    }

    fun leaveComponentInstance() {
        ensureActiveRender()
        if (componentStack.size <= 1) {
            throw HookUsageException("Cannot leave root component hook scope.")
        }
        val frame = componentStack.last()
        if (frame.origin != ComponentFrameOrigin.Explicit) {
            throw HookUsageException(
                "Cannot leave inferred/root component hook scope with explicit leave call."
            )
        }
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
        val frame = currentFrameForHookResolution()
        val normalizedName = validateSegment(delegateName, "custom hook delegated property")
        frame.context.resolveNamedEntry(
            scopeSegments = frame.scopeSegments.toList(),
            delegateName = normalizedName,
            kind = HookEntryKind.CustomScope,
            signature = HookSignatures.kindOnly(HookEntryKind.CustomScope),
            renderEpoch = renderEpoch
        ) { Unit }
        recordInferredHookCallSite(frame)
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
        val frame = currentFrameForHookResolution()
        val normalizedName = validateSegment(delegateName, "delegated property")
        val signature = HookSignatures.kindOnly(kind)
        return resolveNamedEntryWithSignature(frame, kind, signature, normalizedName, initializer)
    }

    fun resolveUnnamedEntry(
        kind: HookEntryKind,
        hookName: String,
        initializer: () -> Any?
    ): ResolvedHookEntry {
        val frame = currentFrameForHookResolution()
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
        val frame = currentFrameForHookResolution()
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
        val frame = currentFrameForHookResolution()
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
            val resolved = frame.context.resolveNamedEntry(
                scopeSegments = frame.scopeSegments.toList(),
                delegateName = delegateName,
                kind = kind,
                signature = signature,
                renderEpoch = renderEpoch,
                initializer = initializer
            )
            recordInferredHookCallSite(frame)
            resolved
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
            val resolved = frame.context.resolveUnnamedEntry(
                scopeSegments = frame.scopeSegments.toList(),
                hookName = hookName,
                kind = kind,
                signature = signature,
                renderEpoch = renderEpoch,
                initializer = initializer
            )
            recordInferredHookCallSite(frame)
            resolved
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
        runCommittedEffectCleanupForSubtree(
            root = root,
            reason = "hot-reload remount/reset"
        )
        committedEffectsByComponent.keys
            .filter { componentId -> componentId.isSameOrDescendantOf(root) }
            .toList()
            .forEach { componentId ->
                committedEffectsByComponent.remove(componentId)
            }
    }

    private fun clearRenderSessionStateOnly() {
        enteredComponentIdsThisRender.clear()
        componentStack.clear()
        pendingStorageHookBindings.clear()
        renderEffectRegistrations.clear()
        renderActive = false
        renderSessionMode = HookRenderSessionMode.Normal
        renderAbortedByHotReloadRemount = false
    }

    private fun registerEffectInvocation(
        runMode: EffectHookRunMode,
        deps: List<Any?>,
        effect: (registerCleanup: (() -> Unit) -> Unit) -> Unit
    ) {
        val frame = currentFrameForHookResolution()
        val resolved = resolveUnnamedEntryWithSignature(
            frame = frame,
            kind = HookEntryKind.Effect,
            signature = HookSignatures.effect(runMode),
            hookName = "useEffect"
        ) { }
        val key = EffectRegistrationKey(
            componentId = frame.componentId,
            path = resolved.path
        )
        renderEffectRegistrations[key] = EffectRenderRegistration(
            componentId = frame.componentId,
            path = resolved.path,
            runMode = runMode,
            deps = deps,
            effect = effect
        )
    }

    private fun applyEffectCommitBatch(batch: PendingEffectCommitBatch) {
        val cleanupQueue: MutableList<Pair<EffectRegistrationKey, () -> Unit>> = arrayListOf()
        val removeQueue: MutableList<EffectRegistrationKey> = arrayListOf()
        val runQueue: MutableList<EffectRenderRegistration> = arrayListOf()

        val visitedComponents = batch.visitedComponents
        val registrationsByComponent: MutableMap<ComponentInstanceId, MutableList<EffectRenderRegistration>> = linkedMapOf()
        batch.registrationsInOrder.forEach { registration ->
            registrationsByComponent.getOrPut(registration.componentId) { arrayListOf() }
                .add(registration)
        }

        committedEffectsByComponent.forEach { (componentId, effectsByPath) ->
            if (componentId !in visitedComponents) {
                effectsByPath.forEach { (path, state) ->
                    val cleanup = state.cleanup
                    if (cleanup != null) {
                        cleanupQueue.add(EffectRegistrationKey(componentId, path) to cleanup)
                    }
                    removeQueue.add(EffectRegistrationKey(componentId, path))
                }
            }
        }

        batch.registrationsInOrder.forEach { registration ->
            val committedByPath = committedEffectsByComponent[registration.componentId]
            val existing = committedByPath?.get(registration.path)
            if (existing == null) {
                runQueue.add(registration)
                return@forEach
            }
            val shouldRerun = when (registration.runMode) {
                EffectHookRunMode.EveryCommit -> true
                EffectHookRunMode.OnDependencyChange -> existing.deps != registration.deps
            }
            if (shouldRerun) {
                val cleanup = existing.cleanup
                if (cleanup != null) {
                    cleanupQueue.add(
                        EffectRegistrationKey(registration.componentId, registration.path) to cleanup
                    )
                }
                runQueue.add(registration)
            }
        }

        committedEffectsByComponent.forEach { (componentId, committedByPath) ->
            if (componentId !in visitedComponents) {
                return@forEach
            }
            val registrations = registrationsByComponent[componentId].orEmpty()
            val visitedPaths = registrations.mapTo(linkedSetOf()) { registration -> registration.path }
            committedByPath.forEach { (path, state) ->
                if (path !in visitedPaths) {
                    val cleanup = state.cleanup
                    if (cleanup != null) {
                        cleanupQueue.add(EffectRegistrationKey(componentId, path) to cleanup)
                    }
                    removeQueue.add(EffectRegistrationKey(componentId, path))
                }
            }
        }

        cleanupQueue.forEach { (key, cleanup) ->
            runEffectCleanup(
                componentId = key.componentId,
                path = key.path,
                cleanup = cleanup,
                reason = "effect cleanup"
            )
        }

        removeQueue.forEach { key ->
            val committedByPath = committedEffectsByComponent[key.componentId] ?: return@forEach
            committedByPath.remove(key.path)
            if (committedByPath.isEmpty()) {
                committedEffectsByComponent.remove(key.componentId)
            }
        }

        runQueue.forEach { registration ->
            val committedByPath = committedEffectsByComponent
                .getOrPut(registration.componentId) { linkedMapOf() }
            val cleanup = runCommittedEffect(
                componentId = registration.componentId,
                path = registration.path,
                effect = registration.effect
            )
            committedByPath[registration.path] = CommittedEffectState(
                runMode = registration.runMode,
                deps = registration.deps,
                cleanup = cleanup
            )
        }
    }

    private fun runCommittedEffect(
        componentId: ComponentInstanceId,
        path: HookPath,
        effect: (registerCleanup: (() -> Unit) -> Unit) -> Unit
    ): (() -> Unit)? {
        var cleanupRegistration: (() -> Unit)? = null
        effect { cleanup ->
            if (cleanupRegistration != null) {
                throw HookUsageException(
                    "Effect at path '$path' in component '${componentId.debugPath()}' " +
                        "registered multiple cleanup handlers via onDispose."
                )
            }
            cleanupRegistration = cleanup
        }
        return cleanupRegistration
    }

    private fun runEffectCleanup(
        componentId: ComponentInstanceId,
        path: HookPath,
        cleanup: () -> Unit,
        reason: String
    ) {
        try {
            cleanup()
        } catch (error: Throwable) {
            println(
                "[DSGL][Hooks] Effect cleanup failed at path '$path' in component '${componentId.debugPath()}' " +
                    "during $reason: ${error.message}"
            )
        }
    }

    private fun runCommittedEffectCleanupForSubtree(
        root: ComponentInstanceId?,
        reason: String
    ) {
        val entries = committedEffectsByComponent.entries.toList()
        entries.forEach { (componentId, byPath) ->
            if (root != null && !componentId.isSameOrDescendantOf(root)) {
                return@forEach
            }
            byPath.forEach { (path, state) ->
                val cleanup = state.cleanup ?: return@forEach
                runEffectCleanup(
                    componentId = componentId,
                    path = path,
                    cleanup = cleanup,
                    reason = reason
                )
            }
        }
    }

    private fun currentFrame(): ComponentFrame {
        ensureActiveRender()
        return componentStack.lastOrNull()
            ?: throw HookUsageException("Hook runtime has no active component frame.")
    }

    private fun currentFrameForHookResolution(): ComponentFrame {
        currentInferenceLeafCallSite = null
        prepareInferredComponentFramesForHookResolution()
        val frame = currentFrame()
        return if (frame.origin == ComponentFrameOrigin.Inferred) {
            maybeAdvanceInferredSiblingFrame(frame)
        } else {
            frame
        }
    }

    private fun prepareInferredComponentFramesForHookResolution() {
        ensureActiveRender()
        val top = componentStack.lastOrNull()
            ?: throw HookUsageException("Hook runtime has no active component frame.")
        if (top.origin == ComponentFrameOrigin.Explicit) {
            currentInferenceLeafCallSite = null
            return
        }
        val snapshot = inferComponentSnapshotFromCallContext()
        currentInferenceLeafCallSite = snapshot.leafCallSite
        reconcileInferredFrames(snapshot.descriptors)
    }

    private fun reconcileInferredFrames(desiredPath: List<InferredComponentDescriptor>) {
        val baseIndex = findDeepestNonInferredFrameIndex()
        var commonPrefixLength = 0
        val existingCount = componentStack.size - (baseIndex + 1)
        while (commonPrefixLength < existingCount && commonPrefixLength < desiredPath.size) {
            val existingFrame = componentStack.elementAt(baseIndex + 1 + commonPrefixLength)
            val existingDescriptor = existingFrame.inferredDescriptor
            if (existingFrame.origin != ComponentFrameOrigin.Inferred || existingDescriptor != desiredPath[commonPrefixLength]) {
                break
            }
            commonPrefixLength += 1
        }

        while (componentStack.size > baseIndex + 1 + commonPrefixLength) {
            popInferredFrameForTransition()
        }

        var parent = componentStack.lastOrNull()
            ?: throw HookUsageException("Hook runtime has no active component frame.")
        var index = commonPrefixLength
        while (index < desiredPath.size) {
            val descriptor = desiredPath[index]
            parent = pushComponentFrame(
                parent = parent,
                componentName = descriptor.componentName,
                key = null,
                origin = ComponentFrameOrigin.Inferred,
                inferredDescriptor = descriptor
            )
            index += 1
        }
    }

    private fun findDeepestNonInferredFrameIndex(): Int {
        var index = componentStack.size - 1
        while (index >= 0) {
            if (componentStack.elementAt(index).origin != ComponentFrameOrigin.Inferred) {
                return index
            }
            index -= 1
        }
        throw HookUsageException("Hook runtime internal error: no non-inferred frame in component stack.")
    }

    private fun popInferredFrameForTransition() {
        val frame = componentStack.lastOrNull()
            ?: throw HookUsageException("Hook runtime has no active component frame.")
        if (frame.origin != ComponentFrameOrigin.Inferred) {
            throw HookUsageException("Hook runtime internal error: attempted to pop non-inferred frame during inference transition.")
        }
        if (frame.scopeSegments.isNotEmpty()) {
            throw HookUsageException(
                "Invalid nested hook scope behavior: component '${frame.componentId.debugPath()}' " +
                    "ended with unclosed custom hook scope '${frame.scopeSegments.last()}'."
            )
        }
        componentStack.removeLast()
    }

    private fun unwindInferredFramesForRenderEnd() {
        while (componentStack.size > 1) {
            val frame = componentStack.last()
            when (frame.origin) {
                ComponentFrameOrigin.Inferred -> popInferredFrameForTransition()
                ComponentFrameOrigin.Explicit -> {
                    throw HookUsageException(
                        "Invalid nested component scope behavior: render ended with unbalanced explicit component scopes."
                    )
                }

                ComponentFrameOrigin.Root -> {
                    throw HookUsageException(
                        "Hook runtime internal error: root frame encountered before stack unwind completed."
                    )
                }
            }
        }
    }

    private fun pushComponentFrame(
        parent: ComponentFrame,
        componentName: String,
        key: Any?,
        origin: ComponentFrameOrigin,
        inferredDescriptor: InferredComponentDescriptor?
    ): ComponentFrame {
        val positionalIndex = if (key == null) {
            val current = parent.positionalChildCounters[componentName] ?: 0
            parent.positionalChildCounters[componentName] = current + 1
            current
        } else {
            -1
        }

        val childId = parent.componentId.child(
            name = componentName,
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
        val childFrame = ComponentFrame(
            componentId = childId,
            context = childContext,
            origin = origin,
            inferredDescriptor = inferredDescriptor
        )
        componentStack.addLast(childFrame)
        return childFrame
    }

    private fun inferComponentSnapshotFromCallContext(): InferenceSnapshot {
        val innerToOuter: MutableList<InferredComponentDescriptor> = arrayListOf()
        var leafCallSite: HookCallSite? = null
        var foundRenderBoundary = false
        val trace = Throwable().stackTrace
        for (frame in trace) {
            if (isInferenceFrameworkFrame(frame)) {
                continue
            }
            val methodName = normalizeInferenceMethodName(frame.methodName) ?: continue
            if (isRenderBoundaryFrame(frame.className, methodName)) {
                foundRenderBoundary = true
                break
            }
            val descriptor = InferredComponentDescriptor(
                ownerClassName = frame.className,
                methodName = methodName,
                componentName = inferredComponentName(frame.className, methodName)
            )
            if (leafCallSite == null) {
                leafCallSite = HookCallSite(
                    className = frame.className,
                    methodName = methodName,
                    lineNumber = frame.lineNumber
                )
            }
            if (innerToOuter.lastOrNull() == descriptor) {
                continue
            }
            innerToOuter += descriptor
        }
        if (!foundRenderBoundary || innerToOuter.isEmpty()) {
            return InferenceSnapshot(
                descriptors = emptyList(),
                leafCallSite = null
            )
        }
        val result = innerToOuter.toMutableList()
        result.reverse()
        return InferenceSnapshot(
            descriptors = result,
            leafCallSite = leafCallSite
        )
    }

    private fun isInferenceFrameworkFrame(frame: StackTraceElement): Boolean {
        val className = frame.className
        if (className.startsWith("org.dreamfinity.dsgl.core.hooks.")) return true
        if (className.startsWith("org.dreamfinity.dsgl.core.dsl.")) return true
        if (className == DsglWindow::class.java.name) return true
        if (className == "org.dreamfinity.dsgl.core.dsl.UiScopeKt") return true
        if (className.startsWith("java.")) return true
        if (className.startsWith("jdk.")) return true
        if (className.startsWith("sun.")) return true
        if (className.startsWith("kotlin.")) return true
        if (className.startsWith("org.jetbrains.")) return true
        return false
    }

    private fun normalizeInferenceMethodName(rawMethodName: String): String? {
        val method = when {
            rawMethodName.endsWith("\$default") -> rawMethodName.removeSuffix("\$default")
            else -> rawMethodName
        }
        if (method.isBlank()) return null
        if (method == "getStackTrace") return null
        if (method.startsWith("invoke")) return null
        if (method.startsWith("access\$")) return null
        if (method.contains("\$lambda")) return null
        if (method.contains("\$annotations")) return null
        if (method == "<init>" || method == "<clinit>") return null
        if (method == "ui") return null
        if (method == "buildUiTree") return null
        return method
    }

    private fun isRenderBoundaryFrame(className: String, methodName: String): Boolean {
        if (methodName != "render") return false
        return windowSubclassCacheByClassName.getOrPut(className) {
            runCatching {
                val cls = Class.forName(className, false, javaClass.classLoader)
                DsglWindow::class.java.isAssignableFrom(cls)
            }.getOrDefault(false)
        }
    }

    private fun inferredComponentName(className: String, methodName: String): String {
        val ownerSimple = className.substringAfterLast('.').replace('$', '_')
        val raw = "inferred_${ownerSimple}_$methodName"
        return validateSegment(
            value = raw.replace('.', '_'),
            label = "inferred component name"
        )
    }

    private fun maybeAdvanceInferredSiblingFrame(frame: ComponentFrame): ComponentFrame {
        if (frame.origin != ComponentFrameOrigin.Inferred) {
            return frame
        }
        val callSite = currentInferenceLeafCallSite ?: return frame
        if (!frame.seenHookCallSitesThisRender.contains(callSite)) {
            return frame
        }
        val descriptor = frame.inferredDescriptor
            ?: throw HookUsageException("Hook runtime internal error: inferred frame without descriptor.")
        if (frame.scopeSegments.isNotEmpty()) {
            throw HookUsageException(
                "Invalid nested hook scope behavior: component '${frame.componentId.debugPath()}' " +
                    "ended with unclosed custom hook scope '${frame.scopeSegments.last()}'."
            )
        }
        componentStack.removeLast()
        val parent = componentStack.lastOrNull()
            ?: throw HookUsageException("Hook runtime has no parent frame for inferred sibling transition.")
        return pushComponentFrame(
            parent = parent,
            componentName = descriptor.componentName,
            key = null,
            origin = ComponentFrameOrigin.Inferred,
            inferredDescriptor = descriptor
        )
    }

    private fun recordInferredHookCallSite(frame: ComponentFrame) {
        if (frame.origin != ComponentFrameOrigin.Inferred) {
            return
        }
        val callSite = currentInferenceLeafCallSite ?: return
        frame.seenHookCallSitesThisRender += callSite
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
