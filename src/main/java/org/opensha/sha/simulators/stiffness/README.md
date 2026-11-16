# Surface-based subsection patches

`SubSectStiffnessCalculator` defaults to `PatchGenerationStrategy.AUTO`:

```java
SubSectStiffnessCalculator calc = new SubSectStiffnessCalculator(
        subSections, 2d, 3e4, 3e4, 0.5,
        PatchAlignment.FILL_OVERLAP, 1d);
```

For each section, the calculator requests its surface once with the requested
spacing and aseismicity setting. AUTO selects `STIRLING` for a
`StirlingGriddedSurface`, otherwise `GENERIC`. Selection and construction use the
same returned instance. Mixed models may use different builders per section.
Surfaces must implement `EvenlyGriddedSurface`; other surface types fail with a
clear diagnostic.

The strategy-taking constructor still accepts explicit `STIRLING` or `GENERIC`
overrides. Passing `null` is equivalent to AUTO. `getPatchGenerationStrategy()`
returns the configured mode, including AUTO, rather than a model-wide resolution.

`STIRLING` requires `StirlingGriddedSurface` for both alignments; it does not
substitute a simpler surface. `GENERIC` can also be explicitly selected for a
Stirling surface, and uses only the `EvenlyGriddedSurface` interface, including
interior rows. Nominal spacings and average dip do not determine its local
geometry. Elastic calculations and aggregation are shared. The generic geometry
still needs real-model plausibility and near-field validation.

## Classes

- `SubSectPatchBuilder`: package-private common builder contract.
- `StirlingSubSectPatchBuilder`: original ribbon-based placement and mechanisms.
- `GriddedSurfaceGeometry`: Cartesian snapshot, four-node interpolation, local
  tangent mechanisms, node depth extrema, cumulative column distances, cell areas.
- `SurfaceReferenceFrame`: orthogonal coordinates in an area-weighted best-fit
  plane, with spatially indexed inverse lookup into original bilinear cells.
- `ProjectedGridPatchLayout`: regular reference rows, evenly fitted side limits,
  logical grid neighbors, and distributed depth-constraint corrections.
- `GenericSubSectPatchBuilder`: physical rectangles and exact depth-bound checks.
- `ColumnRunPatchRefitter`: one guarded pass over coherent physical down-dip columns.
- `NarrowStripPatchInfill`: centered additions only for persistent narrow strips in GENERIC FILL_OVERLAP.
- `ProjectedPatchFootprint`: shared projection of actual rectangle corners, edge-inclusive
  containment, and fresh mutable area copies for coverage comparisons.
- `PatchIntervalPlacement`: count and interval-alignment conventions.

All rectangles retain side length `gridSpacing` and unit slip. Section rake is
interpreted in each patch's local strike/dip frame. Generic plotting corners come
from the same Cartesian corners used by the stiffness kernel.

At a shared grid edge, a tangent is not unique. Centers within 1 cm of an
interior edge use the mean of the adjacent unit normals, including all four cells
at an interior vertex. Positions retain their bilinear interpolation. Outside
that coordinate tolerance, mechanisms retain the containing cell's tangent.

## Implementation conventions

The builder pipeline is intentionally sequential: initial rows → bounded physical
patches → optional column refits → narrow-strip repair → diagnostic centers.
Refinements use `GenericSubSectPatchBuilder.PatchFactory`, which returns null only
for an infeasible optional placement. Invalid geometry and unexpected exceptions
still propagate. The same physical-bounds code constructs every accepted patch.

`ColumnRunPatchRefitter` separates station association/proposals (`proposeColumns`),
physical width and placement (`fitColumn`), and acceptance (`improvesCoverage`).
During its pass, original list indices stay stable: removed footprints become
empty areas, and accepted replacements are appended for subsequent coverage checks.
Source lists and diagnostic identities are updated together after all bands.

Geometry and reference coordinates are kilometers; the stiffness kernel uses
meters. Geometry mechanisms use degrees; kernel patches store radians. Area
thresholds are km² (10 m² required gain, 1 m² tolerated interior loss). These
precision tolerances are numerical conventions, not user-facing tuning knobs.
The frame returns an outline copy, and footprints return area copies, because
Java2D Boolean area operations mutate their receiver. Coverage diagnostics in the
tests retain their independent projection implementation.

## Reference frame

The fitted plane uses exact area-weighted first and second moments of the two
triangles in each input cell. Node density therefore does not determine the fit.
Its horizontal strike and perpendicular down-dip axes preserve planar shapes,
distances, and angles, even with skewed grid columns or irregular boundaries.
An arch-shaped boundary remains an arch rather than becoming a bowtie.

Curved surfaces are projected, not intrinsically unwrapped. Physical positions
and local mechanisms still use the original surface through inverse bilinear
mapping. Diagnostics report minimum projected/physical edge and area ratios and
RMS distance to the plane. Folded cells and self-intersecting projected boundaries
are rejected. Strong curvature may require a future intrinsic unwrapping.

## Layout and boundary policy

`CENTER` initializes a rectangular grid using floor counts and equal subdivisions,
then retains centers inside the projected domain. It accepts coverage gaps.

`FILL_OVERLAP` initializes shared down-dip row positions using ceiling counts and
half-patch offsets. Each row intersects the actual projected boundary, allowing
multiple disconnected intervals. Each interval gets a ceiling count; centers
are equally spaced between half-patch offsets from its ends. An interval shorter
than one patch initially gets one center at its midpoint, subject to the global
extent constraints below. Small side slivers do not create
extra full-size patches merely because a square intersects them.

This is deliberately neutral at sloping side boundaries: the outer edge midpoint
falls on the boundary at the row center line, balancing protruding and missing
triangular portions for a straight side. Two identical planar skewed sections
placed end-to-end have touching patch edges in the regression fixture. Arbitrary
neighboring sections are not guaranteed to share the same grid phase or frame.

Initial row targets remain fixed unless depth or center-containment constraints
require movement. Necessary endpoint corrections are spread linearly over logical
row/column runs, followed by a constrained-center projection if required.
Disconnected row intervals retain separate identities, even when their gap is
smaller than one patch. There is no crowding-deletion pass.

A single optional column-refitting pass then considers broad coherent bands:

1. Associate existing centers with stations along the physical middle trace,
   using inverse grid coordinates rather than the initial layout's column IDs.
   Preserve multiple neighboring columns that occupy the same station bin through
   at least two initial rows. Use the median existing grid-column coordinate for
   each resulting column.
2. Measure each column's physical down-dip width through every supplied row,
   excluding motion parallel to the local strike. This avoids counting the
   along-strike component of skewed grid edges as extra fault width.
3. Choose a ceiling count and distribute centers evenly between half-patch offsets
   along that width. Fit actual physical rectangles using the usual bounds.
4. Consider adjacent columns with the same proposed count together. Require at
   least two columns and three patches down dip; short one/two-row ends keep the
   initial fits. Replace the entire band only if projected covered surface area
   increases and covered protected-interior area does not decrease (square-meter
   numerical tolerances). The protected interior excludes a boundary band of
   half the patch diagonal, matching the diagnostic metric.

This is one deterministic pass, not alternating row/column optimization. A band
may retain small gaps or move them: the guard compares total covered area, not
pointwise coverage, and does not guarantee contact between all physical tangent
rectangles. Candidates failing the guard retain the initial layout. Accepted
columns have distinct diagnostic identities separated from the initial grid;
their row numbers describe their own down-dip ordering, not common horizontal rows.
The existing narrow-strip pass runs afterward. CENTER does not refit columns.

For the named 2 km fixtures, the twisted surface becomes eight coherent columns
of five patches. The flat right-hand neck lobe has three down-dip rows at reference
y=2,4,6 km. The triangle, skewed and uneven-trace layouts retain their initial fits.

Ratios within an absolute 1e-5 of an integer count are snapped to prevent
centimeter-scale Cartesian conversion precision from adding/removing a patch.

## Global along-strike and depth limits

Actual physical patch corners, projected into the reference frame, must lie
between the surface's global minimum and maximum along-strike coordinates when
their projected width fits that range. These are global bounds, not per-row
containment: balanced protrusion across sloping sides remains allowed inside
the overall extent. For example, the triangle's short bottom row aligns to its
straight outer side rather than protruding past the section end.

The initial center-domain constraint spreads the along-strike adjustment across
row runs. A subsequent physical check recomputes the actual projected width as
local orientation changes, checking along-strike and depth constraints jointly.
If a whole surface is narrower than a patch's projected width, the along-strike
bounds are relaxed without shrinking the patch. Nonnegative depth remains
required, including when both ranges are undersized.


Generic physical rectangles stay between the shallowest and deepest original
supplied grid-node depths whenever that range can contain their vertical extent.
All four Cartesian corners are checked, with a one-micrometer numerical tolerance.
These limits apply to both alignments. They are global section depth bounds;
local lower-trace irregularities can still have protruding corners.

A projected center-domain constraint spreads depth adjustments over planar runs.
For curved geometry (node offsets from the fitted plane exceeding 1 cm), the
plane-depth strip is skipped because it can exclude physically valid centers.
Actual physical corner checks handle curved depth clearance;
if necessary, a feasible center is sought along the projected column.

If a patch's vertical extent exceeds the original section depth range, those
original depth limits are relaxed. The center remains on the supplied surface
when possible. If the rectangle would cross depth zero, it is translated vertically
downward just enough to keep every corner underground (with one-micrometer
padding). That exceptional center can lie off the original surface, and the saved
center/corners consistently describe the translated physical patch. Patch size is
never reduced. Otherwise construction fails diagnostically if a feasible center
cannot be found. Depth constraints can increase boundary gaps near a tip.

## Validation and diagnostics

```sh
./gradlew test --tests org.opensha.sha.simulators.stiffness.StiffnessTestSuite
```

The suite covers interface-only and approximate surfaces, triangles, arch cutouts,
planar skew, neighboring skewed sections, listric geometry, frame invariance to
sampling density, inverse interpolation, global along-strike and depth limits, invalid projections,
configuration/cache behavior, automatic per-surface dispatch, and far-field Coulomb spacing refinement.

`GenericPatchDiagnostics.main(outputDirectory)` exports CSV files for reference
nodes, physical centers/corners, logical grid identities, represented areas, and
projected footprint coverage. Run this optional test-source main from an IDE with
one output-directory argument. Coverage samples 32-by-32 points per input cell;
represented areas use 16-by-16 nearest-center assignments. Both use cell-area
weights. The diagnostic's protected interior is farther than half a patch diagonal
from the projected boundary. These are sampling diagnostics, not exact guarantees.

Independent local planes can intersect or leave gaps on a curved surface. Exact
coverage, local edge-midpoint containment, equal represented area, and global
self-intersection detection in 3D are not guaranteed. Collapsed boundary tips are
supported; collapsed interiors and single-row/column surfaces are rejected.
Real-model plausibility and near-field spacing comparisons remain outstanding.

Plausibility JSON records the configured strategy. Missing or null fields default
to AUTO; explicit STIRLING and GENERIC fields retain their meaning. Cache filenames
include the configured mode and a generation version (currently generic v10).
AUTO and GENERIC keys fingerprint ordered physical patches and mechanisms, so
mixed-model and surface-geometry changes cannot reuse the same geometry key. Older cache filenames are
not selected automatically. Explicit loading of an arbitrary cache file remains
the caller's responsibility. Alignment changes clear self-stiffness and aggregate
caches.


## Additional pathological fixtures

`PathologicalSurfaceFixtures` and `PathologicalSubSectPatchBuilderTest` add twisted
geometry, uneven upper/lower depths, narrow necks/tongues, patch-size threshold
cases, horizontal/vertical limits, deterministic perturbations, and equivalent
resampling. `GenericPatchDiagnostics.main(outputDirectory, "pathological")` exports
the named fixtures and 2/1/0.5 km remote-Coulomb comparisons.

These checks revealed and fixed a near-horizontal reference-strike tolerance
mismatch and loss of very small dip angles. Dip extraction now uses atan2.
The current FILL_OVERLAP supplement is deliberately limited to narrow strips:

1. Sample regular reference columns and measure each connected interval's physical
   arc length. Intervals at least one patch width long do not qualify.
2. Require a narrow interval to overlap one in an immediately adjacent sampled
   column. This geometric support is evaluated before checking existing coverage.
3. Propose one physical midpoint per qualifying interval only when it is not
   already represented by an existing projected patch footprint.
4. Apply the existing physical bounds and add only candidates that still cover
   their missing midpoint. Skip optional candidates with no feasible placement.

There are no general top/bottom boundary probes and no additions in wider
intervals. Existing patches are not moved by this pass. Additions are indexed
immediately; there is no supplemental-row fitting, smoothing, or crowding deletion.
The triangle, twisted and uneven-trace examples receive no narrow-strip additions. Only the
narrow necks and tongue get supplemental patches in the named fixtures. An isolated
single-column sliver and an already represented narrow run receive none.
CENTER gets no additions. The independent actual-depth correction for curved
surface centers remains in place for both alignments.

Coverage is still approximate and gaps/crowding can remain. Full-size infill
sources increase total moment as well as overlap, especially in narrow strips;
improved coverage alone does not establish stress accuracy. The diagnostic
protected-interior metric can also understate an omitted thin feature, so the
new tests explicitly sample coverage along the tongue and necks.

Use `GenericPatchDiagnostics.main(outputDirectory, "all")` to export the original
and pathological fixtures together for both alignments. The metrics CSV includes
infill counts, and the patches CSV marks additions. Unique negative row IDs
identify ungrouped infill patches rather than fitted row fragments.
