package org.opensha.sha.simulators.stiffness.patches;

import java.util.List;

import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultSection;

/** Constructs patches from the supplied surface; elastic kernels and aggregation are separate. */
public interface SubSectPatchBuilder {
	List<PatchLocation> buildPatches(FaultSection section, EvenlyGriddedSurface surface);
}
