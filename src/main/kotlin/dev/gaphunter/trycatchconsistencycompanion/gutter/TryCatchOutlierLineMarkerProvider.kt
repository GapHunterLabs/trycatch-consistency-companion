package dev.gaphunter.trycatchconsistencycompanion.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import dev.gaphunter.trycatchconsistencycompanion.detect.TryCatchConsistencyFinder
import dev.gaphunter.trycatchconsistencycompanion.review.ReviewPrompt

/**
 * Marks every call site that breaks with the dominant exception-
 * handling convention THIS FILE already established for the same
 * method (see [TryCatchConsistencyFinder] for the real mechanism --
 * a convention learned from the file's own code, not a rule this
 * plugin knew in advance).
 */
class TryCatchOutlierLineMarkerProvider : LineMarkerProviderDescriptor(), DumbAware {

    override fun getName(): String = "Inconsistent exception handling (breaks this file's own convention)"

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: MutableList<out PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        val file = elements.firstOrNull()?.containingFile ?: return
        if (file.language.id != "JAVA") return

        val outliers = TryCatchConsistencyFinder.findOutliers(file)
        if (outliers.isEmpty()) return

        val outliersByCall = outliers.associateBy { it.callSite.anchor }
        for (element in elements) {
            val outlier = outliersByCall[element] ?: continue
            result.add(buildMarker(element, outlier))

            val path = file.virtualFile?.path ?: continue
            val lineNumber = file.viewProvider.document?.getLineNumber(element.textRange.startOffset) ?: -1
            ReviewPrompt.recordHit(file.project, "$path:$lineNumber")
        }
    }

    private fun buildMarker(element: PsiElement, outlier: TryCatchConsistencyFinder.Outlier): LineMarkerInfo<PsiElement> {
        val majority = if (outlier.majorityCaught.isEmpty()) "not caught at all" else "caught for ${outlier.majorityCaught.joinToString(", ")}"
        val here = if (outlier.callSite.caughtExceptionSimpleNames.isEmpty()) "not caught here" else "caught here for ${outlier.callSite.caughtExceptionSimpleNames.joinToString(", ")}"
        val tooltip = "${outlier.majorityCount} of ${outlier.totalCount} calls to this method in this file are $majority -- " +
            "this call site is $here, breaking with the file's own established pattern."
        return LineMarkerInfo(
            element,
            element.textRange,
            OutlierIcons.INCONSISTENT,
            { _: PsiElement -> tooltip },
            null,
            GutterIconRenderer.Alignment.RIGHT,
            { tooltip },
        )
    }
}
