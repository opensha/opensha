package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.uncertainty.Uncertainty;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.JSON_TypeAdapterBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoSlipProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.SectMappedUncertainDataConstraint;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_DeformationModels;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

import com.google.common.base.Preconditions;
import com.google.gson.GsonBuilder;

import scratch.UCERF3.utils.aveSlip.U3AveSlipConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.U3PaleoRateConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.UCERF3_PaleoProbabilityModel;
import scratch.UCERF3.utils.paleoRateConstraints.UCERF3_PaleoRateConstraintFetcher;

/**
 * Data container module for paleoseismic data, including paleo rate constraints, and paleo average slip constraints
 * (from which rates can be inferred when combined with slip rates and scaling relationships)
 * 
 * @author kevin
 *
 */
public class PaleoseismicConstraintData implements SubModule<FaultSystemRupSet>,
JSON_TypeAdapterBackedModule<PaleoseismicConstraintData>, BranchAverageableModule<PaleoseismicConstraintData>,
SplittableRuptureSubSetModule<PaleoseismicConstraintData> {
	
	private transient FaultSystemRupSet rupSet;
	private List<? extends SectMappedUncertainDataConstraint> paleoRateConstraints;
	private PaleoProbabilityModel paleoProbModel;
	private List<? extends SectMappedUncertainDataConstraint> paleoSlipConstraints;
	private PaleoSlipProbabilityModel paleoSlipProbModel;
	
	public static PaleoseismicConstraintData loadUCERF3(FaultSystemRupSet rupSet) throws IOException {
		// paleo event rate
		List<U3PaleoRateConstraint> paleoRateConstraints =
				UCERF3_PaleoRateConstraintFetcher.getConstraints(rupSet.getFaultSectionDataList());
		UCERF3_PaleoProbabilityModel paleoProbModel = UCERF3_PaleoProbabilityModel.load();

		// paleo slip
		List<U3AveSlipConstraint> aveSlipConstraints = U3AveSlipConstraint.load(rupSet.getFaultSectionDataList());
		PaleoSlipProbabilityModel paleoSlipProbModel = U3AveSlipConstraint.slip_prob_model;
		
		return new PaleoseismicConstraintData(rupSet, paleoRateConstraints, paleoProbModel,
				aveSlipConstraints, paleoSlipProbModel);
	}
	
	@SuppressWarnings("unused") // used for deserialization
	private PaleoseismicConstraintData() {}

	public PaleoseismicConstraintData(FaultSystemRupSet rupSet,
			List<? extends SectMappedUncertainDataConstraint> paleoRateConstraints,
			PaleoProbabilityModel paleoProbModel,
			List<? extends SectMappedUncertainDataConstraint> paleoSlipConstraints,
			PaleoSlipProbabilityModel paleoSlipProbModel) {
		this.rupSet = rupSet;
		this.paleoRateConstraints = paleoRateConstraints;
		this.paleoProbModel = paleoProbModel;
		this.paleoSlipConstraints = paleoSlipConstraints;
		this.paleoSlipProbModel = paleoSlipProbModel;
	}

	@Override
	public String getName() {
		return "Paleoseismic Constraint Data";
	}

	@Override
	public void setParent(FaultSystemRupSet parent) throws IllegalStateException {
		if (this.rupSet != null && parent != null)
			Preconditions.checkState(rupSet.areSectionsEquivalentTo(parent));
		this.rupSet = parent;
	}

	@Override
	public FaultSystemRupSet getParent() {
		return rupSet;
	}

	@Override
	public SubModule<FaultSystemRupSet> copy(FaultSystemRupSet newParent) throws IllegalStateException {
		if (this.rupSet != null && newParent != null)
			Preconditions.checkState(rupSet.areSectionsEquivalentTo(newParent));
		return new PaleoseismicConstraintData(newParent, paleoRateConstraints, paleoProbModel,
				paleoSlipConstraints, paleoSlipProbModel);
	}

	@Override
	public String getFileName() {
		return "paleo_constraint_data.json";
	}

	@Override
	public Type getType() {
		return PaleoseismicConstraintData.class;
	}

	@Override
	public PaleoseismicConstraintData get() {
		return this;
	}

	@Override
	public void set(PaleoseismicConstraintData value) {
		rupSet = value.rupSet;
		paleoRateConstraints = value.paleoRateConstraints;
		paleoProbModel = value.paleoProbModel;
		paleoSlipConstraints = value.paleoSlipConstraints;
		paleoSlipProbModel = value.paleoSlipProbModel;
	}

	@Override
	public void registerTypeAdapters(GsonBuilder builder) {}
	
	public boolean hasPaleoRateConstraints() {
		return paleoRateConstraints != null && !paleoRateConstraints.isEmpty();
	}

	public List<? extends SectMappedUncertainDataConstraint> getPaleoRateConstraints() {
		return paleoRateConstraints;
	}

	public PaleoProbabilityModel getPaleoProbModel() {
		return paleoProbModel;
	}
	
	public boolean hasPaleoSlipConstraints() {
		return paleoSlipConstraints != null && !paleoSlipConstraints.isEmpty();
	}

	public List<? extends SectMappedUncertainDataConstraint> getPaleoSlipConstraints() {
		return paleoSlipConstraints;
	}

	public PaleoSlipProbabilityModel getPaleoSlipProbModel() {
		return paleoSlipProbModel;
	}
	
	private boolean prevAppliedRateUncertainty;
	List<SectMappedUncertainDataConstraint> prevInferred = null;
	
	/**
	 * Converts paleo slip constraints to rate constraints using the rupture set target slip rates
	 * 
	 * @param applySlipRateUncertainty if true, uncertainties are expanded to consider slip-rate standard deviations
	 * @return list of rate constraints inferred from average slip constraints
	 */
	public synchronized List<SectMappedUncertainDataConstraint> inferRatesFromSlipConstraints(boolean applySlipRateUncertainty) {
		if (prevInferred != null && prevAppliedRateUncertainty == applySlipRateUncertainty)
			return new ArrayList<>(prevInferred);
		prevInferred = inferRatesFromSlipConstraints(rupSet, paleoSlipConstraints, applySlipRateUncertainty);
		prevAppliedRateUncertainty = applySlipRateUncertainty;
		return new ArrayList<>(prevInferred);
	}
	
	/**
	 * Converts paleo slip constraints to rate constraints using the rupture set target slip rates, propagating uncertainties
	 * 
	 * @param targetSlipRates target slip rate data
	 * @param paleoSlipConstraints paleo-slip constraints, to be converted to rate constraints
	 * @param applySlipRateUncertainty if true, uncertainties are expanded to consider slip-rate standard deviations
	 * @return list of rate constraints inferred from average slip constraints
	 */
	public static List<SectMappedUncertainDataConstraint> inferRatesFromSlipConstraints(
			FaultSystemRupSet rupSet, List<? extends SectMappedUncertainDataConstraint> paleoSlipConstraints,
			boolean applySlipRateUncertainty) {
		SectSlipRates targetSlipRates = rupSet.requireModule(SectSlipRates.class);
		double[] slipRateStdDevs = null;
		if (applySlipRateUncertainty) {
			slipRateStdDevs = targetSlipRates.getSlipRateStdDevs();
			for (int s=0; s<slipRateStdDevs.length; s++) {
				FaultSection sect = rupSet.getFaultSectionData(s);
				double slip = targetSlipRates.getSlipRate(s);
				if (slip > 0 && sect instanceof GeoJSONFaultSection) {
					double origFractSlip = ((GeoJSONFaultSection)sect).getProperty(
							NSHM23_DeformationModels.ORIG_FRACT_STD_DEV_PROPERTY_NAME, Double.NaN);
					if (origFractSlip > 0d)
						slipRateStdDevs[s] = Math.max(slipRateStdDevs[s], slip*origFractSlip);
				}
			}
		}
		
		List<SectMappedUncertainDataConstraint> inferred = new ArrayList<>();
		
		for (SectMappedUncertainDataConstraint constraint : paleoSlipConstraints) {
			// this is a constraint on average slip, but we need to convert it to a constraint on rates
			
			// slip rate, in m/yr
			double targetSlipRate = targetSlipRates.getSlipRate(constraint.sectionIndex);
			// slip rate std dev, in m/yr
			double targetSlipRateStdDev = applySlipRateUncertainty ? slipRateStdDevs[constraint.sectionIndex] : 0d;
			
			// average slip, in m
			double aveSlip = constraint.bestEstimate;
			// average slip std dev, in m
			double aveSlipStdDev = constraint.getPreferredStdDev();

			
			System.out.println("Inferring rate constraint from paleo slip constraint on "+constraint.sectionName);
			System.out.println("\tslip="+(float)aveSlip+" +/- "+(float)aveSlipStdDev);
			System.out.println("\tslip rate="+(float)targetSlipRate+" +/- "+(float)targetSlipRateStdDev);
			
			// rate estimate: r = s / d
			double meanRate = targetSlipRate / aveSlip;
			/*
			 * uncertainty propagation:
			 * 		r +/- deltaR = (s +/- deltaS)/(d +/- deltaD)
			 * simplifies to (see https://www.geol.lsu.edu/jlorenzo/geophysics/uncertainties/Uncertaintiespart2.html):
			 * 		deltaR/r = sqrt((deltaS/s)^2 + (deltaD/d)^2)
			 * 		deltaR = r*sqrt((deltaS/s)^2 + (deltaD/d)^2)
			 * 
			 * Note: UCERF3 used a different version:
			 * 		r_high = s / d_low
			 * 		r_low = s / d_high
			 * 		deltaR = = (r_high - r_low)/4
			 * This worked fine for and is pretty similar to the above values for small-ish uncertainties with
			 * applySlipRateUncertainty == false, but goes to infinity as d_low approaches 0. These issues were never
			 * encountered as the UCERF3 slip uncertainties were small, but is not a good general solution. 
			 */
			double rateSD;
			if (applySlipRateUncertainty) {
				if (targetSlipRate == 0d)
					// slip rate is zero, so mean rate is zero, set the uncertainty to the upper bound implied by
					// taking both high options (high slip rate, 0+sd, and low slip, slip-sd)
					rateSD = targetSlipRateStdDev / (aveSlip-aveSlipStdDev);
				else 
					rateSD = meanRate * Math.sqrt(
							Math.pow(targetSlipRateStdDev/targetSlipRate, 2) + Math.pow(aveSlipStdDev/aveSlip, 2));
			} else {
				// even simpler: deltaR = r*sqrt((deltaS/s)^2) = r*deltaD/d
				rateSD = meanRate * aveSlipStdDev/aveSlip;
			}
			
			System.out.println("\trate="+(float)meanRate+" +/- "+(float)rateSD);
			
			inferred.add(new SectMappedUncertainDataConstraint(constraint.name, constraint.sectionIndex,
					constraint.sectionName, constraint.dataLocation, meanRate, new Uncertainty(rateSD)));
		}
		
		return inferred;
	}
	
	public static void main(String[] args) throws IOException {
		FaultSystemRupSet rupSet = FaultSystemRupSet.load(
				new File("/home/kevin/markdown/inversions/fm3_1_u3ref_uniform_coulomb.zip"));
		
		PaleoseismicConstraintData data = loadUCERF3(rupSet);
		data.inferRatesFromSlipConstraints(true);
	}

	@Override
	public AveragingAccumulator<PaleoseismicConstraintData> averagingAccumulator() {
		// TODO Auto-generated method stub
		return new AveragingAccumulator<PaleoseismicConstraintData>() {
			
			PaleoseismicConstraintData paleoData;

			@Override
			public void process(PaleoseismicConstraintData module, double relWeight) {
				if (paleoData == null) {
					paleoData = module;
				} else {
					// make sure it's the same
					boolean same = paleoConstraintsSame(paleoData.getPaleoRateConstraints(),
							module.getPaleoRateConstraints());
					same = same && paleoConstraintsSame(paleoData.getPaleoSlipConstraints(),
							module.getPaleoSlipConstraints());
					if (same && paleoData.getPaleoProbModel() != null)
						same = paleoData.getPaleoProbModel().getClass().equals(module.getPaleoProbModel().getClass());
					if (same && paleoData.getPaleoSlipProbModel() != null)
						same = paleoData.getPaleoSlipProbModel().getClass().equals(module.getPaleoSlipProbModel().getClass());
					if (!same)
						throw new IllegalStateException("Paleo-seismic data varies by branch, averaging not (yet) supported");
				}
			}

			@Override
			public PaleoseismicConstraintData getAverage() {
				return paleoData;
			}

			@Override
			public Class<PaleoseismicConstraintData> getType() {
				return PaleoseismicConstraintData.class;
			}
		};
	}
	
	private static boolean paleoConstraintsSame(List<? extends SectMappedUncertainDataConstraint> constr1,
			List<? extends SectMappedUncertainDataConstraint> constr2) {
		if ((constr1 == null) != (constr2 == null))
			return false;
		if (constr1 == null && constr2 == null)
			return true;
		if (constr1.size() != constr2.size())
			return false;
		for (int i=0; i<constr1.size(); i++) {
			SectMappedUncertainDataConstraint c1 = constr1.get(i);
			SectMappedUncertainDataConstraint c2 = constr2.get(i);
			if (c1.sectionIndex != c2.sectionIndex)
				return false;
			if ((float)c1.bestEstimate != (float)c2.bestEstimate)
				return false;
		}
		return true;
	}

	@Override
	public PaleoseismicConstraintData getForRuptureSubSet(FaultSystemRupSet rupSubSet, RuptureSubSetMappings mappings) {
		List<SectMappedUncertainDataConstraint> filteredRateConstraints = getRemapped(paleoRateConstraints, mappings);
		List<SectMappedUncertainDataConstraint> filteredSlipConstraints = getRemapped(paleoSlipConstraints, mappings);
		if (filteredRateConstraints == null && filteredSlipConstraints == null)
			return null;
		return new PaleoseismicConstraintData(rupSubSet, filteredRateConstraints, paleoProbModel,
				filteredSlipConstraints, paleoSlipProbModel);
	}
	
	private static List<SectMappedUncertainDataConstraint> getRemapped(
			List<? extends SectMappedUncertainDataConstraint> constraints, RuptureSubSetMappings mappings) {
		if (constraints == null || constraints.isEmpty())
			return null;
		List<SectMappedUncertainDataConstraint> remapped = new ArrayList<>();
		for (SectMappedUncertainDataConstraint constraint : constraints)
			if (mappings.isSectRetained(constraint.sectionIndex))
				remapped.add(constraint.forRemappedSectionIndex(mappings.getNewSectID(constraint.sectionIndex)));
		if (remapped.isEmpty())
			return null;
		return remapped;
	}

}
