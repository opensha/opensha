package org.opensha.sha.earthquake.rupForecastImpl.nshm23.util;

import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionSolver;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.SerialSimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.modules.InfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfits;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

public class AnalyticalSingleFaultInversionSolver extends InversionSolver.Default {
	
	private double bVal;

	public AnalyticalSingleFaultInversionSolver(double bVal) {
		this.bVal = bVal;
	}
	
	public static boolean isSingleFault(FaultSystemRupSet rupSet) {
		Integer parentID = null;
		for (FaultSection sect : rupSet.getFaultSectionDataList()) {
			int myParentID = sect.getParentSectionId();
			if (parentID == null)
				parentID = myParentID;
			else if (myParentID != parentID)
				return false;
		}
		return true;
	}

	@Override
	public FaultSystemSolution run(FaultSystemRupSet rupSet, InversionConfiguration config, String info) {
		// make sure that it's actually a single fault
		Preconditions.checkState(isSingleFault(rupSet), "Analytical solution only works for a single-fault "
				+ "rupture set, encountered multiple parents");
		
		ModSectMinMags minMags = rupSet.getModule(ModSectMinMags.class);
		
		InversionTargetMFDs targets = rupSet.requireModule(InversionTargetMFDs.class);
		
		List<? extends IncrementalMagFreqDist> supraNuclMFDs = targets.getOnFaultSupraSeisNucleationMFDs();
		Preconditions.checkNotNull(supraNuclMFDs,
				"Must have section supra-seis nucleation MFDs to solve classic model analytically");
		
		double[] rates = new double[rupSet.getNumRuptures()];
		for (int s=0; s<rupSet.getNumSections(); s++) {
			// solve it in the world of each subsection
			IncrementalMagFreqDist sectMFD = supraNuclMFDs.get(s);
			Preconditions.checkNotNull(sectMFD,
					"Must have section supra-seis nucleation MFDs to solve classic model analytically");
			if (sectMFD.calcSumOfY_Vals() == 0d)
				continue;
			
			// TODO support (and adjust for) waterlevel
			
			List<List<Integer>> rupsInBins = new ArrayList<>(sectMFD.size());
			for (int i=0; i<sectMFD.size(); i++)
				rupsInBins.add(null);
			
			// identify ruptures above section minimum magnitude, and figure out how many occupy each input MFD bin
			for (int rupIndex : rupSet.getRupturesForSection(s)) {
				double mag = rupSet.getMagForRup(rupIndex);
				if (minMags != null && minMags.isBelowSectMinMag(s, mag, sectMFD))
					continue;
				int magIndex = sectMFD.getClosestXIndex(mag);
				List<Integer> binRups = rupsInBins.get(magIndex);
				if (binRups == null) {
					binRups = new ArrayList<>();
					rupsInBins.set(magIndex, binRups);
				}
				binRups.add(rupIndex);
			}
			
			// now figure out rupture rates to satisfy this nucleation MFD
			for (int i=0; i<sectMFD.size(); i++) {
				List<Integer> rups = rupsInBins.get(i);
				double binRate = sectMFD.getY(i);
				if (rups == null || binRate == 0d)
					continue;
				
				if (this.bVal == 0d || allMagsSame(rupSet, rups)) {
					// simple case
					double rateEach = binRate / rups.size();
					
					for (int rupIndex : rups)
						rates[rupIndex] += rateEach;
				} else {
					// complicated, need to spread it across ruptures of different magnitudes
					// do so according to the target b-value
					
					double binCenter = sectMFD.getX(i);
					double centerGR = calcGR(bVal, binCenter);
					double[] weights = new double[rups.size()];
					double sumWeight = 0d;
					for (int r=0; r<weights.length; r++) {
						double mag = rupSet.getMagForRup(rups.get(r));
						double myGR = calcGR(bVal, mag);
						double weight = myGR/centerGR;
						sumWeight += weight;
						weights[r] = weight;
					}
					for (int r=0; r<weights.length; r++)
						rates[rups.get(r)] += binRate*weights[r]/sumWeight;
				}
			}
		}
		
		FaultSystemSolution sol = new FaultSystemSolution(rupSet, rates);
		
		// TODO: water level?
		
		if (config != null) {
			sol.addModule(config);
			List<InversionConstraint> constraints = config.getConstraints();
			if (constraints != null && !constraints.isEmpty()) {
				InversionInputGenerator inputGen = new InversionInputGenerator(rupSet, config);
				inputGen.generateInputs(false);
				double[] data_eq = inputGen.getD();
				double[] misfits_eq = new double[data_eq.length];
				SerialSimulatedAnnealing.calculateMisfit(inputGen.getA(), data_eq, rates, misfits_eq);
				
				double[] data_ineq = null;
				double[] misfits_ineq = null;
				if (inputGen.getA_ineq() != null) {
					data_ineq = inputGen.getD_ineq();
					misfits_ineq = new double[data_eq.length];
					SerialSimulatedAnnealing.calculateMisfit(inputGen.getA_ineq(), data_ineq, rates, misfits_ineq);
				}
				
				InversionMisfits misfits = new InversionMisfits(inputGen.getConstraintRowRanges(), misfits_eq, data_eq,
						misfits_ineq, data_ineq);
				sol.addModule(misfits);
				sol.addModule(misfits.getMisfitStats());
			}
		}
		
		if (info != null)
			sol.addModule(new InfoModule(info));
		
		return sol;
	}
	
	private static boolean allMagsSame(FaultSystemRupSet rupSet, List<Integer> binRups) {
		if (binRups.size() == 1)
			return true;
		Double mag = null;
		for (int rupIndex : binRups) {
			double myMag = rupSet.getMagForRup(rupIndex);
			if (mag == null)
				mag = myMag;
			else if (mag != myMag)
				return false;
		}
		return true;
	}
	
	private static double calcGR(double bVal, double mag) {
		return Math.pow(10, -bVal*mag);
	}

}
