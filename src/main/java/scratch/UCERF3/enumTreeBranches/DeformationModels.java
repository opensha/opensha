package scratch.UCERF3.enumTreeBranches;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.dom4j.Document;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetSubsectioningModel;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import scratch.UCERF3.logicTree.U3LogicTreeBranchNode;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.U3FaultSystemIO;
import scratch.UCERF3.utils.UCERF3_DataUtils;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum DeformationModels implements U3LogicTreeBranchNode<DeformationModels>, RupSetDeformationModel, RupSetSubsectioningModel {
	
	//						Name					ShortName	Weight	FaultModel			File
	// UCERF2
	UCERF2_ALL(				"UCERF2 All",			"UC2ALL",	0d,		FaultModels.FM2_1),
	UCERF2_NCAL(			"UCERF2 NCal", 			"NCAL", 	0d,		FaultModels.FM2_1),
	UCERF2_BAYAREA(			"UCERF2 Bay Area", 		"BAY",		0d,		FaultModels.FM2_1),
	
	// UCERF3
	// AVERAGE BLOCK MODEL
	ABM(					"Average Block Model",	"ABM",		0.10d,	FaultModels.FM3_1,	"ABM_slip_rake_fm_3_1_2013_04_09.csv",
																		FaultModels.FM3_2,	"ABM_slip_rake_fm_3_2_2013_04_09.csv"),
	// GEOBOUNDED INVERSION
	GEOBOUND(				"Geobounded",			"GEOB",		0d,		FaultModels.FM3_1,	"geobound_slip_rake__MAPPED_2012_06_05.csv"),
	// NEOKINEMA
	NEOKINEMA(				"Neokinema",			"NEOK",		0.30d,	FaultModels.FM3_1,	"neokinema_slip_rake_fm_3_1_2013_04_09.csv",
																		FaultModels.FM3_2,	"neokinema_slip_rake_fm_3_2_2013_04_09.csv"),
	// ZENG
	ZENG(					"Zeng Unbounded",		"ZENG",		0.00d,	FaultModels.FM3_1,	"zeng_slip_rake_fm_3_1_2012_10_11.csv",
																		FaultModels.FM3_2,	"zeng_slip_rake_fm_3_2_2012_10_11.csv"),
	ZENGAB(					"Zeng All Bounded",		"ZENGAB",	0.00d,	FaultModels.FM3_1,	"zeng_slip_rake_fm_3_1_all_bounded_2013_01_10.csv",
																		FaultModels.FM3_2,	"zeng_slip_rake_fm_3_2_all_bounded_2013_01_10.csv"),
	ZENGBB(					"Zeng B-Fault Bounded",	"ZENGBB",	0.30d,	FaultModels.FM3_1,	"zeng_slip_rake_fm_3_1_b_bounded_2013_04_09.csv",
																		FaultModels.FM3_2,	"zeng_slip_rake_fm_3_2_b_bounded_2013_04_09.csv"),
	// GEOLOGIC
	GEOLOGIC(				"Geologic",				"GEOL", 	0.30d,	FaultModels.FM3_1,	"geologic_slip_rake_fm_3_1_2013_04_09.csv",
																		FaultModels.FM3_2,	"geologic_slip_rake_fm_3_2_2013_04_09.csv"),
	GEOLOGIC_UPPER(			"Geologic Upper Bound",	"GLUP", 	0d,		FaultModels.FM3_1,	"geologic_slip_rake_fm_3_1_upperbound_2013_04_09.csv",
																		FaultModels.FM3_2,	"geologic_slip_rake_fm_3_2_upperbound_2013_04_09.csv"),
	GEOLOGIC_LOWER(			"Geologic Lower Bound",	"GLLOW", 	0d,		FaultModels.FM3_1,	"geologic_slip_rake_fm_3_1_lowerbound_2013_04_09.csv",
																		FaultModels.FM3_2,	"geologic_slip_rake_fm_3_2_lowerbound_2013_04_09.csv"),
	// GEOLOGIC + ABM
	GEOLOGIC_PLUS_ABM(		"Geologic + ABM",		"GLpABM",	0d,		FaultModels.FM3_1,	"geologic_plus_ABM_slip_rake_fm_3_1_2012_06_08.csv",
																		FaultModels.FM3_2,	"geologic_plus_ABM_slip_rake_fm_3_2_2012_06_08.csv"),
	GEOL_P_ABM_OLD_MAPPED(	"Geologic + ABM OLD",	"GLpABMOLD",0d,		FaultModels.FM3_1,	"geologic_plus_ABM_slip_rake__MAPPED_2012_06_05.csv"),
	
	MEAN_UCERF3(			"Mean UCERF3 DM",		"MeanU3DM",	0d,		FaultModels.FM3_1,	null,
																		FaultModels.FM3_2,	null);
	
	private List<FaultModels> faultModels;
	private List<String> fileNames;
	private String name, shortName;
	private double weight;
	
	private DeformationModels(String name, String shortName, double weight, FaultModels model) {
		this(name, shortName, weight, Lists.newArrayList(model), null);
	}

	private DeformationModels(String name, String shortName, double weight, FaultModels model, String file) {
		this(name, shortName, weight, Lists.newArrayList(model), Lists.newArrayList(file));
	}

	private DeformationModels(String name, String shortName, double weight, FaultModels model1, String file1,
			FaultModels model2, String file2) {
		this(name, shortName, weight, Lists.newArrayList(model1, model2), Lists.newArrayList(file1, file2));
	}
	
	private DeformationModels(String name, String shortName, double weight, List<FaultModels> faultModels, List<String> fileNames) {
		Preconditions.checkNotNull(faultModels, "fault models cannot be null!");
		Preconditions.checkArgument(!faultModels.isEmpty(), "fault models cannot be empty!");
		Preconditions.checkArgument(fileNames == null || fileNames.size() == faultModels.size(),
				"file names must either be null or the same size as fault models!");
		this.faultModels = faultModels;
		this.fileNames = fileNames;
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
	}
	
	public String getName() {
		return name;
	}
	
	public String getShortName() {
		return shortName;
	}
	
	@Override
	public String toString() {
		return name;
	}
	
	public boolean isApplicableTo(RupSetFaultModel faultModel) {
		return faultModels.contains(faultModel);
	}
	
	public List<FaultModels> getApplicableFaultModels() {
		return Collections.unmodifiableList(faultModels);
	}

	public URL getDataFileURL(FaultModels faultModel) {
		String fileName = getDataFileName(faultModel);
		if (fileName == null)
			return null;
		return UCERF3_DataUtils.locateResource("DeformationModels", fileName);
	}
	
	public String getDataFileName(FaultModels faultModel) {
		Preconditions.checkState(isApplicableTo(faultModel),
				"Deformation model "+name()+" isn't applicable to fault model: "+faultModel);
		if (fileNames == null)
			return null;
		return fileNames.get(faultModels.indexOf(faultModel));
	}
	
	public static List<DeformationModels> forFaultModel(FaultModels fm) {
		ArrayList<DeformationModels> mods = new ArrayList<DeformationModels>();
		for (DeformationModels mod : values())
			if (mod.isApplicableTo(fm))
				mods.add(mod);
		return mods;
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
		return "Deformation Model";
	}
	
	@Override
	public String getShortBranchLevelName() {
		return "DM";
	}
	
	private static File getCacheDir() {
		File scratchDir = UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR;
		if (scratchDir.exists()) {
			// eclipse project
			File dir = new File(scratchDir, "SubSections");
			if (!dir.exists())
				Preconditions.checkState(dir.mkdir());
			return dir;
		} else {
			// use home dir
			String path = System.getProperty("user.home");
			File homeDir = new File(path);
			Preconditions.checkState(homeDir.exists(), "user.home dir doesn't exist: "+path);
			File openSHADir = new File(homeDir, ".opensha");
			if (!openSHADir.exists())
				Preconditions.checkState(openSHADir.mkdir(),
						"Couldn't create OpenSHA store location: "+openSHADir.getAbsolutePath());
			File uc3Dir = new File(openSHADir, "ucerf3_sub_sects");
			if (!uc3Dir.exists())
				Preconditions.checkState(uc3Dir.mkdir(),
						"Couldn't create UCERF3 ERF store location: "+uc3Dir.getAbsolutePath());
			return uc3Dir;
		}
	}
	
	public static List<? extends FaultSection> loadSubSects(FaultModels fm, DeformationModels dm) {
		File cacheDir = getCacheDir();
		File xmlFile = new File(cacheDir, fm.encodeChoiceString()+"_"+dm.encodeChoiceString()+"_sub_sects.xml");
		if (xmlFile.exists()) {
			try {
				return FaultModels.loadStoredFaultSections(xmlFile);
			} catch (Exception e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
		}
		System.out.println("No sub section cache exists for "+fm.getShortName()+", "+dm.getShortName());
		List<? extends FaultSection> sects = new DeformationModelFetcher(
				fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, 0.1).getSubSectionList();
		// write to XML
		Document doc = XMLUtils.createDocumentWithRoot();
		U3FaultSystemIO.fsDataToXML(doc.getRootElement(), FaultModels.XML_ELEMENT_NAME, fm, null, sects);
		try {
			XMLUtils.writeDocumentToFile(xmlFile, doc);
		} catch (IOException e) {
			System.err.println("WARNING: Couldn't write cache file: "+xmlFile.getAbsolutePath());
			e.printStackTrace();
		}
		return sects;
	}

	@Override
	public List<? extends FaultSection> build(RupSetFaultModel faultModel, RupSetSubsectioningModel subSectionModel,
			LogicTreeBranch<? extends LogicTreeNode> branch) throws IOException {
		Preconditions.checkState(faultModel instanceof FaultModels, "Given fault model is not a UCERF3 fault model");
		Preconditions.checkState(subSectionModel == null || subSectionModel == this, "UCERF3 DMs build their own subsections");
		return new DeformationModelFetcher((FaultModels)faultModel, this, null, 0.1).getSubSectionList();
	}

	@Override
	public List<? extends FaultSection> apply(RupSetFaultModel faultModel,
			LogicTreeBranch<? extends LogicTreeNode> branch, List<? extends FaultSection> subSects) throws IOException {
		throw new UnsupportedOperationException("Not supported, UCERF3 must build the subsections");
	}

	@Override
	public List<? extends FaultSection> apply(RupSetFaultModel faultModel,
			LogicTreeBranch<? extends LogicTreeNode> branch, List<? extends FaultSection> fullSects,
			List<? extends FaultSection> subSects) throws IOException {
		throw new UnsupportedOperationException("Not supported, UCERF3 must build the subsections");
	}

	@Override
	public List<? extends FaultSection> buildSubSects(RupSetFaultModel faultModel,
			List<? extends FaultSection> fullSections) {
		try {
			return build(faultModel, this, null);
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}
}