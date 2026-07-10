package gov.usgs.earthquake.nshmp.erf.nshm27.logicTree;

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.apache.commons.numbers.core.Precision;
import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.EvenlyDiscrFuncContinuousDistribution;
import org.opensha.commons.data.function.EvenlyDiscrFuncContinuousDistribution.DiscretizationType;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.dmSampling.DeformationModelDistSampler;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.dmSampling.DeformationModelDistSampler.AverageSampler;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.dmSampling.DeformationModelDistSampler.FixedFractileSampler;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.dmSampling.DeformationModelDistSampler.FixedSampler;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.dmSampling.DeformationModelDistSampler.GroupedFractileSampler;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.dmSampling.DeformationModelDistSampler.SectionGroupingType;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.dmSampling.RupSetDeformationModelDistribution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_CrustalDeformationModels;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME) // if rate balancing enabled
//@DoesNotAffect(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME) // if rate balancing disabled
public class NSHM27_CrustalDeformationModels extends RupSetDeformationModelDistribution.Simple {
	
	private static final String PATH = "/data/erf/nshm27/gnmi/deformation_models/crustal/2026_03_02";
	private static Map<Integer, ContinuousDistribution> dists = null;
	
	public static class SamplingLevel extends RupSetDeformationModelDistribution.GroupedSamplingLevel<NSHM27_CrustalDeformationModels> {
		
		public static String NAME = "Crustal Deformation Model Sample";
		public static String SHORT_NAME = "DMSample";

		public SamplingLevel() {
			super(NAME, SHORT_NAME, SectionGroupingType.PARENT);
		}

		@Override
		public NSHM27_CrustalDeformationModels build(GroupedFractileSampler value,
				double weight, String name, String shortName, String filePrefix) {
			return new NSHM27_CrustalDeformationModels(name, shortName, filePrefix, weight, value);
		}

		@Override
		public Class<? extends NSHM27_CrustalDeformationModels> getType() {
			return NSHM27_CrustalDeformationModels.class;
		}
		
	}
	
	@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
	@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
	@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
	@Affects(FaultSystemSolution.RATES_FILE_NAME)
	@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
	@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
	@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME) // if rate balancing enabled
	//@DoesNotAffect(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME) // if rate balancing disabled
	public static enum Aggregated implements RupSetDeformationModel, FixedWeightNode {
		UPPER("Upper Bounds", "Upper", new FixedFractileSampler(1d)),
		AVERAGE("Average", "Average", new AverageSampler()),
		LOWER("Lower Bounds", "Lower", new FixedFractileSampler(0d));
		
		private String name;
		private String shortName;
		private FixedSampler sampler;

		private Aggregated(String name, String shortName,
				FixedSampler sampler) {
			this.name = name;
			this.shortName = shortName;
			this.sampler = sampler;
		}

		@Override
		public double getNodeWeight() {
			return 0; // only for plots
		}

		@Override
		public String getFilePrefix() {
			return name();
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
		public boolean isApplicableTo(RupSetFaultModel faultModel) {
			return faultModel instanceof NSHM27_CrustalFaultModels;
		}

		@Override
		public List<? extends FaultSection> apply(RupSetFaultModel faultModel,
				LogicTreeBranch<? extends LogicTreeNode> branch, List<? extends FaultSection> fullSects,
				List<? extends FaultSection> subSects) throws IOException {
			return new NSHM27_CrustalDeformationModels().apply(branch, fullSects, subSects, sampler);
		}

	}

	@SuppressWarnings("unused") // for deserialization
	private NSHM27_CrustalDeformationModels() {}
	
	NSHM27_CrustalDeformationModels(String name, String shortName, String filePrefix,
			double weight, DeformationModelDistSampler sampler) {
		super(name, shortName, filePrefix, weight, sampler);
	}

	@Override
	public boolean isApplicableTo(RupSetFaultModel faultModel) {
		return faultModel instanceof NSHM27_CrustalFaultModels;
	}
	
	public void initDistributions(LogicTreeBranch<? extends LogicTreeNode> branch,
			List<? extends FaultSection> fullSects, List<? extends FaultSection> subSects) throws IOException {
		checkLoadPDFs(fullSects);
	}

	@Override
	public ContinuousDistribution getSlipRateDistribution(FaultSection subSect) {
		return dists.get(subSect.getParentSectionId());
	}

	@Override
	public UnaryOperator<List<? extends FaultSection>> getPostProcessor() {
		return PRVI25_CrustalDeformationModels.DEFAULTS;
	}
	
	private synchronized static void checkLoadPDFs(List<? extends FaultSection> fullSects) throws IOException {
		if (dists == null) {
			dists = new HashMap<>(fullSects.size());
			for (FaultSection sect : fullSects) {
//				System.out.println("Loading slip PDF for "+sect.getSectionName());
				String fName = sect.getSectionName()+".csv";
				String path = PATH+"/"+fName;
				InputStream is = NSHM27_CrustalDeformationModels.class.getResourceAsStream(path);
				Preconditions.checkNotNull(is, "%s was not found", path);
				CSVFile<String> csv = CSVFile.readStream(is, true);
				List<Double> xVals = new ArrayList<>(csv.getNumRows());
				List<Double> yVals = new ArrayList<>(csv.getNumRows());
				for (int row=0; row<csv.getNumRows(); row++) {
					double x = csv.getDouble(row, 0);
					double y = csv.getDouble(row, 1);
					if (row > 0)
						Preconditions.checkState(x > xVals.get(row-1));
					Preconditions.checkState(x >= 0d, "Bad x-value for %s row %s: %s", fName, row, x);
					Preconditions.checkState(y >= 0d, "Bad y-value for %s row %s: %s", fName, row, y);
					xVals.add(x);
					yVals.add(y);
				}
				Preconditions.checkState(xVals.size() > 1, "Need at least 2 values to form a distribution");
				EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(xVals.get(0), xVals.get(xVals.size()-1), xVals.size());
				for (int i=0; i<func.size(); i++) {
					double funcX = func.getX(i);
					double fileX = xVals.get(i);
					Preconditions.checkState(Precision.equals(funcX, fileX, 1e-6),
							"Supplied distribution (%s) is not evenly discretized; expected x=%s at value %s, encountered x=%s",
							fName, funcX, i, fileX);
					func.set(i, yVals.get(i));
				}
				// normalize
				func.scale(1d/func.calcSumOfY_Vals());
				dists.put(sect.getSectionId(), new EvenlyDiscrFuncContinuousDistribution(func, DiscretizationType.INTERPOLATE));
			}
		} else {
			Preconditions.checkState(dists.size() == fullSects.size());
		}
	}
	
//	public static void main(String[] args) throws IOException {
//		int numSamples = 750;
//		long randSeed = 12345678l;
//		Random rand = new Random(randSeed);
//		
//		NSHM27_CrustalDeformationModels.SamplingLevel level = new NSHM27_CrustalDeformationModels.SamplingLevel();
//		level.build(randSeed, numSamples);
//		List<? extends NSHM27_CrustalDeformationModels> nodes = level.getNodes();
//		NSHM27_CrustalFaultModels fm = NSHM27_CrustalFaultModels.GNMI_V1;
//		List<? extends FaultSection> fullSects = fm.getFaultSections();
//		
//		List<MinMaxAveTracker> parentTracks = new ArrayList<>();
//		for (int i=0; i<fullSects.size(); i++)
//			parentTracks.add(new MinMaxAveTracker());
//		for (NSHM27_CrustalDeformationModels node : nodes) {
//			Map<Integer, Double> slipSample = node.drawSectSlipRates(fullSects);
//			for (int i=0; i<fullSects.size(); i++) {
//				int id = fullSects.get(i).getSectionId();
//				double slip = slipSample.get(id);
//				parentTracks.get(i).addValue(slip);
//			}
//		}
//		
//		DecimalFormat slipDF = new DecimalFormat("0.000");
//		for (int i=0; i<fullSects.size(); i++) {
//			FaultSection sect = fullSects.get(i);
//			double origMean = sect.getOrigAveSlipRate();
//			double origUpper = ((GeoJSONFaultSection)sect).getProperties().getDouble(NSHM27_CrustalFaultModels.HIGH_RATE_PROP_NAME, Double.NaN);
//			double origLower = ((GeoJSONFaultSection)sect).getProperties().getDouble(NSHM27_CrustalFaultModels.LOW_RATE_PROP_NAME, Double.NaN);
//			DiscretizedFunc pdf = pdfs.get(sect.getSectionId());
//			MinMaxAveTracker track = parentTracks.get(i);
//			double sampleMean = track.getAverage();
//			
//			double pdfMean = 0d;
//			double pdfMin = Double.POSITIVE_INFINITY;
//			double pdfMax = Double.NEGATIVE_INFINITY;
//			for (Point2D pt : pdf) {
//				if (pt.getY() > 0d) {
//					pdfMean += pt.getX() * pt.getY();
//					pdfMin = Math.min(pdfMin, pt.getX());
//					pdfMax = Math.max(pdfMax, pt.getX());
//				}
//			}
//			
//			FaultSection unprojectedSect = fullSectsUnprojected.get(i);
//			double unprojectedMean = unprojectedSect.getOrigAveSlipRate();
//			double unprojectedUpper = ((GeoJSONFaultSection)unprojectedSect).getProperties().getDouble(PRVI25_CrustalFaultModels.HIGH_RATE_PROP_NAME, Double.NaN);
//			double unprojectedLower = ((GeoJSONFaultSection)unprojectedSect).getProperties().getDouble(PRVI25_CrustalFaultModels.LOW_RATE_PROP_NAME, Double.NaN);
//			boolean projected = (float)unprojectedMean != (float)origMean;
//			
//			System.out.println(sect.getSectionId()+". "+sect.getSectionName()+"\t(dip="+(float)sect.getAveDip()+", rake="+(float)sect.getAveRake()+")");
//			if ((float)unprojectedMean != (float)origMean) {
//				System.out.println("\tOriginal mean and range:\t"+slipDF.format(unprojectedMean)
//						+"\t["+slipDF.format(unprojectedLower)+","+slipDF.format(unprojectedUpper)+"]");
//			}
//			System.out.println("\t"+(projected ? "Projected" : "Original")+" mean and range:\t"+slipDF.format(origMean)
//					+"\t["+slipDF.format(origLower)+","+slipDF.format(origUpper)
//					+"]\tmeanFract="+slipDF.format((origMean - origLower)/(origUpper - origLower)));
////			System.out.println("\tSampled mean and range:\t"+slipDF.format(sampleMean)
////					+"\t["+slipDF.format(track.getMin())+","+slipDF.format(track.getMax())+"]");
////			System.out.println("\tCompared to original:\t"+compPercentStr(sampleMean, origMean)
////					+"\t["+compPercentStr(track.getMin(), origLower)+","+compPercentStr(track.getMax(), origUpper)+"]");
//			System.out.println("\tGeo DM PDF mean and range:\t"+slipDF.format(pdfMean)
//					+"\t["+slipDF.format(pdfMin)+","+slipDF.format(pdfMax)+"]");
//			System.out.println("\tCompared to "+(projected ? "projected" : "original")+":\t"+compPercentStr(sampleMean, origMean)
//					+"\t["+compPercentStr(pdfMin, origLower)+","+compPercentStr(pdfMax, origUpper)+"]");
//		}
//	}
	
	private static final DecimalFormat pDF = new DecimalFormat("0.00%");
	
	private static String compPercentStr(double testVal, double refVal) {
		String plus = testVal > refVal ? "+" : "";
		return plus+pDF.format((testVal-refVal)/refVal);
	}

}
