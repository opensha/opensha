package org.opensha.sha.simulators.stiffness;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessAggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessResult;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

public class RuptureCoulombResult {
	
	public final ImmutableMap<FaultSection, Double> sectSumCFFs;
	public final ImmutableMap<FaultSection, Double> sectFractPositiveCFFs;
	public final int numNetPositiveCFFs;
	public final int numNetNegativeCFFs;
	public final int numSects;
	
	public enum RupCoulombQuantity {
		MEAN_SECT_CFF("Mean Sect Net ΔCFF", "MPa"),
		MIN_SECT_CFF("Min Sect Net ΔCFF", "MPa"),
		MEAN_SECT_FRACT_POSITIVES("Mean Sect Fract ΔCFF > 0"),
		MIN_SECT_FRACT_POSITIVES("Min Sect Fract ΔCFF > 0"),
		NUM_NET_NEGATIVE_SECTS("Num Sects w/ Net ΔCFF < 0");
		
		private String name;
		private String units;
		
		private RupCoulombQuantity(String name) {
			this(name, null);
		}
		
		private RupCoulombQuantity(String name, String units) {
			this.name = name;
			this.units = units;
		}
		
		@Override
		public String toString() {
			if (units == null)
				return name;
			return name+" ("+units+")";
		}
		
	}
	
	public RuptureCoulombResult(ClusterRupture rupture, SubSectStiffnessCalculator calc,
			StiffnessAggregationMethod calcAggMethod) {
		this(clusterRupSects(rupture), calc, calcAggMethod);
	}
	
	private static List<FaultSection> clusterRupSects(ClusterRupture rupture) {
		List<FaultSection> allSects = new ArrayList<>();
		for (FaultSubsectionCluster cluster : rupture.getClustersIterable())
			allSects.addAll(cluster.subSects);
		return allSects;
	}
	
	public RuptureCoulombResult(List<? extends FaultSection> rupSects, SubSectStiffnessCalculator calc,
			StiffnessAggregationMethod calcAggMethod) {
		Builder<FaultSection, Double> sumCFFsBuilder = ImmutableMap.builder();
		Builder<FaultSection, Double> fractPosCFFsBuilder = ImmutableMap.builder();
		
		Preconditions.checkArgument(rupSects.size() > 1);
		this.numSects = rupSects.size();
		int numNetPositive = 0;
		int numNetNegative = 0;
		for (FaultSection receiver : rupSects) {
			double sumCFF = 0d;
			double fractPositives = 0d;
			for (FaultSection source : rupSects) {
				if (source == receiver)
					continue;
				StiffnessResult[] stiffness = calc.calcStiffness(source, receiver);
				double cff = calc.getValue(stiffness, StiffnessType.CFF, calcAggMethod);
				double fractPositive = calc.getValue(
						stiffness, StiffnessType.CFF, StiffnessAggregationMethod.FRACT_POSITIVE);
				Preconditions.checkState(Double.isFinite(cff), "CFF is %s from %s to %s",
						cff, source.getSectionName(), receiver.getSectionName());
				sumCFF += cff;
				fractPositives += fractPositive;
			}
			fractPositives /= (rupSects.size()-1);
			sumCFFsBuilder.put(receiver, sumCFF);
			fractPosCFFsBuilder.put(receiver, fractPositives);
			if (sumCFF > 0d)
				numNetPositive++;
			if (sumCFF < 0d)
				numNetNegative++;
		}
		this.sectSumCFFs = sumCFFsBuilder.build();
		this.sectFractPositiveCFFs = fractPosCFFsBuilder.build();
		this.numNetPositiveCFFs = numNetPositive;
		this.numNetNegativeCFFs = numNetNegative;
	}
	
	public double getValue(RupCoulombQuantity quantity) {
		switch (quantity) {
		case MEAN_SECT_CFF:
			return mean(sectSumCFFs.values());
		case MIN_SECT_CFF:
			return min(sectSumCFFs.values());
		case MEAN_SECT_FRACT_POSITIVES:
			return mean(sectFractPositiveCFFs.values());
		case MIN_SECT_FRACT_POSITIVES:
			return min(sectFractPositiveCFFs.values());
		case NUM_NET_NEGATIVE_SECTS:
			return numNetNegativeCFFs;

		default:
			throw new IllegalStateException();
		}
	}
	
	private static double mean(Collection<Double> values) {
		double sum = 0d;
		for (double val : values)
			sum += val;
		return sum/(double)values.size();
	}
	
	private static double min(Collection<Double> values) {
		double min = Double.POSITIVE_INFINITY;
		for (Double val : values)
			min = Math.min(val, min);
		return min;
	}

}
