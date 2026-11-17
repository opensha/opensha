package org.opensha.sha.simulators.stiffness.patches;

import java.util.List;

import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchLocation;

/**
 * Constructs fixed-size source/receiver patches from a section's supplied surface.
 * Implementations share the calculator's public patch types and alignment policy;
 * elastic calculations and aggregation remain in the parent stiffness package.
 */
public interface SubSectPatchBuilder {
	List<PatchLocation> buildPatches(FaultSection section, EvenlyGriddedSurface surface, PatchAlignment alignment);
}
