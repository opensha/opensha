package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import scratch.UCERF3.FaultSystemRupSet;

/**
 * MFD Smoothness Constraint - Constrain participation MFD to be uniform for each fault subsection.
 * 
 * This was not ultimately used in UCERF3
 * 
 * @author Morgan Page & Kevin Milner
 *
 */
public class MFDParticipationSmoothnessInversionConstraint extends InversionConstraint {
	
	private FaultSystemRupSet rupSet;
	private double weight;
	private double particMagBinSize;

	public MFDParticipationSmoothnessInversionConstraint(FaultSystemRupSet rupSet, double weight,
			double particMagBinSize) {
		this.rupSet = rupSet;
		this.weight = weight;
		this.particMagBinSize = particMagBinSize;
	}

	@Override
	public String getShortName() {
		return "MFDParticSmooth";
	}

	@Override
	public String getName() {
		return "MFD Participation Smoothness";
	}

	@Override
	public int getNumRows() {
		int totalNumMagParticipationConstraints = 0;
		int numSections = rupSet.getNumSections();
		for (int sect=0; sect<numSections; sect++) { 
			List<Integer> rupturesForSection = rupSet.getRupturesForSection(sect);
			// Find minimum and maximum rupture-magnitudes for that subsection
			double minMag = Double.POSITIVE_INFINITY; double maxMag = Double.NEGATIVE_INFINITY;
			for (int rupIndex=0; rupIndex<rupturesForSection.size(); rupIndex++) {
				double mag = rupSet.getMagForRup(rupturesForSection.get(rupIndex));
				minMag = Math.min(minMag, mag);
				maxMag = Math.max(maxMag, mag);
			}
			if (!Double.isFinite(minMag)) {
				continue;  // Skip this section, go on to next section constraint
			}
			// Find total number of section magnitude-bins
			for (double m=minMag; m<maxMag; m=m+particMagBinSize) { 
				for (int rupIndex=0; rupIndex<rupturesForSection.size(); rupIndex++) {
					double mag = rupSet.getMagForRup(rupturesForSection.get(rupIndex));
					if (mag >=m && mag < m+particMagBinSize) {
						totalNumMagParticipationConstraints++;
						break;
					}				
				}
			}
		}
		return totalNumMagParticipationConstraints;
	}

	@Override
	public boolean isInequality() {
		return false;
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		int numSections = rupSet.getNumSections();
		List<Integer> numRupsForMagBin = new ArrayList<>();
		int rowIndex = startRow;
		for (int sect=0; sect<numSections; sect++) {
			List<Integer> rupturesForSection = rupSet.getRupturesForSection(sect);
			
			// Find minimum and maximum rupture-magnitudes for that subsection
			double minMag = Double.POSITIVE_INFINITY; double maxMag = Double.NEGATIVE_INFINITY;
			for (int rupIndex=0; rupIndex<rupturesForSection.size(); rupIndex++) {
				double mag = rupSet.getMagForRup(rupturesForSection.get(rupIndex));
				minMag = Math.min(minMag, mag);
				maxMag = Math.max(maxMag, mag);
			}
			if (!Double.isFinite(minMag)) {
				System.out.println("NO RUPTURES FOR SECTION #"+sect);  
				continue;  // Skip this section, go on to next section constraint
			}
			
			// Find number of ruptures for this section for each magnitude bin & total number
			// of magnitude-bins with ruptures
			numRupsForMagBin.clear();
			int numNonzeroMagBins = 0;
			for (double m=minMag; m<maxMag; m=m+particMagBinSize) {
				numRupsForMagBin.add(0);
				for (int rupIndex=0; rupIndex<rupturesForSection.size(); rupIndex++) {
					double mag = rupSet.getMagForRup(rupturesForSection.get(rupIndex));
					if (mag >=m && mag < m+particMagBinSize)
						numRupsForMagBin.set(numRupsForMagBin.size()-1,
								numRupsForMagBin.get(numRupsForMagBin.size()-1)+1); // numRupsForMagBin(end)++
				}
				if (numRupsForMagBin.get(numRupsForMagBin.size()-1) > 0)
					numNonzeroMagBins++;
			}
			
			// Put together A matrix elements: A_avg_rate_per_mag_bin * x - A_rate_for_particular_mag_bin * x = 0
			// Each mag bin (that contains ruptures) for each subsection adds one row to A & d
			int magBinIndex=0;
			for (double m=minMag; m<maxMag; m=m+particMagBinSize) {
				if (numRupsForMagBin.get(magBinIndex) > 0) {
					for (int rupIndex=0; rupIndex<rupturesForSection.size(); rupIndex++) {
						// Average rate per magnitude bin for this section
						int col = rupturesForSection.get(rupIndex);
						double val = weight/numNonzeroMagBins;	
						numNonZeroElements++;
						double mag = rupSet.getMagForRup(col);
						if (mag >=m && mag < m+particMagBinSize) {
							// Subtract off rate for this mag bin (difference between average rate per mag bin
							// & rate for this mag bin is set to 0)
							val -= weight;
						}
						setA(A, rowIndex, col, val);
					}
					d[rowIndex] = 0;
					rowIndex++;
				}	
				magBinIndex++;				
			}	
			
		}
		return numNonZeroElements;
	}

}
