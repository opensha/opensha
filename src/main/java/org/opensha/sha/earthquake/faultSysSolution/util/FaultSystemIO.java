package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.Named;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.XMLUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.StatefulModule;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class FaultSystemIO {
	
	public static void writeRupSet(FaultSystemRupSet rupSet, File outputFile) throws IOException {
		System.out.println("Writing rupture set to"+outputFile.getAbsolutePath());
		EntryTrackingZOUT zout = new EntryTrackingZOUT(new BufferedOutputStream(new FileOutputStream(outputFile)));
		writeRupSet(rupSet, zout);
		if (rupSet.hasModule(CopyOverModule.class))
			rupSet.getModule(CopyOverModule.class).copyOver(zout);
		zout.close();
	}
	
	public static void writeRupSet(FaultSystemRupSet rupSet, ZipOutputStream zout) throws IOException {
		writeRupSetNoModules(rupSet, zout);
		
		writeModules(zout, rupSet.getModules(), "modules.json");
	}
	
	private static void writeRupSetNoModules(FaultSystemRupSet rupSet, ZipOutputStream zout) throws IOException {
		// ruptures CSV
		System.out.println("Writing ruptures.csv");
		zout.putNextEntry(new ZipEntry("ruptures.csv"));
		buildRupturesCSV(rupSet).writeToStream(zout);
		zout.flush();
		zout.closeEntry();
		
		// sections CSV
		System.out.println("Writing sections.csv");
		zout.putNextEntry(new ZipEntry("sections.csv"));
		buildSectsCSV(rupSet).writeToStream(zout);
		zout.flush();
		zout.closeEntry();
		
		// fault sections
		// TODO: retire the old XML format
		Document doc = XMLUtils.createDocumentWithRoot();
		Element root = doc.getRootElement();
		scratch.UCERF3.utils.FaultSystemIO.fsDataToXML(root, FaultSectionPrefData.XML_METADATA_NAME+"List",
				null, null, rupSet.getFaultSectionDataList());
		zout.putNextEntry(new ZipEntry("fault_sections.xml"));
		XMLWriter writer = new XMLWriter(new BufferedWriter(new OutputStreamWriter(zout)),
				OutputFormat.createPrettyPrint());
		writer.write(doc);
		writer.flush();
		zout.flush();
		zout.closeEntry();
		
		String info = rupSet.getInfoString();
		if (info != null && !info.isBlank())
			writeInfoToZip(zout, info, "rupture_info.txt");
	}
	
	public static FaultSystemRupSet loadRupSet(File rupSetFile) throws IOException {
		System.out.println("Loading rupture set from "+rupSetFile.getAbsolutePath());
		return loadRupSet(new ZipFile(rupSetFile));
	}
	
	public static FaultSystemRupSet loadRupSet(ZipFile zip) throws IOException {
		FaultSystemRupSet rupSet = loadRupSetWithoutModules(zip);
		
		if (zip.getEntry("modules.json") != null) {
			List<StatefulModuleRecord> records = loadModuleRecords(zip, "modules.json");
			loadModules(rupSet, null, records, zip);
		}
		
		rupSet.addModule(new CopyOverModule(rupSet, zip));
		
		return rupSet;
	}
	
	private static FaultSystemRupSet loadRupSetWithoutModules(ZipFile zip) throws IOException {
		System.out.println("\tLoading ruptures CSV...");
		ZipEntry rupsEntry = new ZipEntry("ruptures.csv");
		CSVFile<String> rupsCSV = CSVFile.readStream(zip.getInputStream(rupsEntry), false);
		System.out.println("\tLoading sections CSV...");
		ZipEntry sectsEntry = new ZipEntry("sections.csv");
		CSVFile<String> sectsCSV = CSVFile.readStream(zip.getInputStream(sectsEntry), true);
		
		// fault sections
		// TODO: retire old XML format
		ZipEntry fsdEntry = zip.getEntry("fault_sections.xml");
		System.out.println("\tLoading sections XML...");
		Document doc;
		try {
			doc = XMLUtils.loadDocument(
					new BufferedInputStream(zip.getInputStream(fsdEntry)));
		} catch (DocumentException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		Element fsEl = doc.getRootElement().element(FaultSectionPrefData.XML_METADATA_NAME+"List");
		List<FaultSection> sections = scratch.UCERF3.utils.FaultSystemIO.fsDataFromXML(fsEl);
		
		// info
		String info = loadInfoFromZip(zip, "rupture_info.txt");
		
		return loadRupSetCSVs(sections, rupsCSV, sectsCSV, info);
	}
	
	private static CSVFile<String> buildRupturesCSV(FaultSystemRupSet rupSet) {
		CSVFile<String> csv = new CSVFile<>(false);
		
		csv.addLine("Rupture Index", "Magnitude", "Average Rake (degrees)", "Area (m^2)", "Length (m)",
				"Num Sections", "Section Index 1", "Section Index N");
		
		double[] lengths = rupSet.getLengthForAllRups();
		for (int r=0; r<rupSet.getNumRuptures(); r++) {
			List<Integer> sectIDs = rupSet.getSectionsIndicesForRup(r);
			List<String> line = new ArrayList<>(5+sectIDs.size());
			
			line.add(r+"");
			line.add(rupSet.getMagForRup(r)+"");
			line.add(rupSet.getAveRakeForRup(r)+"");
			line.add(rupSet.getAreaForRup(r)+"");
			if (lengths == null)
				line.add("");
			else
				line.add(lengths[r]+"");
			line.add(sectIDs.size()+"");
			for (int s : sectIDs)
				line.add(s+"");
			csv.addLine(line);
		}
		
		return csv;
	}
	
	private static CSVFile<String> buildSectsCSV(FaultSystemRupSet rupSet) {
		CSVFile<String> csv = new CSVFile<>(true);
		
		// TODO: what should we put in here? how should we represent sections in these files?
		csv.addLine("Section Index", "Section Name", "Area (m^2)", "Slip Rate (m/yr)", "Slip Rate Standard Deviation (m/yr)");
		double[] sectAreas = rupSet.getAreaForAllSections();
		double[] sectSlipRates = rupSet.getSlipRateForAllSections();
		double[] sectSlipRateStdDevs = rupSet.getSlipRateStdDevForAllSections();
		for (int s=0; s<rupSet.getNumSections(); s++) {
			FaultSection sect = rupSet.getFaultSectionData(s);
			Preconditions.checkState(sect.getSectionId() == s, "Section ID mismatch, expected %s but is %s", s, sect.getSectionId());
			List<String> line = new ArrayList<>(5);
			line.add(s+"");
			line.add(sect.getSectionName());
			line.add(sectAreas == null ? "" : sectAreas[s]+"");
			line.add(sectSlipRates == null ? "" : sectSlipRates[s]+"");
			line.add(sectSlipRateStdDevs == null ? "" : sectSlipRateStdDevs[s]+"");
			csv.addLine(line);
		}
		
		return csv;
	}
	
	private static FaultSystemRupSet loadRupSetCSVs(List<? extends FaultSection> sections,
			CSVFile<String> rupturesCSV, CSVFile<String> sectsCSV, String info) {
		int numRuptures = rupturesCSV.getNumRows()-1;
		Preconditions.checkState(numRuptures > 0, "No ruptures found in CSV file");
		int numSections = sectsCSV.getNumRows()-1;
		
		// load rupture data
		double[] mags = new double[numRuptures];
		double[] rakes = new double[numRuptures];
		double[] areas = new double[numRuptures];
		double[] lengths = null;
		List<List<Integer>> rupSectsList = new ArrayList<>(numRuptures);
		boolean shortSafe = numSections < Short.MAX_VALUE;
		System.out.println("\tParsing ruptures CSV");
		for (int r=0; r<numRuptures; r++) {
			int row = r+1;
			int col = 0;
			Preconditions.checkState(r == rupturesCSV.getInt(row, col++),
					"Ruptures out of order or not 0-based in CSV file, expected id=%s at row %s", r, row);
			mags[r] = rupturesCSV.getDouble(row, col++);
			rakes[r] = rupturesCSV.getDouble(row, col++);
			areas[r] = rupturesCSV.getDouble(row, col++);
			String lenStr = rupturesCSV.get(row, col++);
			if ((r == 0 && !lenStr.isBlank())) {
				lengths = new double[numRuptures];
				lengths[r] = Double.parseDouble(lenStr);
			} else {
				if (lengths != null)
					lengths[r] = Double.parseDouble(lenStr);
				else
					Preconditions.checkState(lenStr.isBlank(),
							"Rupture lenghts must be populated for all ruptures, or omitted for all. We have a length for "
							+ "rupture %s but the first rupture did not have a length.", r);
			}
			int numRupSects = rupturesCSV.getInt(row, col++);
			Preconditions.checkState(numRupSects > 0, "Rupture %s has no sections!", r);
			List<Integer> rupSects;
			if (shortSafe) {
				short[] sectIDs = new short[numRupSects];
				for (int i=0; i<numRupSects; i++)
					sectIDs[i] = (short)rupturesCSV.getInt(row, col++);
				rupSects = new ShortListWrapper(sectIDs);
			} else {
				int[] sectIDs = new int[numRupSects];
				for (int i=0; i<numRupSects; i++)
					sectIDs[i] = rupturesCSV.getInt(row, col++);
				rupSects = new IntListWrapper(sectIDs);
			}
			int rowSize = rupturesCSV.getLine(row).size();
			Preconditions.checkState(col == rowSize,
					"Unexpected line lenth for rupture %s, have %s columns but expected %s", r, rowSize, col);
			rupSectsList.add(rupSects);
		}
		
		double[] sectAreas = null;
		double[] sectSlipRates = null;
		double[] sectSlipRateStdDevs = null;
		System.out.println("\tParsing sections CSV");
		for (int s=0; s<numSections; s++) {
			int row = s+1;
			int col = 0;
			Preconditions.checkState(s == sectsCSV.getInt(row, col++),
					"Sections out of order or not 0-based in CSV file, expected id=%s at row %s", s, row);
			
			col++; // don't need name
			String areaStr = sectsCSV.get(row, col++);
			String rateStr = sectsCSV.get(row, col++);
			String rateStdDevStr = sectsCSV.get(row, col++);
			
			
			if (s == 0) {
				if (!areaStr.isBlank())
					sectAreas = new double[numSections];
				if (!rateStr.isBlank())
					sectSlipRates = new double[numSections];
				if (!rateStdDevStr.isBlank())
					sectSlipRateStdDevs = new double[numSections];
			}
			if (sectAreas != null)
				sectAreas[s] = Double.parseDouble(areaStr);
			if (sectSlipRates != null)
				sectSlipRates[s] = Double.parseDouble(rateStr);
			if (sectSlipRateStdDevs != null)
				sectSlipRateStdDevs[s] = Double.parseDouble(rateStdDevStr);
			int rowSize = sectsCSV.getLine(row).size();
			Preconditions.checkState(col == rowSize,
					"Unexpected line lenth for section %s, have %s columns but expected %s", s, rowSize, col);
		}
		
		return new FaultSystemRupSet(sections, sectSlipRates, sectSlipRateStdDevs, sectAreas,
				rupSectsList, mags, rakes, areas, lengths, info);
	}
	
	/**
	 * Memory efficient list that is backed by an array of short values for memory efficiency
	 * @author kevin
	 *
	 */
	private static class ShortListWrapper extends AbstractList<Integer> {
		
		private short[] vals;
		
		public ShortListWrapper(short[] vals) {
			this.vals = vals;
		}

		@Override
		public Integer get(int index) {
			return (int)vals[index];
		}

		@Override
		public int size() {
			this.equals(null);
			return vals.length;
		}
		
		public boolean equals(Object o) {
	        if (o == this)
	            return true;
	        if (!(o instanceof List))
	            return false;
	        List<?> oList = (List<?>)o;
	        if (size() != oList.size())
	        	return false;
	        if (Integer.class.isAssignableFrom(oList.get(0).getClass()))
	        	return false;
	        
	        for (int i=0; i<vals.length; i++)
	        	if ((int)vals[i] != ((Integer)oList.get(i)).intValue())
	        		return false;
	        return true;
	    }
		
	}
	
	/**
	 * Memory efficient list that is backed by an array of integer values for memory efficiency
	 * @author kevin
	 *
	 */
	private static class IntListWrapper extends AbstractList<Integer> {
		
		private int[] vals;
		
		public IntListWrapper(int[] vals) {
			this.vals = vals;
		}

		@Override
		public Integer get(int index) {
			return vals[index];
		}

		@Override
		public int size() {
			this.equals(null);
			return vals.length;
		}
		
		public boolean equals(Object o) {
	        if (o == this)
	            return true;
	        if (!(o instanceof List))
	            return false;
	        List<?> oList = (List<?>)o;
	        if (size() != oList.size())
	        	return false;
	        if (Integer.class.isAssignableFrom(oList.get(0).getClass()))
	        	return false;
	        
	        for (int i=0; i<vals.length; i++)
	        	if (vals[i] != ((Integer)oList.get(i)).intValue())
	        		return false;
	        return true;
	    }
		
	}
	
	public static void writeSol(FaultSystemSolution sol, File outputFile) throws IOException {
		System.out.println("Writing solution to"+outputFile.getAbsolutePath());
		EntryTrackingZOUT zout = new EntryTrackingZOUT(new BufferedOutputStream(new FileOutputStream(outputFile)));
		writeRupSetNoModules(sol.getRupSet(), zout);
		
		// sections CSV
		System.out.println("Writing rates.csv");
		zout.putNextEntry(new ZipEntry("rates.csv"));
		CSVFile<String> ratesCSV = new CSVFile<>(true);
		ratesCSV.addLine("Rupture Index", "Annual Rate");
		double[] rates = sol.getRateForAllRups();
		for (int r=0; r<rates.length; r++)
			ratesCSV.addLine(r+"", rates[r]+"");
		ratesCSV.writeToStream(zout);
		zout.flush();
		zout.closeEntry();
		
		String info = sol.getInfoString();
		if (info != null && !info.isBlank())
			writeInfoToZip(zout, info, "solution_info.txt");
		
		writeModules(zout, sol.getModules(), "modules.json");
		
		if (sol.getRupSet().hasModule(CopyOverModule.class))
			sol.getRupSet().getModule(CopyOverModule.class).copyOver(zout);
		zout.close();
	}
	
	public static FaultSystemSolution loadSol(File solFile) throws IOException {
		System.out.println("Loading solution from "+solFile.getAbsolutePath());
		
		ZipFile zip = new ZipFile(solFile);
		
		FaultSystemRupSet rupSet = loadRupSetWithoutModules(zip);
		
		ZipEntry ratesEntry = new ZipEntry("rates.csv");
		CSVFile<String> ratesCSV = CSVFile.readStream(zip.getInputStream(ratesEntry), true);
		double[] rates = new double[rupSet.getNumRuptures()];
		Preconditions.checkState(ratesCSV.getNumRows() == rupSet.getNumRuptures()+1, "Unexpected number of rows in rates CSV");
		for (int r=0; r<rates.length; r++) {
			Preconditions.checkState(ratesCSV.getInt(r+1, 0) == r, "Rates CSV out of order or not 0-based");
			rates[r] = ratesCSV.getDouble(r+1, 1);
		}
		
		// info
		String info = loadInfoFromZip(zip, "solution_info.txt");
		
		FaultSystemSolution sol = new FaultSystemSolution(rupSet, rates);
		if (info != null && !info.isBlank())
			sol.setInfoString(info);
		
		if (zip.getEntry("modules.json") != null) {
			List<StatefulModuleRecord> records = loadModuleRecords(zip, "modules.json");
			loadModules(rupSet, sol, records, zip);
		}
		
		rupSet.addModule(new CopyOverModule(rupSet, zip));
		
		return sol;
	}
	
	private static void writeModules(ZipOutputStream zout, List<? extends Named> modules, String entryName) throws IOException {
		List<StatefulModuleRecord> written = new ArrayList<>();
		for (Named module : modules) {
			if (module instanceof StatefulModule) {
				System.out.println("Writing stateful module: "+module.getName());
				StatefulModule stateful = (StatefulModule)module;
				stateful.writeToArchive(zout, null);
				written.add(new StatefulModuleRecord(stateful));
				try {
					Preconditions.checkNotNull(stateful.getLoadingClass().getDeclaredConstructor());
				} catch (Throwable t) {
					System.err.println("WARNING: Loading class for module doesn't contain a no-arg constructor, "
							+ "loading from a zip file will fail: "+stateful.getLoadingClass().getName());
				}
			} else {
				System.out.println("Skipping transient module: "+module.getName());
			}
		}
		if (!written.isEmpty()) {
			System.out.println("Wrote "+written.size()+" modules, writing index");
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			
			zout.putNextEntry(new ZipEntry(entryName));
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zout));
			gson.toJson(written, writer);
			writer.write("\n");
			writer.flush();
			zout.closeEntry();
		}
	}
	
	private static class StatefulModuleRecord {
		public final String name;
		public final String className;
		
		public StatefulModuleRecord(StatefulModule module) {
			this.name = module.getName();
			this.className = module.getLoadingClass().getName();
		}
	}
	
	private static List<StatefulModuleRecord> loadModuleRecords(ZipFile zip, String entryName) throws IOException {
		ZipEntry entry = zip.getEntry(entryName);
		Preconditions.checkNotNull(entry, "Modules entry not found in zip archive");
		
		Gson gson = new GsonBuilder().create();
		
		InputStream zin = zip.getInputStream(entry);
		BufferedReader reader = new BufferedReader(new InputStreamReader(zin));
		List<StatefulModuleRecord> records = gson.fromJson(reader,
				TypeToken.getParameterized(List.class, StatefulModuleRecord.class).getType());
		reader.close();
		
		zin.close();
		return records;
	}
	
	/// TODO: should module loading be lazy? e.g., wrap them in stubs and only load when queried
	
	@SuppressWarnings("unchecked")
	private static void loadModules(FaultSystemRupSet rupSet, FaultSystemSolution sol,
			List<StatefulModuleRecord> records, ZipFile zip) throws IOException {
		for (StatefulModuleRecord record : records) {
			System.out.println("Loading module: "+record.name);
			Class<?> clazz;
			try {
				clazz = Class.forName(record.className);
			} catch(Exception e) {
				System.err.println("WARNING: Skipping module '"+record.name
						+"', couldn't locate class: "+record.className);
				continue;
			}
			try {
				if (RupSetModule.class.isAssignableFrom(clazz)) {
					RupSetModule module = RupSetModule.instance((Class<? extends RupSetModule>)clazz, rupSet);
					if (!(module instanceof StatefulModule)) {
						System.err.println("WARNING: Module class is not stateful, skipping: "+record.className);
						continue;
					}
					((StatefulModule)module).initFromArchive(zip, null);
					rupSet.addModule(module);
				} else if (SolutionModule.class.isAssignableFrom(clazz)) {
					if (sol == null) {
						System.err.println("WARNING: Skipping loading module '"+record.name+"' as it applies to solutions, "
								+ "but we only have a rupture set.");
						continue;
					}
					SolutionModule module = SolutionModule.instance((Class<? extends SolutionModule>)clazz, sol);
					if (!(module instanceof StatefulModule)) {
						System.err.println("WARNING: Module class is not stateful, skipping: "+record.className);
						continue;
					}
					((StatefulModule)module).initFromArchive(zip, null);
					sol.addModule(module);
				} else {
					System.err.println("WARNING: Skipping module '"+record.name
							+"' as the specified class isn't a RupSetModule: "+record.className);
					continue;
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("Error loading module '"+record.name+"', skipping.");
			}
		}
	}
	
	private static String loadInfoFromZip(ZipFile zip, String entryName) throws IOException {
		ZipEntry infoEntry = zip.getEntry(entryName);
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
		} else {
			return null;
		}
	}
	
	private static void writeInfoToZip(ZipOutputStream zout, String info, String entryName) throws IOException {
		zout.putNextEntry(new ZipEntry(entryName));
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zout));
		writer.write(info);
		writer.flush();
		zout.flush();
		zout.closeEntry();
	}
	
	/**
	 * Special utility module class to copy over any information stored in an input zip file when writing a new output.
	 * This will copy over modules that could not be loaded at runtime, e.g., because the referenced class wass missing,
	 * or any other custom data stored in the zip file.
	 * 
	 * @author kevin
	 *
	 */
	private static class CopyOverModule extends RupSetModule {

		private ZipFile zip;
//		private ZipO

		public CopyOverModule(FaultSystemRupSet rupSet, ZipFile zip) {
			super(rupSet);
			this.zip = zip;
		}

		@Override
		public String getName() {
			return "Zip Copy-Over";
		}
		
		public void copyOver(EntryTrackingZOUT zout) throws IOException {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				
				if (!zout.entries.containsKey(entry.getName())) {
					// need to copy this over
					System.out.println("Copying over unknown file from previous archive: "+entry.getName());
					zout.putNextEntry(new ZipEntry(entry.getName()));
					
					BufferedInputStream bin = new BufferedInputStream(zip.getInputStream(entry));
					bin.transferTo(zout);
					zout.flush();
					
					zout.closeEntry();
				}
			}
		}
		
	}
	
	private static class EntryTrackingZOUT extends ZipOutputStream {
		
		private final HashMap<String, ZipEntry> entries;

		public EntryTrackingZOUT(OutputStream out) {
			super(out);
			this.entries = new HashMap<>();
		}

		@Override
		public void putNextEntry(ZipEntry e) throws IOException {
			this.entries.put(e.getName(), e);
			super.putNextEntry(e);
		}
		
	}
	
	public static void main(String[] args) throws Exception {
		Thread.sleep(3000);
		FaultSystemRupSet rupSet = loadRupSet(new File("/tmp/test_rup_set.zip"));
		writeRupSet(rupSet, new File("/tmp/test_rup_set_2.zip"));
	}

}
