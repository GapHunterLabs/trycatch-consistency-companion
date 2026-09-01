package dev.gaphunter.trycatchconsistencycompanion.detect

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Uses a custom checked exception class DECLARED IN THE TEST SOURCE
 * ITSELF (`ParseException`/`OtherException`) rather than a real JDK
 * class like `java.text.ParseException` -- `BasePlatformTestCase`'s
 * lightweight fixture does not index the full JDK, so `.resolve()` on
 * a real `java.*` checked-exception type returns null there even
 * though its `canonicalText` looks correct (confirmed by direct
 * diagnostic tracing during development). A self-contained checked
 * exception avoids that gap entirely and is also more representative
 * of the common real case (a project's own business exception).
 */
class TryCatchConsistencyFinderTest : BasePlatformTestCase() {

    private val supportClasses = """

        class Parser {
            Object parse(String s) throws ParseException {
                return null;
            }
        }

        class ParseException extends Exception { }
        class OtherException extends Exception { }
    """.trimIndent()

    /** 8 consistent call sites (all caught) + 1 outlier (not caught) -- the base fixture every test tweaks. */
    private fun buildSource(outlierCatchesToo: Boolean): String {
        val outlierBody = if (outlierCatchesToo) {
            """
            try {
                parser.parse("h");
            } catch (ParseException e) {
            }
            """.trimIndent()
        } else {
            "parser.parse(\"i\"); // not caught -- the outlier"
        }
        return """
        class Client {
            Parser parser = new Parser();

            void callers() {
                try { parser.parse("a"); } catch (ParseException e) { }
                try { parser.parse("b"); } catch (ParseException e) { }
                try { parser.parse("c"); } catch (ParseException e) { }
                try { parser.parse("d"); } catch (ParseException e) { }
                try { parser.parse("e"); } catch (ParseException e) { }
                try { parser.parse("f"); } catch (ParseException e) { }
                try { parser.parse("g"); } catch (ParseException e) { }
                $outlierBody
            }
        }
        $supportClasses
        """.trimIndent()
    }

    fun `test one uncaught call site among 7 consistently caught is flagged as outlier`() {
        val file = myFixture.configureByText("Client.java", buildSource(outlierCatchesToo = false))
        val outliers = TryCatchConsistencyFinder.findOutliers(file)
        assertEquals(1, outliers.size)
        assertEquals(7, outliers[0].majorityCount)
        assertEquals(8, outliers[0].totalCount)
        assertTrue(outliers[0].callSite.caughtExceptionSimpleNames.isEmpty())
    }

    fun `test all call sites consistently caught produces no outliers`() {
        val file = myFixture.configureByText("Client.java", buildSource(outlierCatchesToo = true))
        assertTrue(TryCatchConsistencyFinder.findOutliers(file).isEmpty())
    }

    fun `test fewer than the minimum call sites never triggers a signal even with real disagreement`() {
        val file = myFixture.configureByText(
            "Client.java",
            """
            class Client {
                Parser parser = new Parser();

                void callers() {
                    try { parser.parse("a"); } catch (ParseException e) { }
                    try { parser.parse("b"); } catch (ParseException e) { }
                    parser.parse("c"); // would be an outlier, but only 3 total call sites
                }
            }
            $supportClasses
            """.trimIndent(),
        )
        assertTrue(TryCatchConsistencyFinder.findOutliers(file).isEmpty())
    }

    fun `test method with no checked exceptions is never analyzed`() {
        val file = myFixture.configureByText(
            "Client.java",
            """
            class Client {
                Parser parser = new Parser();

                void callers() {
                    parser.parse("a");
                    parser.parse("b");
                    parser.parse("c");
                    parser.parse("d");
                    parser.parse("e");
                    parser.parse("f");
                    parser.parse("g");
                    parser.parse("h");
                }
            }

            class Parser {
                Object parse(String s) { // no throws clause -- nothing checked to be consistent about
                    return null;
                }
            }
            """.trimIndent(),
        )
        assertTrue(TryCatchConsistencyFinder.findOutliers(file).isEmpty())
    }

    fun `test even three-way split with no real majority produces no outliers`() {
        // 4 caught-A, 4 not-caught -- 4*2 == 8, not a strict majority (the tie-break: no dominant convention).
        val file = myFixture.configureByText(
            "Client.java",
            """
            class Client {
                Parser parser = new Parser();

                void callers() {
                    try { parser.parse("a"); } catch (ParseException e) { }
                    try { parser.parse("b"); } catch (ParseException e) { }
                    try { parser.parse("c"); } catch (ParseException e) { }
                    try { parser.parse("d"); } catch (ParseException e) { }
                    parser.parse("e");
                    parser.parse("f");
                    parser.parse("g");
                    parser.parse("h");
                }
            }
            $supportClasses
            """.trimIndent(),
        )
        assertTrue(TryCatchConsistencyFinder.findOutliers(file).isEmpty())
    }

    fun `test multi-catch is correctly recognized as catching the relevant exception`() {
        val file = myFixture.configureByText(
            "Client.java",
            """
            class Client {
                Parser parser = new Parser();

                void callers() {
                    try { parser.parse("a"); } catch (ParseException e) { }
                    try { parser.parse("b"); } catch (ParseException e) { }
                    try { parser.parse("c"); } catch (ParseException e) { }
                    try { parser.parse("d"); } catch (ParseException e) { }
                    try { parser.parse("e"); } catch (ParseException e) { }
                    try { parser.parse("f"); } catch (ParseException e) { }
                    try { parser.parse("g"); } catch (ParseException | OtherException e) { }
                    parser.parse("h"); // the real outlier -- not caught at all
                }
            }
            $supportClasses
            """.trimIndent(),
        )
        val outliers = TryCatchConsistencyFinder.findOutliers(file)
        assertEquals(1, outliers.size)
        assertTrue(outliers[0].callSite.caughtExceptionSimpleNames.isEmpty())
    }
}
