package scratch.UCERF3.erf.ETAS.analysis;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.mapping.gmt.GMT_Map;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.analysis.FaultBasedMapGen;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;

public class ETAS_GriddedNucleationPlot extends ETAS_AbstractPlot {
	
	private static double[] mags = { 2.5, 5d, 6d, 7d };
	private static double discr = 0.02;

	private GriddedRegion reg;
	private GriddedGeoDataSet[] totalXYZs;
	private GriddedGeoDataSet[] triggeredXYZs;
	private GriddedGeoDataSet[] triggeredPrimaryXYZs;
	
	private int numCatalogs = 0;
	private int numRupsSkipped = 0;
	
	// this is used to find the minimum magnitude in the catalogs, ignoring any event chains that were
	// preserved below the mag cutoff. the magnitude with the highest incremental count should
	// reflect the minimum correctly
	private IncrementalMagFreqDist totalIncrCounts;
	private double modalMag;
	
	private String prefix;
	private boolean annualize;
	
	private boolean hasSpont;
	private boolean hasTriggered;

	protected ETAS_GriddedNucleationPlot(ETAS_Config config, ETAS_Launcher launcher, String prefix, boolean annualize) {
		super(config, launcher);
		this.prefix = prefix;
		this.annualize = annualize;
		
		reg = new GriddedRegion(new CaliforniaRegions.RELM_TESTING(), discr, GriddedRegion.ANCHOR_0_0);
		
		this.hasTriggered = config.hasTriggers();
		this.hasSpont = config.isIncludeSpontaneous()
				|| (config.getTriggerCatalogFile() != null && config.isTreatTriggerCatalogAsSpontaneous());
		
		if (hasSpont) {
			totalXYZs = new GriddedGeoDataSet[mags.length];
			for (int i=0; i<mags.length; i++)
				totalXYZs[i] = new GriddedGeoDataSet(reg, false);
		}
		if (hasTriggered) {
			triggeredXYZs = new GriddedGeoDataSet[mags.length];
			triggeredPrimaryXYZs = new GriddedGeoDataSet[mags.length];
			for (int i=0; i<mags.length; i++) {
				triggeredXYZs[i] = new GriddedGeoDataSet(reg, false);
				triggeredPrimaryXYZs[i] = new GriddedGeoDataSet(reg, false);
			}
		}
		
		totalIncrCounts = new IncrementalMagFreqDist(
				ETAS_MFD_Plot.mfdMinMag, ETAS_MFD_Plot.mfdNumMag, ETAS_MFD_Plot.mfdDelta);
	}

	@Override
	public int getVersion() {
		return 1;
	}

	@Override
	public boolean isFilterSpontaneous() {
		return hasTriggered;
	}

	@Override
	protected void doProcessCatalog(ETAS_Catalog completeCatalog, ETAS_Catalog triggeredOnlyCatalog,
			FaultSystemSolution fss) {
		if (hasSpont)
			doProcessCatalog(completeCatalog, totalXYZs, null, totalIncrCounts);
		if (hasTriggered)
			doProcessCatalog(triggeredOnlyCatalog, triggeredXYZs, triggeredPrimaryXYZs, hasSpont ? null : totalIncrCounts);
		
		numCatalogs++;
	}
	
	private void doProcessCatalog(List<ETAS_EqkRupture> catalog, GriddedGeoDataSet[] xyzs, GriddedGeoDataSet[] primaryXYZs,
			IncrementalMagFreqDist incrCounts) {
		for (ETAS_EqkRupture rup : catalog) {
			double mag = rup.getMag();
			if (incrCounts != null)
				incrCounts.add(incrCounts.getClosestXIndex(mag), 1d);
			Location loc = rup.getHypocenterLocation();
			int index = reg.indexForLocation(loc);
			// Preconditions.checkState(index > 0);
			if (index < 0) {
				numRupsSkipped++;
				continue;
			}
			for (int i = 0; i < mags.length; i++) {
				if (mag >= mags[i]) {
					xyzs[i].set(index, xyzs[i].get(index) + 1d);
					if (primaryXYZs != null && rup.getGeneration() == 1)
						primaryXYZs[i].set(index, primaryXYZs[i].get(index) + 1d);
				}
			}
		}
	}

	@Override
	protected List<? extends Runnable> doFinalize(File outputDir, FaultSystemSolution fss, ExecutorService exec)
			throws IOException {
		if (numRupsSkipped > 0)
			System.out.println("GriddedNucleation: skipped "+numRupsSkipped+" ruptures outside of region");
		
		double scalar;
		if (annualize)
			scalar = 1d/(getConfig().getDuration()*numCatalogs);
		else
			scalar = 1d/numCatalogs;
		
		int modalIndex = totalIncrCounts.getXindexForMaxY();
		modalMag = totalIncrCounts.getX(modalIndex)-0.5*totalIncrCounts.getDelta();
		
		System.out.println("GriddedParticipation modal magnitude (will skip plots below): "+(float)modalMag);
		
		List<Runnable> runnables = new ArrayList<>();
		if (hasSpont)
			runnables.add(new MapRunnable(totalXYZs, modalMag, scalar, outputDir, prefix));
		if (hasTriggered) {
			runnables.add(new MapRunnable(triggeredXYZs, modalMag, scalar, outputDir, prefix+"_triggered"));
			runnables.add(new MapRunnable(triggeredPrimaryXYZs, modalMag, scalar, outputDir, prefix+"_triggered_primary"));
		}
		return runnables;
	}
	
	private class MapRunnable implements Runnable {
		private GriddedGeoDataSet[] xyzs;
		private double modalMag;
		private double scalar;
		private File outputDir;
		private String prefix;

		public MapRunnable(GriddedGeoDataSet[] xyzs, double modalMag, double scalar, File outputDir, String prefix) {
			this.xyzs = xyzs;
			this.modalMag = modalMag;
			this.scalar = scalar;
			this.outputDir = outputDir;
			this.prefix = prefix;
		}

		@Override
		public void run() {
			try {
				// now log10 and scale
				for (GriddedGeoDataSet xyz : xyzs) {
					xyz.scale(scalar);
					xyz.log10();
				}

				CPT cpt = GMT_CPT_Files.MAX_SPECTRUM.instance();
				cpt.setNanColor(Color.GRAY);
				cpt.setBelowMinColor(Color.BLUE);

				Region plotReg = new Region(new Location(reg.getMinGridLat(), reg.getMinGridLon()),
						new Location(reg.getMaxGridLat(), reg.getMaxGridLon()));

				double minZ = Math.floor(Math.log10(scalar));

				for (int i = 0; i < mags.length; i++) {
					if ((float)mags[i] < (float)modalMag)
						continue;
					
					GriddedGeoDataSet xyz = xyzs[i];
					
					double maxZ = Math.ceil(xyz.getMaxZ());
					if (xyz.getMaxZ() == Double.NEGATIVE_INFINITY)
						maxZ = minZ + 4;
					if (maxZ == minZ)
						maxZ++;
					
					for (int j=0; j<xyz.size(); j++)
						if (Double.isInfinite(xyz.get(j)))
							xyz.set(j, minZ);

					Preconditions.checkState(minZ < maxZ, "minZ=%s >= maxZ=%s", minZ, maxZ);

					double mag = mags[i];
					String label = "Log10 M>=" + (float) mag;
					if (annualize)
						label += " Nucleation Rate";
					else
						label += " Expected Num";
					String myPrefix = prefix+"_m"+(float)mag;
					GMT_Map map = FaultBasedMapGen.buildMap(cpt.rescale(minZ, maxZ), null, null,
							xyzs[i], discr, plotReg, false, label);
					map.setCPTCustomInterval(1d);
					String baseURL = FaultBasedMapGen.plotMap(outputDir, myPrefix, false, map);
//				if (downloadZip)
//					FileUtils.downloadURL(baseURL + "/allFiles.zip", new File(outputDir, myPrefix + ".zip"));
				}
			} catch (IOException | GMT_MapException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			
		}
	}

	@Override
	public List<String> generateMarkdown(String relativePathToOutputDir, String topLevelHeading, String topLink)
			throws IOException {
		List<String> lines = new ArrayList<>();
		
		lines.add(topLevelHeading+" Gridded Nucleation");
		lines.add(topLink); lines.add("");
		
		TableBuilder builder = MarkdownUtils.tableBuilder();
		
		builder.initNewLine();
		builder.addColumn("Min Mag");
		if (hasSpont)
			builder.addColumn("Complete Catalog (including spontaneous)");
		if (hasTriggered) {
			builder.addColumn("Triggered Ruptures (no spontaneous)");
			builder.addColumn("Triggered Ruptures (primary aftershocks only)");
		}
		builder.finalizeLine();
		for (int i=0; i<mags.length; i++) {
			if ((float)mags[i] < (float)modalMag)
				continue;
			builder.initNewLine();
			builder.addColumn("**M&ge;"+optionalDigitDF.format(mags[i])+"**");
			String magStr = "_m"+(float)mags[i];
			if (hasSpont) {
				builder.addColumn("![Nucleation Plot]("+relativePathToOutputDir+"/"+prefix+magStr+".png)");
			}
			if (hasTriggered) {
				builder.addColumn("![Nucleation Plot]("+relativePathToOutputDir+"/"+prefix+"_triggered"+magStr+".png)");
				builder.addColumn("![Nucleation Plot]("+relativePathToOutputDir+"/"+prefix+"_triggered_primary"+magStr+".png)");
			}
			builder.finalizeLine();
		}
		lines.addAll(builder.build());
		
		return lines;
	}

}
