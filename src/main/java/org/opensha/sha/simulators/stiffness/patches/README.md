# Surface-based subsection patches

`SubSectStiffnessCalculator` supports four generation strategies:

- `STIRLING_FILL_OVERLAP`: overlapping equal-sized rectangular Okada patches.
- `STIRLING_CENTERED`: equal-sized rectangles centered in evenly divided intervals.
- `TRIANGULAR`: split every supplied grid cell into two finite triangles,
  including on Stirling surfaces.
- `AUTO` (the default, also selected by null): use fill-overlap rectangles for each
  supplied Stirling surface and triangles for every other `EvenlyGriddedSurface`.
  Rectangular and triangular patches can interact in the same calculation.

Both explicit Stirling strategies require `StirlingGriddedSurface`; other types
throw an exception. No strategy substitutes a simpler surface for the section's
supplied geometry. All strategies require `EvenlyGriddedSurface`.

```java
SubSectStiffnessCalculator calc = new SubSectStiffnessCalculator(
		subSections, 2d, 3e4, 3e4, 0.5, PatchGenerationStrategy.AUTO, 1d);
```

`getPatchGenerationStrategy()` returns the configured mode, including AUTO,
rather than a per-section resolution. Strategy and placement are fixed at
construction; create a new calculator to change them. There is no separate
alignment setting on the calculator or shared builder interface. Placement is
configured only inside `StirlingSubSectPatchBuilder`.

Historical note: the removed GENERIC/projected rectangle algorithms are preserved
in commit `895586383a2a5fb82ed661d13f55a3c5148e05a9` (`new triangular pathway based
on cutde`), before their deletion. That snapshot includes the planar and ribbon
reference frames, patch layout/refinement helpers, tests, and diagnostic exporters.
Start with `GenericSubSectPatchBuilder.java` in this package to locate that work.

## Surface sampling and patch geometry

Triangle mode requests the surface at the specified spacing (2 km above).
Stirling rectangle placement requests a finer surface at `spacing/10` to preserve
legacy center interpolation and local strikes. AUTO first requests the coarse
surface to determine its type, then requests the finer surface only for Stirling
placement. Both requests preserve the calculator's aseismicity settings.
Cache naming and weighting selection do not construct surfaces or patches.

Triangles split each cell on the same diagonal. They preserve supplied boundary
nodes and tile the piecewise-planar mesh without overlap or spillover. There is no
projection, reference frame, adaptive remeshing, or refinement of skinny triangles.
A completely collapsed outer column is a valid triangular tip; its zero-area
half-triangles are omitted. Folded cells, collapsed interiors, and surfaces with
fewer than two rows or columns are rejected. Failure diagnostics identify the
section when patches are built through its section-aware builder.

Triangles always tile the supplied cells. Stirling rectangles retain the legacy
boundary spillover and, for `STIRLING_FILL_OVERLAP`, overlapping placement. Area weights use
actual patch areas; they do not compensate for rectangle overlap or spillover.

Sources have uniform unit slip. Both kernels evaluate receivers at their
centroids; there are no finite-receiver averages or far-field approximations.
The source shape selects the elastic kernel, independently of the receiver shape.

`StiffnessPatch` contains shared Cartesian geometry. `RectangularPatch` adds
rectangle length/width; `TriangularPatch` adds the triangle kernel basis.
`patches.PatchLocation` supplies geographic centers and corners. Polygon consumers
must support both three and four corners. Import the patch types directly from
`org.opensha.sha.simulators.stiffness.patches`; there are no nested compatibility
aliases on `StiffnessCalc` or `SubSectStiffnessCalculator`. The concrete patch and
location classes are final.

## Triangular elastic kernel

`TriangularDislocationCalc` is a Java port of the Nikkhoo-Walter half-space strain
equations from cutde 26.3.6, with its MIT attribution retained. It needs no Python
or native runtime. Coordinates use meters and positive Z upward; the free surface
is Z=0. Stress and CFF keep the existing compression-positive convention.
Near-vertical triangle edges expose cancellation in the reference equations.
`TriangularDislocationExtended` evaluates the same correction with two-double
arithmetic (about 31 decimal digits) within about 0.57 degrees of vertical.
Repeated expressions are generated once and reused, without rearranging the
algebra. For the most extreme edges, within about 0.0057 degrees, the original
60-digit `TriangularDislocationPrecision` path remains. Exact vertical edges have
an analytical zero correction and bypass both. Both precision paths keep endpoint
geometry consistent during subtraction; no vertices are snapped or displaced.

Extended precision is more expensive than ordinary doubles but much cheaper than
arbitrary precision. It is needed for accuracy: angle-only cutoff reductions
failed near shallow vertices and on skinny triangles, including CFF sign changes.
The precision regression tests compare 3,264 combinations of edge angle, size,
skew, depth, observation location, slip, and Poisson ratio to the 60-digit path.
Their stress tolerance is `1e-8 * maximum reference tensor component + 1e-9 MPa`
(the absolute floor is 0.001 Pa for weak signals). This is a tested numerical
criterion, not a universal bound for arbitrary degenerate geometries. Independent
arithmetic tests compare the internal `DoubleDouble` operations with 80-digit
`BigDecimal` results. A real Bennett Valley pair checks interaction signs and
receiver-area fractions against the original precision path.

Neither precision path regularizes physical stress singularities; the optional
CFF clipping policy is described below.
Singular/nonfinite values are rejected by weighted aggregation rather than
silently counted as negative interactions.

### Unequal-area statistics

The raw `StiffnessDistribution.get(type)` matrix always contains physical stress
from unit slip on each finite source patch. Source area is already integrated
by the elastic kernel; it must not be multiplied into those raw values again.
`receiverAggregate(SUM)` and the first aggregation-layer `SUM` add these physical
source contributions directly.

For individual-interaction statistics, let `A0 = spacing²` in square meters.
The aggregation pipeline uses `q = rawStress / (sourceArea/A0)` and weights
`(sourceArea/A0)*(receiverArea/A0)`. Thus means/medians compare stresses from a
common source area, and sign fractions measure represented area instead of
element counts. After summing sources, receiver statistics use receiver area
only. A section-pair SUM is therefore
`sum_receiver (receiverArea/A0) * sum_source rawStress`, preserving the legacy
equal-patch SUM scale. Changing the reference spacing changes that legacy scale;
refining a mesh while holding the reference area fixed does not.

`NUM_POSITIVE` and `NUM_NEGATIVE` become equivalent-area counts, and
`NORM_BY_COUNT`/interaction thresholds use the corresponding weighted interaction
measure. `COUNT` remains the actual diagnostic number of interactions. The
weighted interaction measure survives flattening, caching, and later aggregation
layers. Both explicit Stirling strategies retain its existing unweighted statistics. AUTO and
TRIANGULAR use this same weighted pipeline for every patch. A Stirling square has
unit reference-area weight, so an all-Stirling AUTO calculation retains the
legacy equal-area definitions (up to floating-point summation differences).
Weighted caches have an `Interaction Weight` CSV column;
weighted and unweighted cache files cannot be interchanged. Geometry hashing
remains disabled, so callers must clear/reversion caches when their inputs change.

`AggregationMethod` defines both weighted and unweighted statistics. Its
`calculate(values, weights)` overload accepts null weights for ordinary samples.
`ReceiverDistribution` supplies the stage-specific receiver-area correction and
diagnostic interaction count. Batch caches use the same definitions and reuse
intermediate statistics, including the median. Weighted sums and means use Apache
Commons Math; `DataUtils.weightedMedian` implements the cumulative-area median,
averaging adjacent values when the weight divides in half. Weighted median
calculations preserve the original value/weight pairing without changing either
array.

The reference triangle equations use fixed continuation indentation rather than
alignment by nesting depth. Formatter guards preserve that layout in Eclipse.
The precision-algebra generator emits tabs and checks equations independently of
whitespace, so indentation changes alone do not require regeneration.

### Self-stiffness cap

AUTO and TRIANGULAR support the same positive self-stiffness cap multiplier as
the Stirling strategies; zero leaves CFF uncapped. For source `s` and receiver `r`, either weighted
mode clips each physical CFF contribution to
`+/- multiplier * abs(selfCFF[r]) * sourceArea[s] / receiverArea[r]`.
Self-CFF is evaluated at the patch's own centroid with uniform unit slip and
cached per receiver. Nonfinite self-CFF is rejected rather than silently disabling
the cap. Sigma and tau are unchanged; clipping applies only to CFF, before area
normalization or aggregation.

The area ratio compares interaction and self-CFF per unit source area, using the
same normalization as the interaction statistics. Equal-area patches recover the
original limit. Subdividing a source divides its cap allowance among its children,
instead of giving each child the original full allowance. Both explicit Stirling strategies keep
the existing receiver-only cap.

This does not make clipping invariant under arbitrary remeshing: receiver
self-stiffness depends on shape and dimensions, and clipping contributions before
summing can change their cancellation. Genuine edge singularities in the kernel
are still present; the cap is a CFF clipping policy, not a finite-receiver average.
Triangle cache version 3 includes this capping convention.

## Configuration and caches

Plausibility JSON records only `patchGenerationStrategy`; new files omit the old
`patchAlignment` field. A missing or null strategy defaults to AUTO unless a
legacy alignment-only setting is present: `CENTER` selects `STIRLING_CENTERED`,
and `FILL_OVERLAP` selects `STIRLING_FILL_OVERLAP`. An explicit current strategy
wins over a legacy alignment field, independent of JSON field order. The
short-lived `STIRLING` strategy name and removed projected `GENERIC` strategy
are not supported.

Cache filenames include the configured mode and generation version: AUTO v14,
both explicit Stirling strategies v1, and TRIANGULAR v3. There is no separate
alignment component. AUTO always writes weighted statistics. Bump AUTO's version
whenever changes to either builder, kernel, cap, or aggregation affect its
results; bump the corresponding explicit strategy version as well. Cache keys
do not fingerprint patch geometry, so lookup does not require patch construction.
Input changes still require clearing or reversioning caches. Weighted and
unweighted CSV files cannot be interchanged, and caches from different generation
strategies cannot be copied into each other.

## Validation and regeneration

```sh
./gradlew test --tests org.opensha.sha.simulators.stiffness.StiffnessTestSuite
```

The suite covers legacy Stirling placement; per-surface and forced strategy
selection; mixed kernels, area-weighted aggregation and caps in both directions;
weighted cache round trips and configuration compatibility; and invalid 3-D cells.
Synthetic fixtures cover twists, narrow necks and tongues, uneven traces, listric
geometry, triangular tips, and near-horizontal and near-vertical planes.
Aubrey, Bare Mountain, and Beaverhead fixtures exercise real surfaces that
challenged the former projected layouts.

Kernel tests include frozen cutde half-space strain cases, comparisons of two
coplanar triangles against an Okada rectangle, and horizontal, vertical, skinny,
near-edge, and near-vertical precision cases. Aggregation tests check refinement
with a fixed reference area, multiple sources, and cache preservation.

Tools under `src/test/resources/org/opensha/sha/simulators/stiffness/tools`
regenerate the cutde reference CSV and precision algebra. Only oracle regeneration
needs Python packages (`cutde==26.3.6` and NumPy).
`regenerate_triangle_precision.py --check` uses the Python standard library to
verify that the decimal and two-double expressions match the ordinary Java kernel.

Either along-strike node order is supported. Physical normals point upward for
dipping surfaces; vertical patches retain the grid's directional convention.
Validation compares the original cell tangents before choosing upward normals,
so reversed winding is accepted without allowing a folded cell.

All patch-construction classes and package-level tests live in
`org.opensha.sha.simulators.stiffness.patches`. The builder interface separates
patch generation from the elastic kernels and aggregation in the parent package.
`GriddedSurfaceGeometry` contains only Cartesian conversion, node access, cell
areas, and validation. Synthetic fixture helpers are shared by geometry and
builder tests; no projected layout or projected-coverage tooling remains.
