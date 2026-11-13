package org.opensha.sha.simulators.stiffness;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipException;

import org.dom4j.DocumentException;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.Range;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.utm.UTM;
import org.opensha.commons.gui.plot.GraphWidget;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.cpt.CPTVal;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.StiffnessAggregation;
import org.opensha.sha.simulators.stiffness.StiffnessCalc.Patch;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import scratch.UCERF3.utils.U3FaultSystemIO;

/**
 * This class calculates stiffness aggregated between fault subsections. First, each subsection is divided
 * up into a number of small square patches (specified via the gridSpacing parameter in km, 2 km seems to
 * be a good tradeoff between calculation accuracy and speed). Then, to calculate stiffness between
 * subsections, stiffness is calculated between each combination of patches between the two subsections.
 * An aggregate measure is then used to pick a single value from the set of all patch stiffness calculations,
 * e.g., the median or sum (see StiffnessAggregationMethod).
 * 
 * @author kevin
 *
 */
public class SubSectStiffnessCalculator {
	
	private List<? extends FaultSection> subSects;
	private Map<Integer, Integer> idToIndexMap = null;
	
	private int utmZone;
	private char utmChar;
	
	public static final PatchAlignment alignment_default = PatchAlignment.FILL_OVERLAP;
	private PatchAlignment alignment = alignment_default;
	
	private transient Map<FaultSection, List<PatchLocation>> patchesMap;
	
	private transient double[][] selfStiffnessCache;
	
	private transient AggregatedStiffnessCache[] caches;
	
	public static class PatchLocation {
		public final Patch patch;
		public final Location center;
		public final Location[] corners;
		
		public PatchLocation(Patch patch, Location center, Location[] corners) {
			this.patch = patch;
			this.center = center;
			this.corners = corners;
		}
	}
	
	/** Geometry interpretation used to construct the fixed-size patches. */
	public enum PatchGenerationStrategy {
		STIRLING,
		GENERIC
	}

	private final PatchGenerationStrategy patchGenerationStrategy;

	public enum PatchAlignment {
		CENTER,
		FILL_OVERLAP
	}

	private double gridSpacing;
	private double lameLambda;
	private double lameMu;
	private double coeffOfFriction;
	private double selfStiffnessCap;
	
	public enum StiffnessType {
		SIGMA("ΔSigma", "&Delta;Sigma", "MPa", 0),
		TAU("ΔTau", "&Delta;Tau", "MPa", 1),
		CFF("ΔCFF", "&Delta;CFF", "MPa", 2);
		
		private String name;
		private String html;
		private String units;
		
		private StiffnessType(String name, String html, String units, int arrayIndex) {
			this.name = name;
			this.html = html;
			this.units = units;
		}
		
		@Override
		public String toString() {
			return name+" ("+units+")";
		}
		
		public String getName() {
			return name;
		}
		
		public String getHTML() {
			return html;
		}
		
		public String getUnits() {
			return units;
		}
	}

	/**
	 * 
	 * @param subSects subsections list
	 * @param gridSpacing grid spacing used to divide subsections into square patches
	 * @param lameLambda Lame's first parameter (lambda) in MPa
	 * @param lameMu Lame's mu (shear modulus, mu) in MPa
	 * @param coeffOfFriction coefficient of friction for Coulomb calculations
	 */
	public SubSectStiffnessCalculator(List<? extends FaultSection> subSects, double gridSpacing,
			double lameLambda, double lameMu, double coeffOfFriction) {
		this(subSects, gridSpacing, lameLambda, lameMu, coeffOfFriction, alignment_default, 0d);
	}

	/**
	 * 
	 * @param subSects subsections list
	 * @param gridSpacing grid spacing used to divide subsections into square patches
	 * @param lameLambda Lame's first parameter (lambda) in MPa
	 * @param lameMu Lame's mu (shear modulus, mu) in MPa
	 * @param coeffOfFriction coefficient of friction for Coulomb calculations
	 * @param alignment patch alignment algorithm
	 * @param selfStiffnessCap self-stiffness CFF cap, applied as a multiple of a patch's self stiffness (bounded positive or negative)
	 */
	public SubSectStiffnessCalculator(List<? extends FaultSection> subSects, double gridSpacing,
			double lameLambda, double lameMu, double coeffOfFriction, PatchAlignment alignment,
			double selfStiffnessCap) {
		this(subSects, gridSpacing, lameLambda, lameMu, coeffOfFriction, alignment,
				selfStiffnessCap, PatchGenerationStrategy.STIRLING);
	}

	/** Constructs a calculator with an explicit surface interpretation strategy. */
	public SubSectStiffnessCalculator(List<? extends FaultSection> subSects, double gridSpacing,
			double lameLambda, double lameMu, double coeffOfFriction, PatchAlignment alignment,
			double selfStiffnessCap, PatchGenerationStrategy strategy) {
		Preconditions.checkArgument(Double.isFinite(gridSpacing) && gridSpacing > 0, "Invalid patch spacing: %s", gridSpacing);
		this.patchGenerationStrategy = Preconditions.checkNotNull(strategy);
		Preconditions.checkNotNull(alignment);
		this.subSects = subSects;
		this.gridSpacing = gridSpacing;
		this.lameLambda = lameLambda;
		this.lameMu = lameMu;
		this.coeffOfFriction = coeffOfFriction;
		this.alignment = alignment;
		this.selfStiffnessCap = selfStiffnessCap;
		
		MinMaxAveTracker latTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonTrack = new MinMaxAveTracker();
		for (FaultSection sect : subSects) {
			for (Location loc : sect.getFaultTrace()) {
				latTrack.addValue(loc.getLatitude());
				lonTrack.addValue(loc.getLongitude());
			}
		}
		
		double centerLat = latTrack.getMin() + 0.5*(latTrack.getMax() - latTrack.getMin());
		double centerLon = lonTrack.getMin() + 0.5*(lonTrack.getMax() - lonTrack.getMin());
		utmZone = UTM.calcZone(centerLon);
		utmChar = UTM.calcLetter(centerLat);
//		System.out.println("UTM zone: "+utmZone+" "+utmChar);
		
		boolean indexed = true;
		for (int i=0; indexed&&i<subSects.size(); i++)
			indexed &= subSects.get(i).getSectionId() == i;
		if (!indexed) {
			// build mappings
			idToIndexMap = new HashMap<>(subSects.size());
			for (int i=0; i<subSects.size(); i++) {
				FaultSection sect = subSects.get(i);
				int id = sect.getSectionId();
				Preconditions.checkState(!idToIndexMap.containsKey(id),
						"Duplicate id=%s; 2nd encountered was '%s'", id, sect);
				idToIndexMap.put(id, i);
			}
		}
	}
	
	int index(int sectID) {
		return idToIndexMap == null ? sectID : idToIndexMap.get(sectID);
	}
	
	int index(FaultSection sect) {
		return idToIndexMap == null ? sect.getSectionId() : idToIndexMap.get(sect.getSectionId());
	}
	
	public synchronized void setPatchAlignment(PatchAlignment alignment) {
		if (this.alignment != alignment) {
			Preconditions.checkNotNull(alignment);
			patchesMap = null;
			this.alignment = alignment;
			clearCaches();
		}
	}
	
	public List<? extends FaultSection> getSubSects() {
		return subSects;
	}
	
	public List<PatchLocation> getPatches(FaultSection sect) {
		checkInitPatches();
		return patchesMap.get(sect);
	}
	
	/**
	 * Builds patches for the given section with the current patch settings and without regard to any caching
	 * @param sect
	 * @return
	 */
	public List<PatchLocation> buildPatches(FaultSection sect) {
		SubSectPatchBuilder builder = patchGenerationStrategy == PatchGenerationStrategy.STIRLING
				? new StirlingSubSectPatchBuilder(gridSpacing, utmZone, utmChar, isApplyAseisReduction())
				: new GenericSubSectPatchBuilder(gridSpacing, utmZone, utmChar, isApplyAseisReduction());
		return builder.buildPatches(sect, alignment);
	}

	public boolean isApplyAseisReduction() {
		return false;
	}

	public PatchGenerationStrategy getPatchGenerationStrategy() {
		return patchGenerationStrategy;
	}

	/** Fingerprint of the actual ordered patches, including mechanisms and Cartesian frame. */
	String getPatchGeometryFingerprint() {
		checkInitPatches();
		Hasher hash = Hashing.sha256().newHasher();
		hash.putInt(utmZone).putChar(utmChar).putBoolean(isApplyAseisReduction());
		for (FaultSection section : subSects) {
			List<PatchLocation> patches = patchesMap.get(section);
			hash.putInt(section.getSectionId()).putInt(patches.size());
			for (PatchLocation location : patches) {
				Patch patch = location.patch;
				for (double value : patch.center) hash.putDouble(value);
				hash.putDouble(patch.L).putDouble(patch.W).putDouble(patch.strike)
					.putDouble(patch.dip).putDouble(patch.rake);
			}
		}
		return hash.hash().toString();
	}


	private void checkInitPatches() {
		if (patchesMap == null) {
			synchronized (this) {
				if (patchesMap != null)
					return;
				System.out.println("Building source patches...");
				Map<FaultSection, List<PatchLocation>> patchesMap = new HashMap<>();
				MinMaxAveTracker patchCountTrack = new MinMaxAveTracker();
				for (FaultSection sect : subSects) {
					List<PatchLocation> myPatches = buildPatches(sect);
					Preconditions.checkState(myPatches.size() > 0, "must have at least 1 patch");
					patchCountTrack.addValue(myPatches.size());
//					System.out.println(sect.getSectionName()+" has "+myPatches.size()
//						+" patches, aveStrike="+sect.getFaultTrace().getAveStrike());
//					System.out.println("\tStrike range: "+strikeTrack);
//					System.out.println("\tLength range: "+lenTrack);
//					System.out.println("\tWidth range: "+widthTrack);
					patchesMap.put(sect, myPatches);
				}
				System.out.println("Patch stats: "+patchCountTrack);
				this.patchesMap = patchesMap;
			}
		}
	}
	
	/**
	 * Calculates stiffness between the given sub sections, returning the full distribution without any caching
	 * 
	 * @param sourceSect
	 * @param receiverSect
	 * @return stiffness
	 */
	public StiffnessDistribution calcStiffnessDistribution(FaultSection sourceSect, FaultSection receiverSect) {
		return calcStiffnessDistribution(sourceSect.getSectionId(), receiverSect.getSectionId());
	}
	
	/**
	 * Calculates stiffness between the given sub sections, returning the full distribution without any caching
	 * 
	 * @param sourceID
	 * @param receiverID
	 * @return stiffness distribution
	 */
	public StiffnessDistribution calcStiffnessDistribution(int sourceID, int receiverID) {
		checkInitPatches();
		int sIndex = index(sourceID);
		int rIndex = index(receiverID);
		List<PatchLocation> sourcePatches = patchesMap.get(subSects.get(sIndex));
		List<PatchLocation> receiverPatches = patchesMap.get(subSects.get(rIndex));
		
		double[] selfStiffness = null;
		if (selfStiffnessCap > 0)
			selfStiffness = getSelfStiffness(rIndex, receiverPatches);
		
		return calcStiffnessDistribution(sourcePatches, receiverPatches, selfStiffness);
	}
	
	public StiffnessDistribution calcStiffnessDistribution(List<PatchLocation> sourcePatches,
			List<PatchLocation> receiverPatches, double[] receiverSelfStiffnes) {
		double[][][] values = new double[StiffnessType.values().length][receiverPatches.size()][sourcePatches.size()];

		for (int r=0; r<receiverPatches.size(); r++) {
			PatchLocation receiver = receiverPatches.get(r);
			double cap = Double.NaN;
			if (receiverSelfStiffnes != null)
				cap = Math.abs(receiverSelfStiffnes[r])*selfStiffnessCap;
			for (int s=0; s<sourcePatches.size(); s++) {
				PatchLocation source = sourcePatches.get(s);
				double[] stiffness = StiffnessCalc.calcStiffness(
						lameLambda, lameMu, source.patch, receiver.patch);
				double sigma, tau, cff;
				if (stiffness == null) {
					sigma = Double.NaN;
					tau = Double.NaN;
					cff = Double.NaN;
				} else {
					sigma = stiffness[0];
					tau = stiffness[1];
					cff = StiffnessCalc.calcCoulombStress(tau, sigma, coeffOfFriction);
					if (receiverSelfStiffnes != null) {
						if (cff > cap)
							cff = cap;
						else if (cff < -cap)
							cff = -cap;
					}
				}
				values[StiffnessType.SIGMA.ordinal()][r][s] = sigma;
				values[StiffnessType.TAU.ordinal()][r][s] = tau;
				values[StiffnessType.CFF.ordinal()][r][s] = cff;
			}
		}
		
		return new StiffnessDistribution(sourcePatches, receiverPatches, values);
	}
	
	private double[] getSelfStiffness(int sectIndex, List<PatchLocation> receiverPatches) {
		if (selfStiffnessCache == null) {
			synchronized (this) {
				if (selfStiffnessCache == null)
					selfStiffnessCache = new double[subSects.size()][];
			}
		}
		if (selfStiffnessCache[sectIndex] == null) {
			// calculate it
			selfStiffnessCache[sectIndex] = calcSelfStiffness(receiverPatches);
		}
		return selfStiffnessCache[sectIndex];
	}
	
	public double[] calcSelfStiffness(List<PatchLocation> patches) {
		double[] values = new double[patches.size()];
		for (int r=0; r<values.length; r++) {
			PatchLocation receiver = patches.get(r);
			double[] stiffness = StiffnessCalc.calcStiffness(
					lameLambda, lameMu, receiver.patch, receiver.patch);
			values[r] = StiffnessCalc.calcCoulombStress(stiffness[1], stiffness[0], coeffOfFriction);
		}
		return values;
	}
	
	public static class LogDistributionPlot {
		public final PlotSpec negativeSpec;
		public final PlotSpec positiveSpec;
		public final Range xRange;
		public final Range yRange;
		
		public LogDistributionPlot(PlotSpec negativeSpec, PlotSpec positiveSpec, Range xRange, Range yRange) {
			this.negativeSpec = negativeSpec;
			this.positiveSpec = positiveSpec;
			this.xRange = xRange;
			this.yRange = yRange;
		}
		
		public void plotInGW(GraphWindow gw) {
			GraphWidget widget = gw.getGraphWidget();
			List<PlotSpec> specs = Lists.newArrayList(negativeSpec, positiveSpec);
			List<Range> xRanges = Lists.newArrayList(xRange, xRange);
			List<Range> yRanges = Lists.newArrayList(yRange);
			widget.getGraphPanel().setxAxisInverteds(new boolean[] { true, false });
			widget.setMultiplePlotSpecs(specs, xRanges, yRanges);
			widget.setX_Log(true);
			gw.setVisible(true);
		}
	}
	
	public LogDistributionPlot plotDistributionHistograms(StiffnessDistribution dist, StiffnessType type,
			int sourceID, int receiverID) {
		double[][] vals = dist.get(type);
		
		int totSize = 0;
		for (double[] sub : vals)
			totSize += sub.length;
		
		double[] flattened = new double[totSize];
		int index = 0;
		for (double[] sub : vals) {
			System.arraycopy(sub, 0, flattened, index, sub.length);
			index += sub.length;
		}
		
		StiffnessAggregation result = new StiffnessAggregation(flattened, flattened.length);
		
		double maxAbsVal = Math.abs(result.get(AggregationMethod.MIN));
		maxAbsVal = Math.max(maxAbsVal, Math.abs(result.get(AggregationMethod.MAX)));
		if (maxAbsVal > 10d)
			maxAbsVal = 10d*Math.ceil(maxAbsVal/2d);
		else
			maxAbsVal = Math.ceil(maxAbsVal);
		double logMax = Math.max(Math.ceil(Math.log10(maxAbsVal)),
				// allow it to go up to 1e2 to capture the sum (but don't if unnecessary)
				Math.min(2d, Math.ceil(Math.log10(Math.abs(result.get(AggregationMethod.SUM))))));
		double logMin = -1;
		for (double[] inner : vals) {
			for (double val : inner) {
				double abs = Math.abs(val);
				if (val > 0)
					logMin = Math.min(logMin, Math.log10(abs));
			}
		}
		logMin = Math.max(-8, Math.floor(logMin));
		
		double logDelta = 0.1;
		
		HistogramFunction posLogVals = HistogramFunction.getEncompassingHistogram(
				logMin, logMax, logDelta);
		HistogramFunction negLogVals = new HistogramFunction(
				posLogVals.getMinX(), posLogVals.getMaxX(), posLogVals.size());
		
		for (double[] innerVals : vals) {
			for (double val : innerVals) {
				if (val > 0d) {
					int ind = posLogVals.getClosestXIndex(Math.log10(val));
					posLogVals.add(ind, 1d);
				} else if (val == 0d) {
					posLogVals.add(0, 1d);
				} else {
					int ind = negLogVals.getClosestXIndex(Math.log10(-val));
					negLogVals.add(ind, 1d);
				}
			}
		}
		
		double maxY = Math.max(negLogVals.getMaxY(), posLogVals.getMaxY());
		maxY *= 1.1;
		
		Range xRange = new Range(Math.pow(10, logMin), Math.pow(10, logMax));
		Range yRange = new Range(0d, maxY);
		
		List<PlotSpec> specs = new ArrayList<>();
		
		double annOuterX = Math.pow(10, logMin + 0.95*(logMax - logMin));
		double annInnerX = Math.pow(10, logMin + 0.05*(logMax - logMin));
		double annY1 = 0.94*maxY;
		double annY2 = 0.88*maxY;
		double maxVertY = 0.87*maxY;
		PlotSpec negativeSpec = null;
		PlotSpec positiveSpec = null;
		
		DecimalFormat pDF = new DecimalFormat("0.0%");
		for (boolean positive : new boolean[] {false, true}) {
			ArbitrarilyDiscretizedFunc hist = new ArbitrarilyDiscretizedFunc();
			if (positive)
				for (Point2D pt : posLogVals)
					hist.set(Math.pow(10, pt.getX()), pt.getY());
			else
				for (Point2D pt : negLogVals)
					hist.set(Math.pow(10, pt.getX()), pt.getY());
			
			List<XY_DataSet> funcs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars = new ArrayList<>();
			
			funcs.add(hist);
			chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.GRAY));
			
			List<XYTextAnnotation> anns = new ArrayList<>();
			Font annFont = new Font(Font.SANS_SERIF, Font.BOLD, 18);
			
			String percentText;
			if (positive)
				percentText = pDF.format(result.get(AggregationMethod.FRACT_POSITIVE))+"≥0";
			else
				percentText = pDF.format(1d-result.get(AggregationMethod.FRACT_POSITIVE))+"<0";
			XYTextAnnotation percentAnn = new XYTextAnnotation(percentText, annInnerX, annY1);
			if (positive)
				percentAnn.setTextAnchor(TextAnchor.BOTTOM_LEFT);
			else
				percentAnn.setTextAnchor(TextAnchor.BOTTOM_RIGHT);
			percentAnn.setFont(annFont);
			percentAnn.setPaint(Color.BLACK);
			anns.add(percentAnn);
			
			if (!positive) {
				XYTextAnnotation meanAnn = new XYTextAnnotation("mean="+getValStr(result.get(AggregationMethod.MEAN)),
						annOuterX, annY1);
				meanAnn.setTextAnchor(TextAnchor.BOTTOM_LEFT);
				meanAnn.setFont(annFont);
				meanAnn.setPaint(Color.BLACK);
				anns.add(meanAnn);
			}
			if (positive == isPositive(result.get(AggregationMethod.MEAN))) {
				XY_DataSet meanXY = new DefaultXY_DataSet();
				meanXY.set(Math.abs(result.get(AggregationMethod.MEAN)), 0d);
				meanXY.set(Math.abs(result.get(AggregationMethod.MEAN)), maxVertY);
//				meanXY.setName("mean="+getValStr(result.mean));
				funcs.add(meanXY);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));
			}
			
			if (!positive) {
				XYTextAnnotation medianAnn = new XYTextAnnotation("mdn.="+getValStr(result.get(AggregationMethod.MEDIAN)),
						annOuterX, annY2);
				medianAnn.setTextAnchor(TextAnchor.BOTTOM_LEFT);
				medianAnn.setFont(annFont);
				medianAnn.setPaint(Color.BLUE);
				anns.add(medianAnn);
			}
			if (positive == isPositive(result.get(AggregationMethod.MEDIAN))) {
				XY_DataSet medianXY = new DefaultXY_DataSet();
				medianXY.set(Math.abs(result.get(AggregationMethod.MEDIAN)), 0d);
				medianXY.set(Math.abs(result.get(AggregationMethod.MEDIAN)), maxVertY);
//				medianXY.setName("mdn.="+getValStr(result.median));
				funcs.add(medianXY);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLUE));
			}
			
			if (positive) {
				XYTextAnnotation sumAnn = new XYTextAnnotation("sum="+getValStr(result.get(AggregationMethod.SUM)),
						annOuterX, annY2);
				sumAnn.setTextAnchor(TextAnchor.BOTTOM_RIGHT);
				sumAnn.setFont(annFont);
				sumAnn.setPaint(Color.RED);
				anns.add(sumAnn);
			}
			if (positive == isPositive(result.get(AggregationMethod.SUM))) {
				XY_DataSet sumXY = new DefaultXY_DataSet();
				sumXY.set(Math.abs(result.get(AggregationMethod.SUM)), 0d);
				sumXY.set(Math.abs(result.get(AggregationMethod.SUM)), maxVertY);
//				sumXY.setName("sum="+getValStr(result.sum));
				funcs.add(sumXY);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.RED));
			}

			if (positive) {
				XYTextAnnotation boundsAnn = new XYTextAnnotation(
						"["+getValStr(result.get(AggregationMethod.MIN))+","+getValStr(result.get(AggregationMethod.MAX))+"]",
						annOuterX, annY1);
				boundsAnn.setTextAnchor(TextAnchor.BOTTOM_RIGHT);
				boundsAnn.setFont(annFont);
				boundsAnn.setPaint(Color.DARK_GRAY);
				anns.add(boundsAnn);
			}
			if (positive == isPositive(result.get(AggregationMethod.MIN))) {
				XY_DataSet lowerXY = new DefaultXY_DataSet();
				lowerXY.set(Math.abs(result.get(AggregationMethod.MIN)), 0d);
				lowerXY.set(Math.abs(result.get(AggregationMethod.MIN)), maxVertY);
//				lowerXY.setName("bounds=["+getValStr(result.min)+","+getValStr(result.max)+"]");
				funcs.add(lowerXY);
				chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.DARK_GRAY));
			}

			if (positive == isPositive(result.get(AggregationMethod.MAX))) {
				XY_DataSet upperXY = new DefaultXY_DataSet();
				upperXY.set(Math.abs(result.get(AggregationMethod.MAX)), 0d);
				upperXY.set(Math.abs(result.get(AggregationMethod.MAX)), maxVertY);
				funcs.add(upperXY);
				chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.DARK_GRAY));
			}
			
			String title = type.name+" Distribution";
			if (sourceID >= 0 && receiverID >= 0)
				title += ", "+sourceID+" => "+receiverID;
			String xAxisLabel = type.name+" ("+type.units+")";
			if (!positive)
				xAxisLabel = "-"+xAxisLabel;
			PlotSpec spec = new PlotSpec(funcs, chars, title, xAxisLabel, "Count");
			if (positive)
				positiveSpec = spec;
			else
				negativeSpec = spec;
			spec.setLegendVisible(false);
			spec.setPlotAnnotations(anns);
			specs.add(spec);
		}
		
		return new LogDistributionPlot(negativeSpec, positiveSpec, xRange, yRange);
	}
	
	private boolean isPositive(double value) {
		return value >= 0d;
	}

	private static final DecimalFormat df1 = new DecimalFormat("0.0");
	private static final DecimalFormat df2 = new DecimalFormat("0.00");
	private static final DecimalFormat df3 = new DecimalFormat("0.000");
	private static final DecimalFormat dfE = new DecimalFormat("0.0E0");
	
	private static String getValStr(double val) {
		double abs = Math.abs(val);
		if (abs >= 1)
			return df1.format(val);
		if (abs >= 0.1)
			return df2.format(val);
		if (abs >= 0.01)
			return df3.format(val);
		return dfE.format(val).toLowerCase();
	}
	
	/**
	 * @return calculated UTM zone for the center of the fault region
	 */
	public int getUTMZone() {
		return utmZone;
	}

	/**
	 * @return calculated UTM letter for the center of the fault region
	 */
	public char getUTMLetter() {
		return utmChar;
	}

	/**
	 * @return grid spacing used to subdivide subsections into patches for stiffness calculations (km)
	 */
	public double getGridSpacing() {
		return gridSpacing;
	}

	/**
	 * @return Lame lambda (first parameter) MPa
	 */
	public double getLameLambda() {
		return lameLambda;
	}

	/**
	 * Lame mu (second parameter, shear modulus) in MPa
	 * @return
	 */
	public double getLameMu() {
		return lameMu;
	}
	
	/**
	 * @return coefficient of friction used in Coulomb stress change calculations
	 */
	public double getCoeffOfFriction() {
		return coeffOfFriction;
	}
	
	/**
	 * @return self stiffness cap. if >0, CFF values will be capped by this times the receiving patch's self-stiffness
	 */
	public double getSelfStiffnessCap() {
		return selfStiffnessCap;
	}
	
	/**
	 * @return alignment of patches across section surfaces
	 */
	public PatchAlignment getPatchAlignment() {
		return alignment;
	}
	
	public static class StiffnessDistribution {
		public final List<PatchLocation> sourcePatches;
		public final List<PatchLocation> receiverPatches;
		
		private final double[][][] values;
		
		private StiffnessDistribution(List<PatchLocation> sourcePatches, List<PatchLocation> receiverPatches,
				double[][][] values) {
			super();
			this.sourcePatches = sourcePatches;
			this.receiverPatches = receiverPatches;
			Preconditions.checkState(values.length == StiffnessType.values().length);
			this.values = values;
		}
		
		public StiffnessDistribution receiverAggregate(AggregationMethod method) {
			Preconditions.checkState(method.isTerminal());
			double[][][] agg = new double[values.length][][];
			for (int t=0; t<agg.length; t++) {
				if (values[t] == null)
					continue;
				agg[t] = new double[values[t].length][1];
				for (int r=0; r<agg[t].length; r++)
					agg[t][r] = new double[] { method.calculate(values[t][r]) };
			}
			
			return new StiffnessDistribution(null, receiverPatches, agg);
		}
		
		public StiffnessDistribution add(StiffnessDistribution o) {
			double[][][] combined = new double[values.length][][];
			for (int t=0; t<combined.length; t++) {
				if (values[t] == null || o.values[t] == null)
					continue;
				combined[t] = new double[values[t].length+o.values[t].length][];
				int ind = 0;
				for (double[] sourceVals : values[t])
					combined[t][ind++] = sourceVals;
				for (double[] sourceVals : o.values[t])
					combined[t][ind++] = sourceVals;
			}
			List<PatchLocation> newSourcePatches = null;
			if (this.sourcePatches != null && this.sourcePatches.equals(o.sourcePatches))
				newSourcePatches = sourcePatches;
			return new StiffnessDistribution(newSourcePatches, null, combined);
		}
		
		public double[][] get(StiffnessType type) {
			return values[type.ordinal()];
		}
	}
	
	public synchronized AggregatedStiffnessCache getAggregationCache(StiffnessType type) {
		if (caches == null)
			caches = new AggregatedStiffnessCache[StiffnessType.values().length];
		if (caches[type.ordinal()] == null)
			caches[type.ordinal()] = new AggregatedStiffnessCache(this, type);
		return caches[type.ordinal()];
	}
	
	public synchronized void clearCaches() {
		selfStiffnessCache = null;
		if (caches != null)
			for (AggregatedStiffnessCache cache : caches)
				if (cache != null)
					cache.clear();
	}
	
	public static CPT getLogCPT(double maxVal, boolean positive) {
		
		Color minColor, oneColor, maxColor;
		if (positive) {
			minColor = new Color(255, 220, 220);
			oneColor = new Color(255, 0, 0);
			maxColor = new Color(100, 0, 0);
		} else {
			minColor = new Color(220, 220, 255);
			oneColor = new Color(0, 0, 255);
			maxColor = new Color(0, 0, 100);
		}
		CPT cpt = new CPT(-4, 0, minColor, oneColor);
		cpt.add(new CPTVal(cpt.getMaxValue(), oneColor, (float)Math.log10(maxVal), maxColor));
		cpt.setBelowMinColor(Color.WHITE);
		cpt.setAboveMaxColor(maxColor);
		return cpt;
	}
	
	public static CPT getPreferredPosNegCPT() {
		return getPreferredPosNegCPT(10d);
	}
	
	public static CPT getPreferredPosNegCPT(double maxVal) {
		CPT posLogCPT = getLogCPT(maxVal, true);
		CPT negLogCPT = getLogCPT(maxVal, false);
		EvenlyDiscretizedFunc logDiscr = new EvenlyDiscretizedFunc(posLogCPT.getMinValue(), posLogCPT.getMaxValue(), 100);
		CPT cpt = new CPT();
		for (int i=logDiscr.size(); --i>0;) {
			double x1 = logDiscr.getX(i); // abs larger value, neg smaller
			double x2 = logDiscr.getX(i-1); // abs smaller value, neg larger
			Color c1 = negLogCPT.getColor((float)x1);
			Color c2 = negLogCPT.getColor((float)x2);
			cpt.add(new CPTVal(-(float)Math.pow(10, x1), c1, -(float)Math.pow(10, x2), c2));
		}
		cpt.add(new CPTVal(cpt.getMaxValue(), cpt.getMaxColor(), 0f, Color.WHITE));
		cpt.add(new CPTVal(0f, Color.WHITE, (float)Math.pow(10, posLogCPT.getMinValue()),
				posLogCPT.getMinColor()));
		for (int i=0; i<logDiscr.size()-1; i++) {
			double x1 = logDiscr.getX(i);
			double x2 = logDiscr.getX(i+1);
			Color c1 = posLogCPT.getColor((float)x1);
			Color c2 = posLogCPT.getColor((float)x2);
			cpt.add(new CPTVal((float)Math.pow(10, x1), c1, (float)Math.pow(10, x2), c2));
		}
		cpt.setBelowMinColor(cpt.getMinColor());
		cpt.setAboveMaxColor(cpt.getMaxColor());
		return cpt;
	}
	
	public static void main(String[] args) throws ZipException, IOException, DocumentException {
//		System.out.println(Joiner.on(",").join(calcFullOverlapCenters(7, 2)));
//		System.out.println(Joiner.on(",").join(calcFullOverlapCenters(5.5, 2)));
//		System.out.println(Joiner.on(",").join(calcFullOverlapCenters(5, 2)));
//		System.out.println(Joiner.on(",").join(calcFullOverlapCenters(5, 1)));
//		System.exit(0);
		File fssFile = new File("/home/kevin/Simulators/catalogs/rundir4983_stitched/fss/"
				+ "rsqsim_sol_m6.5_skip5000_sectArea0.2.zip");
		FaultSystemRupSet rupSet = U3FaultSystemIO.loadRupSet(fssFile);
		double lambda = 30000;
		double mu = 30000;
		double coeffOfFriction = 0.5;
//		SubSectStiffnessCalculator calc = new SubSectStiffnessCalculator(
//				rupSet.getFaultSectionDataList(), 2d, lambda, mu, coeffOfFriction);
		SubSectStiffnessCalculator calc = new SubSectStiffnessCalculator(
				rupSet.getFaultSectionDataList(), 1d, lambda, mu, coeffOfFriction);
		calc.setPatchAlignment(PatchAlignment.FILL_OVERLAP);
		
		System.out.println(calc.utmZone+" "+calc.utmChar);

//		FaultSection[] sects = {
////				calc.subSects.get(1836), calc.subSects.get(1837),
//				calc.subSects.get(622), calc.subSects.get(1778),
////				calc.subSects.get(625), calc.subSects.get(1772),
////				calc.subSects.get(771), calc.subSects.get(1811)
//		};
//		
//		StiffnessType type = StiffnessType.CFF;
//		
//		List<AggregatedStiffnessCalculator> aggregators = new ArrayList<>();
//		
//		aggregators.add(AggregatedStiffnessCalculator.builder(type, calc)
//				.receiverPatchAgg(AggregationMethod.SUM).sectToSectAgg(AggregationMethod.SUM).get());
//		aggregators.add(AggregatedStiffnessCalculator.builder(type, calc)
//				.receiverPatchAgg(AggregationMethod.SUM).sectToSectAgg(AggregationMethod.FRACT_POSITIVE).get());
//		aggregators.add(AggregatedStiffnessCalculator.builder(type, calc)
//				.sectToSectAgg(AggregationMethod.FRACT_POSITIVE).get());
//		
//		for (int r=0; r<sects.length; r++) {
//			for (int s=0; s<sects.length; s++) {
//				if (r == s)
//					continue;
//				FaultSection source = sects[s];
//				FaultSection receiver = sects[r];
//				System.out.println("Source: "+source.getSectionName());
//				System.out.println("Receiver: "+receiver.getSectionName());
//				
//				for (AggregatedStiffnessCalculator aggCalc : aggregators)
//					System.out.println("\t"+aggCalc+": "+aggCalc.calc(source, receiver));
//				
////				StiffnessDistribution dist = calc.calcStiffnessDistribution(sects[i], sects[j]);
////				
////				GraphWidget graph = new GraphWidget();
////				graph.getPlotPrefs().setBackgroundColor(Color.WHITE);
////				GraphWindow gw = new GraphWindow(graph);
////				gw.setDefaultCloseOperation(GraphWindow.EXIT_ON_CLOSE);
////				LogDistributionPlot plot = calc.plotDistributionHistograms(dist, StiffnessType.CFF);
////				plot.plotInGW(gw);
//			}
//		}
		
//		AggregatedStiffnessCalculator aggCalc = new AggregatedStiffnessCalculator(StiffnessType.CFF, calc, true,
//				AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM);
//		List<? extends FaultSection> subSects = calc.getSubSects();
//		List<FaultSection> sources = Lists.newArrayList(
//				subSects.get(516),
//				subSects.get(515),
//				subSects.get(514),
//				subSects.get(513),
//				subSects.get(512),
//				subSects.get(511),
//				subSects.get(522)
//		);
//		List<FaultSection> receivers = Lists.newArrayList(
//				subSects.get(521)
//				
//		);
//		
//		System.out.println(aggCalc.calc(sources, receivers));
		
		AggregatedStiffnessCalculator aggCalc = new AggregatedStiffnessCalculator(StiffnessType.CFF, calc, false,
//				AggregationMethod.MAX, AggregationMethod.MAX);
				AggregationMethod.SUM, AggregationMethod.SUM);
		List<? extends FaultSection> subSects = calc.getSubSects();
		FaultSection source = subSects.get(1398);
		List<FaultSection> receivers = Lists.newArrayList(
				subSects.get(1399),
				subSects.get(324),
				subSects.get(1979)
		);
		
		for (FaultSection receiver : receivers) {
			System.out.println(source.getSectionId()+"=>"+receiver.getSectionId()+": "+aggCalc.calc(source, receiver));
		}
		
		
//		calc.writeCacheFile(new File("/tmp/stiffness_cache_test.csv"), StiffnessType.CFF);
//		File rupSetsDir = new File("/home/kevin/OpenSHA/UCERF4/rup_sets");
//		File rupSetFile = new File(rupSetsDir, "fm3_1_ucerf3.zip");
//		List<? extends FaultSection> subSects = FaultSystemIO.loadRupSet(rupSetFile).getFaultSectionDataList();
//		SubSectStiffnessCalculator calc = new SubSectStiffnessCalculator(
//				subSects, 2d, 3e4, 3e4, 0.5);
//		StiffnessType type = StiffnessType.CFF;
//		calc.loadCacheFile(new File(rupSetsDir, "cff_cache_2606sects_2km_lambda30000_mu30000_coeff0.5.csv"), type);
//		int numMedianPositive = 0;
//		int numSumPositive = 0;
//		int totalNum = 0;
//		int numMedPosSumNeg = 0;
//		int numSumPosMedNeg = 0;
//		for (int i=0; i<subSects.size(); i++) {
//			for (int j=0; j<subSects.size(); j++) {
//				if (i == j)
//					continue;
//				if (calc.cache[i] == null || calc.cache[i][j] == null)
//					continue;
//				StiffnessResult result = calc.cache[i][j][type.arrayIndex];
//				if (result.median > 0)
//					numMedianPositive++;
//				if (result.sum > 0)
//					numSumPositive++;
//				if (result.median > 0 && result.sum < 0)
//					numMedPosSumNeg++;
//				if (result.median < 0 && result.sum > 0)
//					numSumPosMedNeg++;
//				totalNum++;
//			}
//		}
//		System.out.println("Median positive:\t"+getFractStr(numMedianPositive, totalNum));
//		System.out.println("Sum positive:\t"+getFractStr(numSumPositive, totalNum));
//		System.out.println("Median positive, sum negative:\t"+getFractStr(numMedPosSumNeg, totalNum));
//		System.out.println("Sum positive, median negative:\t"+getFractStr(numSumPosMedNeg, totalNum));
	}
	
	private static DecimalFormat pDF = new DecimalFormat("0.0%");
	private static String getFractStr(int num, int total) {
		return pDF.format((double)num/(double)total)+"\t("+num+"/"+total+")";
	}
	
}
