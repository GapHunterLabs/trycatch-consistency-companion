package dev.gaphunter.trycatchconsistencycompanion.model

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethodCallExpression

/**
 * One resolved call to a given method, plus the set of checked
 * exception simple names it is caught for (directly or via an
 * enclosing `try` block up the PSI tree) at that exact call site --
 * used to compare this call's exception-handling shape against every
 * other call to the same method in the file (see [TryCatchConsistencyFinder]).
 *
 * [caughtExceptionSimpleNames] is empty when the call is not inside
 * any `try` block, or inside one whose `catch` clauses don't cover
 * checked exceptions relevant to comparison (e.g. only
 * `catch (RuntimeException e)` while comparing a checked exception --
 * still recorded as empty, a real absence of handling).
 *
 * [anchor] is the LEAF PSI element of the call (see
 * [TryCatchConsistencyFinder]'s `leafOf`) -- `collectSlowLineMarkers`
 * is handed leaf elements by the platform, so matching against the
 * full [call] expression would never hit; same pattern used
 * catalog-wide (e.g. `ClientBuildHit.callElement`).
 */
data class CallSite(
    val call: PsiMethodCallExpression,
    val anchor: PsiElement,
    val caughtExceptionSimpleNames: Set<String>,
)
