package org.opensha.sha.calc.IM_EventSet;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;

/**
 * Abstract base class for writing IM Event Set calculation outputs.
 * Provides common functionality for generating seismic hazard results in different formats.
 * <p>
 * Handles IMT string formatting, site parameter initialization, source filtering, and output coordination.
 * Concrete implementations HAZ01Writer and OriginalModWriter generate specific file formats.
 * </p>
 */
public abstract class IM_EventSetOutputWriter {
	
	protected static Logger logger = AbstractIMEventSetCalc.logger;
	
	protected IMEventSetCalcAPI calc;

	public static final DecimalFormat meanSigmaFormat = new DecimalFormat("0.####");
	public static final DecimalFormat distFormat = new DecimalFormat("0.###");
	public static final DecimalFormat rateFormat = new DecimalFormat("####0E0");
	
	public IM_EventSetOutputWriter(IMEventSetCalcAPI calc) {
		this.calc = calc;
	}
	
	public abstract void writeFiles(ArrayList<ERF> erfs, ArrayList<ScalarIMR> attenRels,
			ArrayList<String> imts) throws IOException;
	
	public void writeFiles(ERF erf, ArrayList<ScalarIMR> attenRels,
			ArrayList<String> imts) throws IOException {
		ArrayList<ERF> erfs = new ArrayList<ERF>();
		erfs.add(erf);
        writeErfImrMetaFile(erfs, attenRels);
        writeSiteMetaFile();
		writeFiles(erfs, attenRels, imts);
	}
	
	public void writeFiles(ERF erf, ScalarIMR imr,
			String imt) throws IOException {
		ArrayList<ScalarIMR> imrs = new ArrayList<ScalarIMR>();
		imrs.add(imr);
		ArrayList<String> imts = new ArrayList<String>();
		imts.add(imt);
		writeFiles(erf, imrs, imts);
	}
	
	public abstract String getName();

    /**
     * Writes out ERF and IMR inputs to a metadata file with output files.
     */
    private void writeErfImrMetaFile(ArrayList<ERF> erfs, ArrayList<ScalarIMR> attenRels) throws IOException {
        logger.log(Level.INFO, "Writing ERF/IMR metadata file");
        String fname = "erf_imr_metadata.txt";
        File outputDir = calc.getOutputDir();
        FileWriter fw = new FileWriter(outputDir.getAbsolutePath() + File.separator + fname);

        fw.write("IMR Param List:\n---------------");
        for (ScalarIMR attenRel : attenRels) {
            fw.write("\nIMR = " + attenRel.getName()+"; ");
            fw.write(attenRel.getOtherParams().getVisibleParams().getParameterListMetadataString());
        }
        fw.write("\n\nForecast Param List:\n---------------");
        for (ERF erf : erfs) {
            fw.write("\nEqk Rup Forecast = " + erf.getName()+"; ");
            fw.write(erf.getAdjustableParameterList().getParameterListMetadataString());
        }
        fw.close();
    }

    /**
     * Writes out the site information including site data parameters.
     */
    private void writeSiteMetaFile() throws IOException {
        logger.log(Level.INFO, "Writing site metadata file");
        String fname = "site_metadata.txt";
        File outputDir = calc.getOutputDir();
        FileWriter fw = new FileWriter(outputDir.getAbsolutePath() + File.separator + fname);

        ArrayList<ParameterList> siteData = calc.getSitesData();
        ArrayList<Site> sites = calc.getSites();
        for (int i = 0; i < calc.getNumSites(); i++) {
            Location loc = sites.get(i).getLocation();
            String coordinates = String.format("%s,%s", loc.getLatitude(), loc.getLongitude());
            fw.write("Site = " + coordinates + "; ");
            fw.write(siteData.get(i).getParameterListMetadataString()+"\n");
        }
        fw.close();
    }

    /**
     * HAZ01 IMT period strings only have precision up to 0.1 seconds.
     * Example: SA 1.0 = SA10, SA 0.1 = SA01, SA 0.25 = SA03, SA 0.005 = SA00
     * @param param
     * @return
     */
	public static String getHAZ01IMTString(Parameter<?> param) {
		String imt = param.getName();
		
		if (param instanceof Parameter) {
			Parameter<?> depParam = (Parameter<?>)param;
			for (Parameter<?> dep : depParam.getIndependentParameterList()) {
				if (dep.getName().equals(PeriodParam.NAME)) {
					double period = (Double)dep.getValue();
                    int p10 = (int)(period * 10.0 + 0.5);
					String p10Str = p10 + "";
					if (p10Str.length() < 2)
						p10Str = "0" + p10Str;
					imt += p10Str;
					break;
				}
			}
		}
		return imt;
	}

    /**
     * Regular IMT strings have precision of `double` type.
     * @param param
     * @return
     */
	public static String getRegularIMTString(Parameter<?> param) {
		String imt = param.getName();
		
		if (param instanceof Parameter) {
			Parameter<?> depParam = (Parameter<?>)param;
			for (Parameter<?> dep : depParam.getIndependentParameterList()) {
				if (dep.getName().equals(PeriodParam.NAME)) {
					double period = (Double)dep.getValue();
					imt += " " + (float)period;
					break;
				}
			}
		}
		return imt;
	}
	
	/**
	 * Sets the IMT from the string specification
	 * 
	 * @param imtStr
	 * @param attenRel
	 */
	public static void setIMTFromString(String imtStr, ScalarIMR attenRel) {
		String imt = imtStr.trim();
		if ((imt.startsWith("SA") || imt.startsWith("SD"))) {
			logger.log(Level.FINE, "Parsing IMT with Period: " + imt);
			// this is SA/SD
			String perSt = imt.substring(2);
			String theIMT = imt.substring(0, 2);
			double period;
			if (perSt.startsWith(" ") || perSt.startsWith("\t")) {
				// this is a 'SA period' format IMT
				logger.log(Level.FINEST, "Split into IMT: " + theIMT + ", Period portion: " + perSt);
				period = Double.parseDouble(perSt.trim());
				
			} else {
				// this is a HAZ01 style IMT
				logger.log(Level.FINEST, "Split into IMT: " + theIMT + ", Period portion (HAZ01 style): " + perSt);
				period = Double.parseDouble(perSt) / 10d;
			}
			attenRel.setIntensityMeasure(theIMT);
			Parameter imtParam = (Parameter)attenRel.getIntensityMeasure();
			imtParam.getIndependentParameter(PeriodParam.NAME).setValue(period);
			logger.log(Level.FINE, "Parsed IMT with Period: " + imt + " => " + theIMT + ", period: " + period);
//			System.out.println("imtstr: " + imt + ", " + attenRel.getIntensityMeasure().getName()
//						+ ": " + attenRel.getParameter(PeriodParam.NAME).getValue());
		} else {
			logger.log(Level.FINE, "Setting IMT from String");
			attenRel.setIntensityMeasure(imt);
		}
	}
	
	/**
	 * Gets all of the default site params from the attenuation relationship
	 * 
	 * @param attenRel
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected ArrayList<Parameter> getDefaultSiteParams(ScalarIMR attenRel) {
		logger.log(Level.FINE, "Storing default IMR site related params.");
		ListIterator<Parameter<?>> siteParamsIt = attenRel.getSiteParamsIterator();
		ArrayList<Parameter> defaultSiteParams = new ArrayList<Parameter>();

		while (siteParamsIt.hasNext()) {
			defaultSiteParams.add((Parameter) siteParamsIt.next().clone());
		}

		return defaultSiteParams;
	}

	/**
	 * Sets the site params for the given Attenuation Relationship to the value in the given params.
	 * @param attenRel
	 * @param defaultSiteParams
	 */
	@SuppressWarnings("unchecked")
	protected void setSiteParams(ScalarIMR attenRel, ArrayList<Parameter> defaultSiteParams) {
		logger.log(Level.FINE, "Restoring default IMR site related params.");
		for (Parameter param : defaultSiteParams) {
			Parameter attenParam = attenRel.getParameter(param.getName());
			attenParam.setValue(param.getValue());
		}
	}

	/**
	 * This goes through each site and makes sure that it has a parameter for each site
	 * param from the Attenuation Relationship. It then tries to set that parameter from
	 * its own data values, and if it can't, uses the attenuation relationship's default.
	 * 
	 * @param attenRel
	 * @return
	 */
	protected ArrayList<Site> getInitializedSites(ScalarIMR attenRel) {
		logger.log(Level.FINE, "Retrieving and setting Site related params for IMR");
		// get the list of sites
		ArrayList<Site> sites = this.calc.getSites();
		ArrayList<ParameterList> sitesData = this.calc.getSitesData();

		// we need to make sure that the site has parameters for this atten rel
		ListIterator<Parameter<?>> siteParamsIt = attenRel.getSiteParamsIterator();
		while (siteParamsIt.hasNext()) {
			Parameter attenParam = siteParamsIt.next();
			for (int i=0; i<sites.size(); i++) {
				Site site = sites.get(i);
				ParameterList siteData = sitesData.get(i);
				Parameter siteParam;
				if (site.containsParameter(attenParam.getName())) {
					siteParam = site.getParameter(attenParam.getName());
				} else {
					siteParam = (Parameter)attenParam.clone();
					site.addParameter(siteParam);
				}
				// now try to set this parameter from the site data
                boolean success = false;
                for (Parameter<?> siteDatum : siteData) {
                    if (siteDatum.getName().equals(siteParam.getName())) {
                        siteParam.setValue(siteDatum.getValue());
                        success = true;
                        break;
                    }
                }
				if (success) {
					logger.log(Level.FINE, "Set site "+i+" param '"+siteParam.getName()
							+"' from data. New value: "+siteParam.getValue());
				} else {
					// if we couldn't set it from our data, use the atten rel's default
					logger.log(Level.FINE, "Couldn't set site "+i+" param '"+siteParam.getName()
							+"' from data, setting to IMR default of: "+attenParam.getValue());
					siteParam.setValue(attenParam.getValue());
				}
			}
		}
//		for (int i=0; i<sites.size(); i++) {
//			Site site = sites.get(i);
//			ParameterList siteData = sitesData.get(i);
//            System.out.println(siteData);
//		}
		return sites;
	}

	@Override
	public String toString() {
		return getName();
	}

}
