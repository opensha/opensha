package scratch.UCERF3.oldStuff;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.FileParameter;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurfaceWithSubsets;
import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.SimpleFaultData;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.utils.FaultSystemIO;

/**
 * This is a relatively simple ERF constructed from a SimpleFaultSystemSolution file. Rates are converted
 * to probabilities as such:
 * <br>
 * <br><code>prob = 1 - Math.exp(-rate * years)</code>
 * <br>
 * <br> where rate is the rupture rate (1/yr) from the solution, and years is the duration of the forecast.
 * <br>
 * <br>Ruptures are grouped into sources that share that same set of parent fault sections. For multi fault ruptures,
 * an incredibly Kludgey "<code>KludgeMultiSurface</code>" implementation of <code>EvenlyGriddedSurface</code> was
 * constructed. This simply creats one large surface where nCols is the sum of nCols for each subSection surface,
 * and nRows euqals the greatest row count of any sub surface. For subSection i where <code>nRows(i) < maxNRows</code>,
 * the last location in each column is duplicated to fill in the rest of the required rows. This works for the
 * purposes of distance calculations, but needs to be replaced with a true multi fault surface implementation.
 * Dip is also averaged among all fault surfaces.
 * <br>
 * <br>Outstanding issues:
 * <ul>
 * 		<li>Kludgey multi fault rupture surfaces</li>
 * 		<li>I'm not quite sure what to do with the "Aseis Factor Reduces Area?" parameter. It is required for
 * Stirling surfaces, but setting it to false won't update the rates.</li>
 * </ul>
 * 
 * @author kevin
 *
 */
public class OldInversionSolutionERF extends AbstractERF {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private static final boolean D = false;

	public static final String NAME = "Inversion Solution ERF";
	
	private static final String FILE_PARAM_NAME = "Solution Input File";
	private FileParameter fileParam;
	
	// these help keep track of what's changed
	private File prevFile = null;
	private double faultGridSpacing = -1;
	private Boolean aseisReduce = null;
	
	private static final String ASEIS_INTER_PARAM_NAME = "Aseis Factor Reduces Area?";
	private static final String ASEIS_INTER_PARAM_INFO = "Otherwise it reduces slip rate";
	private static final boolean ASEIS_INTER_PARAM_DEFAULT = false;
	private BooleanParameter aseisFactorInterParam;
	
	private static final String FAULT_GRID_SPACING_PARAM_NAME = "Fault Grid Spacing";
	private static final String FAULT_GRID_SPACING_UNITS = "KM";
	private static final double FAULT_GRID_SPACING_DEFAULT = 1.0d;
	private static final double FAULT_GRID_SPACING_MIN = 0.1d;
	private static final double FAULT_GRID_SPACING_MAX = 10d;
	private DoubleParameter faultGridSpacingParam;
	
	private FaultSystemSolution solution;
	
	private HashMap<String, SimpleSource> sourceNameMap = new HashMap<String, OldInversionSolutionERF.SimpleSource>();
	private ArrayList<ProbEqkSource> sources = new ArrayList<ProbEqkSource>();
	
	private HashMap<FaultSectionPrefData, AbstractEvenlyGriddedSurface> surfMap =
		new HashMap<FaultSectionPrefData, AbstractEvenlyGriddedSurface>();
	
	public OldInversionSolutionERF() {
		fileParam = new FileParameter(FILE_PARAM_NAME);
		fileParam.addParameterChangeListener(this);
		adjustableParams.addParameter(fileParam);
		
		// TODO is this applicable here? Its needed by for stirling surfaces,
		// but setting to false won't change slip rates 
		aseisFactorInterParam = new BooleanParameter(ASEIS_INTER_PARAM_NAME, ASEIS_INTER_PARAM_DEFAULT);
		aseisFactorInterParam.setDefaultValue(ASEIS_INTER_PARAM_DEFAULT);
		aseisFactorInterParam.setInfo(ASEIS_INTER_PARAM_INFO);
		aseisFactorInterParam.addParameterChangeListener(this);
		adjustableParams.addParameter(aseisFactorInterParam);
		
		faultGridSpacingParam = new DoubleParameter(FAULT_GRID_SPACING_PARAM_NAME,
				FAULT_GRID_SPACING_MIN, FAULT_GRID_SPACING_MAX, FAULT_GRID_SPACING_UNITS);
		faultGridSpacingParam.setDefaultValue(FAULT_GRID_SPACING_DEFAULT);
		faultGridSpacingParam.setValue(FAULT_GRID_SPACING_DEFAULT);
		faultGridSpacingParam.addParameterChangeListener(this);
		adjustableParams.addParameter(faultGridSpacingParam);
		
		timeSpan = new TimeSpan(TimeSpan.NONE, TimeSpan.YEARS);
		timeSpan.setDuration(30, TimeSpan.YEARS);
	}
	
	public void setSolutionFile(File file) {
		fileParam.setValue(file);
	}

	@Override
	public void updateForecast() {
		if (parameterChangeFlag) {
			if (D) System.out.println("Updating forecast");
			File file = fileParam.getValue();
			if (file == null) {
				if (D) System.out.println("No solution loaded");
				sourceNameMap.clear();
				sources.clear();
				surfMap.clear();
				return;
			}
			boolean isNewSolution = file != prevFile;
			prevFile = file;
			double years = timeSpan.getDuration(TimeSpan.YEARS);
			boolean rebuildSources = false;
			if (isNewSolution) {
				if (D) System.out.println("Loading solution from: "+file.getAbsolutePath());
				try {
					solution = FaultSystemIO.loadSol(file);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				if (D) System.out.println("Loaded solution with "
						+solution.getRupSet().getNumRuptures()+" ruptures.");
				rebuildSources = true;
			}
			
			// rebuild if fault grid spacing has changed
			if (faultGridSpacing > 0 && faultGridSpacing != faultGridSpacingParam.getValue()) {
				if (D) System.out.println("Fault grid spacing has changed, rebuilding");
				rebuildSources = true;
			}
			
			// rebuild if aseis reduce param has changed
			if (aseisReduce != null && aseisReduce != aseisFactorInterParam.getValue()) {
				if (D) System.out.println("Aseis reduce has changed, rebuilding");
				rebuildSources = true;
			}
			
			
			if (rebuildSources) {
				faultGridSpacing = faultGridSpacingParam.getValue();
				aseisReduce = aseisFactorInterParam.getValue();
				
				sourceNameMap.clear();
				sources.clear();
				surfMap.clear();
				
				FaultSystemRupSet rupSet = solution.getRupSet();
				
				int totRups = 0;
				for (int rupID=0; rupID<rupSet.getNumRuptures(); rupID++) {
					double rate = solution.getRateForRup(rupID);
					
					if (rate <= 0)
						continue;
					
					List<FaultSectionPrefData> datas = rupSet.getFaultSectionDataForRupture(rupID);
					
					String sourceName = getRuptureSourceName(datas);
					if (!sourceNameMap.containsKey(sourceName)) {
						if (D) System.out.println("Building source: "+sourceName);
						SimpleSource src = new SimpleSource(sourceName);
						sourceNameMap.put(sourceName, src);
						sources.add(src);
					}
					
					SimpleSource source = sourceNameMap.get(sourceName);
					
					double mag = rupSet.getMagForRup(rupID);
					double rake = rupSet.getAveRakeForRup(rupID);
					double prob = calcProb(rate, years);
					
					ProbEqkRupture rup = buildRupture(datas, mag, rake, prob);
					source.addRupture(rup, rate);
					totRups++;
				}
				if (D) System.out.println("Created "+sources.size()+" sources, "+totRups+" ruptures.");
			} else {
				// just changed the time span
				if (D) System.out.println("Updating rates for new time span: "+years+" years.");
				for (ProbEqkSource source : sources) {
					((SimpleSource)source).calcProbs(years);
				}
			}
			
			if (D) System.out.println("Done updating forecast.");
		}
	}
	
	public static double calcProb(double rate, double years) {
		// P = 1 - exp(-lambda*t)
		
		return 1d - Math.exp(-rate * years);
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public int getNumSources() {
		return sources.size();
	}

	@Override
	public ProbEqkSource getSource(int iSource) {
		return sources.get(iSource);
	}

	@Override
	public ArrayList<ProbEqkSource> getSourceList() {
		return sources;
	}
	
	public static boolean isRuptureSingleParent(List<FaultSectionPrefData> datas) {
		int parentID = -1;
		for (FaultSectionPrefData data : datas) {
			if (parentID == -1)
				parentID = data.getParentSectionId();
			if (parentID != data.getParentSectionId())
				return false;
		}
		return true;
	}
	
	public static String getRuptureSourceName(List<FaultSectionPrefData> datas) {
		if (isRuptureSingleParent(datas))
			return datas.get(0).getParentSectionName();
		HashSet<String> parentNames = new HashSet<String>();
		
		String sourceName = null;
		for (FaultSectionPrefData data : datas) {
			String name = data.getParentSectionName();
			if (!parentNames.contains(name)) {
				parentNames.add(name);
				
				if (sourceName == null)
					sourceName = "";
				else
					sourceName += " + ";
				sourceName += name;
			}
		}
		return sourceName;
	}
	
	private ProbEqkRupture buildRupture(List<FaultSectionPrefData> datas, double mag, double rake, double prob) {
		AbstractEvenlyGriddedSurface surface;
		if (isRuptureSingleParent(datas)) {
			// simple case
			ArrayList<SimpleFaultData> sfds = new ArrayList<SimpleFaultData>();
			for (FaultSectionPrefData data : datas)
				sfds.add(data.getSimpleFaultData(aseisReduce));
			surface = new StirlingGriddedSurface(sfds, faultGridSpacing);
		} else {
			ArrayList<AbstractEvenlyGriddedSurface> surfaces = new ArrayList<AbstractEvenlyGriddedSurface>();
			
			double dip = 0d;
			
			for (FaultSectionPrefData data : datas) {
				if (!surfMap.containsKey(data))
					surfMap.put(data, new StirlingGriddedSurface(data.getSimpleFaultData(aseisReduce), 1.0d));
				AbstractEvenlyGriddedSurface surf = surfMap.get(data);
				surfaces.add(surf);
				dip += data.getAveDip();
			}
			dip /= (double)datas.size();
			surface = new KludgeMultiSurface(surfaces, dip);
		}
		return new ProbEqkRupture(mag, rake, prob, surface, null);
	}
	
	private static int calcNCol(List<AbstractEvenlyGriddedSurface> surfaces) {
		int ncol = 0;
		for (AbstractEvenlyGriddedSurface surf : surfaces)
			ncol += surf.getNumCols();
		return ncol;
	}
	
	private static int calcNRow(List<AbstractEvenlyGriddedSurface> surfaces) {
		int nrow = 0;
		for (AbstractEvenlyGriddedSurface surf : surfaces)
			if (surf.getNumRows() > nrow)
				nrow = surf.getNumRows();
		return nrow;
	}
	
	private class KludgeMultiSurface extends AbstractEvenlyGriddedSurfaceWithSubsets {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public KludgeMultiSurface(List<AbstractEvenlyGriddedSurface> surfaces, double dip) {
			super(calcNRow(surfaces), calcNCol(surfaces), faultGridSpacing);
//			aveDip = dip;
			
			// TODO: this is DIRTYYYYYYYYYYYYYYYYY...need ruptures with multiple surfaces
			
			int colCnt = 0;
			for (AbstractEvenlyGriddedSurface dataSurf : surfaces) {
				int myNRows = dataSurf.getNumRows();
				for (int col=0; col<dataSurf.getNumCols(); col++) {
					Location loc = null;
					for (int row=0; row<getNumRows(); row++) {
						if (row < myNRows)
							loc = dataSurf.get(row, col);
						// this just duplicates extra rows at the bottom of each column
						set(row, colCnt, loc);
					}
					colCnt++;
				}
			}
		}

		@Override
		public double getAveDip() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public double getAveDipDirection() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public double getAveRupTopDepth() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public double getAveStrike() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public LocationList getPerimeter() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public FaultTrace getUpperEdge() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		protected AbstractEvenlyGriddedSurface getNewInstance() {
			// TODO Auto-generated method stub
			return null;
		}
	}
	
	private class SimpleSource extends ProbEqkSource {
		
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		private EvenlyGriddedSurface surface;
		private ArrayList<Double> rates = new ArrayList<Double>();
		private ArrayList<ProbEqkRupture> rups = new ArrayList<ProbEqkRupture>();
		
		public SimpleSource(String name) {
			this.name = name;
		}
		
		protected void addRupture(ProbEqkRupture rup, double rate) {
			rups.add(rup);
			rates.add(rate);
			
			EvenlyGriddedSurface rupSurf = (EvenlyGriddedSurface)rup.getRuptureSurface();
			// TODO kludge for setting the source surface
			if (surface == null || rupSurf.getNumCols() > surface.getNumCols())
				surface = rupSurf;
		}
		
		protected void calcProbs(double years) {
			for (int i=0; i<getNumRuptures(); i++) {
				double rate = rates.get(i);
				ProbEqkRupture rup = rups.get(i);
				double prob = calcProb(rate, years);
				rup.setProbability(prob);
			}
		}

		@Override
		public LocationList getAllSourceLocs() {
			return surface.getEvenlyDiscritizedListOfLocsOnSurface();
		}

		@Override
		public RuptureSurface getSourceSurface() {
			return surface;
		}

		@Override
		public double getMinDistance(Site site) {
			double min = Double.MAX_VALUE;
			
			int ncol = surface.getNumCols();
			int num = ncol / 100;
			if (num < 3)
				num = 3;
			
			LocationVector dir;
			for (int i=0; i<num; i++) {
				int surfI = (int)(i * (ncol-1) / (double)num + 0.5);
				dir = LocationUtils.vector(site.getLocation(), surface.get(0,surfI));
				if (min > dir.getHorzDistance())
					min = dir.getHorzDistance();
			}

			return min;
		}

		@Override
		public int getNumRuptures() {
			return rups.size();
		}

		@Override
		public ProbEqkRupture getRupture(int nRupture) {
			return rups.get(nRupture);
		}
		
	}

}
