package org.opensha.sha.calc.hazardMap.components;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.FileUtils;
import org.opensha.sha.calc.hazardMap.dagGen.HazardDataSetDAGCreator;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.imr.AbstractIMR;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

/**
 * This class represents all of the inputs to the hazard map calculation process,
 * and handles writing/loading them to/from XML.
 * 
 * @author kevin
 *
 */
public class CalculationInputsXMLFile implements XMLSaveable {
	
	private ERF erf;
	private List<Map<TectonicRegionType, ScalarIMR>> imrMaps;
	private List<Site> sites;
	private List<Parameter<Double>> imts;
	private CalculationSettings calcSettings;
	private CurveResultsArchiver archiver;
	
	private boolean erfSerialized = false;
	private String serializedERFFile;
	
	public CalculationInputsXMLFile(ERF erf,
			List<Map<TectonicRegionType, ScalarIMR>> imrMaps,
			List<Site> sites,
			CalculationSettings calcSettings,
			CurveResultsArchiver archiver) {
		this(erf, imrMaps, null, sites, calcSettings, archiver);
	}
	
	public CalculationInputsXMLFile(ERF erf,
		List<Map<TectonicRegionType, ScalarIMR>> imrMaps,
		List<Parameter<Double>> imts,
		List<Site> sites,
		CalculationSettings calcSettings,
		CurveResultsArchiver archiver) {
		this.erf = erf;
		this.imrMaps = imrMaps;
		this.imts = imts;
		this.sites = sites;
		this.calcSettings = calcSettings;
		this.archiver = archiver;
	}
	
	public ERF getERF() {
		return erf;
	}
	
	public void serializeERF(String odir) throws IOException {
		erf.updateForecast();
		FileUtils.saveObjectInFile(serializedERFFile, erf);
		String serializedERFFile = odir + HazardDataSetDAGCreator.ERF_SERIALIZED_FILE_NAME;
		setSerialized(serializedERFFile);
	}
	
	public void setSerialized(String serializedERFFile) {
		erfSerialized = serializedERFFile != null;
		this.serializedERFFile = serializedERFFile;
	}

	public List<Map<TectonicRegionType, ScalarIMR>> getIMRMaps() {
		return imrMaps;
	}
	
	public void setIMTs(List<Parameter<Double>> imts) {
		this.imts = imts;
	}
	
	public List<Parameter<Double>> getIMTs() {
		return this.imts;
	}

	public List<Site> getSites() {
		return sites;
	}

	public CalculationSettings getCalcSettings() {
		return calcSettings;
	}

	public CurveResultsArchiver getArchiver() {
		return archiver;
	}

	public Element toXMLMetadata(Element root) {
		if (erf instanceof AbstractERF) {
			AbstractERF newERF = (AbstractERF)erf;
			root = newERF.toXMLMetadata(root);
			if (erfSerialized) {
				// load the erf element from metadata
				Element erfElement = root.element(AbstractERF.XML_METADATA_NAME);

				// rename the old erf to ERF_REF so that the params are preserved, but it is not used for calculation
				root.add(erfElement.createCopy("ERF_REF"));
				erfElement.detach();
				
				// create new ERF element and add to root
				Element newERFElement = root.addElement(AbstractERF.XML_METADATA_NAME);
				newERFElement.addAttribute("fileName", serializedERFFile);
			}
		} else {
			throw new ClassCastException("Currently only EqkRupForecast subclasses can be saved" +
			" to XML.");
		}
		ArrayList<ScalarIMR> imrs =
			new ArrayList<ScalarIMR>();
		ArrayList<Map<TectonicRegionType, ScalarIMR>> newList =
			new ArrayList<Map<TectonicRegionType,ScalarIMR>>();
		for (Map<TectonicRegionType,ScalarIMR> map : imrMaps) {
			newList.add(map);
			for (TectonicRegionType tect : map.keySet()) {
				ScalarIMR imr = map.get(tect);
				boolean add = true;
				for (ScalarIMR newIMR : imrs) {
					if (newIMR.getShortName().equals(imr.getShortName())) {
						add = false;
						break;
					}
				}
				if (add)
					imrs.add(imr);
			}
		}
		imrsToXML(imrs, root);
		imrMapsToXML(newList, imts, root);
		Site.writeSitesToXML(sites, root);
		calcSettings.toXMLMetadata(root);
		archiver.toXMLMetadata(root);
		return null;
	}
	
	public static CalculationInputsXMLFile loadXML(Document doc) throws InvocationTargetException, IOException {
		return loadXML(doc, 1, false)[0];
	}
	
	public static CalculationInputsXMLFile[] loadXML(Document doc, int threads, boolean multERFs)
	throws InvocationTargetException, IOException {
		Preconditions.checkArgument(threads >= 1, "threads must be >= 1");
		Element root = doc.getRootElement();
		
		/* Load the ERF 							*/
		ERF[] erfs = new ERF[threads];
		for (int i=0; i<threads; i++) {
			ERF erf;
			if (i == 0 || multERFs) {
				Element erfElement = root.element(AbstractERF.XML_METADATA_NAME);
				Attribute className = erfElement.attribute("className");
				if (className == null) { // load it from a file
					String erfFileName = erfElement.attribute("fileName").getValue();
					erf = (AbstractERF)FileUtils.loadObject(erfFileName);
				} else {
					erf = AbstractERF.fromXMLMetadata(erfElement);
					System.out.println("Updating Forecast");
					erf.updateForecast();
				}
			} else {
				// this means i>0 but not using mult erfs
				erf = erfs[0]; // just resuse the first one
			}
			erfs[i] = erf;
		}
		
		/* Load the IMRs							*/
		Element imrsEl = root.element(XML_IMRS_NAME);
		ArrayList<ArrayList<ScalarIMR>> imrsList = new ArrayList<ArrayList<ScalarIMR>>();
		for (int i=0; i<threads; i++) {
			ArrayList<ScalarIMR> imrs = imrsFromXML(imrsEl);
			imrsList.add(imrs);
		}
		
		ArrayList<Parameter<?>> paramsToAdd = new ArrayList<Parameter<?>>();
		for (ScalarIMR imr : imrsList.get(0)) {
			for (Parameter<?> param : imr.getSiteParams()) {
				boolean add = true;
				for (Parameter<?> prevParam : paramsToAdd) {
					if (param.getName().equals(prevParam.getName())) {
						add = false;
						break;
					}
				}
				if (add)
					paramsToAdd.add(param);
			}
		}
		
		/* Load the IMR Maps						*/
		Element imrMapsEl = root.element(XML_IMR_MAP_LIST_NAME);
		ArrayList<List<Map<TectonicRegionType, ScalarIMR>>> imrMapsList =
			new  ArrayList<List<Map<TectonicRegionType, ScalarIMR>>>();
		for (int i=0; i<threads; i++) {
			imrMapsList.add(imrMapsFromXML(imrsList.get(i), imrMapsEl));
		}
		
		/* Load the IMTs if applicaple				*/
		List<Parameter<Double>> imts = imtsFromXML(imrsList.get(0).get(0), imrMapsEl);
		
		/* Load the sites 							*/
		Element sitesEl = root.element(Site.XML_METADATA_LIST_NAME);
		ArrayList<Site> sites = Site.loadSitesFromXML(sitesEl, paramsToAdd);
		
		/* Load calc settings						*/
		Element calcSettingsEl = root.element(CalculationSettings.XML_METADATA_NAME);
		CalculationSettings calcSettings = CalculationSettings.fromXMLMetadata(calcSettingsEl);
		
		/* Load Curve Archiver						*/
		CurveResultsArchiver archiver;
		// first try ASCII
		Element archiverEl = root.element(AsciiFileCurveArchiver.XML_METADATA_NAME);
		if (archiverEl != null) {
			archiver = AsciiFileCurveArchiver.fromXMLMetadata(archiverEl);
		} else {
			archiverEl = root.element(BinaryCurveArchiver.XML_METADATA_NAME);
			Map<String, DiscretizedFunc> xValsMap = Maps.newHashMap();
			List<Map<TectonicRegionType, ScalarIMR>> thread0IMRMaps = imrMapsList.get(0);
			for (int i=0; i<thread0IMRMaps.size(); i++) {
				// i is the actual imr index
				ScalarIMR imr = thread0IMRMaps.get(i).values().iterator().next();
				xValsMap.put("imrs"+(i+1), calcSettings.getXValues(imr.getIntensityMeasure().getName()));
			}
			archiver = BinaryCurveArchiver.fromXMLMetadata(archiverEl, sites.size(), xValsMap);
		}
		
		CalculationInputsXMLFile[] inputs = new CalculationInputsXMLFile[threads];
		
		for (int i=0; i<threads; i++) {
			inputs[i] = new CalculationInputsXMLFile(erfs[i], imrMapsList.get(i), imts, sites, calcSettings, archiver);
		}
		
		return inputs;
	}
	
	public static final String XML_IMRS_NAME = "IMRs";
	
	public static Element imrsToXML(ArrayList<ScalarIMR> imrs,
			Element root) {
		Element imrsEl = root.addElement(XML_IMRS_NAME);
		
		for (ScalarIMR imr : imrs) {
			if (imr instanceof AbstractIMR) {
				AbstractIMR attenRel = (AbstractIMR)imr;
				attenRel.toXMLMetadata(imrsEl);
			} else {
				throw new ClassCastException("Currently only IntensityMeasureRelationship subclasses can be saved" +
						" to XML.");
			}
		}
		
		return root;
	}
	
	public static ArrayList<ScalarIMR> imrsFromXML(Element imrsEl) throws InvocationTargetException {
		ArrayList<ScalarIMR> imrs =
			new ArrayList<ScalarIMR>();
		
		Iterator<Element> it = imrsEl.elementIterator();
		while (it.hasNext()) {
			Element imrEl = it.next();
			
			ScalarIMR imr = 
				(ScalarIMR) AbstractIMR.fromXMLMetadata(imrEl, null);
			imrs.add(imr);
		}
		
		return imrs;
	}
	
	public static final String XML_IMR_MAP_NAME = "IMR_Map";
	public static final String XML_IMR_MAPING_NAME = "IMR_Maping";
	
	public static Element imrMapToXML(Map<TectonicRegionType, ScalarIMR> map,
			List<Parameter<Double>> imts,
			Element root, int index) {
		Element mapEl = root.addElement(XML_IMR_MAP_NAME);
		mapEl.addAttribute("index", index + "");
		
		for (TectonicRegionType tect : map.keySet()) {
			Element mapingEl = mapEl.addElement(XML_IMR_MAPING_NAME);
			ScalarIMR imr = map.get(tect);
			mapingEl.addAttribute("tectonicRegionType", tect.toString());
			mapingEl.addAttribute("imr", imr.getShortName());
		}
		
		if (imts != null) {
			imts.get(index).toXMLMetadata(mapEl, AbstractIMR.XML_METADATA_IMT_NAME);
		}
		
		return root;
	}
	
	public static Parameter<Double> imtFromXML(
			ScalarIMR testIMR,
			Element imrMapEl) {
		Element imtElem = imrMapEl.element(AbstractIMR.XML_METADATA_IMT_NAME);
		if (imtElem == null)
			return null;
		
		String imtName = imtElem.attributeValue("name");

		System.out.println("IMT Name: " + imtName);

		testIMR.setIntensityMeasure(imtName);

		Parameter<Double> imt = (Parameter<Double>) testIMR.getIntensityMeasure();

		imt.setValueFromXMLMetadata(imtElem);
		
		return imt;
	}
	
	public static List<Parameter<Double>> imtsFromXML(
			ScalarIMR testIMR,
			Element imrMapsEl) {
		ArrayList<Parameter<Double>> imts = new ArrayList<Parameter<Double>>();
		
		Iterator<Element> it = imrMapsEl.elementIterator(XML_IMR_MAP_NAME);
		
		// this makes sure they get loaded in correct order
		HashMap<Integer, Parameter<Double>> listsMap = 
			new HashMap<Integer, Parameter<Double>>();
		while (it.hasNext()) {
			Element imrMapEl = it.next();
			int index = Integer.parseInt(imrMapEl.attributeValue("index"));
			Parameter<Double> imt = imtFromXML(testIMR, imrMapEl);
			if (imt == null)
				return null;
			listsMap.put(new Integer(index), (Parameter<Double>) imt.clone());
		}
		for (int i=0; i<listsMap.size(); i++) {
			Parameter<Double> imt = listsMap.get(i);
			String meta = imt.getName();
			if (imt.getName().equals(SA_Param.NAME)) {
				double period = (Double) imt.getIndependentParameter(PeriodParam.NAME).getValue();
				meta += " (Period: "+period+" sec)";
			}
			System.out.println("IMT "+i+": "+meta);
			imts.add(imt);
		}
		
		return imts;
	}
	
	public static Map<TectonicRegionType, ScalarIMR> imrMapFromXML(
			List<ScalarIMR> imrs, Element imrMapEl) {
		HashMap<TectonicRegionType, ScalarIMR> map =
			new HashMap<TectonicRegionType, ScalarIMR>();
		
		Iterator<Element> it = imrMapEl.elementIterator(XML_IMR_MAPING_NAME);
		
		while (it.hasNext()) {
			Element mappingEl = it.next();
			
			String tectName = mappingEl.attributeValue("tectonicRegionType");
			String imrName = mappingEl.attributeValue("imr");
			
			TectonicRegionType tect = TectonicRegionType.getTypeForName(tectName);
			ScalarIMR imr = null;
			for (ScalarIMR testIMR : imrs) {
				if (imrName.equals(testIMR.getShortName())) {
					imr = testIMR;
					break;
				}
			}
			if (imr == null)
				throw new RuntimeException("IMR '" + imrName + "' not found in XML mapping lookup");
			map.put(tect, imr);
		}
		
		return map;
	}
	
	public static final String XML_IMR_MAP_LIST_NAME = "IMR_Maps";
	
	public static Element imrMapsToXML(
			ArrayList<Map<TectonicRegionType, ScalarIMR>> maps,
			List<Parameter<Double>> imts, Element root) {
		Element mapsEl = root.addElement(XML_IMR_MAP_LIST_NAME);
		
		for (int i=0; i<maps.size(); i++) {
			Map<TectonicRegionType, ScalarIMR> map = maps.get(i);
			mapsEl = imrMapToXML(map, imts, mapsEl, i);
		}
		
		return root;
	}
	
	public static List<Map<TectonicRegionType, ScalarIMR>> imrMapsFromXML(
			List<ScalarIMR> imrs,
			Element imrMapsEl) {
		ArrayList<Map<TectonicRegionType, ScalarIMR>> maps =
			new ArrayList<Map<TectonicRegionType,ScalarIMR>>();
		
		Iterator<Element> it = imrMapsEl.elementIterator(XML_IMR_MAP_NAME);
		
		// this makes sure they get loaded in correct order
		Map<Integer, Map<TectonicRegionType,ScalarIMR>> mapsMap = 
			new HashMap<Integer, Map<TectonicRegionType,ScalarIMR>>();
		while (it.hasNext()) {
			Element imrMapEl = it.next();
			int index = Integer.parseInt(imrMapEl.attributeValue("index"));
			Map<TectonicRegionType, ScalarIMR> map = imrMapFromXML(imrs, imrMapEl);
			mapsMap.put(new Integer(index), map);
		}
		for (int i=0; i<mapsMap.size(); i++) {
			maps.add(mapsMap.get(new Integer(i)));
		}
		
		return maps;
	}

}
