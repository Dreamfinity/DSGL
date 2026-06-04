# Architecture

## Core model

- A window (`DsglWindow`) defines UI by returning a DOM tree in `render()`.
- A screen host drives lifecycle, input, rebuild scheduling, layout, and painting.
- The retained DOM tree is reconciled across rebuilds instead of recreated from scratch every frame.
- A per-window hook runtime tracks hook state/effects across rebuilds.

## Main execution path

In MC 1.7.10, the primary loop is in `DsglScreenHost`:

1. `initGui()` creates/attaches the window and host services.
2. Each frame (`drawScreen`), the host checks whether a rebuild is needed.
3. If needed, the host calls `window.render()` inside a hook render session.
4. The new tree is reconciled into the retained tree (`DomTree.reconcileWith(...)`).
5. Layout commit runs through `DomTree.render(...)`.
6. Paint commands are generated via `DomTree.paint(...)`.
7. The adapter (`Mc1710UiAdapter`) executes render commands.

The host can still paint previously committed commands if rebuild/layout/paint fails in a frame.

## Rebuild and reconcile

Rebuild triggers include:

- window invalidation (`state(...)`, hook state changes)
- viewport/size changes
- hot-reload signal (when enabled)

On rebuild, DSGL:

- builds a fresh tree from the window
- reconciles into the retained tree
- keeps compatible nodes
- cleans up detached subtrees/listeners

This is why stable keys matter for focus/state continuity.

## Style resolution in the runtime

Style data is resolved from:

- inline style DSL on elements/components
- DSS stylesheets loaded by `StylesheetManager`
- inspector overrides (application scope)

`StyleEngine` applies computed styles before layout and also during paint when style revisions or pseudo/selector state
require it.

Style scope is explicit:

- Application root and Application portal trees use application scope
- System root and System portal trees use system scope
- Debug root and Debug portal trees use debug scope

## Layout and paint responsibilities

`DomTree.render(...)`:

- applies styles for current scope
- resolves layout style values
- performs layout
- commits ref bindings

`DomTree.paint(...)`:

- updates style/scroll/sticky visual state if needed
- rebuilds render-command chunks when dirty
- emits final render commands for the frame

So, layout is not a one-time setup step, it is part of the frame pipeline when invalidated.

## Hook runtime integration

The host controls hook session boundaries:

- `beginRenderBuild(...)`
- `endRenderBuild()`
- `commitRenderBuild()` on successful rebuild
- `discardRenderBuild()` on failed/aborted rebuild

This is the basis for DSGL effect semantics (commit-bound effects, discard-safe behaviour).

When hot-reload mode is active, hook signature mismatches can request subtree remount/reset instead of crashing the
entire rebuild.

## Screen domain model

DSGL screens are composed from domain surfaces. The authoritative surface contract is `ScreenDomainSurfaces`.

The screen domains are:

- Application
- System
- Debug

Every domain has the same pair of surfaces:

- root
- portal

Empty roots and empty portals are valid. System root and Debug portal do not require special mechanics just because some
screens currently have no content there. The current Debug pane is mounted as Debug root DOM, and Debug has a real portal
host even when no Debug portal entries are active.

## Domain surface ordering contract

`ScreenDomainSurfaces` defines the render and input ordering:

- paint order: `Application root -> Application portal -> System root -> System portal -> Debug root -> Debug portal`
- input priority: `Debug portal -> Debug root -> System portal -> System root -> Application portal -> Application root`

Any future refactor must preserve these orders unless a deliberate architecture change is explicitly approved.

## Domain surface ownership contract

### `DsglScreenHost` owns

- adapter/backend lifecycle integration
- frame orchestration across domain surfaces
- rebuild/layout/paint guard rails and fallback behaviour
- top-level domain-surface composition and contract-ordered input routing
- global cleanup across host-managed portal services on close

`DsglScreenHost` is the current screen-level orchestrator.

### Domain surface hosts own

- a specific `ScreenDomainSurface`
- viewport-local lifecycle for that surface
- render/paint/input forwarding for that surface
- cleanup of state mounted through that surface

Screen/runtime ownership classes now use domain/portal terminology, and their runtime contract is domain-surface-first
through `DomainSurfaceHost`.

### Portal hosts own

- portal entry registration
- active entry paint/input order
- entry lifecycle cleanup
- validation that entries are mounted into their owning domain portal surface
- generic portal-entry policy metadata and evaluation for dismiss, backdrop consumption, focus intent, lifecycle,
  placement, and protected inside regions

Portal hosts are physical mount points for floating UI in their owning domain. They are not separate domains or hidden
widget runtimes.

Portal entry policies are generic. Component-specific behavior remains in the mounted portal DOM subtree or in temporary
helper engines during migration. Outside-pointer policy is evaluated topmost-first against active portal entries. Inside
entry interaction is determined from the entry DOM, entry bounds, or explicit protected bounds; it must not depend on
widget type. Once a portal DOM tree is selected for input, events bubble inside that physical portal tree only, not into
the owning domain root DOM or another domain.

## Shared domain mechanics

The following mechanics should converge into common expectations across all domains:

- explicit per-frame phases (`input frame prep -> sync -> render -> paint -> clear refs`)
- explicit surface input enable/disable gating
- predictable DOM and policy-based input routing for eligible surface content
- screen/domain-aware keyboard targeting: key-down and key-up target the one focused node only when that node belongs to
  the selected physical domain surface tree
- shared DOM-node pointer capture bookkeeping for root and portal DOM dispatch (`PointerCaptureSession`): captured
  nodes continue receiving move/release/cancel until capture ends, and keyed captures can restore across
  retained-DOM reconciliation
- stable portal-entry ownership (`id`, active/open state, placement/drag session ownership where applicable)
- explicit viewport/bounds/coordinate handling with no hidden fallback geometry assumptions

## Domain-specific ownership

- Application root and Application portal share application styling semantics.
- System root and System portal are isolated from application styling.
- Debug root and Debug portal are isolated from application styling.
- Domain-specific behaviour stays in its owning domain or portal service.

## Intentional distinctions vs accidental gaps

Intentional distinctions:

- split between Application, System, and Debug ownership
- split between each domain root and portal surface
- scope-driven ownership for transient portal routing
- explicit Debug domain ownership for debug-only diagnostics and controls

Remaining explicit limits:

- `DsglScreenHost` still owns top-level pointer, hover, and input routing between domain surfaces. DOM-node pointer
  capture bookkeeping is shared between root and portal DOM dispatch. Application-owned DnD drag ghosts now paint through
  an Application portal entry while preserving the existing DnD session model.
- implementation names that describe screen/runtime ownership now use domain/portal terminology.
- system Inspector/color-picker entries still use system-specific manual dispatch plus DOM fallback where text editing
  and panel dragging require it.
- modal focus/session state is still modal-specific through `ModalPortalSessionStore`, but modal focus requests are
  scoped to the mounted modal portal root where available.

## Non-regression invariants for domain refactors

- preserve domain-surface ordering for both paint and input
- preserve ownership separation between Application, System, and Debug domains
- preserve application/system/debug scope separation and style isolation boundaries
- preserve inspector drag behaviour and pointer-capture semantics
- preserve transient portal ownership as owner-token/session based, not cursor-derived ownership
- preserve anti-click-through behaviour: once a higher-priority surface consumes input, lower surfaces do not receive it
- preserve pointer-sequence ownership: when a higher-priority surface consumes pointer-down, the matching pointer-up
  remains owned by that higher surface sequence and must not synthesize an Application root click if the portal state
  changes before release
- preserve central pointer-capture cleanup: captured DOM nodes receive move/release/cancel until capture ends, consumed
  pointer events do not arm lower-root DnD, and active modal portals must not leak Application-root DnD ghosts
- preserve DnD ghost portal ownership: Application-owned drag ghosts paint through Application portal entries without
  adding another root/portal/widget capture bookkeeping path

Public helper APIs such as modal/select/context-menu/color picker sit on top of this model; low-level domain/portal
internals are not a stable app extension API.

Keyboard events follow DOM-like targeting. A screen still has one active focused element. The focused element belongs to
a domain through its mounted DOM tree, and key-down/key-up dispatch selects a domain surface before posting the event to
that focused node. Bubbling then stays inside that node's physical DOM tree. Screen/global shortcuts such as debug
toggles and style reloads remain explicit screen-host dispatcher behavior.

Select popup ownership is scope-aware: application-owned selects route through the application portal service, and
system-owned selects route through the system portal service. The select popup is mounted as a portal entry with a real
portal DOM node. Pointer and wheel input for the popup routes through that portal DOM tree first; outside pointer
dismissal uses generic portal-entry policy evaluation before lower-priority surfaces can receive the same pointer
sequence. `SelectEngine` remains a temporary helper for select state, measurement, placement, animation, keyboard
navigation, and paint primitives rather than an independent screen-level runtime owner.

Modal presentation is mounted as application portal DOM while `modalPortal` keeps regular content in the application root.
Modal pointer-down containment, backdrop dismissal, consume-only static backdrop behavior, and anti-click-through use
generic portal-entry policies. The screen coordinator also preserves higher-surface pointer sequence ownership so a
portal-consumed backdrop press cannot release into an Application root click after modal state changes.
`ModalPortalSessionStore` owns the modal-specific stack/focus/trap/restore session state as a bounded implementation
detail, with focus restore/trap requests scoped to the modal portal DOM root where available.

Inspector and system color picker UI are system portal entries. They keep system style isolation and may still use
system-domain manual dispatch plus DOM fallback internally where native Inspector/color-picker DOM editing requires it.

DnD drag visuals are floating UI. Application-owned drag ghosts are represented as Application portal entries and are
painted as part of the Application portal surface, after Application root content and before later higher-domain
surfaces. The DnD engine still owns drag session state, smoothing, source hiding, drop target tracking, and preview
command production. Portal ownership only decides where the visual ghost is mounted and staged (`ApplicationDndGhostPortalController`
in `ApplicationPortalHost.kt` implements this entry). While an Application
modal portal is active, lower Application-root DnD ghost output remains suppressed so modal-owned pointer sequences do
not leak stale root ghosts. System/Debug-owned drag visuals are reserved for the owning domain portal when such drag
sources exist.

## Where to inspect next

- Runtime host path: `adapters/mc-forge-1-7-10/src/main/kotlin/org/dreamfinity/dsgl/mcForge1710/DsglScreenHost.kt`
- Retained tree + reconcile/layout/paint: `core/src/main/kotlin/org/dreamfinity/dsgl/core/DomTree.kt`
- Style runtime: `core/src/main/kotlin/org/dreamfinity/dsgl/core/style/StyleEngine.kt`
- Stylesheet loading: `core/src/main/kotlin/org/dreamfinity/dsgl/core/style/StylesheetManager.kt`
- Hook lifecycle runtime: `core/src/main/kotlin/org/dreamfinity/dsgl/core/hooks/ComponentHookRuntime.kt`
- Portal input capture: `core/src/main/kotlin/org/dreamfinity/dsgl/core/portal/input/PointerCaptureSession.kt`
- Application portal host + DnD ghost controller: `core/src/main/kotlin/org/dreamfinity/dsgl/core/portal/ApplicationPortalHost.kt`
