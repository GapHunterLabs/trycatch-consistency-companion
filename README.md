# Try/Catch Consistency Companion

IntelliJ-family plugin. Learns the dominant checked-exception-handling
convention your own file already established for a method, and flags
the call sites that break with it.

## Why it exists

Every other plugin in this catalog (and every linter/inspection found
on Marketplace) ships a fixed rule it already knows: "this specific
API call is dangerous", "this specific pattern is wrong". This plugin
is a different kind of tool -- it doesn't know anything about your
code in advance. It learns a convention FROM your own file (how the
majority of calls to a given method handle its checked exceptions)
and flags whichever call sites contradict what it just observed.

This is an original tool (no third-party paid competitor with real
complaints motivated it) -- a deliberate bet on a genuinely new
mechanism for this catalog: per-file statistical consensus over real
PSI, not a pre-written pattern.

## Why built this way

- **A real per-file consensus, not a hardcoded rule.** For every
  method called 8+ times in the same file, `TryCatchConsistencyFinder`
  resolves each call site (`PsiMethodCallExpression.resolveMethod()`),
  walks up the PSI tree from each call to find which of the method's
  declared checked exceptions (from its real `throws` clause) are
  caught there, and groups call sites by that exact signature. If more
  than half agree, every call site that disagrees is flagged.
- **Bounded to the current file.** Comparing against every call site
  in the whole project would need a project-wide `ReferencesSearch`
  running inside the line-marker hot path (re-run on every background
  highlight pass) with no caching layer -- out of scope for an honest
  v0.1 with no backend and no external analysis engine. A file with
  several calls to the same service/utility method is still a common,
  fast, honest signal on its own.
- **Checked exceptions only.** Unchecked (`RuntimeException`-derived)
  handling is never required by the compiler, so disagreement there
  carries much weaker signal -- deliberately out of scope for v0.1.
- **A hard floor against noise.** Fewer than 8 calls to the same
  method in a file never triggers anything -- not enough data for a
  real convention. A near-even split (no call-site group with a real
  majority) also never triggers anything.
- **Multi-catch (`catch (A | B e)`) is handled correctly** -- each
  disjunct is resolved individually, not silently missed.
- Off-EDT-safe (`collectSlowLineMarkers`), no network calls, no
  telemetry.

## Usage

Open a Java file where the same method (declaring at least one checked
exception) is called 8 or more times. If most of those calls agree on
how they handle it (same exception caught, or all deliberately left
uncaught) and one disagrees, a gutter warning appears on the outlier
with a tooltip naming the majority pattern and how many call sites
support it.

## Enterprise / Team Licensing

Need enterprise features, custom rules, or team licensing? Contact us at
**gaphunterlabs@gmail.com**.

## Development

```
./gradlew test           # unit tests
./gradlew buildPlugin    # generates build/distributions/*.zip
./gradlew verifyPlugin   # checks compatibility against real IDEs
```

## License

Apache-2.0. See `LICENSE`.
