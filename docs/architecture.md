# Architecture

This document describes the DSGL runtime flow and how the modules fit together.

## Module boundaries

- `core`: UI DSL, DOM tree, layout, events, and render command model.
- `mc1710`: Minecraft 1.7.10 host and renderer adapter.

## Render pipeline

1) `DsglWindow.render()` builds a `DomTree` using the DSL.
2) The host calls `DomTree.render(...)` to measure and lay out nodes.
3) The host calls `DomTree.paint(...)` to build a list of `RenderCommand`s.
4) A platform adapter (e.g., `Mc1710UiAdapter`) executes those commands.

## Event flow

- Input events are turned into `Event` instances and posted through `EventBus`.
- Most events bubble up through parent nodes; enter/leave/over are non-bubbling.
- Focus is managed by `FocusManager` and restored across rebuilds by key/path.

## State and rebuilds

- Use `DsglWindow.state(...)` to create observable state.
- When state changes, `DsglWindow` triggers a rebuild via the host.
- Rebuilds generate a new `DomTree`, so avoid storing node references across renders.

## Host integration

Hosts implement `DsglWindowHost` and drive:
- Lifecycle (`onOpen`, `onClose`, `onResize`)
- Input event routing
- Render loop (`render`/`paint` pipeline)

For Minecraft 1.7.10, `DsglScreenHost` provides this integration on top of `GuiScreen`.
