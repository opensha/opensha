package org.opensha.sha.calc.IM_EventSet.v03.outputImpl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.logging.Level;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.Parameter;
import org.opensha.sha.calc.IM_EventSet.v03.IM_EventSetCalc_v3_0_API;
import org.opensha.sha.calc.IM_EventSet.v03.IM_EventSetOutputWriter;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.OtherParams.StdDevTypeParam;
import org.opensha.sha.util.SourceUtil;

public class OriginalModWriter extends IM_EventSetOutputWriter {
	public static final String NAME = "OpenSHA Format Writer";
	
	File outputDir;

	public OriginalModWriter(IM_EventSetCalc_v3_0_API calc) {
		super(calc);
	}

	@Override
	public void writeFiles(ArrayList<ERF> erfs,
			ArrayList<ScalarIMR> attenRels, ArrayList<String> imts)
			throws IOException {
		logger.log(Level.INFO, "Writing old format files files");
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
	 * This writes the mean and logarithmic standard deviation values to a file following the
	 * original IM Event Set calculator format, with the only change being the addition of
	 * a column for inter event std dev (at Erdem's request).
	 * 
	 * @param erf
	 * @param attenRel
	 * @throws IOException
	 */
	private void writeOriginalMeanSigmaFiles(ERF erf, ScalarIMR attenRel, String imt) throws IOException {
		setIMTFromString(imt, attenRel);
		logger.log(Level.INFO, "Writing Mean/Sigma file for " + attenRel.getShortName() + ", " + imt);
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
			fname += ".txt";
		} else {
			fname += "_" + imt + ".txt";
		}
		
		FileWriter fw = new FileWriter(outputDir.getAbsolutePath() + File.separator + fname);

		erf.updateForecast();
		
		int numSources = erf.getNumSources();
		
		for (int sourceID=0; sourceID<numSources; sourceID++) {
			ProbEqkSource source = erf.getSource(sourceID);
			for (int rupID=0; rupID<source.getNumRuptures(); rupID++) {
                boolean shouldWriteRup = false; // Don't write if all NaN vals for all sites
				ProbEqkRupture rup = source.getRupture(rupID);
				attenRel.setEqkRupture(rup);
				String line = sourceID + " " + rupID;
				for (Site site : sites) {
                    double mean = Double.NaN, total = Double.NaN, inter = Double.NaN;
                    if (!SourceUtil.canSkipSource(calc.getSourceFilters(), source, site) &&
                        !SourceUtil.canSkipRupture(calc.getSourceFilters(), rup, site)) {
                        attenRel.setSite(site);
                        mean = attenRel.getMean();
                        if (stdDevParam != null) {
                            if (stdDevParam.isAllowed(StdDevTypeParam.STD_DEV_TYPE_TOTAL)) {
                                stdDevParam.setValue(StdDevTypeParam.STD_DEV_TYPE_TOTAL);
                            } else if (stdDevParam.isAllowed(StdDevTypeParam.STD_DEV_TYPE_TOTAL_MAG_DEP)) {
                                stdDevParam.setValue(StdDevTypeParam.STD_DEV_TYPE_TOTAL_MAG_DEP);
                            }
                        }
                        total = attenRel.getStdDev();
                        if (hasInterIntra) {
                            stdDevParam.setValue(StdDevTypeParam.STD_DEV_TYPE_INTER);
                            inter = attenRel.getStdDev();
                        }
                    }

                    // Track if the line contains at least one site with valid values
                    boolean hasNumber = Arrays.stream(new double[]{mean, total, inter})
                            .anyMatch((i) -> !Double.isNaN(i));
                    if (hasNumber) shouldWriteRup = true;

					line += " " + meanSigmaFormat.format(mean) + " " + meanSigmaFormat.format(total)
									+ " " + meanSigmaFormat.format(inter);
				}
				if (shouldWriteRup) fw.write(line + "\n");
			}
		}
		fw.close();
		logger.log(Level.INFO, "Done writing " + fname);
		// restore the default site params for the atten rel
		setSiteParams(attenRel, defaultSiteParams);
	}
	
	/**
	 * This writes the rupture distance files following the format of the original IM Event Set Calculator.
	 * The file 'rup_dist_info.txt' is equivalent to the old files, and 'rup_dist_jb_info.txt' is similar
	 * but with JB distances (at Erdem's request).
	 * 
	 * @param erf
	 * @throws IOException
	 */
	private void writeOriginalRupDistFile(ERF erf) throws IOException {
		logger.log(Level.INFO, "Writing rupture distance files");
		String fname = "rup_dist_info.txt";
		String fname_jb = "rup_dist_jb_info.txt";
		FileWriter fw = new FileWriter(outputDir.getAbsolutePath() + File.separator + fname);
		FileWriter fw_jb = new FileWriter(outputDir.getAbsolutePath() + File.separator + fname_jb);
		
		ArrayList<Site> sites = calc.getSites();
		
		erf.updateForecast();
		
		int numSources = erf.getNumSources();
		
		for (int sourceID=0; sourceID<numSources; sourceID++) {
			ProbEqkSource source = erf.getSource(sourceID);
			for (int rupID=0; rupID<source.getNumRuptures(); rupID++) {
                boolean shouldWriteRup = false;
                boolean shouldWriteRupJB = false;
				ProbEqkRupture rup = source.getRupture(rupID);
				String line = sourceID + " " + rupID;
				String lineJB = line;
				for (Site site : sites) {
                    double rupDist = Double.NaN, distJB = Double.NaN;
                    if (!SourceUtil.canSkipSource(calc.getSourceFilters(), source, site) &&
                        !SourceUtil.canSkipRupture(calc.getSourceFilters(), rup, site)) {
                        rupDist = rup.getRuptureSurface().getDistanceRup(site.getLocation());
                        distJB = rup.getRuptureSurface().getDistanceJB(site.getLocation());
                    }

                    // Track if the line contains at least one site with valid values
                    if (!Double.isNaN(rupDist)) shouldWriteRup = true;
                    if (!Double.isNaN(distJB)) shouldWriteRupJB = true;

					line += " " + distFormat.format(rupDist);
					lineJB += " " + distFormat.format(distJB);
				}
				if (shouldWriteRup) fw.write(line + "\n");
				if (shouldWriteRupJB) fw_jb.write(lineJB + "\n");
			}
		}
		fw.close();
		fw_jb.close();
	}
	
	/**
	 * This writes source/rupture metadata to the file 'src_rup_metadata.txt'
	 * 
	 * @param erf
	 * @throws IOException
	 */
	private void writeOriginalSrcRupMetaFile(ERF erf) throws IOException {
		logger.log(Level.INFO, "Writing source/rupture metadata file");
		String fname = "src_rup_metadata.txt";
		FileWriter fw = new FileWriter(outputDir.getAbsolutePath() + File.separator + fname);
		
		ArrayList<Site> sites = calc.getSites();
		
		erf.updateForecast();
		
		int numSources = erf.getNumSources();
		
		double duration = ((TimeSpan)erf.getTimeSpan()).getDuration();

		for (int sourceID=0; sourceID<numSources; sourceID++) {
			ProbEqkSource source = erf.getSource(sourceID);
			for (int rupID=0; rupID<source.getNumRuptures(); rupID++) {
				ProbEqkRupture rup = source.getRupture(rupID);
				double rate = rup.getMeanAnnualRate(duration);
				fw.write(sourceID + "  " + rupID + " " + (float)rate + "  "
						+ (float)rup.getMag() + "  " + source.getName() + "\n");
			}
		}
		fw.close();
	}
	
	public String getName() {
		return NAME;
	}

}
