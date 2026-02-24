# Getting Started

## 1) Publish to Maven Local

```shell
.\gradlew :core:publishToMavenLocal :mc1710:publishToMavenLocal
```

## 2) Add Dependencies

```kotlin
dependencies {
    implementation("org.dreamfinity:dsgl-core:0.0.1-beta")
    implementation("org.dreamfinity:dsgl-mc1710:0.0.1-beta")
}
```

## 3) Create a Window

```kotlin
class MyWindow : DsglWindow() {
    private var clicks by state(0)

    override fun render(): DomTree = ui {
        div(ComponentProps(padding = 8, gap = 6).asFlexColumn()) {
            text(TextProps("Hello DSGL"))
            button(ButtonProps("Click me").apply {
                onMouseClick = { clicks += 1 }
            })
            text { "Clicks: $clicks" }
        }
    }
}
```

## 4) Open a Screen (MC 1.7.10)

```kotlin
class MyScreen : DsglScreenHost(MyWindow())

Minecraft.getMinecraft().displayGuiScreen(MyScreen())
```

## Example

For an end-to-end example with input handling and state, see:
`mc1710-demo/src/main/kotlin/org/dreamfinity/dsgl/mc1710/demo/ShowcaseWindow.kt`.
