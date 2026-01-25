# Dreamfinity Superior GUI Library (DSGL)

DSGL is a retained-mode UI DSL written in Kotlin. It renders a DOM-like tree into
platform-specific render commands, with Minecraft 1.7.10 support provided by the
`mc1710` module.

Modules:

- `core`: Platform-agnostic UI tree, layout, events, and render commands.
- `mc1710`: Minecraft 1.7.10 host and renderer that turns render commands into MC calls.

## Quick Start

1. Publish to Maven Local:

```shell
.\gradlew :core:publishToMavenLocal :mc1710:publishToMavenLocal
```

2. Add dependencies in your mod:

```kotlin
dependencies {
    implementation("org.dreamfinity:dsgl-core:0.0.1-beta")
    implementation("org.dreamfinity:dsgl-mc1710:0.0.1-beta")
}
```

3. Create a window and open it:

```kotlin
class MyScreen : DsglScreenHost(MyWindow())

// Open from client code
Minecraft.getMinecraft().displayGuiScreen(MyScreen())
```

For a complete example, see `mc1710/src/main/kotlin/org/dreamfinity/dsgl/mc1710/demo/DemoScreen.kt`.

## What is this

DSGL lets you describe UI with a Kotlin DSL. It builds a retained DOM tree,
measures and lays out nodes, then emits render commands. A host adapter (like
Minecraft 1.7.10) executes those commands and routes input events back to the tree.

## Main Concepts

- **DsglWindow**: The entry point for building UI. Override `render()` to return a `DomTree`.
- **DOM nodes**: Elements like `ContainerNode`, `TextNode`, `ButtonNode`, etc.
- **Layout**: `ContainerNode` supports `Column`, `Row`, and `Stack`.
- **Event bus**: `EventBus` routes input events to node handlers.
- **State**: `DsglWindow.state(...)` returns state that triggers rebuilds on change.
- **Host adapter**: `DsglScreenHost` owns lifecycle, `Mc1710UiAdapter` paints commands.

## Public API Entry Points

- `DsglWindow` and `DsglWindowHost`
- DSL builders in `Dsl.kt` (`ui`, `UiScope`, `ComponentProps`, etc.)
- Event types and `EventBus`
- `RenderCommand` and `UiMeasureContext`
- MC host: `DsglScreenHost`, `Mc1710UiAdapter`

## Build/Run

Build the project:

```shell
.\gradlew build
```

Run your mod as usual with ForgeGradle. For UI usage, open a screen derived from
`DsglScreenHost` on the client side.

## Common pitfalls and constraints

- **Rebuilds**: UI is rebuilt on demand. Use `state(...)` or `invalidate()` to trigger rebuilds.
- **Focus**: Key events are routed to the focused node; set stable `key` values to retain focus.
- **Threading**: Build and mutate UI state on the host UI thread.
- **Sizing**: Nodes measure from content unless `width`/`height` are provided.
- **Images**: In MC 1.7.10, `ImageNode` supports resource IDs, `file://` paths, and http(s) URLs.

## More Docs

- [Getting started](docs/getting-started.md)
- [Architecture](docs/architecture.md)
