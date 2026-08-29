# tidemod — the worked example from EXTENDING.md

A third Elysium mod. Not the library, not `elysium-core`.

It registers one of everything the library offers — a stat, an element, a race,
a class, a rune, a faction rule, a dispatcher, a reward provider, an ore and a
codex — and it is **compiled by the same harness that builds the library**,
with `elysium-core` deliberately kept off the classpath.

That last part is the point. A guide that has never been run is a guess. If an
extension point stops being usable from outside, or the guide drifts from the
API, this stops compiling — which is how you find out before an add-on author
does.

It is not a shipping mod: there is no `@Mod` annotation, no manifest and no
resources, because none of that is what is under test. Copy the shapes, not the
file.
