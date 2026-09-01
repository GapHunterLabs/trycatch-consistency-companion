package dev.gaphunter.trycatchconsistencycompanion.detect

import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiCatchSection
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiTryStatement
import com.intellij.psi.util.PsiTreeUtil
import dev.gaphunter.trycatchconsistencycompanion.model.CallSite

/**
 * Learns the dominant exception-handling shape for each distinct
 * method called more than once in a file, and reports the call sites
 * that break with it -- a real "convention inferred from this
 * project's own code", not a rule the plugin knew in advance.
 *
 * Concretely: for a method resolved to the same [PsiMethod] declaration,
 * group every call site in the file by which checked-exception simple
 * name(s) it is caught for (an empty set means "not caught at all"
 * here). If [MIN_CALL_SITES_FOR_SIGNAL] or more call sites agree on
 * the exact same set, and at least one call site to that same method
 * disagrees, every disagreeing call site is an outlier.
 *
 * **v0.1 scope, stated honestly:**
 * - Bounded to the CURRENT FILE only -- comparing against every call
 *   site in the whole project would need a `ReferencesSearch` pass
 *   from inside the line-marker hot path (`collectSlowLineMarkers`,
 *   re-run on every background highlight pass), which is unbounded
 *   cost with no caching layer in this v0.1. A file with several
 *   call sites to the same helper (a service class, a utility) is
 *   still common and gives a real, fast, honest signal.
 * - "Caught" means a `catch` clause up the PSI ancestor chain from the
 *   call whose declared type is exactly one of the checked exceptions
 *   the resolved method's `throws` clause declares (or a supertype of
 *   one) -- matched by simple name text, same acknowledged limitation
 *   as every other text/PSI-based plugin in this catalog (never full
 *   type hierarchy resolution against an unrelated class sharing a
 *   name). Only checked exceptions are considered: unchecked
 *   (RuntimeException-derived) handling is deliberately out of scope
 *   for v0.1 -- catching those is never required by the compiler, so
 *   "inconsistent" carries much weaker signal there.
 * - A method declaring zero checked exceptions in its `throws` clause
 *   is never analyzed -- there is nothing to be consistent or
 *   inconsistent ABOUT.
 */
object TryCatchConsistencyFinder {

    const val MIN_CALL_SITES_FOR_SIGNAL = 8

    data class Outlier(val callSite: CallSite, val majorityCaught: Set<String>, val majorityCount: Int, val totalCount: Int)

    fun findOutliers(file: PsiFile): List<Outlier> {
        val callsByMethod = mutableMapOf<PsiMethod, MutableList<CallSite>>()

        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                val resolvedMethod = expression.resolveMethod() ?: return
                if (checkedExceptionSimpleNames(resolvedMethod).isEmpty()) return
                val caught = caughtExceptionSimpleNamesFor(expression, checkedExceptionSimpleNames(resolvedMethod))
                callsByMethod.getOrPut(resolvedMethod) { mutableListOf() } += CallSite(expression, leafOf(expression), caught)
            }
        })

        val outliers = mutableListOf<Outlier>()
        for ((_, callSites) in callsByMethod) {
            if (callSites.size < MIN_CALL_SITES_FOR_SIGNAL) continue

            val bySignature = callSites.groupBy { it.caughtExceptionSimpleNames }
            val majorityEntry = bySignature.maxByOrNull { it.value.size } ?: continue
            val majoritySignature = majorityEntry.key
            val majorityCount = majorityEntry.value.size

            // The majority itself must be a real majority (more than half) -- a 4/4/4/4
            // three-way split among equally-sized groups has no dominant convention to break.
            if (majorityCount * 2 <= callSites.size) continue

            for ((signature, sites) in bySignature) {
                if (signature == majoritySignature) continue
                sites.forEach { outliers += Outlier(it, majoritySignature, majorityCount, callSites.size) }
            }
        }
        return outliers
    }

    private fun checkedExceptionSimpleNames(method: PsiMethod): Set<String> {
        val uncheckedRoots = setOf("RuntimeException", "Error")
        return method.throwsList.referencedTypes
            .mapNotNull { it.resolve() }
            .filterNot { thrown -> uncheckedRoots.any { root -> isSameOrSubclassByName(thrown, root) } }
            .mapNotNull { it.name }
            .toSet()
    }

    private fun isSameOrSubclassByName(psiClass: com.intellij.psi.PsiClass, ancestorSimpleName: String): Boolean {
        var current: com.intellij.psi.PsiClass? = psiClass
        var guard = 0
        while (current != null && guard < 50) {
            if (current.name == ancestorSimpleName) return true
            current = current.superClass
            guard++
        }
        return false
    }

    /**
     * Walks up from [call] through enclosing `try` statements, collecting
     * `catch` clause type simple names that intersect [relevantSimpleNames]
     * -- stops climbing once outside the containing method (a call site's
     * exception handling never comes from an enclosing method's own try).
     */
    private fun caughtExceptionSimpleNamesFor(call: PsiMethodCallExpression, relevantSimpleNames: Set<String>): Set<String> {
        val containingMethod = PsiTreeUtil.getParentOfType(call, PsiMethod::class.java)
        val caught = mutableSetOf<String>()
        var current: com.intellij.psi.PsiElement? = call
        while (current != null && current != containingMethod) {
            val tryStatement = PsiTreeUtil.getParentOfType(current, PsiTryStatement::class.java, true)
                ?: break
            // Only counts if `call` is inside the try BLOCK itself (not inside a catch/finally of an outer try).
            if (PsiTreeUtil.isAncestor(tryStatement.tryBlock, call, false) || tryStatement.tryBlock == null) {
                for (catchSection: PsiCatchSection in tryStatement.catchSections) {
                    // A multi-catch (`catch (IOException | SQLException e)`) is a single
                    // PsiCatchSection whose catchType is a PsiDisjunctionType, NOT a plain
                    // PsiClassType -- disjunctions() flattens both shapes to the same list
                    // of individual class types so multi-catch is never silently missed.
                    val caughtType = catchSection.catchType ?: continue
                    val disjuncts = when (caughtType) {
                        is com.intellij.psi.PsiDisjunctionType -> caughtType.disjunctions
                        else -> listOf(caughtType)
                    }
                    for (disjunct in disjuncts) {
                        (disjunct as? com.intellij.psi.PsiClassType)?.resolve()?.name?.let {
                            if (it in relevantSimpleNames) caught += it
                        }
                    }
                }
            }
            current = tryStatement
        }
        return caught
    }

    private fun leafOf(element: com.intellij.psi.PsiElement): com.intellij.psi.PsiElement {
        var current = element
        while (current.firstChild != null) current = current.firstChild
        return current
    }
}
