package scratch.UCERF3.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.opensha.commons.data.function.AbstractDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.metadata.MetadataLoader;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.XMLUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import scratch.UCERF3.AverageFaultSystemSolution;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.griddedSeismicity.GridSourceFileReader;
import scratch.UCERF3.griddedSeismicity.GridSourceProvider;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.inversion.laughTest.UCERF3PlausibilityConfig;
import scratch.UCERF3.inversion.laughTest.OldPlausibilityConfiguration;
import scratch.UCERF3.logicTree.LogicTreeBranch;

public class FaultSystemIO {
	
	private static final boolean D = true;
	private static final boolean DD = D && false;
	
	/*	******************************************
	 * 		FILE READING
	 *	******************************************/
	
	/**
	 * Loads a FaultSystemRupSet from a zip file. If possible, it will be loaded as an applicable subclass.
	 * @param file
	 * @return
	 * @throws DocumentException 
	 * @throws IOException 
	 * @throws ZipException 
	 */
	public static FaultSystemRupSet loadRupSet(File file) throws ZipException, IOException, DocumentException {
		return loadRupSetAsApplicable(file);
	}
	
	/**
	 * Load an InversionFaultSystemRupSet from a zip file.
	 * @param file
	 * @return
	 * @throws DocumentException 
	 * @throws IOException 
	 * @throws ZipException 
	 */
	public static InversionFaultSystemRupSet loadInvRupSet(File file) throws ZipException, IOException, DocumentException {
		FaultSystemRupSet rupSet = loadRupSetAsApplicable(file);
		Preconditions.checkArgument(rupSet instanceof InversionFaultSystemRupSet,
				"Rupture set cannot be loaded as an InversionFaultSystemRupSet");
		return (InversionFaultSystemRupSet)rupSet;
	}
	
	/**
	 * Load an FaultSystemSolution from a zip file. If possible, it will be loaded as an applicable subclass.
	 * @param file
	 * @return
	 * @throws DocumentException 
	 * @throws IOException 
	 */
	public static FaultSystemSolution loadSol(File file) throws IOException, DocumentException {
		return loadSolAsApplicable(file);
	}
	
	/**
	 * Load an InversionFaultSystemSolution from a zip file. If possible, it will be loaded as an applicable subclass.
	 * @param file
	 * @return
	 * @throws DocumentException 
	 * @throws IOException 
	 */
	public static InversionFaultSystemSolution loadInvSol(File file) throws IOException, DocumentException {
		FaultSystemSolution sol = loadSolAsApplicable(file);
		Preconditions.checkArgument(sol instanceof InversionFaultSystemSolution,
				"Solution cannot be loaded as an InversionFaultSystemSolution");
		return (InversionFaultSystemSolution)sol;
	}
	
	/**
	 * 
	 * @param file
	 * @return true if the given zip file is a fault sytem solution, false otherwise
	 */
	public static boolean isSolution(File file) throws IOException {
		ZipFile zip = new ZipFile(file);
		boolean found = false;
		
		Enumeration<? extends ZipEntry> entries = zip.entries();
		while (entries.hasMoreElements()) {
			ZipEntry entry = entries.nextElement();
			if (entry.getName().endsWith("rates.bin")) {
				found = true;
				break;
			}
		}
		
		zip.close();
		return found;
	}
	
	/**
	 * Load an AverageFaultSystemSolution from a zip file
	 * @param file
	 * @return
	 * @throws DocumentException 
	 * @throws IOException 
	 */
	public static AverageFaultSystemSolution loadAvgInvSol(File file) throws IOException, DocumentException {
		FaultSystemSolution sol = loadSolAsApplicable(file);
		Preconditions.checkArgument(sol instanceof AverageFaultSystemSolution,
				"Solution cannot be loaded as an AverageFaultSystemSolution");
		return (AverageFaultSystemSolution)sol;
	}
	
	/*	******************************************
	 * 		FILE WRITING
	 *	******************************************/
	
	/**
	 * Writes a FaultSystemRupSet to a zip file
	 * @param rupSet
	 * @param file
	 * @return
	 * @throws IOException 
	 */
	public static void writeRupSet(FaultSystemRupSet rupSet, File file) throws IOException {
		File tempDir = FileUtils.createTempDir();
		
		HashSet<String> zipFileNames = new HashSet<String>();
		
		toZipFile(rupSet, file, tempDir, zipFileNames);
	}
	
	/**
	 * Write an FaultSystemSolution to a zip file
	 * @param sol
	 * @param file
	 * @return
	 * @throws IOException 
	 */
	public static void writeSol(FaultSystemSolution sol, File file) throws IOException {
		File tempDir = FileUtils.createTempDir();
		
		HashSet<String> zipFileNames = new HashSet<String>();
		
		toZipFile(sol, file, tempDir, zipFileNames);
	}
	
	/*	******************************************
	 * 		FILE READING UTIL METHODS
	 *	******************************************/
	
	/**
	 * Loads a rup set from the given zip file as the deepest possible subclass
	 * 
	 * @param file
	 * @return
	 * @throws IOException 
	 * @throws ZipException 
	 * @throws DocumentException 
	 */
	private static FaultSystemRupSet loadRupSetAsApplicable(File file) throws ZipException, IOException, DocumentException {
		return loadRupSetAsApplicable(new ZipFile(file), null);
	}
	
	/**
	 * Loads a rup set from the given zip file as the deepest possible subclass
	 * 
	 * @param file
	 * @return
	 * @throws IOException 
	 * @throws DocumentException 
	 */
	private static FaultSystemRupSet loadRupSetAsApplicable(ZipFile zip, Map<String, String> nameRemappings) throws IOException, DocumentException {
		if (DD) System.out.println("loadRupSetAsApplicable started");
		
		if (DD) System.out.println("loading mags");
		ZipEntry magEntry = zip.getEntry(getRemappedName("mags.bin", nameRemappings));
		double[] mags = MatrixIO.doubleArrayFromInputStream(
				new BufferedInputStream(zip.getInputStream(magEntry)), magEntry.getSize());
		
		if (DD) System.out.println("loading sect slips");
		ZipEntry sectSlipsEntry = zip.getEntry(getRemappedName("sect_slips.bin", nameRemappings));
		double[] sectSlipRates;
		if (sectSlipsEntry != null)
			sectSlipRates = MatrixIO.doubleArrayFromInputStream(
					new BufferedInputStream(zip.getInputStream(sectSlipsEntry)),
					sectSlipsEntry.getSize());
		else
			sectSlipRates = null;

		if (DD) System.out.println("loading sect slip stds");
		ZipEntry sectSlipStdDevsEntry = zip.getEntry(getRemappedName("sect_slips_std_dev.bin", nameRemappings));
		double[] sectSlipRateStdDevs;
		if (sectSlipStdDevsEntry != null)
			sectSlipRateStdDevs = MatrixIO.doubleArrayFromInputStream(
					new BufferedInputStream(zip.getInputStream(sectSlipStdDevsEntry)),
					sectSlipStdDevsEntry.getSize());
		else
			sectSlipRateStdDevs = null;
		
		if (DD) System.out.println("loading rakes");
		ZipEntry rakesEntry = zip.getEntry(getRemappedName("rakes.bin", nameRemappings));
		double[] rakes = MatrixIO.doubleArrayFromInputStream(
				new BufferedInputStream(zip.getInputStream(rakesEntry)), rakesEntry.getSize());
		
		ZipEntry rupAreasEntry = zip.getEntry(getRemappedName("rup_areas.bin", nameRemappings));
		double[] rupAreas;
		if (rupAreasEntry != null)
			rupAreas = MatrixIO.doubleArrayFromInputStream(
					new BufferedInputStream(zip.getInputStream(rupAreasEntry)),
					rupAreasEntry.getSize());
		else
			rupAreas = null;
		
		if (DD) System.out.println("loading rakes");
		ZipEntry rupLenghtsEntry = zip.getEntry(getRemappedName("rup_lengths.bin", nameRemappings));
		double[] rupLengths;
		if (rupLenghtsEntry != null)
			rupLengths = MatrixIO.doubleArrayFromInputStream(
					new BufferedInputStream(zip.getInputStream(rupLenghtsEntry)),
					rupLenghtsEntry.getSize());
		else
			rupLengths = null;

		if (DD) System.out.println("loading sect areas");
		ZipEntry sectAreasEntry = zip.getEntry(getRemappedName("sect_areas.bin", nameRemappings));
		double[] sectAreas;
		if (sectAreasEntry != null)
			sectAreas = MatrixIO.doubleArrayFromInputStream(
					new BufferedInputStream(zip.getInputStream(sectAreasEntry)),
					sectAreasEntry.getSize());
		else
			sectAreas = null;

		if (DD) System.out.println("loading rup sections");
		ZipEntry rupSectionsEntry = zip.getEntry(getRemappedName("rup_sections.bin", nameRemappings));
		List<List<Integer>> sectionForRups;
		if (rupSectionsEntry != null)
			sectionForRups = MatrixIO.intListListFromInputStream(
					new BufferedInputStream(zip.getInputStream(rupSectionsEntry)));
		else
			sectionForRups = null;
		
		if (DD) System.out.println("loading FSD");
		String fsdRemappedName = getRemappedName("fault_sections.xml", nameRemappings);
		ZipEntry fsdEntry = zip.getEntry(fsdRemappedName);
		if (fsdEntry == null && fsdRemappedName.startsWith("FM")) {
			// might be a legacy compound solution before the bug fix
			// try removing the DM from the name
			int ind = fsdRemappedName.indexOf("fault_sections");
			// the -1 removes the underscore before fault
			String prefix = fsdRemappedName.substring(0, ind-1);
			prefix = prefix.substring(0, prefix.lastIndexOf("_"));
			fsdRemappedName = prefix+"_fault_sections.xml";
			fsdEntry = zip.getEntry(fsdRemappedName);
			if (fsdEntry != null)
				System.out.println("WARNING: using old non DM-specific fault_sections.xml file, " +
						"may have incorrect non reduced slip rates: "+fsdRemappedName);
		}
		Document doc = XMLUtils.loadDocument(
				new BufferedInputStream(zip.getInputStream(fsdEntry)));
		Element fsEl = doc.getRootElement().element(FaultSectionPrefData.XML_METADATA_NAME+"List");
		ArrayList<FaultSection> faultSectionData = fsDataFromXML(fsEl);
		
		ZipEntry infoEntry = zip.getEntry(getRemappedName("info.txt", nameRemappings));
		String info = loadInfoFromEntry(zip, infoEntry);
		
		// IVFSRS specific. Try to load any IVFSRS specific files. Unfortunately a little messy to allow
		// for loading in legacy files
		
		if (DD) System.out.println("loading inv matadata");
		ZipEntry invXMLEntry = zip.getEntry(getRemappedName("inv_rup_set_metadata.xml", nameRemappings));
		LogicTreeBranch branch = null;
		UCERF3PlausibilityConfig filter = null;
		if (invXMLEntry != null) {
			Document invDoc = XMLUtils.loadDocument(zip.getInputStream(invXMLEntry));
			Element invRoot = invDoc.getRootElement().element("InversionFaultSystemRupSet");
			
			Element branchEl = invRoot.element(LogicTreeBranch.XML_METADATA_NAME);
			if (branchEl != null)
				branch = LogicTreeBranch.fromXMLMetadata(branchEl);
			
			Element filterEl = invRoot.element(UCERF3PlausibilityConfig.XML_METADATA_NAME);
			if (filterEl != null)
				filter = UCERF3PlausibilityConfig.fromXMLMetadata(filterEl);
		}
		
		// try to load the logic tree branch via other means for legacy files
		if (branch == null && invXMLEntry == null) {
			ZipEntry rupSectionSlipModelEntry = zip.getEntry(getRemappedName("rup_sec_slip_type.txt", nameRemappings));
			SlipAlongRuptureModels slipModelType = null;
			if (rupSectionSlipModelEntry != null) {
				StringWriter writer = new StringWriter();
				IOUtils.copy(zip.getInputStream(rupSectionSlipModelEntry), writer);
				String slipModelName = writer.toString().trim();
				try {
					slipModelType = SlipAlongRuptureModels.valueOf(slipModelName);
				} catch (Exception e) {}
			}
			
			DeformationModels defModName = null;
			Attribute defModAtt = fsEl.attribute("defModName");
			try {
				if (defModAtt != null && !defModAtt.getValue().isEmpty())
					defModName = DeformationModels.valueOf(defModAtt.getValue());
			} catch (Exception e) {}
			FaultModels faultModel = null;
			Attribute faultModAtt = fsEl.attribute("faultModName");
			try {
				if (faultModAtt != null && !faultModAtt.getValue().isEmpty())
					faultModel = FaultModels.valueOf(faultModAtt.getValue());
			} catch (Exception e) {}
			if (faultModel == null && defModAtt != null) {
				if (defModName == null) {
					// hacks for loading in old files
					String defModText = defModAtt.getValue();
					if (defModText.contains("GEOLOGIC") && !defModText.contains("ABM"))
						defModName = DeformationModels.GEOLOGIC;
					else if (defModText.contains("NCAL"))
						defModName = DeformationModels.UCERF2_NCAL;
					else if (defModText.contains("ALLCAL"))
						defModName = DeformationModels.UCERF2_ALL;
					else if (defModText.contains("BAYAREA"))
						defModName = DeformationModels.UCERF2_BAYAREA;
				}
				
				if (defModName != null)
					faultModel = defModName.getApplicableFaultModels().get(0);
			}
			
			// we need at least a FM and Scaling Relationship to load this as an IVFSRS
			if (faultModel != null && slipModelType != null) {
				branch = LogicTreeBranch.fromValues(false, faultModel, defModName, slipModelType);
			}
		}
		
		if (DD) System.out.println("instantiating FSRS");
		FaultSystemRupSet rupSet = new FaultSystemRupSet(faultSectionData, sectSlipRates,
				sectSlipRateStdDevs, sectAreas, sectionForRups, mags, rakes, rupAreas, rupLengths, info);

		if (DD) System.out.println("loading plausibility");
		ZipEntry plausibilityEntry = zip.getEntry(getRemappedName("plausibility.json", nameRemappings));
		if (plausibilityEntry != null) {
			InputStreamReader json = new InputStreamReader(new BufferedInputStream(zip.getInputStream(plausibilityEntry)));
			try {
				PlausibilityConfiguration plausibilityConfig =
						PlausibilityConfiguration.readJSON(json, faultSectionData);
				rupSet.setPlausibilityConfiguration(plausibilityConfig);
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("WARNING: Plausibilty configuration specified, but reading it failed. Skipping");
				if (json != null)
					json.close();
			}
		}
		
		if (DD) System.out.println("loading cluster ruptures");
		ZipEntry clustersEntry = zip.getEntry(getRemappedName("cluster_ruptures.json", nameRemappings));
		if (clustersEntry != null) {
			InputStreamReader json = new InputStreamReader(
					new BufferedInputStream(zip.getInputStream(clustersEntry), 1024*128));
			List<ClusterRupture> clusterRuptures = ClusterRupture.readJSON(json, faultSectionData);
			rupSet.setClusterRuptures(clusterRuptures);
		}
		
		if (branch != null) {
			// it's an IVFSRS
			
			if (DD) System.out.println("loading rup avg slips");
			ZipEntry rupSlipsEntry = zip.getEntry(getRemappedName("rup_avg_slips.bin", nameRemappings));
			double[] rupAveSlips;
			if (rupSlipsEntry != null)
				rupAveSlips = MatrixIO.doubleArrayFromInputStream(
						new BufferedInputStream(zip.getInputStream(rupSlipsEntry)),
					rupSlipsEntry.getSize());
			else
				rupAveSlips = null;

			if (DD) System.out.println("loading close sections");
			ZipEntry closeSectionsEntry = zip.getEntry(getRemappedName("close_sections.bin", nameRemappings));
			List<List<Integer>> closeSections;
			if (closeSectionsEntry != null)
				closeSections = MatrixIO.intListListFromInputStream(
						new BufferedInputStream(zip.getInputStream(closeSectionsEntry)));
			else
				closeSections = null;

			if (DD) System.out.println("loading cluster rups");
			ZipEntry clusterRupsEntry = zip.getEntry(getRemappedName("cluster_rups.bin", nameRemappings));
			List<List<Integer>> clusterRups;
			if (clusterRupsEntry != null)
				clusterRups = MatrixIO.intListListFromInputStream(
						new BufferedInputStream(zip.getInputStream(clusterRupsEntry)));
			else
				clusterRups = null;
			
			ZipEntry clusterSectsEntry = zip.getEntry(getRemappedName("cluster_sects.bin", nameRemappings));
			List<List<Integer>> clusterSects;
			if (clusterSectsEntry != null)
				clusterSects = MatrixIO.intListListFromInputStream(
						new BufferedInputStream(zip.getInputStream(clusterSectsEntry)));
			else
				clusterSects = null;
			
			// maybe restore if we ever need it
//			// don't use remapping here - this is legacy and new files will never have it
//			ZipEntry rupSectionSlipsEntry = zip.getEntry("rup_sec_slips.bin");
//			List<double[]> rupSectionSlips;
//			if (rupSectionSlipsEntry != null)
//				rupSectionSlips = MatrixIO.doubleArraysListFromInputStream(
//						new BufferedInputStream(zip.getInputStream(rupSectionSlipsEntry)));
//			else
//				rupSectionSlips = null;
			

			
			// set dates of last events in fault sections 
			Map<Integer, List<LastEventData>> data;
			try {
				data = LastEventData.load();
				LastEventData.populateSubSects(rupSet.getFaultSectionDataList(), data);
			} catch (IOException e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
			
			if (DD) System.out.println("instantiationg IFSRS");
			return new InversionFaultSystemRupSet(rupSet, branch, filter, rupAveSlips, closeSections, clusterRups, clusterSects);
		}
		
		return rupSet;
	}
	
	public static ArrayList<FaultSection> fsDataFromXML(Element el) {
		ArrayList<FaultSection> list = new ArrayList<>();
		
		for (int i=0; i<el.elements().size(); i++) {
			Element subEl = el.element("i"+i);
			
			Attribute classAt = subEl.attribute("class");
			FaultSection sect;
			if (classAt == null || classAt.getValue().equals(FaultSectionPrefData.class.getCanonicalName())) {
				// default to FaultSectionPrefData
				sect = FaultSectionPrefData.fromXMLMetadata(subEl);
			} else {
				// use reflection
				String className = classAt.getValue();
				Object sectObj;
				try {
					sectObj = MetadataLoader.loadXMLwithReflection(subEl, className);
				} catch (ClassNotFoundException e) {
					throw new IllegalStateException(
							"Defined fault section class not found, cannot load from XML: "+className, e);
				} catch (NoSuchMethodException e) {
					throw new IllegalStateException(
							"Defined fault section class does not contain static "
							+ "fromXMLMetadata(Element) method, cannot load from XML: "+className, e);
				} catch (IllegalArgumentException e) {
					throw new IllegalStateException(
							"Defined fault section class does has unexpected method signature for "
							+ "fromXMLMetadata(Element) method, cannot load from XML: "+className, e);
				} catch (Exception e) {
					throw new IllegalStateException(
							"Other error loading fault section class from XML via reflection: "+className, e);
				}
				Preconditions.checkState(sectObj instanceof FaultSection,
						"Fault section could be instantiated from XML, "
						+ "but does not implement FaultSection: %s", className);
				sect = (FaultSection)sectObj;
			}
			list.add(sect);
		}
		
		return list;
	}
	
	private static String loadInfoFromEntry(ZipFile zip, ZipEntry infoEntry) throws IOException {
		if (infoEntry != null) {
			StringBuilder text = new StringBuilder();
			String NL = System.getProperty("line.separator");
			Scanner scanner = new Scanner(
					new BufferedInputStream(zip.getInputStream(infoEntry)));
			try {
				while (scanner.hasNextLine()){
					text.append(scanner.nextLine() + NL);
				}
			}
			finally{
				scanner.close();
			}
			return text.toString();
		} else
			return null;
	}
	
	/**
	 * Loads a solution from the given zip file as the deepest possible subclass
	 * 
	 * @param file
	 * @return
	 * @throws DocumentException 
	 * @throws IOException 
	 */
	private static FaultSystemSolution loadSolAsApplicable(File file) throws IOException, DocumentException {
		return loadSolAsApplicable(file, null);
	}
	
	/**
	 * Loads a solution from the given zip file as the deepest possible subclass
	 * 
	 * @param file
	 * @param nameRemappings
	 * @return
	 * @throws DocumentException 
	 * @throws IOException 
	 */
	private static FaultSystemSolution loadSolAsApplicable(File file, Map<String, String> nameRemappings)
			throws IOException, DocumentException {
		ZipFile zip = new ZipFile(file);
		return loadSolAsApplicable(zip, nameRemappings);
	}
	
	/**
	 * Loads a solution from the given zip file as the deepest possible subclass
	 * 
	 * @param zip
	 * @param nameRemappings
	 * @return
	 * @throws IOException
	 * @throws DocumentException
	 */
	public static FaultSystemSolution loadSolAsApplicable(ZipFile zip, Map<String, String> nameRemappings)
			throws IOException, DocumentException {
		// first load the rupture set
		FaultSystemRupSet rupSet = loadRupSetAsApplicable(zip, nameRemappings);
		
		// safe to use rupSet info string as we just loaded it from the same zip file
		String infoString = rupSet.getInfoString();
		
		if (DD) System.out.println("loading rates");
		// now load rates
		ZipEntry ratesEntry = zip.getEntry(getRemappedName("rates.bin", nameRemappings));
		double[] rates = MatrixIO.doubleArrayFromInputStream(
					new BufferedInputStream(zip.getInputStream(ratesEntry)), ratesEntry.getSize());
		
		FaultSystemSolution sol;
		
		if (rupSet instanceof InversionFaultSystemRupSet) {
			// it's an IVFSS
			InversionFaultSystemRupSet invRupSet = (InversionFaultSystemRupSet)rupSet;
			
			if (DD) System.out.println("loading inversion metadata");
			ZipEntry invXMLEntry = zip.getEntry(getRemappedName("inv_sol_metadata.xml", nameRemappings));
			
			UCERF3InversionConfiguration conf = null;
			Map<String, Double> energies = null;
			if (invXMLEntry != null) {
				// new file, we can load directly from XML
				Document invDoc = XMLUtils.loadDocument(zip.getInputStream(invXMLEntry));
				Element invRoot = invDoc.getRootElement().element("InversionFaultSystemSolution");
				
				Element confEl = invRoot.element(UCERF3InversionConfiguration.XML_METADATA_NAME);
				if (confEl != null)
					conf = UCERF3InversionConfiguration.fromXMLMetadata(confEl);
				
				Element energiesEl = invRoot.element("Energies");
				if (energiesEl != null) {
					energies = Maps.newHashMap();
					for (Element energyEl : XMLUtils.getSubElementsList(energiesEl)) {
						String type = energyEl.attributeValue("type");
						double value = Double.parseDouble(energyEl.attributeValue("value"));
						energies.put(type, value);
					}
				}
			} else {
				// legacy, do string parsing
				InversionFaultSystemSolution legacySol = new InversionFaultSystemSolution(invRupSet, infoString, rates);
				invRupSet.setLogicTreeBranch(legacySol.getLogicTreeBranch());
				conf = legacySol.getInversionConfiguration();
				energies = legacySol.getEnergies();
			}
			
			// now see if it's an average fault system solution
			String ratesPrefix = getRemappedRatesPrefix(nameRemappings);
			
			List<double[]> ratesList = loadIndSolRates(ratesPrefix, zip, nameRemappings);
			if (ratesList == null)
				// try legacy format
				ratesList = loadIndSolRates("sol_rates", zip, nameRemappings);
			if (ratesList != null)
				// it's an AverageFSS
				sol =  new AverageFaultSystemSolution(invRupSet, ratesList, conf, energies);
			else
				// it's a regular IFSS
				sol = new InversionFaultSystemSolution(invRupSet, rates, conf, energies);
		} else {
			sol = new FaultSystemSolution(rupSet, rates);
		}
		
		// look for rup MFDs
		ZipEntry rupMFDsEntry = zip.getEntry(getRemappedName("rup_mfds.bin", nameRemappings));
		if (rupMFDsEntry != null) {
			if (DD) System.out.println("loading rup MFDs");
			DiscretizedFunc[] rupMFDs = MatrixIO.discFuncsFromInputStream(zip.getInputStream(rupMFDsEntry));
			sol.setRupMagDists(rupMFDs);
		}
		
		// look for sub seismo MFDs
		ZipEntry subSeisMFDsEntry = zip.getEntry(getRemappedName("sub_seismo_on_fault_mfds.bin", nameRemappings));
		if (subSeisMFDsEntry != null) {
			if (DD) System.out.println("loading sub seismo MFDs");
			DiscretizedFunc[] origSubSeisMFDs = MatrixIO.discFuncsFromInputStream(zip.getInputStream(subSeisMFDsEntry));
			Preconditions.checkState(origSubSeisMFDs.length == rupSet.getNumSections());
			List<IncrementalMagFreqDist> subSeisMFDs = Lists.newArrayList();
			for (int i=0; i<origSubSeisMFDs.length; i++)
				subSeisMFDs.add(asIncr(origSubSeisMFDs[i]));
			sol.setSubSeismoOnFaultMFD_List(subSeisMFDs);
		}
		
		// finally look for grid sources
		ZipEntry gridSourcesEntry = zip.getEntry(getRemappedName("grid_sources.xml", nameRemappings));
		ZipEntry gridSourcesBinEntry = zip.getEntry(getRemappedName("grid_sources.bin", nameRemappings));
		ZipEntry gridSourcesRegEntry = zip.getEntry(getRemappedName("grid_sources_reg.xml", nameRemappings));
		if (gridSourcesEntry != null) {
			if (DD) System.out.println("loading grid sources");
			sol.setGridSourceProvider(GridSourceFileReader.fromInputStream(zip.getInputStream(gridSourcesEntry)));
		} else if (gridSourcesBinEntry != null && gridSourcesRegEntry != null) {
			// now look for bin files
			if (DD) System.out.println("loading grid sources");
			sol.setGridSourceProvider(GridSourceFileReader.fromBinStreams(
					zip.getInputStream(gridSourcesBinEntry), zip.getInputStream(gridSourcesRegEntry)));
		}
		
		return sol;
	}
	
	private static List<double[]> loadIndSolRates(String ratesPrefix, ZipFile zip, Map<String, String> nameRemappings) throws IOException {
		int max_digits = 10;
		
		for (int digits=1; digits<=max_digits; digits++) {
			int c = 0;
			String ratesName = ratesPrefix+"_"+getPaddedNumStr(c++, digits)+".bin";
			ZipEntry entry = zip.getEntry(ratesName);
			if (entry != null) {
				// it's an average sol
				List<double[]> ratesList = Lists.newArrayList();
				
				while (true) {
					double[] rates = MatrixIO.doubleArrayFromInputStream(
							new BufferedInputStream(zip.getInputStream(entry)), entry.getSize());
					ratesList.add(rates);
					
					ratesName = ratesPrefix+"_"+getPaddedNumStr(c++, digits)+".bin";
					entry = zip.getEntry(ratesName);
					if (entry == null)
						break;
				}
				
				if (ratesList.size() > 1)
					return ratesList;
				else
					return null;
			}
		}
		return null;
	}
	
	/*	******************************************
	 * 		FILE WRITING UTIL METHODS
	 *	******************************************/
	
	private static void toZipFile(FaultSystemRupSet rupSet, File file, File tempDir, HashSet<String> zipFileNames) throws IOException {
		final boolean D = true;
		if (D) System.out.println("Saving rup set with "+rupSet.getNumRuptures()+" rups to: "+file.getAbsolutePath());
		writeRupSetFilesForZip(rupSet, tempDir, zipFileNames, null);
		
		if (D) System.out.println("Making zip file: "+file.getName());
		FileUtils.createZipFile(file.getAbsolutePath(), tempDir.getAbsolutePath(), zipFileNames);
		
		if (D) System.out.println("Deleting temp files");
		FileUtils.deleteRecursive(tempDir);
		
		if (D) System.out.println("Done saving!");
	}
	
	public static void writeRupSetFilesForZip(FaultSystemRupSet rupSet, File tempDir,
			HashSet<String> zipFileNames, Map<String, String> nameRemappings) throws IOException {
		// first save fault section data as XML
		if (D) System.out.println("Saving fault section xml");
		File fsdFile = new File(tempDir, getRemappedName("fault_sections.xml", nameRemappings));
		if (!zipFileNames.contains(fsdFile.getName())) {
			Document doc = XMLUtils.createDocumentWithRoot();
			Element root = doc.getRootElement();
			fsDataToXML(root, FaultSectionPrefData.XML_METADATA_NAME+"List", rupSet);
			XMLUtils.writeDocumentToFile(fsdFile, doc);
			zipFileNames.add(fsdFile.getName());
		}
		
		// write mags
		if (D) System.out.println("Saving mags");
		File magFile = new File(tempDir, getRemappedName("mags.bin", nameRemappings));
		if (!zipFileNames.contains(magFile.getName())) {
			MatrixIO.doubleArrayToFile(rupSet.getMagForAllRups(), magFile);
			zipFileNames.add(magFile.getName());
		}
		
		// write sect slips
		double[] sectSlipRates = rupSet.getSlipRateForAllSections();
		if (sectSlipRates != null) {
			if (D) System.out.println("Saving section slips");
			File sectSlipsFile = new File(tempDir, getRemappedName("sect_slips.bin", nameRemappings));
			if (!zipFileNames.contains(sectSlipsFile.getName())) {
				MatrixIO.doubleArrayToFile(sectSlipRates, sectSlipsFile);
				zipFileNames.add(sectSlipsFile.getName());
			}
		}
		
		// write sec slip std devs
		double[] sectSlipRateStdDevs = rupSet.getSlipRateStdDevForAllSections();
		if (sectSlipRateStdDevs != null) {
			if (D) System.out.println("Saving slip std devs");
			File sectSlipStdDevsFile = new File(tempDir, getRemappedName("sect_slips_std_dev.bin", nameRemappings));
			if (!zipFileNames.contains(sectSlipStdDevsFile.getName())) {
				MatrixIO.doubleArrayToFile(sectSlipRateStdDevs, sectSlipStdDevsFile);
				zipFileNames.add(sectSlipStdDevsFile.getName());
			}
		}
		
		// write rakes
		if (D) System.out.println("Saving rakes");
		File rakesFile = new File(tempDir, getRemappedName("rakes.bin", nameRemappings));
		if (!zipFileNames.contains(rakesFile.getName())) {
			MatrixIO.doubleArrayToFile(rupSet.getAveRakeForAllRups(), rakesFile);
			zipFileNames.add(rakesFile.getName());
		}
		
		// write rup areas
		if (D) System.out.println("Saving rup areas");
		File rupAreasFile = new File(tempDir, getRemappedName("rup_areas.bin", nameRemappings));
		if (!zipFileNames.contains(rupAreasFile.getName())) {
			MatrixIO.doubleArrayToFile(rupSet.getAreaForAllRups(), rupAreasFile);
			zipFileNames.add(rupAreasFile.getName());
		}
		
		// write rup areas
		if (rupSet.getLengthForAllRups() != null) {
			if (D) System.out.println("Saving rup lengths");
			File rupLengthsFile = new File(tempDir, getRemappedName("rup_lengths.bin", nameRemappings));
			if (!zipFileNames.contains(rupLengthsFile.getName())) {
				MatrixIO.doubleArrayToFile(rupSet.getLengthForAllRups(), rupLengthsFile);
				zipFileNames.add(rupLengthsFile.getName());
			}
		}
		
		// write sect areas
		double[] sectAreas = rupSet.getAreaForAllSections();
		if (sectAreas != null) {
			if (D) System.out.println("Saving sect areas");
			File sectAreasFile = new File(tempDir, getRemappedName("sect_areas.bin", nameRemappings));
			if (!zipFileNames.contains(sectAreasFile.getName())) {
				MatrixIO.doubleArrayToFile(sectAreas, sectAreasFile);
				zipFileNames.add(sectAreasFile.getName());
			}
		}
		
		// write sections for rups
		if (D) System.out.println("Saving rup sections");
		File sectionsForRupsFile = new File(tempDir, getRemappedName("rup_sections.bin", nameRemappings));
		if (!zipFileNames.contains(sectionsForRupsFile.getName())) {
			MatrixIO.intListListToFile(rupSet.getSectionIndicesForAllRups(), sectionsForRupsFile);
			zipFileNames.add(sectionsForRupsFile.getName());
		}
		
		String info = rupSet.getInfoString();
		if (info != null && !info.isEmpty()) {
			if (D) System.out.println("Saving info");
			File infoFile = new File(tempDir, getRemappedName("info.txt", nameRemappings));
			if (!zipFileNames.contains(infoFile.getName())) {
				FileWriter fw = new FileWriter(infoFile);
				fw.write(info+"\n");
				fw.close();
				zipFileNames.add(infoFile.getName());
			}
		}
		
		PlausibilityConfiguration plausibilityConfig = rupSet.getPlausibilityConfiguration();
		if (plausibilityConfig != null) {
			if (D) System.out.println("Saving plausibility config");
			File plausibilityFile = new File(tempDir, getRemappedName("plausibility.json", nameRemappings));
			plausibilityConfig.writeJSON(plausibilityFile);
			zipFileNames.add(plausibilityFile.getName());
		}
		
		List<ClusterRupture> clusterRuptures = rupSet.getClusterRuptures();
		if (clusterRuptures != null) {
			if (D) System.out.println("Saving cluster ruptures");
			File clusterFile = new File(tempDir, getRemappedName("cluster_ruptures.json", nameRemappings));
			ClusterRupture.writeJSON(clusterFile, clusterRuptures, rupSet.getFaultSectionDataList());
			zipFileNames.add(clusterFile.getName());
		}
		
		// InversionFaultSystemRupSet specific
		
		if (rupSet instanceof InversionFaultSystemRupSet) {
			if (D) System.out.println("Saving InversionFaultSystemRupSet specific data");
			InversionFaultSystemRupSet invRupSet = (InversionFaultSystemRupSet)rupSet;
			
			// save IVFSRS metadata
			if (D) System.out.println("Saving inversion rup set metadata xml");
			File invFile = new File(tempDir, getRemappedName("inv_rup_set_metadata.xml", nameRemappings));
			if (!zipFileNames.contains(invFile.getName())) {
				Document doc = XMLUtils.createDocumentWithRoot();
				Element root = doc.getRootElement();
				invRupSetDataToXML(root, invRupSet);
				XMLUtils.writeDocumentToFile(invFile, doc);
				zipFileNames.add(invFile.getName());
			}
			
			// write rup slips
			double[] rupAveSlips = invRupSet.getAveSlipForAllRups();
			if (rupAveSlips != null) {
				if (D) System.out.println("Saving rup avg slips");
				File rupSlipsFile = new File(tempDir, getRemappedName("rup_avg_slips.bin", nameRemappings));
				if (!zipFileNames.contains(rupSlipsFile.getName())) {
					MatrixIO.doubleArrayToFile(rupAveSlips, rupSlipsFile);
					zipFileNames.add(rupSlipsFile.getName());
				}
			}
			
			List<List<Integer>> closeSections = invRupSet.getCloseSectionsListList();
			if (closeSections != null) {
				// write close sections
				if (D) System.out.println("Saving close sections");
				File closeSectionsFile = new File(tempDir, getRemappedName("close_sections.bin", nameRemappings));
				if (!zipFileNames.contains(closeSectionsFile.getName())) {
					MatrixIO.intListListToFile(closeSections, closeSectionsFile);
					zipFileNames.add(closeSectionsFile.getName());
				}
			}
			
			if (invRupSet.getNumClusters() > 0) {
				List<List<Integer>> clusterRups = Lists.newArrayList();
				List<List<Integer>> clusterSects = Lists.newArrayList();
				
				for (int c=0; c<invRupSet.getNumClusters(); c++) {
					clusterRups.add(invRupSet.getRupturesForCluster(c));
					clusterSects.add(invRupSet.getSectionsForCluster(c));
				}
				
				// write close sections
				if (D) System.out.println("Saving cluster rups");
				File clusterRupsFile = new File(tempDir, getRemappedName("cluster_rups.bin", nameRemappings));
				if (!zipFileNames.contains(clusterRupsFile.getName())) {
					MatrixIO.intListListToFile(clusterRups, clusterRupsFile);
					zipFileNames.add(clusterRupsFile.getName());
				}
				
				
				// write close sections
				if (D) System.out.println("Saving cluster sects");
				File clusterSectsFile = new File(tempDir, getRemappedName("cluster_sects.bin", nameRemappings));
				if (!zipFileNames.contains(clusterSectsFile.getName())) {
					MatrixIO.intListListToFile(clusterSects, clusterSectsFile);
					zipFileNames.add(clusterSectsFile.getName());
				}
			}
		}
	}
	
	public static void fsDataToXML(Element parent, String elName, FaultSystemRupSet rupSet) {
		FaultModels fm = null;
		DeformationModels dm = null;
		if (rupSet instanceof InversionFaultSystemRupSet) {
			InversionFaultSystemRupSet invRupSet = (InversionFaultSystemRupSet)rupSet;
			fm = invRupSet.getFaultModel();
			dm = invRupSet.getDeformationModel();
		}
		fsDataToXML(parent, elName, fm, dm, rupSet.getFaultSectionDataList());
	}
	
	public static void fsDataToXML(Element parent, String elName,
			FaultModels fm, DeformationModels dm, List<? extends FaultSection> fsd) {
		Element el = parent.addElement(elName);
		
		if (dm != null)
			el.addAttribute("defModName", dm.name());
		if (fm != null)
			el.addAttribute("faultModName", fm.name());
		
		for (int i=0; i<fsd.size(); i++) {
			FaultSection data = fsd.get(i);
			data.toXMLMetadata(el, "i"+i);
		}
	}
	
	private static void invRupSetDataToXML(Element root, InversionFaultSystemRupSet invRupSet) {
		Element el = root.addElement("InversionFaultSystemRupSet");
		
		// add LogicTreeBranch
		LogicTreeBranch branch = invRupSet.getLogicTreeBranch();
		if (branch != null)
			branch.toXMLMetadata(el);
		
		// add LaughTestFilter
		OldPlausibilityConfiguration filter = invRupSet.getOldPlausibilityConfiguration();
		if (filter != null)
			filter.toXMLMetadata(el);
	}
	
	private static String getRemappedName(String name, Map<String, String> nameRemappings) {
		if (nameRemappings == null)
			return name;
		return nameRemappings.get(name);
	}
	
	private static void toZipFile(FaultSystemSolution sol, File file, File tempDir, HashSet<String> zipFileNames) throws IOException {
		final boolean D = true;
		if (D) System.out.println("Saving solution with "+sol.getRupSet().getNumRuptures()+" rups to: "+file.getAbsolutePath());
		writeSolFilesForZip(sol, tempDir, zipFileNames, null);
		
		if (D) System.out.println("Making zip file: "+file.getName());
		FileUtils.createZipFile(file.getAbsolutePath(), tempDir.getAbsolutePath(), zipFileNames);
		
		if (D) System.out.println("Deleting temp files");
		FileUtils.deleteRecursive(tempDir);
		
		if (D) System.out.println("Done saving!");
	}
	
	public static void writeSolFilesForZip(FaultSystemSolution sol, File tempDir,
			HashSet<String> zipFileNames, Map<String, String> nameRemappings) throws IOException {
		// first save rup set files
		writeRupSetFilesForZip(sol.getRupSet(), tempDir, zipFileNames, nameRemappings);
		
		// write rates
		File ratesFile = new File(tempDir, getRemappedName("rates.bin", nameRemappings));
		MatrixIO.doubleArrayToFile(sol.getRateForAllRups(), ratesFile);
		zipFileNames.add(ratesFile.getName());
		
		// write rup MFDs if applicable
		DiscretizedFunc[] rupMFDs = sol.getRupMagDists();
		if (rupMFDs != null) {
			File mfdFile = new File(tempDir, getRemappedName("rup_mfds.bin", nameRemappings));
			MatrixIO.discFuncsToFile(rupMFDs, mfdFile);
			zipFileNames.add(mfdFile.getName());
		}
		
		// write sub seismo MFDs if applicable
		List<? extends IncrementalMagFreqDist> subSeisMFDs = sol.getSubSeismoOnFaultMFD_List();
		if (subSeisMFDs != null) {
			File mfdFile = new File(tempDir, getRemappedName("sub_seismo_on_fault_mfds.bin", nameRemappings));
			DiscretizedFunc[] subSeisMFDsArray = new DiscretizedFunc[subSeisMFDs.size()];
			for (int i=0; i<subSeisMFDs.size(); i++)
				subSeisMFDsArray[i] = subSeisMFDs.get(i);
			MatrixIO.discFuncsToFile(subSeisMFDsArray, mfdFile);
			zipFileNames.add(mfdFile.getName());
		}
		
		// overwrite info string
		String info = sol.getInfoString();
		if (info != null && !info.isEmpty()) {
			if (D) System.out.println("Saving info");
			File infoFile = new File(tempDir, getRemappedName("info.txt", nameRemappings));
			// always overwrite info from rupSet
			FileWriter fw = new FileWriter(infoFile);
			fw.write(info+"\n");
			fw.close();
			zipFileNames.add(infoFile.getName());
		}
		
		GridSourceProvider gridSources = sol.getGridSourceProvider();
		if (gridSources != null) {
			// new binary format
//			if (D) System.out.println("Saving grid sources to xml");
//			File gridSourcesFile = new File(tempDir, getRemappedName("grid_sources.xml", nameRemappings));
//			if (!zipFileNames.contains(gridSourcesFile.getName())) {
//				GridSourceFileReader.writeGriddedSeisFile(gridSourcesFile, gridSources);
//				zipFileNames.add(gridSourcesFile.getName());
//			}
			if (D) System.out.println("Saving grid sources to binary file");
			File gridSourcesBinFile = new File(tempDir, getRemappedName("grid_sources.bin", nameRemappings));
			File gridSourcesRegFile = new File(tempDir, getRemappedName("grid_sources_reg.xml", nameRemappings));
			if (!zipFileNames.contains(gridSourcesBinFile.getName()) && !zipFileNames.contains(gridSourcesRegFile.getName())) {
				GridSourceFileReader.writeGriddedSeisBinFile(gridSourcesBinFile, gridSourcesRegFile, gridSources, 0d);
				zipFileNames.add(gridSourcesBinFile.getName());
				zipFileNames.add(gridSourcesRegFile.getName());
			}
		}
		
		// InversionFaultSystemSolution specific
		
		if (sol instanceof InversionFaultSystemSolution) {
			if (D) System.out.println("Saving InversionFaultSystemSolution specific data");
			InversionFaultSystemSolution invSol = (InversionFaultSystemSolution)sol;
			
			// save IFSS metadata
			if (D) System.out.println("Saving inversion solution metadata xml");
			File invFile = new File(tempDir, getRemappedName("inv_sol_metadata.xml", nameRemappings));
			if (!zipFileNames.contains(invFile.getName())) {
				Document doc = XMLUtils.createDocumentWithRoot();
				Element root = doc.getRootElement();
				invSolDataToXML(root, invSol);
				XMLUtils.writeDocumentToFile(invFile, doc);
				zipFileNames.add(invFile.getName());
			}
			
			if (sol instanceof AverageFaultSystemSolution) {
				AverageFaultSystemSolution avgSol = (AverageFaultSystemSolution)sol;
				int numSols = avgSol.getNumSolutions();
				
				if (D) System.out.println("Saving AverageFaultSystemSolution specific data for "+numSols+" solutions");
				
				String ratesPrefix = getRemappedRatesPrefix(nameRemappings);
				
				int digits = new String(""+(numSols-1)).length();
				for (int s=0; s<numSols; s++) {
					double[] rates = avgSol.getRates(s);
					String rateStr = getPaddedNumStr(s, digits);
					File rateSubFile = new File(tempDir, ratesPrefix+"_"+rateStr+".bin");
					MatrixIO.doubleArrayToFile(rates, rateSubFile);
					zipFileNames.add(rateSubFile.getName());
				}
			}
		}
	}
	
	private static void invSolDataToXML(Element root, InversionFaultSystemSolution invSol) {
		Element el = root.addElement("InversionFaultSystemSolution");
		
		// add InversionConfiguration
		UCERF3InversionConfiguration conf = invSol.getInversionConfiguration();
		if (conf != null)
			conf.toXMLMetadata(el);
		
		// add LaughTestFilter
		Map<String, Double> energies = invSol.getEnergies();
		if (energies != null && !energies.isEmpty()) {
			Element energiesEl = el.addElement("Energies");
			for (String type : energies.keySet()) {
				double energy = energies.get(type);
				Element energyEl = energiesEl.addElement("Energy");
				energyEl.addAttribute("type", type);
				energyEl.addAttribute("value", energy+"");
			}
		}
	}
	
	private static String getRemappedRatesPrefix(Map<String, String> nameRemappings) {
		String ratesPrefix = getRemappedName("rates.bin", nameRemappings);
		ratesPrefix = ratesPrefix.substring(0, ratesPrefix.indexOf(".bin"));
		return ratesPrefix;
	}
	
	private static String getPaddedNumStr(int num, int digits) {
		String str = num+"";
		while (str.length() < digits)
			str = "0"+str;
		return str;
	}

	public static IncrementalMagFreqDist asIncr(DiscretizedFunc func) {
		IncrementalMagFreqDist mfd;
		if (func instanceof EvenlyDiscretizedFunc) {
			EvenlyDiscretizedFunc eFunc = (EvenlyDiscretizedFunc)func;
			mfd = new IncrementalMagFreqDist(eFunc.getMinX(), eFunc.size(), eFunc.getDelta());
		} else {
			mfd = new IncrementalMagFreqDist(func.getMinX(), func.size(), func.getX(1) - func.getX(0));
		}
		mfd.setInfo(func.getInfo());
		mfd.setName(func.getName());
		mfd.setXAxisName(func.getXAxisName());
		mfd.setYAxisName(func.getYAxisName());
		for (int i=0; i<func.size(); i++) {
			mfd.set(i, func.getY(i));
			Preconditions.checkState((float)mfd.getX(i) == (float)func.getX(i));
		}
		
		return mfd;
	}

}
