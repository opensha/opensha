package org.opensha.sha.simulators.stiffness;

import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import com.google.common.base.Preconditions;

/** One-dimensional alignment policy shared by generic row and column placement. */
final class PatchIntervalPlacement {
	static double[] centers(double length, double spacing, PatchAlignment alignment) {
		Preconditions.checkArgument(Double.isFinite(length) && length >= 0, "Invalid interval length: %s", length);
		Preconditions.checkArgument(Double.isFinite(spacing) && spacing > 0, "Invalid spacing: %s", spacing);
		// UTM conversion rounds coordinates to centimeters. Snap ratios within
		// 10 ppm of an integer so that this precision does not add/remove a row.
		double ratio = length/spacing;
		double nearest = Math.rint(ratio);
		if (Math.abs(ratio-nearest) < 1e-5) ratio=nearest;
		int count = Math.max(1, (int)(alignment == PatchAlignment.CENTER ? Math.floor(ratio) : Math.ceil(ratio)));
		double[] centers = new double[count];
		for (int i=0; i<count; i++)
			centers[i] = alignment == PatchAlignment.CENTER || count == 1
					? length*(i+0.5)/count
					: 0.5*spacing+(length-spacing)*i/(count-1);
		return centers;
	}
}
