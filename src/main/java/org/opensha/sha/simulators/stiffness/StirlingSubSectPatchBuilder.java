package org.opensha.sha.simulators.stiffness;

import java.util.ArrayList;
import java.util.List;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
import org.opensha.sha.simulators.stiffness.StiffnessCalc.Patch;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchLocation;
import com.google.common.base.Preconditions;

/** Ribbon-based construction, preserving the original Stirling discretization. */
final class StirlingSubSectPatchBuilder implements SubSectPatchBuilder {
	private final double gridSpacing;
	private final int utmZone;
	private final char utmChar;

	StirlingSubSectPatchBuilder(double gridSpacing, int utmZone, char utmChar) {
		this.gridSpacing = gridSpacing;
		this.utmZone = utmZone;
		this.utmChar = utmChar;
	}

	@Override
	public List<PatchLocation> buildPatches(FaultSection section, EvenlyGriddedSurface surface, PatchAlignment alignment) {
		Preconditions.checkState(surface instanceof StirlingGriddedSurface,
				"STIRLING patch generation requires StirlingGriddedSurface; section %s supplies %s. Use GENERIC or AUTO.",
				section.getSectionName(), surface.getClass().getName());
		StirlingGriddedSurface stirling = (StirlingGriddedSurface)surface;
		return alignment == PatchAlignment.CENTER ? buildCenterPatches(section, stirling) : buildFillOverlapPatches(section, stirling);
	}

	private List<PatchLocation> buildCenterPatches(FaultSection sect, StirlingGriddedSurface surf) {

		double surfLength = surf.getAveLength();
		double surfWidth = surf.getAveWidth();
		int numAS = Integer.max(1, (int)(surfLength/gridSpacing));
		int numDD = Integer.max(1, (int)(surfWidth/gridSpacing));
		double outerSpacingAS = surfLength/numAS;
		double outerSpacingDD = surfWidth/numDD;
		
		
		double aveDip = surf.getAveDip();
		double aveRake = sect.getAveRake();
		
		List<PatchLocation> myPatches = new ArrayList<>();
		
		double halfSpacingKM = gridSpacing*0.5;
		double dipRad = Math.toRadians(aveDip);
		
		for (int i=0; i<numAS; i++) {
			// we want the middle so add 0.5 to index
			double das = outerSpacingAS*(i+0.5);
			for (int j=0; j<numDD; j++) {
				double ddw = outerSpacingDD*(j+0.5);
				
				PatchLocation patchLoc = buildPatch(surf, aveDip, aveRake, halfSpacingKM, dipRad, das, ddw);
				myPatches.add(patchLoc);
			}
		}
		return myPatches;
	}
	
	private List<PatchLocation> buildFillOverlapPatches(FaultSection sect, StirlingGriddedSurface surf) {

		double surfLength = surf.getAveLength();
		double surfWidth = surf.getAveWidth();
		
		List<Double> dasCenters = calcFullOverlapCenters(surfLength, gridSpacing);
		List<Double> dasWidths = calcFullOverlapCenters(surfWidth, gridSpacing);
		
		
		double aveDip = surf.getAveDip();
		double aveRake = sect.getAveRake();
		
		List<PatchLocation> myPatches = new ArrayList<>();
		
		double halfSpacingKM = gridSpacing*0.5;
		double dipRad = Math.toRadians(aveDip);
		
		for (double das : dasCenters) {
			for (double ddw : dasWidths) {
				PatchLocation patchLoc = buildPatch(surf, aveDip, aveRake, halfSpacingKM, dipRad, das, ddw);
				myPatches.add(patchLoc);
			}
		}
		return myPatches;
	}
	
	private static List<Double> calcFullOverlapCenters(double length, double gridSpacing) {
		List<Double> centers = new ArrayList<>();
		if (length <= gridSpacing) {
			centers.add(0.5*length);
			return centers;
		}
		double firstCenter = 0.5*gridSpacing;
		double lastCenter = length - 0.5*gridSpacing;
		centers.add(firstCenter);
		double residual = length - 2*gridSpacing;
		if (residual > 0) {

			// we have extra in the middle that we need to fill
			int numMiddles = (int)Math.ceil(residual/gridSpacing);
			if (numMiddles == 1) {
				// just put it in the very middle
				centers.add(length*0.5);
			} else {
				// more complicated, we need to space it out for equal overlap
				
				// this is the length between the center of the first and last ones
				double subLength = length - gridSpacing;
				// this is the center-to-center spacing
				double subSpacing = subLength/(2 + numMiddles - 1);
				for (int i=0; i<numMiddles; i++)
					centers.add(firstCenter + subSpacing*(i+1));
				double lastMiddleCenter = centers.get(centers.size()-1);
				double calcLastCenter = lastMiddleCenter + subSpacing;
				Preconditions.checkState((float)calcLastCenter == (float)lastCenter,
						"middle alignment is off for length=%s, spacing=%s.\n\tcalcLast=%s != last=%s"
						+ "\n\tresidual=%s, subLen=%s, numMiddles=%s, subSpacing=%s"
						+ "\n\tcenters excluding end: %s",
						length, gridSpacing, calcLastCenter, lastCenter, residual, subLength, numMiddles,
						subSpacing, centers);
			}
		}
		
		centers.add(lastCenter);
		return centers;
	}

	private PatchLocation buildPatch(AbstractEvenlyGriddedSurface surf, double aveDip, double aveRake,
			double halfSpacingKM, double dipRad, double das, double ddw) {
		Location center = surf.getInterpolatedLocation(das, ddw);
		int col = Math.min(surf.getNumCols()-1, Math.max(1,
				(int)(das/surf.getGridSpacingAlongStrike())));
		double strike = LocationUtils.azimuth(surf.getLocation(0, col-1), surf.getLocation(0, col));
		
		FocalMechanism mech = new FocalMechanism(strike, aveDip, aveRake);
		// use the input grid spacing instead. this will be less than or equal
		// to the full grid spacing, and ensures that the moment is identical for
		// all patches across all subsections
		double lengthM = gridSpacing*1000d; // km -> m
		double widthM = lengthM;
		Patch patch = new Patch(center, utmZone, utmChar, lengthM, widthM, mech);
		Location[] corners = new Location[4];
		// locs above are bounding corners, switch to the real patch corners
		LocationVector alongStrikeHalfForward = new LocationVector(strike, halfSpacingKM, 0d);
		LocationVector alongStrikeHalfBackward = new LocationVector(strike, -halfSpacingKM, 0d);
		double halfDipVert =  Math.sin(dipRad)*halfSpacingKM;
		double halfDipHorz = Math.cos(dipRad)*halfSpacingKM;
		LocationVector downDipHalf = new LocationVector(strike+90d, halfDipHorz, halfDipVert);
		LocationVector upDipHalf = new LocationVector(strike-90d, halfDipHorz, -halfDipVert);
		corners[0] = LocationUtils.location(LocationUtils.location(center, upDipHalf),
				alongStrikeHalfBackward); // upper left
		corners[1] = LocationUtils.location(LocationUtils.location(center, upDipHalf),
				alongStrikeHalfForward); // upper right
		corners[2] = LocationUtils.location(LocationUtils.location(center, downDipHalf),
				alongStrikeHalfForward); // bottom right
		corners[3] = LocationUtils.location(LocationUtils.location(center, downDipHalf),
				alongStrikeHalfBackward); // bottom left
		PatchLocation patchLoc = new PatchLocation(patch, center, corners);
		return patchLoc;
	}
	
}
