package scratch.kevin.ucerf3.etas.weeklyRuns;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import org.opensha.commons.data.comcat.ComcatAccessor;
import org.opensha.commons.data.comcat.ComcatRegion;
import org.opensha.commons.data.comcat.ComcatRegionAdapter;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.earthquake.observedEarthquake.parsers.UCERF3_CatalogParser;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

import scratch.UCERF3.erf.ETAS.ETAS_CubeDiscretizationParams;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_ProbabilityModelOptions;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_AbstractComcatConfigBuilder;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_ComcatConfigBuilder;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_ComcatEventFetcher;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_ConfigBuilder;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_ConfigBuilder.HPC_Sites;

public class AfterLargeEventScriptWriter {

	public static void main(String[] args) throws IOException {
		GregorianCalendar startDate = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
		startDate.clear();
		startDate.set(1986, 0, 1);
		GregorianCalendar endDate = new GregorianCalendar(startDate.getTimeZone());
		int deltaDays = 7;
		
		double minMag = 6d;
		
		double duration = 1d;
		int numCatalogs = 100000;
		
		DecimalFormat batchDF = new DecimalFormat("000");
		
//		String kCOV = null;
		
//		String kCOV = "1.5";
//		U3ETAS_ProbabilityModelOptions probModel = U3ETAS_ProbabilityModelOptions.FULL_TD;
//		String parentDir = "${ETAS_SIM_DIR}/2021_02_02-after-m6-1986-present-full_td-kCOV1.5";
//		boolean griddedOnly = false;
		
//		String kCOV = null;
//		U3ETAS_ProbabilityModelOptions probModel = U3ETAS_ProbabilityModelOptions.FULL_TD;
//		String parentDir = "${ETAS_SIM_DIR}/2021_02_08-after-m6-1986-present-full_td";
//		boolean griddedOnly = false;
		
//		String kCOV = "1.5";
//		U3ETAS_ProbabilityModelOptions probModel = U3ETAS_ProbabilityModelOptions.NO_ERT;
//		String parentDir = "${ETAS_SIM_DIR}/2021_02_08-after-m6-1986-present-no_ert-kCOV1.5";
//		boolean griddedOnly = false;
		
		String kCOV = "1.5";
		U3ETAS_ProbabilityModelOptions probModel = null;
		String parentDir = "${ETAS_SIM_DIR}/2021_02_08-after-m6-1986-present-gridded-kCOV1.5";
		boolean griddedOnly = true;
		
		File resolvedParentDir = ETAS_Config.resolvePath(parentDir);
		Preconditions.checkState(resolvedParentDir.exists() || resolvedParentDir.mkdir());
		
		File u3CatalogFile = ETAS_Config.resolvePath("${ETAS_LAUNCHER}/inputs/u3_historical_catalog.txt");
		
		ObsEqkRupList u3Catalog = UCERF3_CatalogParser.loadCatalog(u3CatalogFile);
		long comcatStartMillis = u3Catalog.get(u3Catalog.size()-1).getOriginTime();
		
		DateFormat nameDF = new SimpleDateFormat("yyyy/MM/dd");
		nameDF.setTimeZone(startDate.getTimeZone());
		
		ObsEqkRupList filteredEvents = new ObsEqkRupList();
		for (ObsEqkRupture rup : u3Catalog)
			if (rup.getMag() >= minMag && rup.getOriginTime() >= startDate.getTimeInMillis())
				filteredEvents.add(rup);
		
		File comcatFile = null;
		if (endDate.getTimeInMillis() > comcatStartMillis) {
			comcatFile = new File(resolvedParentDir, "comcat-events.txt");
			List<? extends ObsEqkRupture> comcatEvents;
			if (!comcatFile.exists()) {
				System.out.println("Pre-fetching ComCat events");
				ComcatAccessor accessor = new ComcatAccessor();
				ComcatRegion cReg = new ComcatRegionAdapter(new CaliforniaRegions.RELM_TESTING());
				comcatEvents = accessor.fetchEventList(null, comcatStartMillis, endDate.getTimeInMillis(), -10d,
						ETAS_CubeDiscretizationParams.DEFAULT_MAX_DEPTH, cReg, false, false, 2.5d);
				ETAS_ComcatEventFetcher.writeCatalogFile(comcatFile, comcatEvents);
				ETAS_AbstractComcatConfigBuilder.CACHE_TRIGGER_RUPS = true;
			} else {
				System.out.println("Using already pre-fetched ComCat events");
				long maxTime = Long.MIN_VALUE;
				try {
					comcatEvents = ETAS_ComcatEventFetcher.loadCatalogFile(comcatFile);
					for (ObsEqkRupture rup : comcatEvents)
						maxTime = Long.max(maxTime, rup.getOriginTime());
					endDate.setTimeInMillis(maxTime);
				} catch (ParseException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
			for (ObsEqkRupture rup : comcatEvents)
				if (rup.getMag() >= minMag)
					filteredEvents.add(rup);
		}
		System.out.println("Found "+filteredEvents.size()+" events with M>="+(float)minMag);
		
		for (ObsEqkRupture matchingEvent : filteredEvents) {
			System.out.println("Processing for "+matchingEvent.getEventId()+", an M"+(float)matchingEvent.getMag());
			List<String> argz = new ArrayList<>();
			argz.add("--num-simulations"); argz.add(numCatalogs+"");
			argz.add("--include-spontaneous");
			argz.add("--historical-catalog");
			argz.add("--duration"); argz.add((float)duration+"");
			if (kCOV != null && !kCOV.isEmpty()) {
				argz.add("--etas-k-cov"); argz.add(kCOV);
			}
			
			GregorianCalendar myStart = new GregorianCalendar(startDate.getTimeZone());
			myStart.setTimeInMillis(matchingEvent.getOriginTime()+10000l); // add 10s
			boolean comcat = myStart.getTimeInMillis() > comcatStartMillis;
			
			String name = "Start After "+matchingEvent.getEventId()+", M"+(float)matchingEvent.getMag()+", "+nameDF.format(myStart.getTime());
			if (kCOV != null && !kCOV.isEmpty())
				name += ", kCOV="+kCOV;
			if (comcat)
				name += ", ComCat Stitch";
			if (griddedOnly) {
				name += ", Gridded Only";
				argz.add("--gridded-only");
			} else if (probModel != U3ETAS_ProbabilityModelOptions.FULL_TD) {
				name += ", "+probModel.toString();
				argz.add("--prob-model"); argz.add(probModel.name());
			}
			System.out.println("\t"+name);
			String outputDir = parentDir+"/"+ETAS_ConfigBuilder.getNamePrefix(name.replaceAll("/", "_"));
			
			if (comcat) {
				argz.add("--start-after-historical");
				argz.add("--end-time"); argz.add(myStart.getTimeInMillis()+"");
				argz.add("--finite-surf-shakemap");
				argz.add("--finite-surf-shakemap-min-mag"); argz.add("6");
				argz.add("--comcat-file"); argz.add(comcatFile.getAbsolutePath());
				argz.add("--skip-input-plots");
			} else {
				argz.add("--start-time"); argz.add(myStart.getTimeInMillis()+"");
			}
			argz.add("--name"); argz.add(name);
			
			argz.add("--hpc-site"); argz.add("TACC_STAMPEDE2");
			argz.add("--nodes"); argz.add("10");
			argz.add("--hours"); argz.add("10");
			argz.add("--threads"); argz.add("20");
			argz.add("--queue"); argz.add("skx-normal");
			argz.add("--output-dir"); argz.add(outputDir);
			
			System.out.println(name);
			System.out.println("\tARGS: "+argz);
			args = argz.toArray(new String[0]);
			if (comcat)
				ETAS_ComcatConfigBuilder.main(args);
			else
				ETAS_ConfigBuilder.main(args);
			
			try {
				Thread.sleep(10); // ensure a new random seed next time
			} catch (InterruptedException e) {}
		}
	}

}
