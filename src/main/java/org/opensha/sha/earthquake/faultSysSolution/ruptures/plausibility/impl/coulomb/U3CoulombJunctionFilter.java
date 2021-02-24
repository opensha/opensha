package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.inversion.coulomb.CoulombRates;
import scratch.UCERF3.inversion.coulomb.CoulombRatesRecord;
import scratch.UCERF3.inversion.coulomb.CoulombRatesTester;
import scratch.UCERF3.inversion.coulomb.CoulombRatesTester.TestType;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

public class U3CoulombJunctionFilter implements PlausibilityFilter {
	
	private CoulombRatesTester tester;
	private CoulombRates coulombRates;
	
	private AggregatedStiffnessCalculator tauCalc;
	private AggregatedStiffnessCalculator cffCalc;
	private transient ClusterConnectionStrategy connStrat;

	public U3CoulombJunctionFilter(CoulombRatesTester tester, CoulombRates coulombRates) {
		this.tester = tester;
		this.coulombRates = coulombRates;
	}
	
	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		if (rupture.getTotalNumJumps() == 0)
			return PlausibilityResult.PASS;
		
		List<List<IDPairing>> paths = new ArrayList<>();
		findPaths(rupture.getTreeNavigator(), paths, new ArrayList<>(), rupture.clusters[0].startSect);
		
		return testPaths(paths, verbose);
	}
	
	private void findPaths(RuptureTreeNavigator navigator,
			List<List<IDPairing>> fullPaths, List<IDPairing> curPath, FaultSection curSect) {
		Collection<FaultSection> descendants = navigator.getDescendants(curSect);
		
		while (descendants.size() == 1) {
			FaultSection destSect = descendants.iterator().next();
			if (curSect.getParentSectionId() != destSect.getParentSectionId())
				// it's a jump
				curPath.add(new IDPairing(curSect.getSectionId(), destSect.getSectionId()));
			
			curSect = destSect;
			descendants = navigator.getDescendants(curSect);
		}
		
		if (descendants.isEmpty()) {
			// we're at the end of a chain
			fullPaths.add(curPath);
		} else {
			// we're at a branching point
			for (FaultSection destSect : descendants) {
				List<IDPairing> branchPath = new ArrayList<>(curPath);
				if (curSect.getParentSectionId() != destSect.getParentSectionId())
					// it's a jump
					branchPath.add(new IDPairing(curSect.getSectionId(), destSect.getSectionId()));
				
				findPaths(navigator, fullPaths, branchPath, destSect);
			}
		}
	}
	
	private PlausibilityResult testPaths(List<List<IDPairing>> paths, boolean verbose) {
		if (verbose)
			System.out.println(getShortName()+": found "+paths.size()+" paths");
		Preconditions.checkState(paths.size() >= 0);
		for (List<IDPairing> path : paths) {
			if (verbose)
				System.out.println(getShortName()+": testing a path with "+path.size()+" jumps");
			if (path.isEmpty())
				continue;
			List<CoulombRatesRecord> forwardRates = new ArrayList<>();
			List<CoulombRatesRecord> backwardRates = new ArrayList<>();
			
			for (IDPairing pair : path) {
				CoulombRatesRecord forwardRate = getCoulombRates(pair);
				Preconditions.checkNotNull(forwardRate, "No coulomb rates for %s", pair);
				CoulombRatesRecord backwardRate = getCoulombRates(pair.getReversed());
				Preconditions.checkNotNull(backwardRate, "No coulomb rates for reversed %s", pair);
				if (verbose) {
					System.out.println(getShortName()+": "+pair.getID1()+" => "+pair.getID2());
					System.out.println("\tForward rate: "+forwardRate);
					System.out.println("\tBackward rate: "+backwardRate);
				}
				forwardRates.add(forwardRate);
				backwardRates.add(0, backwardRate);
			}
			
			boolean passes = tester.doesRupturePass(forwardRates, backwardRates);
			if (verbose)
				System.out.println(getShortName()+": test with "+forwardRates.size()+" jumps. passes ? "+passes);
			if (!passes)
				return PlausibilityResult.FAIL_HARD_STOP;
		}
		return PlausibilityResult.PASS;
	}
	
	public void setFallbackCalculator(SubSectStiffnessCalculator calc, ClusterConnectionStrategy connStrat) {
		Preconditions.checkState(calc.getGridSpacing() == 1d, "Should be 1km grid spacing for fallback calculation");
		this.connStrat = connStrat;
		TestType testType = tester.getTestType();
		if (testType == TestType.SHEAR_STRESS)
			this.tauCalc = new AggregatedStiffnessCalculator(StiffnessType.TAU, calc, false, AggregationMethod.MAX, AggregationMethod.MAX);
		else if (testType == TestType.COULOMB_STRESS)
			this.cffCalc = new AggregatedStiffnessCalculator(StiffnessType.CFF, calc, false, AggregationMethod.MAX, AggregationMethod.MAX);
		else {
			this.tauCalc = new AggregatedStiffnessCalculator(StiffnessType.TAU, calc, false, AggregationMethod.MAX, AggregationMethod.MAX);
			this.cffCalc = new AggregatedStiffnessCalculator(StiffnessType.CFF, calc, false, AggregationMethod.MAX, AggregationMethod.MAX);
		}
	}
	
	private CoulombRatesRecord getCoulombRates(IDPairing pair) {
		CoulombRatesRecord rates;
		synchronized (this) {
			rates = coulombRates.get(pair);
		}
		if (rates == null && (cffCalc != null || tauCalc != null)) {
			// calculate it ourselves
			Preconditions.checkNotNull(connStrat, "have stiffness calculator but not connection strategy");
			List<? extends FaultSection> subSects = cffCalc.getCalc().getSubSects();
			double ds=0, pds=0, dcff=0, pdcff=0;
			StiffnessType[] types;
			switch (tester.getTestType()) {
			case COULOMB_STRESS:
				types = new StiffnessType[] { StiffnessType.CFF };
				break;
			case SHEAR_STRESS:
				types = new StiffnessType[] { StiffnessType.TAU };
				break;
			default:
				types = new StiffnessType[] { StiffnessType.TAU, StiffnessType.CFF };
				break;
			}
			for (StiffnessType type : types) {
				AggregatedStiffnessCalculator stiffnessCalc = type == StiffnessType.CFF ? cffCalc : tauCalc;
				Preconditions.checkNotNull(type, "Don't have a stiffness calculator for %s", type);
				FaultSection source = subSects.get(pair.getID1());
				Preconditions.checkState(source.getSectionId() == pair.getID1());
				FaultSection receiver = subSects.get(pair.getID2());
				Preconditions.checkState(receiver.getSectionId() == pair.getID2());
				// stiffness calculator uses 1m displacement and gives results in MPa
				// CoulombRuptureRates assumes 0.1m displacement and bars
				// these two cancel out exactly (multiply by 0.1 to fix displacement, then by 10 to fix units)
				double val = Math.max(0, stiffnessCalc.calc(source, receiver));
				// scalar to correct for method differences
				// median ratio of the value calculated previously divided by that calucated from our method
				val *= 1.5945456137624658;
				// now calculate pdcff, first by summing across all DCFF values
				double sum = val;
				// check other possible connecting sections
				for (Jump jump : connStrat.getJumpsFrom(source)) {
					if (jump.toCluster.parentSectionID != receiver.getParentSectionId()) {
						// it's to a different parent section, include it
						sum += Math.max(0, stiffnessCalc.calc(source, jump.toSection));
					}
				}
				// check neighbors on the same fault
				if (source.getSectionId() > 0 && source.getParentSectionId() == subSects.get(source.getSectionId()-1).getParentSectionId())
					// section before is a target
					sum += Math.max(0, stiffnessCalc.calc(source, subSects.get(source.getSectionId()-1)));
				if (source.getSectionId() < subSects.size()-1 && source.getParentSectionId() == subSects.get(source.getSectionId()+1).getParentSectionId())
					sum += Math.max(0, stiffnessCalc.calc(source, subSects.get(source.getSectionId()+1)));
				double prob = sum > 0 ? val/sum : 0;
				if (type == StiffnessType.CFF) {
					dcff = val;
					pdcff = prob;
				} else {
					ds = val;
					pds = prob;
				}
			}
			rates = new CoulombRatesRecord(pair, ds, pds, dcff, pdcff);
			synchronized (this) {
				coulombRates.put(pair, rates);
			}
		}
		return rates;
	}

	@Override
	public String getShortName() {
		return "Coulomb";
	}

	@Override
	public String getName() {
		return "Coulomb Jump Filter";
	}

	@Override
	public boolean isDirectional(boolean splayed) {
		// only directional if splayed (different inversions could take different jumping points)
		return splayed;
	}

	@Override
	public TypeAdapter<PlausibilityFilter> getTypeAdapter() {
		return new Adapter();
	}
	
	public static class Adapter extends PlausibilityFilterTypeAdapter {

		private ClusterConnectionStrategy connStrategy;
		private Gson gson;

		@Override
		public void init(ClusterConnectionStrategy connStrategy,
				SectionDistanceAzimuthCalculator distAzCalc, Gson gson) {
			this.connStrategy = connStrategy;
			this.gson = gson;
		}

		@Override
		public void write(JsonWriter out, PlausibilityFilter value) throws IOException {
			U3CoulombJunctionFilter filter = (U3CoulombJunctionFilter)value;
			gson.toJson(filter, filter.getClass(), out);
		}

		@Override
		public PlausibilityFilter read(JsonReader in) throws IOException {
			U3CoulombJunctionFilter filter = gson.fromJson(in, U3CoulombJunctionFilter.class);
			filter.connStrat = connStrategy;
			return filter;
		}
		
	}

}
