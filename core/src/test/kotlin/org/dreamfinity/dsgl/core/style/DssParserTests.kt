package org.dreamfinity.dsgl.core.style

import java.nio.file.Files
import kotlin.test.*

class DssParserTests {
    @Test
    fun parsesSelectorsAndDeclarations() {
        val data = DssParser.parse(
            """
            :root { --primary: #3E6B9E; --fg: #FFFFFF; }
            button { padding: 6px 10px; background-color: #222222; }
            .accent { border-width: 1px; }
            button.primary:hover { color: #FFF; }
            #dangerAction:disabled { background-color: #A34343; }
            """.trimIndent(),
            "selectors.dss"
        )

        assertEquals(4, data.rules.size, "Expected 4 non-root rules.")
        assertEquals("#3E6B9E", data.rootVariables["--primary"])
        assertEquals("#FFFFFF", data.rootVariables["--fg"])

        val typeRule = data.rules[0]
        assertEquals("button", typeRule.selector.typeName)
        assertEquals(null, typeRule.selector.className)
        assertEquals(null, typeRule.selector.id)
        assertEquals(null, typeRule.selector.pseudoState)
        assertIs<StyleExpression.Literal>(typeRule.declarations.get(StyleProperty.PADDING))
        assertIs<StyleExpression.Literal>(typeRule.declarations.get(StyleProperty.BACKGROUND_COLOR))

        val classRule = data.rules[1]
        assertEquals("accent", classRule.selector.className)

        val typeClassPseudo = data.rules[2]
        assertEquals("button", typeClassPseudo.selector.typeName)
        assertEquals("primary", typeClassPseudo.selector.className)
        assertEquals(StylePseudoState.HOVER, typeClassPseudo.selector.pseudoState)

        val idPseudo = data.rules[3]
        assertEquals("dangerAction", idPseudo.selector.id)
        assertEquals(StylePseudoState.DISABLED, idPseudo.selector.pseudoState)
    }

    @Test
    fun parsesVariableReferences() {
        val data = DssParser.parse(
            """
            :root { --primary: #3E6B9E; }
            button.primary { background-color: var(--primary); }
            """.trimIndent(),
            "vars.dss"
        )

        val expr = data.rules.single().declarations.get(StyleProperty.BACKGROUND_COLOR)
        val variableRef = assertIs<StyleExpression.VariableRef>(expr)
        assertEquals("--primary", variableRef.name)
    }

    @Test
    fun assignsStableSourceOrder() {
        val data = DssParser.parse(
            """
            button { width: 10px; }
            .accent { width: 11px; }
            #idOne { width: 12px; }
            """.trimIndent(),
            "order.dss"
        )

        assertEquals(0, data.rules[0].sourceOrder)
        assertEquals(1, data.rules[1].sourceOrder)
        assertEquals(2, data.rules[2].sourceOrder)
    }

    @Test
    fun ignoresBlockComments() {
        val data = DssParser.parse(
            """
            /* comment before rules */
            .accent { width: 10px; } /* inline comment */
            """.trimIndent(),
            "comments.dss"
        )

        assertEquals(1, data.rules.size)
        assertEquals("accent", data.rules[0].selector.className)
    }

    @Test
    fun rejectsVariableOutsideRoot() {
        val error = assertFailsWith<DssParseException> {
            DssParser.parse(
                """
                .card {
                  --primary: #123456;
                }
                """.trimIndent(),
                "bad-vars.dss"
            )
        }
        assertTrue(error.message?.contains("only supported inside :root") == true)
    }

    @Test
    fun rejectsUnsupportedPropertyWithLocation() {
        val error = assertFailsWith<DssParseException> {
            DssParser.parse(
                """
                .card {
                  unsupportedProp: 10;
                }
                """.trimIndent(),
                "bad-prop.dss"
            )
        }
        assertEquals("bad-prop.dss", error.path)
        assertTrue(error.line >= 2)
        assertTrue(error.column >= 1)
    }

    @Test
    fun rejectsInvalidSpacingShorthand() {
        val error = assertFailsWith<DssParseException> {
            DssParser.parse(
                """
                button {
                  padding: 1px 2px 3px 4px 5px;
                }
                """.trimIndent(),
                "bad-spacing.dss"
            )
        }
        assertTrue(error.message?.contains("supports 1, 2, 3, or 4 values") == true)
    }

    @Test
    fun parsesFromFilePath() {
        val tempDir = Files.createTempDirectory("dss-parser-test").toFile()
        try {
            val file = tempDir.resolve("file-parse.dss")
            file.writeText("button { height: 20px; }")
            val data = DssParser.parse(file)
            assertEquals(1, data.rules.size)
            assertEquals("button", data.rules[0].selector.typeName)
            assertEquals(file.path, data.source)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun parsesDisplayFlexAndGridProperties() {
        val data = DssParser.parse(
            """
            .layout {
              display: flex;
              flex-direction: row;
              justify-content: space-between;
              align-items: center;
              gap: 6px;
              text-wrap: nowrap;
            }
            .grid {
              display: grid;
              grid-columns: 4;
              grid-column-span: 2;
            }
            """.trimIndent(),
            "display.dss"
        )

        val layoutRule = data.rules.first { it.selector.className == "layout" }
        assertIs<StyleExpression.Literal>(layoutRule.declarations.get(StyleProperty.DISPLAY))
        assertIs<StyleExpression.Literal>(layoutRule.declarations.get(StyleProperty.FLEX_DIRECTION))
        assertIs<StyleExpression.Literal>(layoutRule.declarations.get(StyleProperty.JUSTIFY_CONTENT))
        assertIs<StyleExpression.Literal>(layoutRule.declarations.get(StyleProperty.ALIGN_ITEMS))
        assertIs<StyleExpression.Literal>(layoutRule.declarations.get(StyleProperty.GAP))
        assertIs<StyleExpression.Literal>(layoutRule.declarations.get(StyleProperty.TEXT_WRAP))

        val gridRule = data.rules.first { it.selector.className == "grid" }
        assertIs<StyleExpression.Literal>(gridRule.declarations.get(StyleProperty.DISPLAY))
        assertIs<StyleExpression.Literal>(gridRule.declarations.get(StyleProperty.GRID_COLUMNS))
        assertIs<StyleExpression.Literal>(gridRule.declarations.get(StyleProperty.GRID_COLUMN_SPAN))
    }

    @Test
    fun parsesCombinatorsAndSpecificity() {
        val data = DssParser.parse(
            """
            .panel .item { color: #FFFFFF; }
            .panel > .item { color: #EEEEEE; }
            .item + .item { color: #DDDDDD; }
            .item ~ .item { color: #CCCCCC; }
            #root.toolbar .btn:hover { color: #DDDDDD; }
            """.trimIndent(),
            "combinators.dss"
        )

        assertEquals(5, data.rules.size)
        assertTrue(data.rules[0].selector.hasCombinators)
        assertEquals(StyleCombinator.Descendant, data.rules[0].selector.steps[1].combinatorToLeft)
        assertEquals(StyleCombinator.Child, data.rules[1].selector.steps[1].combinatorToLeft)
        assertEquals(StyleCombinator.AdjacentSibling, data.rules[2].selector.steps[1].combinatorToLeft)
        assertEquals(StyleCombinator.GeneralSibling, data.rules[3].selector.steps[1].combinatorToLeft)
        assertEquals(1, data.rules[4].selector.specificity.idCount)
        assertEquals(3, data.rules[4].selector.specificity.classLikeCount)
    }

    @Test
    fun `parses universal selector`() {
        val data = DssParser.parse(
            """
            * { padding: 2px; }
            """.trimIndent(),
            "universal.dss"
        )

        assertEquals(1, data.rules.size)
        assertTrue(data.rules[0].selector.steps.single().part.universal)
    }

    @Test
    fun `parses open pseudo state`() {
        val data = DssParser.parse(
            """
            select:open { border-color: #7CB6FF; }
            """.trimIndent(),
            "open-pseudo.dss"
        )

        assertEquals(1, data.rules.size)
        assertEquals(StylePseudoState.OPEN, data.rules.single().selector.pseudoState)
    }

    @Test
    fun parsesImportantDeclarationFlag() {
        val data = DssParser.parse(
            """
            .btn { color: #111111 !important; }
            """.trimIndent(),
            "important.dss"
        )

        val rule = data.rules.single()
        assertTrue(rule.declarations.isImportant(StyleProperty.FOREGROUND_COLOR))
    }

    @Test
    fun `foreground-color alias is accepted and warns once`() {
        val data = DssParser.parse(
            """
            .card { foreground-color: #ff0000; }
            .other { foreground-color: #00ff00; }
            """.trimIndent(),
            "alias.dss"
        )

        assertEquals(2, data.rules.size)
        assertIs<StyleExpression.Literal>(data.rules[0].declarations.get(StyleProperty.FOREGROUND_COLOR))
        assertEquals(1, data.warnings.size)
        assertTrue(data.warnings.single().contains("foreground-color"))
    }

    @Test
    fun `root block supports variables and root selector declarations`() {
        val data = DssParser.parse(
            """
            :root { --base: #222222; color: #abcdef; }
            """.trimIndent(),
            "root-rule.dss"
        )

        assertEquals("#222222", data.rootVariables["--base"])
        assertEquals(1, data.rules.size)
        assertEquals("dsgl-root", data.rules.single().selector.typeName)
    }

    @Test
    fun `unitless non-zero length literal is rejected`() {
        val error = assertFailsWith<DssParseException> {
            DssParser.parse(
                """
                .panel { margin: 12; padding: 4px; }
                """.trimIndent(),
                "unitless-length.dss"
            )
        }
        assertTrue(error.message?.contains("Expected explicit unit") == true)
    }
}
