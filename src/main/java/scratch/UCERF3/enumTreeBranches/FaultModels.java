package scratch.UCERF3.enumTreeBranches;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.XMLUtils;
import org.opensha.refFaultParamDb.dao.db.DB_AccessAPI;
import org.opensha.refFaultParamDb.dao.db.DB_ConnectionPool;
import org.opensha.refFaultParamDb.dao.db.FaultModelDB_DAO;
import org.opensha.refFaultParamDb.dao.db.PrefFaultSectionDataDB_DAO;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.NamedFaults;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.RegionsOfInterest;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.inversion.U3InversionTargetMFDs;
import scratch.UCERF3.logicTree.U3LogicTreeBranchNode;
import scratch.UCERF3.utils.U3FaultSystemIO;
import scratch.UCERF3.utils.UCERF3_DataUtils;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum FaultModels implements U3LogicTreeBranchNode<FaultModels>, RupSetFaultModel {

	FM2_1(	"Fault Model 2.1",	41,		0d),
	FM3_1(	"Fault Model 3.1",	101,	0.5d),
	FM3_2(	"Fault Model 3.2",	102,	0.5d);

	public static final String XML_ELEMENT_NAME = "FaultModel";
	public static final String FAULT_MODEL_STORE_PROPERTY_NAME = "FaultModelStore";
	private static final String FAULT_MODEL_STORE_DIR_NAME = "FaultModels";
	
	private String modelName;
	private int id;
	private double weight;
	private Map<Integer, List<Integer>> namedFaultsMap;
	private Map<String, List<Integer>> namedFaultsMapAlt;
	
	private FaultModels(String modelName, int id, double weight) {
		this.modelName = modelName;
		this.id = id;
		this.weight = weight;
	}
	
	public String getName() {
		return modelName;
	}
	
	public String getShortName() {
		return name();
	}
	
	public int getID() {
		return id;
	}
	
	/**
	 * This returns the Deformation Model that should be used for construction of rupture sets, or null
	 * if any can be used.
	 * 
	 * @return
	 */
	public DeformationModels getFilterBasis() {
		// this has to be hard coded here because DeformationModels can't be instantiated before
		// fault models because they depend on fault models. Complicated enum order of operations
		// junk - just trust me.
		switch (this) {
		case FM3_1:
			return DeformationModels.GEOLOGIC;
		case FM3_2:
			return DeformationModels.GEOLOGIC;

		default:
			return null;
		}
	}
	
	public DB_AccessAPI getDBAccess() {
		switch (this) {
		case FM2_1:
			return DB_ConnectionPool.getDB2ReadOnlyConn();
		case FM3_1:
			return DB_ConnectionPool.getDB3ReadOnlyConn();
		case FM3_2:
			return DB_ConnectionPool.getDB3ReadOnlyConn();

		default:
			throw new IllegalStateException("DB access cannot be created for Fault Model: "+this);
		}
	}
	
	public static ArrayList<FaultSection> loadStoredFaultSections(File fmStoreFile)
			throws MalformedURLException, DocumentException {
		System.out.println("Loading fault model from: "+fmStoreFile.getAbsolutePath());
		Document doc = XMLUtils.loadDocument(fmStoreFile);
		return loadStoredFaultSections(doc);
	}
	
	public static ArrayList<FaultSection> loadStoredFaultSections(Document doc) {
		Element root = doc.getRootElement();
		return U3FaultSystemIO.fsDataFromXML(root.element("FaultModel"));
	}
	
	public Map<Integer, FaultSection> getFaultSectionIDMap() {
		Map<Integer, FaultSection> map = Maps.newHashMap();
		
		for (FaultSection sect : getFaultSections())
			map.put(sect.getSectionId(), sect);
		
		return map;
	}
	
	@Override
	public List<FaultSection> getFaultSections() {
		return fetchFaultSections(false);
	}
	
	public ArrayList<FaultSection> fetchFaultSections(boolean ignoreCache) {
		if (!ignoreCache) {
			// this lets us load the FM from XML if we're on the cluster
			String fmFileName = getShortName()+".xml";
			
			// first see if the system property was set
			String fmStoreProp = System.getProperty("FaultModelStore");
			if (fmStoreProp != null) {
				File fmStoreFile = new File(fmStoreProp, fmFileName);
				if (fmStoreFile.exists()) {
					try {
						return loadStoredFaultSections(fmStoreFile);
					} catch (Exception e) {
						// ok to fail here, will try it the other way
						e.printStackTrace();
					}
				}
			}
			
			// now see if they're cached in the project itself
			try {
				InputStream is = UCERF3_DataUtils.locateResourceAsStream(FAULT_MODEL_STORE_DIR_NAME, fmFileName);
				System.out.println("Loading FM from cached file: "+fmFileName);
				return loadStoredFaultSections(XMLUtils.loadDocument(is));
			} catch (Exception e) {
				// an exception is fine here - means that the data file doesn't exist. load directly from the database
			}
		}
		
		System.out.println("Loading FM from database: "+this);
		// load directly from the database
		DB_AccessAPI db = getDBAccess();
		PrefFaultSectionDataDB_DAO pref2db = new PrefFaultSectionDataDB_DAO(db);
		ArrayList<FaultSectionPrefData> datas = pref2db.getAllFaultSectionPrefData();
		FaultModelDB_DAO fm2db = new FaultModelDB_DAO(db);
		ArrayList<Integer> faultSectionIds = fm2db.getFaultSectionIdList(id);

		ArrayList<FaultSection> faultModel = new ArrayList<FaultSection>();
		for (FaultSection data : datas) {
			if (!faultSectionIds.contains(data.getSectionId()))
				continue;
			faultModel.add(data);
		}

		return faultModel;
	}
	
	/**
	 * This returns a mapping between fault section ids and other faults with the same name. Note that the
	 * returned list will include the section id of the requested id in addition to other faults with the same
	 * name.
	 * @return
	 */
	public Map<Integer, List<Integer>> getNamedFaultsMap() {
		if (namedFaultsMap == null) {
			synchronized (this) {
				try {
					Map<Integer, List<Integer>> map = Maps.newHashMap();
					
					BufferedReader br = new BufferedReader(UCERF3_DataUtils.getReader(FAULT_MODEL_STORE_DIR_NAME,
							getShortName()+"FaultsByName.txt"));
					
					String line = br.readLine();
					
					Splitter s = Splitter.on('\t');
					
					while (line != null) {
						line = line.trim();
						if (!line.isEmpty()) {
							ArrayList<Integer> sects = Lists.newArrayList();
							
							for (String idStr : s.split(line))
								sects.add(Integer.parseInt(idStr));
							
							Preconditions.checkState(!sects.isEmpty(), "Shouldn't be empty here!");
							
							for (Integer id : sects) {
								map.put(id, sects);
							}
						}
						
						line = br.readLine();
					}
					
					if (namedFaultsMap == null)
						namedFaultsMap = map;
				} catch (Throwable t) {
					throw ExceptionUtils.asRuntimeException(t);
				}
			}
		}
		return namedFaultsMap;
	}
	
	
	/**
	 * This returns a mapping between a named fault (String keys) and the sections included in the
	 * named fault (sections IDs ids).
	 * name.
	 * @return
	 */
	public Map<String, List<Integer>> getNamedFaultsMapAlt() {
		if (namedFaultsMapAlt == null) {
			synchronized (this) {
				try {
					Reader reader = UCERF3_DataUtils.getReader(FAULT_MODEL_STORE_DIR_NAME,
							getShortName()+"FaultsByNameAlt.txt");
					Map<String, List<Integer>> map = parseNamedFaultsAltFile(reader);
					
					if (namedFaultsMapAlt == null)
						namedFaultsMapAlt = map;
				} catch (Throwable t) {
					throw ExceptionUtils.asRuntimeException(t);
				}
			}
		}
		return namedFaultsMapAlt;
	}

	public static Map<String, List<Integer>> parseNamedFaultsAltFile(Reader reader) throws IOException {
		Map<String, List<Integer>> map = Maps.newHashMap();
		
		BufferedReader br;
		if (reader instanceof BufferedReader)
			br = (BufferedReader)reader;
		else
			br = new BufferedReader(reader);
		
		String line = br.readLine();
		
		Splitter s = Splitter.on('\t');
		
		while (line != null) {
			line = line.trim();
			if (!line.isEmpty()) {
				ArrayList<Integer> sects = Lists.newArrayList();
				
				String faultname = null;
				boolean isFirst = true;
				for (String idStr : s.split(line)) {
					if(isFirst) {
						faultname = idStr.trim();
						isFirst=false;
					}
					else
						sects.add(Integer.parseInt(idStr));
				}
				
				Preconditions.checkState(!sects.isEmpty(), "Shouldn't be empty here!");
				
				map.put(faultname, sects);
			}
			
			line = br.readLine();
		}
		br.close();
		return map;
	}
	
	@Override
	public String toString() {
		return getName();
	}

	@Override
	public double getRelativeWeight(InversionModels im) {
		return weight;
	}

	@Override
	public String encodeChoiceString() {
		return getShortName();
	}
	
	@Override
	public String getBranchLevelName() {
		return "Fault Model";
	}
	
	@Override
	public String getShortBranchLevelName() {
		return "FM";
	}

	@Override
	public RupSetDeformationModel getDefaultDeformationModel() {
		return getFilterBasis();
	}
	
	@Override
	public void attachDefaultModules(FaultSystemRupSet rupSet) {
		Map<String, List<Integer>> namedFaultsMap = getNamedFaultsMapAlt();
		if (namedFaultsMap != null) {
			rupSet.addAvailableModule(new Callable<NamedFaults>() {

				@Override
				public NamedFaults call() throws Exception {
					return new NamedFaults(rupSet, getNamedFaultsMapAlt());
				}
			}, NamedFaults.class);
		}
		
		rupSet.addAvailableModule(new Callable<RegionsOfInterest>() {

			@Override
			public RegionsOfInterest call() throws Exception {
				return new RegionsOfInterest(
						new CaliforniaRegions.RELM_NOCAL(),
						new CaliforniaRegions.RELM_SOCAL(),
						new CaliforniaRegions.SF_BOX(),
						new CaliforniaRegions.LA_BOX());
			}
		}, RegionsOfInterest.class);
		
		rupSet.addAvailableModule(new Callable<PolygonFaultGridAssociations>() {

			@Override
			public PolygonFaultGridAssociations call() throws Exception {
				if (FaultModels.this == FaultModels.FM3_1 || FaultModels.this == FaultModels.FM3_2) {
					try {
						return FaultPolyMgr.loadSerializedUCERF3(FaultModels.this);
					} catch (IOException e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
				}
				return FaultPolyMgr.create(rupSet.getFaultSectionDataList(), U3InversionTargetMFDs.FAULT_BUFFER);
			}
		}, PolygonFaultGridAssociations.class);
	}
	
}
