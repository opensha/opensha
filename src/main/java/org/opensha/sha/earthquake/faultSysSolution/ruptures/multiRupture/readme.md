# Multi Rupture Package

This package provides experimental classes to generate multi ruptures. Multi ruptures are ruptures that combine crustal and subduction sections.

The main entry point is `RuptureMerger.main()`, which takes a crustal rupture set and a subduction rupture set and merges them together.

This approach was chosen to reduce the combinatorial explosion of creating multi ruptures from scratch.

The number of possible ruptures is controlled in multiple ways:
- `filterFile` in the `main` method of `RuptureMerger` is a text file that lists all the ruptures to keep. This is to support an external rupture thinning algorithm.
- a `TargetRuptureSelector` can be used to reduce the number of target ruptures for each jump from source section to target section. `AreaSpreadSelector` is an implementation of this.
- We only accept jumps from subduction to crustal ruptures if they are within `maxJumpDistance` of each other, and the crustal rupture is at least 1e8 meters^2 large.

There are currently two implementations of Coulomb filters for multi ruptures. They can be set up using `setUpBrucesFilters()` and `setUpKevinsFilters()`.

Performance enhancements:
- Possible section to section jumps are calculated using `SectionProximityIndex` and from there, section to rupture jumps are cached in a lookup in `RuptureProximityLookup`.
- Parallel streams are used where possible.

Note that these classes are still experimental and sometime use NZ-specific business logic, for example how to distinguish between crustal and subduction sections. 
