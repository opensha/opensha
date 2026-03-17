package org.opensha.sha.calc.IM_EventSet.outputImpl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;

import org.opensha.commons.data.CSVWriter;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.Parameter;
import org.opensha.refFaultParamDb.vo.Timespan;
import org.opensha.sha.calc.IM_EventSet.IMEventSetCalcAPI;
import org.opensha.sha.calc.IM_EventSet.IM_EventSetOutputWriter;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.OtherParams.StdDevTypeParam;
import org.opensha.sha.calc.sourceFilters.SourceFilterUtils;

/**
 * Writes the original CSV format files for the IM Event Set Calculator.
 */
public class OriginalModCsvWriter extends IM_EventSetOutputWriter {
	public static final String NAME = "OpenSHA CSV Format Writer";
	
	File outputDir;

	public OriginalModCsvWriter(IMEventSetCalcAPI calc) {
		super(calc);
	}

	@Override
	public void writeFiles(ArrayList<ERF> erfs,
			ArrayList<ScalarIMR> attenRels, ArrayList<String> imts)
			throws IOException {
		logger.log(Level.INFO, "Writing OpenSHA CSV format files");
		outputDir = null;
		boolean multipleERFs = erfs.size() != 1;
        for (int erfID=0; erfID<erfs.size(); erfID++) {
			ERF erf = erfs.get(erfID);
			if (multipleERFs) {
				outputDir = new File(calc.getOutputDir().getAbsolutePath() + File.separator + "erf" + erfID);
			} else {
				outputDir = calc.getOutputDir();
			}
			logger.log(Level.INFO, "Writing files to: " +  outputDir.getAbsolutePath());
			this.writeOriginalSrcRupMetaFile(erf);
			this.writeOriginalRupDistFile(erf);
            for (ScalarIMR attenRel : attenRels) {
                for (String imt : imts) {
                    this.writeOriginalMeanSigmaFiles(erf, attenRel, imt);
                }
            }
		}
		logger.log(Level.INFO, "Done writing files.");
	}
	
	/**
     * This writes the mean and logarithmic standard deviation values to a CSV file
	 *
	 * @param erf
	 * @param attenRel
     * @param imt
	 * @throws IOException
	 */
	private void writeOriginalMeanSigmaFiles(ERF erf, ScalarIMR attenRel, String imt) throws IOException {
		setIMTFromString(imt, attenRel);
		logger.log(Level.INFO, "Writing Mean/Sigma CSV file for " + attenRel.getShortName() + ", " + imt);
		ArrayList<Parameter> defaultSiteParams = getDefaultSiteParams(attenRel);

		ArrayList<Site> sites = getInitializedSites(attenRel);

        StdDevTypeParam stdDevParam = null;
        boolean hasInterIntra = false;
        try {
            stdDevParam = (StdDevTypeParam) attenRel.getParameter(StdDevTypeParam.NAME);
            hasInterIntra = stdDevParam.isAllowed(StdDevTypeParam.STD_DEV_TYPE_INTER) &&
                    stdDevParam.isAllowed(StdDevTypeParam.STD_DEV_TYPE_INTRA);
        } catch (ParameterException e) {
            logger.log(Level.INFO, "IMR " + attenRel.getShortName() + " missing Std Dev Type parameter.");
        }

		Parameter<?> im = attenRel.getIntensityMeasure();
		String fname = attenRel.getShortName();
		StringTokenizer imtTok = new StringTokenizer(imt);
		if (imtTok.countTokens() > 1) {
			while (imtTok.hasMoreTokens())
				fname += "_" + imtTok.nextToken();
			fname += ".csv";
		} else {
			fname += "_" + imt + ".csv";
		}
		
        File file = new File(outputDir.getAbsolutePath() + File.separator + fname);
        try (CSVWriter csvWriter = new CSVWriter(new FileOutputStream(file), true)) {

            // Headers
            List<String> header = new ArrayList<>();
            header.add("SourceId");
            header.add("RuptureId");
            for (int i = 0; i < sites.size(); i++) {
                int siteIndex = i + 1;
                header.add("Mean(" + siteIndex + ")");
                header.add("Total-Std-Dev.(" + siteIndex + ")");
                header.add("Inter-Event-Std-Dev.(" + siteIndex + ")");
            }
            csvWriter.write(header);

            int numSources = erf.getNumSources();

            for (int sourceID = 0; sourceID < numSources; sourceID++) {
                ProbEqkSource source = erf.getSource(sourceID);
                for (int rupID = 0; rupID < source.getNumRuptures(); rupID++) {
                    boolean shouldWriteRup = false; // Don't write if all NaN vals for all sites
                    ProbEqkRupture rup = source.getRupture(rupID);
                    List<String> row = new ArrayList<>();
                    row.add(Integer.toString(sourceID));
                    row.add(Integer.toString(rupID));

                    for (Site site : sites) {
                        double mean = Double.NaN, total = Double.NaN, inter = Double.NaN;
                        if (!SourceFilterUtils.canSkipSource(calc.getSourceFilters(), source, site) &&
                            !SourceFilterUtils.canSkipRupture(calc.getSourceFilters(), rup, site)) {
                            shouldWriteRup = true;
                            attenRel.setSite(site);
                            mean = attenRel.getMean(rup);
                            if (stdDevParam != null) {
                                stdDevParam.setValue(StdDevTypeParam.STD_DEV_TYPE_TOTAL);
                            }
                            total = attenRel.getStdDev(rup);
                            if (hasInterIntra) {
                                stdDevParam.setValue(StdDevTypeParam.STD_DEV_TYPE_INTER);
                                inter = attenRel.getStdDev(rup);
                            }
                        }
                        row.add(meanSigmaFormat.format(mean));
                        row.add(meanSigmaFormat.format(total));
                        row.add(meanSigmaFormat.format(inter));

                        // Track if the line contains at least one site with valid values
                        //if (!Double.isNaN(mean) || !Double.isNaN(total) || !Double.isNaN(inter))
                        //    shouldWriteRup = true;
                    }
                    if (shouldWriteRup) csvWriter.write(row);
                }
            }
        }
		logger.log(Level.INFO, "Done writing " + fname);
		// restore the default site params for the atten rel
		setSiteParams(attenRel, defaultSiteParams);
	}
	
	/**
	 * This writes the rupture distance files in CSV format.
     * <p>
     * rup_dist_info.csv is the shortest distance to a point on the rupture surface
     * rup_dist_jb_info.csv is similar but generated with JB distances
	 * </p>
     *
	 * @param erf
	 * @throws IOException
	 */
	private void writeOriginalRupDistFile(ERF erf) throws IOException {
		logger.log(Level.INFO, "Writing rupture distance files");
		String fname = "rup_dist_info.csv";
		String fname_jb = "rup_dist_jb_info.csv";

        File file = new File(outputDir.getAbsolutePath() + File.separator + fname);
        File file_jb = new File(outputDir.getAbsolutePath() + File.separator + fname_jb);

        try (CSVWriter csvWriter = new CSVWriter(new FileOutputStream(file), true);
             CSVWriter csvWriterJB = new CSVWriter(new FileOutputStream(file_jb), true)) {

            ArrayList<Site> sites = calc.getSites();

            // Headers
            List<String> header = new ArrayList<>();
            header.add("SourceId");
            header.add("RuptureId");
            for (int i = 0; i < sites.size(); i++) {
                int siteIndex = i + 1;
                header.add("RupDist(" + siteIndex + ")");
            }
            csvWriter.write(header);
            csvWriterJB.write(header);

            erf.updateForecast();

            int numSources = erf.getNumSources();

            for (int sourceID = 0; sourceID < numSources; sourceID++) {
                ProbEqkSource source = erf.getSource(sourceID);
                for (int rupID = 0; rupID < source.getNumRuptures(); rupID++) {
                    boolean shouldWriteRup = false;
                    boolean shouldWriteRupJB = false;
                    ProbEqkRupture rup = source.getRupture(rupID);
                    List<String> row = new ArrayList<>();
                    List<String> rowJB = new ArrayList<>();
                    row.add(Integer.toString(sourceID));
                    row.add(Integer.toString(rupID));
                    rowJB.add(Integer.toString(sourceID));
                    rowJB.add(Integer.toString(rupID));

                    for (Site site : sites) {
                        double rupDist = Double.NaN, distJB = Double.NaN;
                        if (!SourceFilterUtils.canSkipSource(calc.getSourceFilters(), source, site) &&
                            !SourceFilterUtils.canSkipRupture(calc.getSourceFilters(), rup, site)) {
                        	RuptureSurface surf = rup.getRuptureSurface();
                            if (surf instanceof PointSurface.DistanceCorrectable) {
                            	SurfaceDistances dists = ((PointSurface.DistanceCorrectable)surf).getAverageDistances(site.getLocation());
                            	rupDist = dists.getDistanceRup();
                            	distJB = dists.getDistanceJB();
                            } else {
                                rupDist = surf.getDistanceRup(site.getLocation());
                                distJB = surf.getDistanceJB(site.getLocation());
                            }
                        }
                        // Track if the line contains at least one site with valid values
                        if (!Double.isNaN(rupDist)) shouldWriteRup = true;
                        if (!Double.isNaN(distJB)) shouldWriteRupJB = true;

                        row.add(distFormat.format(rupDist));
                        rowJB.add(distFormat.format(distJB));
                    }
                    if (shouldWriteRup) csvWriter.write(row);
                    if (shouldWriteRupJB) csvWriterJB.write(rowJB);
                }
            }
        }
        logger.log(Level.INFO, "Done writing " + fname);
        logger.log(Level.INFO, "Done writing " + fname_jb);
	}

	/**
	 * This writes source/rupture metadata to the file 'src_rup_metadata.csv'
	 *
	 * @param erf
	 * @throws IOException
	 */
	private void writeOriginalSrcRupMetaFile(ERF erf) throws IOException {
		logger.log(Level.INFO, "Writing source/rupture metadata file");
		String fname = "src_rup_metadata.csv";

        File file = new File(outputDir.getAbsolutePath() + File.separator + fname);
        try (CSVWriter csvWriter = new CSVWriter(new FileOutputStream(file), true)) {

            // Headers
            List<String> header = Arrays.asList("SourceId", "RuptureId", "annualizedRate", "Mag", "Src-Name");
            csvWriter.write(header);

            erf.updateForecast();

            int numSources = erf.getNumSources();

            TimeSpan timespan = erf.getTimeSpan();
            double duration;
            if (timespan != null)
                duration = timespan.getDuration();
            else
                duration = 1;

            for (int sourceID = 0; sourceID < numSources; sourceID++) {
                ProbEqkSource source = erf.getSource(sourceID);
                for (int rupID = 0; rupID < source.getNumRuptures(); rupID++) {
                    ProbEqkRupture rup = source.getRupture(rupID);
                    double rate = rup.getMeanAnnualRate(duration);
                    List<String> row = Arrays.asList(
                            Integer.toString(sourceID),
                            Integer.toString(rupID),
                            rateFormat.format(rate),
                            Float.toString((float)rup.getMag()),
                            source.getName()
                    );
                    csvWriter.write(row);
                }
            }
        }
        logger.log(Level.INFO, "Done writing " + fname);
	}

    @Override
	public String getName() {
		return NAME;
	}

}
