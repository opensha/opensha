package scratch.UCERF3;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.XMLUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.griddedSeismicity.GridSourceFileReader;
import scratch.UCERF3.griddedSeismicity.GridSourceProvider;
import scratch.UCERF3.inversion.BatchPlotGen;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.logicTree.LogicTreeBranchNode;
import scratch.UCERF3.logicTree.VariableLogicTreeBranch;
import scratch.UCERF3.utils.MatrixIO;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.UCERF3_DataUtils;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * This class loads in a set of InversionFaultSystemSolutions from a zip file. It also has
 * a static method for writing this zip file. The zip files avoid duplicating information
 * between similar logic tree branches. For example, rupture rakes only vary among fault models
 * and deformation models, so they would only be included in the zip file once for each FM/DM
 * combination. See the javadoc for dependencyMap below for specifics of these mappings.
 * 
 * There are also special methods for loading in individual components of a solution when you
 * only need one or two fields and don't want the overhead of loading a fault system solution.
 * 
 * @author kevin
 *
 */
public class CompoundFaultSystemSolution extends FaultSystemSolutionFetcher {
	
	private ZipFile zip;
	private List<LogicTreeBranch> branches;
	
	public CompoundFaultSystemSolution(ZipFile zip) {
		this.zip = zip;
		branches = Lists.newArrayList();
		
		Enumeration<? extends ZipEntry> zipEnum = zip.entries();
		// need to sort to ensure consistent iteration order for parallel runs
		List<ZipEntry> entriesList = Lists.newArrayList();
		while (zipEnum.hasMoreElements())
			entriesList.add(zipEnum.nextElement());
		Collections.sort(entriesList, new Comparator<ZipEntry>() {

			@Override
			public int compare(ZipEntry o1, ZipEntry o2) {
				return o1.getName().compareTo(o2.getName());
			}
		});
		for (ZipEntry entry : entriesList)
			if (entry.getName().endsWith("_rates.bin"))
				branches.add(VariableLogicTreeBranch.fromFileName(entry.getName()));
		
		System.out.println("Detected "+branches.size()+" branches in zip file!");
	}

	@Override
	public Collection<LogicTreeBranch> getBranches() {
		return branches;
	}

	@Override
	protected InversionFaultSystemSolution fetchSolution(LogicTreeBranch branch) {
		try {
			Map<String, String> nameRemappings = getRemappings(branch);
			FaultSystemSolution sol = FaultSystemIO.loadSolAsApplicable(zip, nameRemappings);
			Preconditions.checkState(sol instanceof InversionFaultSystemSolution,
					"Non IVFSS in Compound Sol?");
			
			return (InversionFaultSystemSolution)sol;
		} catch (Exception e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}
	
	public double[] getRates(LogicTreeBranch branch) {
		return loadDoubleArray(branch, "rates.bin");
	}
	
	public double[] loadDoubleArray(LogicTreeBranch branch, String fileName) {
		try {
			Map<String, String> nameRemappings = getRemappings(branch);
			String remapped = nameRemappings.get(fileName);
			if (remapped == null)
				remapped = branch.buildFileName()+"_"+fileName;
			ZipEntry ratesEntry = zip.getEntry(remapped);
			return MatrixIO.doubleArrayFromInputStream(
					new BufferedInputStream(zip.getInputStream(ratesEntry)), ratesEntry.getSize());
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}
	
	public String getInfo(LogicTreeBranch branch) {
		try {
			Map<String, String> nameRemappings = getRemappings(branch);
			ZipEntry infoEntry = zip.getEntry(nameRemappings.get("info.txt"));
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
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}
	
	public double[] getMags(LogicTreeBranch branch) {
		return loadDoubleArray(branch, "mags.bin");
	}
	
	public double[] getLengths(LogicTreeBranch branch) {
		return loadDoubleArray(branch, "rup_lengths.bin");
	}
	
	public List<FaultSection> getSubSects(LogicTreeBranch branch) throws DocumentException, IOException {
		Map<String, String> nameRemappings = getRemappings(branch);
		ZipEntry fsdEntry = zip.getEntry(nameRemappings.get("fault_sections.xml"));
		Document doc = XMLUtils.loadDocument(
				new BufferedInputStream(zip.getInputStream(fsdEntry)));
		Element fsEl = doc.getRootElement().element(FaultSectionPrefData.XML_METADATA_NAME+"List");
		Preconditions.checkNotNull(fsEl, "Fault sections element not found");
		return FaultSystemIO.fsDataFromXML(fsEl);
	}
	
	public GridSourceProvider loadGridSourceProviderFile(LogicTreeBranch branch) throws DocumentException, IOException {
		Map<String, String> nameRemappings = getRemappings(branch);
		ZipEntry gridSourcesEntry = zip.getEntry(nameRemappings.get("grid_sources.xml"));
		ZipEntry gridSourcesBinEntry = zip.getEntry(nameRemappings.get("grid_sources.bin"));
		if (gridSourcesBinEntry == null)
			System.out.println("Doesn't have: "+nameRemappings.get("grid_sources.bin"));
		ZipEntry gridSourcesRegEntry = zip.getEntry(nameRemappings.get("grid_sources_reg.xml"));
		if (gridSourcesRegEntry == null)
			System.out.println("Doesn't have: "+nameRemappings.get("grid_sources_reg.xml"));
		if (gridSourcesEntry != null) {
			return GridSourceFileReader.fromInputStream(zip.getInputStream(gridSourcesEntry));
		} else if (gridSourcesBinEntry != null && gridSourcesRegEntry != null) {
			return GridSourceFileReader.fromBinStreams(
					zip.getInputStream(gridSourcesBinEntry), zip.getInputStream(gridSourcesRegEntry));
		}
		throw new FileNotFoundException();
	}
	
	/**
	 * *********************************************
	 * Files						Dependencies
	 * *********************************************
	 * close_sections.bin			FM
	 * cluster_rups.bin				FM
	 * cluster_sects.bin			FM
	 * fault_sections.xml			FM, DM
	 * info.txt						ALL
	 * mags.bin						FM, DM, Scale
	 * rakes.bin					FM, DM
	 * rates.bin					ALL
	 * rup_areas.bin				FM, DM
	 * rup_lengths.bin				FM
	 * rup_avg_slips.bin			FM, DM, Scale
	 * rup_sec_slip_type.txt		N/A
	 * rup_sections.bin				FM
	 * sect_areas.bin				FM, DM
	 * sect_slips.bin				ALL BUT Dsr
	 * sect_slips_std_dev.bin		ALL BUT Dsr
	 * inv_rup_set_metadata.xml		ALL
	 * inv_sol_metadata.xml			ALL
	 * grid_sources.xml				ALL // old xml format
	 * grid_sources_reg.xml			NONE // new binary format
	 * grid_sources.bin				ALL // new binary format
	 * rup_mfds.bin					ALL
	 * sub_seismo_on_fault_mfds.bin	ALL
	 * 
	 * null entry in map means ALL!
	 */
	private static Map<String, List<Class<? extends LogicTreeBranchNode<?>>>> dependencyMap;
	static {
		dependencyMap = Maps.newHashMap();
		
		dependencyMap.put("close_sections.bin", buildList(FaultModels.class));
		dependencyMap.put("cluster_rups.bin", buildList(FaultModels.class));
		dependencyMap.put("cluster_sects.bin", buildList(FaultModels.class));
		dependencyMap.put("fault_sections.xml", buildList(FaultModels.class, DeformationModels.class));
		dependencyMap.put("info.txt", null);
		dependencyMap.put("mags.bin", buildList(FaultModels.class, DeformationModels.class, ScalingRelationships.class));
		dependencyMap.put("rakes.bin", buildList(FaultModels.class, DeformationModels.class));
		dependencyMap.put("rates.bin", null);
		dependencyMap.put("rup_areas.bin", buildList(FaultModels.class, DeformationModels.class));
		dependencyMap.put("rup_lengths.bin", buildList(FaultModels.class));
		dependencyMap.put("rup_avg_slips.bin", buildList(FaultModels.class, DeformationModels.class, ScalingRelationships.class));
		dependencyMap.put("rup_sec_slip_type.txt", null); // kept for backwards compatibility
		dependencyMap.put("rup_sections.bin", buildList(FaultModels.class));
		dependencyMap.put("rakes.bin", buildList(FaultModels.class, DeformationModels.class));
		dependencyMap.put("sect_areas.bin", buildList(FaultModels.class, DeformationModels.class));
		dependencyMap.put("sect_slips.bin", buildList(FaultModels.class, DeformationModels.class,
				ScalingRelationships.class, InversionModels.class, TotalMag5Rate.class,
				MaxMagOffFault.class, MomentRateFixes.class, SpatialSeisPDF.class));
		dependencyMap.put("sect_slips_std_dev.bin", buildList(FaultModels.class, DeformationModels.class,
				ScalingRelationships.class, InversionModels.class, TotalMag5Rate.class,
				MaxMagOffFault.class, MomentRateFixes.class, SpatialSeisPDF.class));
		dependencyMap.put("inv_rup_set_metadata.xml", null);
		dependencyMap.put("inv_sol_metadata.xml", null);
		dependencyMap.put("grid_sources.xml", null);
		dependencyMap.put("grid_sources_reg.xml", buildList());
		dependencyMap.put("grid_sources.bin", null);
		dependencyMap.put("rup_mfds.bin", null);
		dependencyMap.put("sub_seismo_on_fault_mfds.bin", null);
		dependencyMap.put("plausibility.json", buildList(FaultModels.class));
		dependencyMap.put("cluster_ruptures.json", null);
	}
	
	private static List<Class<? extends LogicTreeBranchNode<?>>> buildList(
			Class<? extends LogicTreeBranchNode<?>>... vals) {
		List<Class<? extends LogicTreeBranchNode<?>>> list = Lists.newArrayList();
		for (Class<? extends LogicTreeBranchNode<?>> val : vals)
			list.add(val);
		return list;
	}
	
	public void toZipFile(File file) throws IOException {
		toZipFile(file, this);
	}
	
	public static void toZipFile(File file, FaultSystemSolutionFetcher fetcher) throws IOException {
		System.out.println("Making compound zip file: "+file.getName());
		File tempDir = FileUtils.createTempDir();
		
		HashSet<String> zipFileNames = new HashSet<String>();
		
		for (LogicTreeBranch branch : fetcher.getBranches()) {
			FaultSystemSolution sol = fetcher.getSolution(branch);
			
			Map<String, String> remappings = getRemappings(branch);
			
			FaultSystemIO.writeSolFilesForZip(sol, tempDir, zipFileNames, remappings);
		}
		
		FileUtils.createZipFile(file.getAbsolutePath(), tempDir.getAbsolutePath(), zipFileNames);
		
		System.out.println("Deleting temp files");
		FileUtils.deleteRecursive(tempDir);
		
		System.out.println("Done saving!");
	}
	
	private static Map<String, String> getRemappings(LogicTreeBranch branch) {
		Map<String, String> remappings = Maps.newHashMap();
		
		for (String name : dependencyMap.keySet())
			remappings.put(name, getRemappedName(name, branch));
		
		return remappings;
	}
	
	/**
	 * Filenames are modified according to the branch elements that influence that file.
	 * This returns the modified filename, for example, rakes.bin could become 'FM3_1_GEOL_rakes.bin'.
	 * @param name
	 * @param branch
	 * @return
	 */
	public static String getRemappedName(String name, LogicTreeBranch branch) {
		String nodeStr = "";
		List<Class<? extends LogicTreeBranchNode<?>>> dependencies = dependencyMap.get(name);
		if (dependencies == null)
			nodeStr = branch.buildFileName()+"_";
		else
			for (Class<? extends LogicTreeBranchNode<?>> clazz : dependencies)
				nodeStr += branch.getValueUnchecked(clazz).encodeChoiceString()+"_";
		return nodeStr+name;
	}
	
	public static CompoundFaultSystemSolution fromZipFile(File file) throws ZipException, IOException {
		ZipFile zip = new ZipFile(file);
		return new CompoundFaultSystemSolution(zip);
	}
	
	public static void main(String[] args) throws IOException {
		if (args.length >= 1) {
			// command line run
			File dir = new File(args[0]);
			List<String> nameGreps = Lists.newArrayList();
			for (int i=1; i<args.length; i++)
				nameGreps.add(args[i]);
			BatchPlotGen.writeCombinedFSS(dir, nameGreps);
			System.exit(0);
		}
//		File dir = new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "InversionSolutions");
		File dir = new File("/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/InversionSolutions");
//		File dir = new File("/tmp/compound_tests_data/subset/");
//		FileBasedFSSIterator it = FileBasedFSSIterator.forDirectory(dir, 1, Lists.newArrayList(FileBasedFSSIterator.TAG_BUILD_MEAN));
		
//		File compoundFile = new File(dir, "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL.zip");
		File compoundFile = new File(dir, "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_WITH_IND_RUNS.zip");
//		File compoundFile = new File(dir, "subset_COMPOUND_SOL.zip");
		Stopwatch watch = Stopwatch.createStarted();
//		watch.start();
//		toZipFile(compoundFile, it);
//		watch.stop();
//		System.out.println("Took "+(watch.elapsedMillis() / 1000d)+" seconds to save");
		
		CompoundFaultSystemSolution compoundSol = fromZipFile(compoundFile);
//		System.exit(0);
		
		for (LogicTreeBranch branch : compoundSol.getBranches()) {
			System.out.println("Loading "+branch);
			System.out.println(ClassUtils.getClassNameWithoutPackage(
					compoundSol.getSolution(branch).getClass()));
		}
		System.out.println("Took "+watch.elapsed(TimeUnit.SECONDS)+" seconds to load");
	}

}
