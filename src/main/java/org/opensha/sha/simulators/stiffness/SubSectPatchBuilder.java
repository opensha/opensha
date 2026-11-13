package org.opensha.sha.simulators.stiffness;

import java.util.List;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchLocation;

/** Builds fixed-size source/receiver patches without calculating stresses. */
interface SubSectPatchBuilder {
	List<PatchLocation> buildPatches(FaultSection section, PatchAlignment alignment);
}
