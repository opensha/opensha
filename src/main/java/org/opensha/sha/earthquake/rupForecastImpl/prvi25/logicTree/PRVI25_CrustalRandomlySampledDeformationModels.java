package org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree;

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.netlib.util.intW;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.IntegerPDF_FunctionSampler;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode.RandomlySampledNode;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.util.SubSectionBuilder;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public class PRVI25_CrustalRandomlySampledDeformationModels implements RandomlySampledNode, RupSetDeformationModel {
	
	private String name;
	private String shortName;
	private String prefix;
	private double weight;
	private long seed;
	
	private static final String PATH = "/data/erf/prvi25/def_models/crustal/synth_dm_pdfs";
	private static Map<Integer, DiscretizedFunc> pdfs = null;
	private static Map<Integer, IntegerPDF_FunctionSampler> pdfSamplers = null;

	@SuppressWarnings("unused") // for deserialization
	private PRVI25_CrustalRandomlySampledDeformationModels() {}
	
	PRVI25_CrustalRandomlySampledDeformationModels(int index, long seed, double weight) {
		init("Deformation Model Sample "+index, "DMSample"+index, "DMSample"+index, weight, seed);
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return prefix;
	}

	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public long getSeed() {
		return seed;
	}
	
	@Override
	public String toString() {
		return shortName;
	}

	@Override
	public void init(String name, String shortName, String prefix, double weight, long seed) {
		this.name = name;
		this.shortName = shortName;
		this.prefix = prefix;
		this.weight = weight;
		this.seed = seed;
	}

	@Override
	public boolean isApplicableTo(RupSetFaultModel faultModel) {
		return faultModel instanceof PRVI25_CrustalFaultModels;
	}

	@Override
	public List<? extends FaultSection> build(RupSetFaultModel faultModel) throws IOException {
		return build(faultModel, 2, 0.5, Double.NaN);
	}

	@Override
	public List<? extends FaultSection> build(RupSetFaultModel faultModel, int minPerFault, double ddwFract,
			double fixedLen) throws IOException {
		List<? extends FaultSection> fullSects = faultModel.getFaultSections();
		return buildDefModel(SubSectionBuilder.buildSubSects(faultModel.getFaultSections(), minPerFault, ddwFract, fixedLen), fullSects);
	}

	@Override
	public List<? extends FaultSection> buildForSubsects(RupSetFaultModel faultModel,
			List<? extends FaultSection> subSects) throws IOException {
		List<? extends FaultSection> fullSects = faultModel.getFaultSections();
		return buildDefModel(subSects, fullSects);
	}
	
	private List<? extends FaultSection> buildDefModel(List<? extends FaultSection> subSects, List<? extends FaultSection> fullSects) throws IOException {
		applySlipRates(subSects, fullSects);
		PRVI25_CrustalDeformationModels.applyStdDevDefaults(subSects);
		PRVI25_CrustalDeformationModels.applyCreepDefaults(subSects);
		return subSects;
	}
	
	private Map<Integer, Double> drawSectSlipRates(List<? extends FaultSection> fullSects) throws IOException {
		checkLoadPDFs(fullSects);
		Random rand = new Random(seed);
		Map<Integer, Double> randSlips = new HashMap<>(fullSects.size());
		for (FaultSection sect : fullSects) {
			DiscretizedFunc pdf = pdfs.get(sect.getSectionId());
			IntegerPDF_FunctionSampler sampler = pdfSamplers.get(sect.getSectionId());
			int index = sampler.getRandomInt(rand);
			double slip = pdf.getX(index);
			randSlips.put(sect.getSectionId(), slip);
		}
		return randSlips;
	}
	
	private void applySlipRates(List<? extends FaultSection> subSects, List<? extends FaultSection> fullSects) throws IOException {
		Map<Integer, Double> randSlips = drawSectSlipRates(fullSects);
		
		for (FaultSection sect : subSects) {
			double slip = randSlips.get(sect.getParentSectionId());
			sect.setAveSlipRate(slip);
		}
	}
	
	private synchronized static void checkLoadPDFs(List<? extends FaultSection> fullSects) throws IOException {
		if (pdfs == null) {
			pdfs = new HashMap<>(fullSects.size());
			pdfSamplers = new HashMap<>(fullSects.size());
			for (FaultSection sect : fullSects) {
//				System.out.println("Loading slip PDF for "+sect.getSectionName());
				String fName = sect.getSectionName()+".csv";
				String path = PATH+"/"+fName;
				InputStream is = PRVI25_CrustalRandomlySampledDeformationModels.class.getResourceAsStream(path);
				Preconditions.checkNotNull(is, "%s was not found", path);
				CSVFile<String> csv = CSVFile.readStream(is, true);
				DiscretizedFunc func = new ArbitrarilyDiscretizedFunc();
				for (int row=0; row<csv.getNumRows(); row++)
					func.set(csv.getDouble(row, 0), csv.getDouble(row, 1));
				// now check for negatives
				int negCount = 0;
				double negWt = 0d;
				boolean hasZero = false;
				for (int i=0; i<func.size(); i++) {
					double x = func.getX(i);
					hasZero = x == 0d;
					double y = func.getY(i);
					Preconditions.checkState(y >= 0d);
					if (x < 0d) {
						negCount++;
						negWt += y;
					}
				}
				if (negCount > 0) {
					System.err.println("WARNING: "+sect.getName()+" pdf has "+negCount+" negative slip rate bin(s) with wt="+(float)negWt);
					ArbitrarilyDiscretizedFunc modFunc = new ArbitrarilyDiscretizedFunc();
					if (!hasZero)
						// add interpolated zero
						modFunc.set(0d, func.getInterpolatedY(0d));
					for (int i=negCount; i<func.size(); i++)
						modFunc.set(func.get(i));
					func = modFunc;
				}
				// normalize
				func.scale(1d/func.calcSumOfY_Vals());
				pdfs.put(sect.getSectionId(), func);
				double[] yVals = new double[func.size()];
				for (int i=0; i<func.size(); i++)
					yVals[i] = func.getY(i);
				pdfSamplers.put(sect.getSectionId(), new IntegerPDF_FunctionSampler(yVals));
				
//				// now convert it to a cumulative where x is cumulative weight and y is slip for easy sampling
//				double[] xVals = new double[func.size()+2];
//				double[] yVals = new double[func.size()+2];
//				xVals[0] = 0d;
//				yVals[0] = func.getX(0);
//				double sum = 0d;
//				for (int i=0; i<func.size(); i++) {
//					if (i == 0 || i == func.size()-1)
//						// these are weights across the whole bins, but values are at the center
//						// put half weight in the first and last bin, which we'll correct for before
//						// we normalize at the end. without doing this, first bin middle will be sampled a lot
//						// and last bin center will never be exactly sampled.
//						sum += 0.5*func.getY(i);
//					else
//						sum += func.getY(i);
//					xVals[i+1] = sum;
//					yVals[i+1] = func.getX(i);
//				}
//				sum += 0.5*func.getY(func.size()-1);
//				Preconditions.checkState((float)sum == 1f);
//				LightFixedXFunc cmlFunc = new LightFixedXFunc(xVals, yVals);
//				pdfs.put(sect.getSectionId(), cmlFunc);
			}
		} else {
			Preconditions.checkState(pdfs.size() == fullSects.size());
		}
	}
	
	public static void main(String[] args) throws IOException {
		int numSamples = 100000;
		
		PRVI25_CrustalRandomlySampledDeformationModelLevel level = new PRVI25_CrustalRandomlySampledDeformationModelLevel(numSamples);
		List<PRVI25_CrustalRandomlySampledDeformationModels> nodes = level.getNodes();
		PRVI25_CrustalFaultModels fm = PRVI25_CrustalFaultModels.PRVI_CRUSTAL_FM_V1p1;
		List<? extends FaultSection> fullSects = fm.getFaultSections();
		
		List<MinMaxAveTracker> parentTracks = new ArrayList<>();
		for (int i=0; i<fullSects.size(); i++)
			parentTracks.add(new MinMaxAveTracker());
		for (PRVI25_CrustalRandomlySampledDeformationModels node : nodes) {
			Map<Integer, Double> slipSample = node.drawSectSlipRates(fullSects);
			for (int i=0; i<fullSects.size(); i++) {
				int id = fullSects.get(i).getSectionId();
				double slip = slipSample.get(id);
				parentTracks.get(i).addValue(slip);
			}
		}
		
		DecimalFormat slipDF = new DecimalFormat("0.000");
		DecimalFormat pDF = new DecimalFormat("0.00%");
		for (int i=0; i<fullSects.size(); i++) {
			FaultSection sect = fullSects.get(i);
			double origMean = sect.getOrigAveSlipRate();
			MinMaxAveTracker track = parentTracks.get(i);
			double sampleMean = track.getAverage();
			System.out.println(sect.getSectionId()+". "+sect.getSectionName()+":\torigMean="+
					slipDF.format(origMean)+"\tsampleMean="+slipDF.format(sampleMean)
					+"\tpDiff="+pDF.format((sampleMean - origMean)/origMean)
					+"\trange=["+slipDF.format(track.getMin())+","+slipDF.format(track.getMax())+"]");
		}
	}

}
