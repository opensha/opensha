package scratch.UCERF3.utils.finiteFaultMap;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.LogPrintStream;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupOrigTimeComparator;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.earthquake.observedEarthquake.parsers.UCERF3_CatalogParser;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.GriddedSurfaceImpl;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.utils.FaultSystemIO;

/**
 * Data structure for saving/loading finite fault mapping data
 * 
 * @author kevin
 *
 */
public class FiniteFaultMappingData implements XMLSaveable {
	
	private List<ObsEqkRupture> rups;
	private List<Map<FaultModels, Integer>> mappedRupIndexes;
	private List<RuptureSurface> mappedSurfaces;
	private Map<ObsEqkRupture, Integer> rupIndexMap;
	
	private FiniteFaultMappingData() {
		rups = Lists.newArrayList();
		mappedRupIndexes = Lists.newArrayList();
		mappedSurfaces = Lists.newArrayList();
		
		rupIndexMap = Maps.newHashMap();
	}
	
	private void addMapping(ObsEqkRupture rup, Map<FaultModels, Integer> mappings, RuptureSurface surface) {
		Preconditions.checkNotNull(rup);
		Preconditions.checkNotNull(rup.getEventId());
		Preconditions.checkState(rup.getEventId().length() > 0);
		Preconditions.checkState(mappings != null || surface != null);
		Preconditions.checkState(mappings == null || surface == null);
		
		rupIndexMap.put(rup, rups.size());
		
		rups.add(rup);
		mappedRupIndexes.add(mappings);
		mappedSurfaces.add(surface);
	}
	
	private RuptureSurface getSurface(ObsEqkRupture rup, FaultModels fm, FaultSystemRupSet rupSet) {
		int index = rupIndexMap.get(rup);
		
		Map<FaultModels, Integer> mappings = mappedRupIndexes.get(index);
		if (mappings != null) {
			Integer rupIndex = mappings.get(fm);
			Preconditions.checkNotNull(rupIndex, "No mapping exists for "+fm.name());
			return rupSet.getSurfaceForRupupture(rupIndex, 1d, false);
		}
		RuptureSurface external = mappedSurfaces.get(index);
		Preconditions.checkNotNull(external);
		return external;
	}
	
	private boolean hasMapping(ObsEqkRupture rupture) {
		return rupIndexMap.containsKey(rupture);
	}
	
	private static final DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss z");

	@Override
	public Element toXMLMetadata(Element root) {
		Element el = root.addElement("FiniteFaultMappingData");
		
		for (int i=0; i<rups.size(); i++) {
			ObsEqkRupture rup = rups.get(i);
			Element rupEl = el.addElement("HistoricalRupture");
			rupEl.addAttribute("id", rup.getEventId());
			rupEl.addAttribute("date", df.format(rup.getOriginTimeCal().getTime()));
			rupEl.addAttribute("mag", rup.getMag()+"");
			
			Map<FaultModels, Integer> mappedIndexes = mappedRupIndexes.get(i);
			if (mappedIndexes != null) {
				Element mapEl = rupEl.addElement("RupSetMappings");
				Preconditions.checkState(!mappedIndexes.isEmpty());
				for (FaultModels fm : mappedIndexes.keySet()) {
					Element fmEl = mapEl.addElement(fm.name());
					fmEl.addAttribute("rupIndex", mappedIndexes.get(fm)+"");
				}
			} else {
				RuptureSurface surf = mappedSurfaces.get(i);
				Preconditions.checkNotNull(surf);
				Preconditions.checkState(surf instanceof XMLSaveable);
				((XMLSaveable)surf).toXMLMetadata(rupEl);
			}
		}
		
		return root;
	}
	
	public static void loadRuptureSurfaces(File xmlFile, ObsEqkRupList ruptures,
			FaultModels fm, FaultSystemRupSet rupSet) throws MalformedURLException, DocumentException {
		loadRuptureSurfaces(XMLUtils.loadDocument(xmlFile), ruptures, fm, rupSet);
	}
	
	public static void loadRuptureSurfaces(InputStream is, ObsEqkRupList ruptures,
			FaultModels fm, FaultSystemRupSet rupSet) throws DocumentException {
		loadRuptureSurfaces(XMLUtils.loadDocument(is), ruptures, fm, rupSet);
	}
	
	public static void loadRuptureSurfaces(Document doc, ObsEqkRupList ruptures,
			FaultModels fm, FaultSystemRupSet rupSet) {
		FiniteFaultMappingData data = load(doc, ruptures);
		
		int count = 0;
		int numMapped = 0;
		
		for (int i=0; i<ruptures.size(); i++) {
			ObsEqkRupture rup = ruptures.get(i);
			if (!data.hasMapping(rup))
				continue;
			count++;
			RuptureSurface surf = data.getSurface(rup, fm, rupSet);
			rup.setRuptureSurface(surf);
			if (surf instanceof CompoundSurface) {
				// set FSS index
				int obsRupIndex = data.rupIndexMap.get(rup);
				int rupIndex = data.mappedRupIndexes.get(obsRupIndex).get(fm);
				ETAS_EqkRupture etasRup = new ETAS_EqkRupture(rup);
				etasRup.setFSSIndex(rupIndex);
				ruptures.set(i, etasRup);
				
				numMapped++;
			}
		}
		System.out.println("Loaded finite fault surfaces for "+count+" ruptures ("+numMapped+" are FaultSystemRupSet ruptures)");
	}
	
	static FiniteFaultMappingData load(File xmlFile, List<? extends ObsEqkRupture> ruptures)
			throws MalformedURLException, DocumentException {
		return load(XMLUtils.loadDocument(xmlFile), ruptures);
	}
	
	static FiniteFaultMappingData load(Document doc, List<? extends ObsEqkRupture> ruptures) {
		Element root = doc.getRootElement();
		Element el = root.element("FiniteFaultMappingData");
		
		FiniteFaultMappingData data = new FiniteFaultMappingData();
		
		for (Element rupEl : XMLUtils.getSubElementsList(el, "HistoricalRupture")) {
			Date date;
			try {
				date = df.parse(rupEl.attributeValue("date"));
			} catch (ParseException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			String id = rupEl.attributeValue("id");
			double mag = Double.parseDouble(rupEl.attributeValue("mag"));
			
			ObsEqkRupture match = findMatch(ruptures, id, date, mag);
			
			Element mappedIndexesEl = rupEl.element("RupSetMappings");
			Element griddedSurfEl = rupEl.element("GriddedSurfaceImpl");
			Element arbDiscSurfEl = rupEl.element("ArbitrarilyDiscretizedSurface");
			Map<FaultModels, Integer> mappedIndexes = null;
			RuptureSurface externalSurface = null;
			if (mappedIndexesEl != null) {
				mappedIndexes = Maps.newHashMap();
				for (Element subEl : XMLUtils.getSubElementsList(mappedIndexesEl)) {
					FaultModels fm = FaultModels.valueOf(subEl.getName());
					int index = Integer.parseInt(subEl.attributeValue("rupIndex"));
					mappedIndexes.put(fm, index);
				}
			} else if (griddedSurfEl != null) {
				externalSurface = GriddedSurfaceImpl.fromXMLMetadata(rupEl.element(GriddedSurfaceImpl.XML_METADATA_NAME));
			} else {
				Preconditions.checkNotNull(arbDiscSurfEl);
				externalSurface = ArbitrarilyDiscretizedSurface.fromXMLMetadata(
						rupEl.element(ArbitrarilyDiscretizedSurface.XML_METADATA_NAME));
			}
			
			data.addMapping(match, mappedIndexes, externalSurface);
		}
		
		return data;
	}
	
	private static ObsEqkRupture findMatch(List<? extends ObsEqkRupture> ruptures, String id, Date date, double mag) {
		for (ObsEqkRupture rup : ruptures) {
			if (rup.getEventId().equals(id) && (float)mag == (float)rup.getMag()) {
				// now check date
				String matchDateStr = df.format(rup.getOriginTimeCal().getTime());
				if (df.format(date).equals(matchDateStr))
					return rup;
			}
		}
		throw new IllegalStateException("Rupture not found: id="+id+", mag="+mag+", date="+df.format(date));
	}

	public static void main(String[] args) throws IOException, DocumentException {
		File outputDir = new File("/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/EarthquakeCatalog");
		File logFile = new File(outputDir, "finite_fault_mappings_log.txt");
		FileWriter log_fw = new FileWriter(logFile);
		System.setOut(new LogPrintStream(System.out, log_fw));
		System.setErr(new LogPrintStream(System.err, log_fw));
		
		File finiteFile = new File("/home/kevin/OpenSHA/UCERF3/historical_finite_fault_mapping/UCERF3_finite.dat");
		ObsEqkRupList inputRups = UCERF3_CatalogParser.loadCatalog(
				new File("/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/EarthquakeCatalog/ofr2013-1165_EarthquakeCat.txt"));
		File outputFile = new File(outputDir, "finite_fault_mappings.xml");
		
		FaultSystemRupSet rupSet31 = FaultSystemIO.loadRupSet(new File("/home/kevin/workspace/OpenSHA/dev/scratch/"
				+ "UCERF3/data/scratch/InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_"
				+ "FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
		FaultSystemRupSet rupSet32 = FaultSystemIO.loadRupSet(new File("/home/kevin/workspace/OpenSHA/dev/scratch/"
				+ "UCERF3/data/scratch/InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_"
				+ "FM3_2_MEAN_BRANCH_AVG_SOL.zip"));
		
//		if (outputFile.exists()) {
//			loadRuptureSurfaces(outputFile, inputRups, FaultModels.FM3_1, rupSet31);
//			System.exit(0);
//		}
		
		for (ObsEqkRupture rup : inputRups) {
			if (rup.getHypocenterLocation().getDepth() > 24 && rup.getMag() >= 4)
				System.out.println(rup.getHypocenterLocation()+", mag="+rup.getMag());
		}
		
		List<ObsEqkRupture> finiteRups = JeanneFileLoader.loadFiniteRups(finiteFile, inputRups);
		Collections.sort(finiteRups, new ObsEqkRupOrigTimeComparator());
//		finiteRups = finiteRups.subList(0, 1);
		System.out.println("Loaded "+finiteRups.size()+" finite rups");
		
		Map<FaultModels, FiniteFaultMapper> mappers = Maps.newHashMap();
		
		mappers.put(FaultModels.FM3_1, new FiniteFaultMapper(rupSet31, true, true));
		mappers.put(FaultModels.FM3_2, new FiniteFaultMapper(rupSet32, true, true));
		
		FiniteFaultMappingData data = new FiniteFaultMappingData();
		
		for (ObsEqkRupture rup : finiteRups) {
			Map<FaultModels, Integer> matches = null;
			RuptureSurface externalSurf = null;
			for (FaultModels fm : mappers.keySet()) {
				FiniteFaultMapper mapper = mappers.get(fm);
				int rupIndex = mapper.getMappedRup(rup);
				if (rupIndex < 0) {
					Preconditions.checkState(matches == null, "No match for "+fm.name()+" but had one in another FM");
					externalSurf = rup.getRuptureSurface();
					Preconditions.checkNotNull(externalSurf);
				} else {
					Preconditions.checkState(externalSurf == null, "Match for "+fm.name()+" but no match in another FM");
					if (matches == null)
						matches = Maps.newHashMap();
					matches.put(fm, rupIndex);
				}
			}
			data.addMapping(rup, matches, externalSurf);
		}
		
		Document doc = XMLUtils.createDocumentWithRoot();
		data.toXMLMetadata(doc.getRootElement());
		XMLUtils.writeDocumentToFile(outputFile, doc);
		
		log_fw.close();
	}

}
