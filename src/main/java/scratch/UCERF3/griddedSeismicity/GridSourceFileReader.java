package scratch.UCERF3.griddedSeismicity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.opensha.commons.data.function.AbstractDiscretizedFunc;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.MFDGridSourceProvider;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import scratch.UCERF3.U3FaultSystemSolution;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.utils.U3FaultSystemIO;
import scratch.UCERF3.utils.MatrixIO;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Deprecated // old formats and UCERF3 specific
public class GridSourceFileReader extends AbstractGridSourceProvider implements XMLSaveable {
	
	private static final String NODE_MFD_LIST_EL_NAME = "MFDNodeList";
	private static final String NODE_MFD_ITEM_EL_NAME = "MFDNode";
	private static final String SUB_SIZE_MFD_EL_NAME = "SubSeisMFD";
	private static final String UNASSOCIATED_MFD_EL_NAME = "UnassociatedFD";
	
	private GriddedRegion region;
	private Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs;
	private Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs;
	
	private double[] fracStrikeSlip,fracNormal,fracReverse;
	
	public GridSourceFileReader(GriddedRegion region,
			Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs,
			Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs) {
		initFocalMechGrids();
		this.region = region;
		this.nodeSubSeisMFDs = nodeSubSeisMFDs;
		this.nodeUnassociatedMFDs = nodeUnassociatedMFDs;
	}
	
	@Override
	public IncrementalMagFreqDist getMFD_Unassociated(int idx) {
		return nodeUnassociatedMFDs.get(idx);
	}

	@Override
	public IncrementalMagFreqDist getMFD_SubSeisOnFault(int idx) {
		return nodeSubSeisMFDs.get(idx);
	}

	@Override
	public GriddedRegion getGriddedRegion() {
		return region;
	}

	@Override
	public Element toXMLMetadata(Element root) {
		region.toXMLMetadata(root);
		
		Element nodeListEl = root.addElement(NODE_MFD_LIST_EL_NAME);
		nodeListEl.addAttribute("num", region.getNumLocations()+"");
		for (int i=0; i<region.getNumLocations(); i++) {
			Element nodeEl = nodeListEl.addElement(NODE_MFD_ITEM_EL_NAME);
			nodeEl.addAttribute("index", i+"");
			
			IncrementalMagFreqDist subSeisMFD = nodeSubSeisMFDs.get(i);
			IncrementalMagFreqDist unassociatedMFD = nodeUnassociatedMFDs.get(i);
			
			if (subSeisMFD != null)
				subSeisMFD.toXMLMetadata(nodeEl, SUB_SIZE_MFD_EL_NAME);
			if (unassociatedMFD != null)
				unassociatedMFD.toXMLMetadata(nodeEl, UNASSOCIATED_MFD_EL_NAME);
		}
		
		return root;
	}
	
	/**
	 * This writes gridded seismicity MFDs to the given XML file
	 * @param file
	 * @throws IOException
	 */
	public void writeGriddedSeisFile(File file) throws IOException {
		Document doc = XMLUtils.createDocumentWithRoot();
		Element root = doc.getRootElement();
		
		toXMLMetadata(root);
		
		XMLUtils.writeDocumentToFile(file, doc);
	}
	
	private static double[] funcToArray(boolean x, DiscretizedFunc func, double minX) {
		int firstIndex = 0;
		for (int i=0; i<func.size(); i++) {
			if (func.getX(i) >= minX) {
				firstIndex = i;
				break;
			}
		}
		int numAbove = func.size()-firstIndex;
		double[] ret = new double[numAbove];
		for (int i=0; i<numAbove; i++) {
			if (x)
				ret[i] = func.getX(firstIndex+i);
			else
				ret[i] = func.getY(firstIndex+i);
		}
		return ret;
	}
	
	/**
	 * This writes gridded seismicity MFDs to the given binary file. XML metadata for the region is stored
	 * in it's own xml file.
	 * @param file
	 * @param region
	 * @param nodeSubSeisMFDs
	 * @param nodeUnassociatedMFDs
	 * @throws IOException
	 */
	public static void writeGriddedSeisBinFile(
			File binFile, File regXMLFile, MFDGridSourceProvider gridProv, double minMag) throws IOException {
		DiscretizedFunc refFunc = null;
		for (int i=0; i<gridProv.getNumLocations(); i++) {
			if (gridProv.getMFD_Unassociated(i) != null) {
				refFunc = gridProv.getMFD_Unassociated(i);
				break;
			} else if (gridProv.getMFD_SubSeisOnFault(i) != null) {
				refFunc = gridProv.getMFD_SubSeisOnFault(i);
				break;
			}
		}
		Preconditions.checkNotNull(refFunc, "All funcs are null!");
		
		List<double[]> arrays = Lists.newArrayList();
		// add x values for ref function
		arrays.add(funcToArray(true, refFunc, minMag));
		
		for (int i=0; i<gridProv.getNumLocations(); i++) {
			DiscretizedFunc unMFD = gridProv.getMFD_Unassociated(i);
			if (unMFD != null && unMFD.getMaxY()>0) {
				Preconditions.checkState(unMFD.getMinX() == refFunc.getMinX()
						&& unMFD.getMaxX() == refFunc.getMaxX());
				arrays.add(funcToArray(false, unMFD, minMag));
			} else {
				arrays.add(new double[0]);
			}
			DiscretizedFunc subSeisMFD = gridProv.getMFD_SubSeisOnFault(i);
			if (subSeisMFD != null && subSeisMFD.getMaxY()>0) {
				Preconditions.checkState(subSeisMFD.getMinX() == refFunc.getMinX()
						&& subSeisMFD.getMaxX() == refFunc.getMaxX());
				arrays.add(funcToArray(false, subSeisMFD, minMag));
			} else {
				arrays.add(new double[0]);
			}
		}
		
		MatrixIO.doubleArraysListToFile(arrays, binFile);
		
		if (regXMLFile == null)
			return;
		
		// now write gridded_region
		Document doc = XMLUtils.createDocumentWithRoot();
		Element root = doc.getRootElement();
		
		gridProv.getGriddedRegion().toXMLMetadata(root);
		
		XMLUtils.writeDocumentToFile(regXMLFile, doc);
	}
	
	public static GridSourceFileReader fromBinFile(File binFile, File regXMLFile) throws IOException, DocumentException {
		return fromBinStreams(new BufferedInputStream(new FileInputStream(binFile)),
				new BufferedInputStream(new FileInputStream(regXMLFile)));
	}
	
	private static GriddedRegion loadU3RegionJSON() {
		System.out.println("Avoided GriddedRegion XML deserialization issue due to new location, loading GeoJSON instead");
		return new CaliforniaRegions.RELM_TESTING_GRIDDED();
	}
	
	public static GridSourceFileReader fromBinStreams(InputStream binFileStream, InputStream regXMLFileStream)
			throws IOException, DocumentException {
		// load region
		Document doc = XMLUtils.loadDocument(regXMLFileStream);
		Element regionEl = doc.getRootElement().element(GriddedRegion.XML_METADATA_NAME);
		
		List<double[]> arrays = MatrixIO.doubleArraysListFromInputStream(binFileStream);
		int calcNumNodes = (arrays.size()-1)/2; // -1 for the x values, divide by to for associated and unassocaited
		GriddedRegion region = GriddedRegion.fromXMLMetadata(regionEl);
		if (region.getNodeCount() == 7637 && calcNumNodes == 7636)
			region = loadU3RegionJSON();
		Preconditions.checkState(calcNumNodes == region.getNodeCount()); // +1 for the x values
		int cnt = 0;
		double[] xVals = arrays.get(cnt++);
		
		Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs = Maps.newHashMap();
		Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs = Maps.newHashMap();
		
		for (int i=0; i<region.getNodeCount(); i++) {
			double[] unY = arrays.get(cnt++); // +1 for X
			double[] subY = arrays.get(cnt++); // +2 for X and unassociated
			
			if (unY.length > 0)
				nodeUnassociatedMFDs.put(i, U3FaultSystemIO.asIncr(new LightFixedXFunc(xVals, unY)));
			if (subY.length > 0)
				nodeSubSeisMFDs.put(i, U3FaultSystemIO.asIncr(new LightFixedXFunc(xVals, subY)));
		}
		Preconditions.checkState(cnt == arrays.size());
		
		return new GridSourceFileReader(region, nodeSubSeisMFDs, nodeUnassociatedMFDs);
	}
	
	/**
	 * This writes gridded seismicity MFDs to the given XML file
	 * @param file
	 * @param region
	 * @param nodeSubSeisMFDs
	 * @param nodeUnassociatedMFDs
	 * @throws IOException
	 */
	public static void writeGriddedSeisFile(File file, MFDGridSourceProvider gridProv) throws IOException {
		GridSourceFileReader fileBased;
		
		if (gridProv instanceof GridSourceFileReader) {
			fileBased = (GridSourceFileReader)gridProv;
		} else {
			GriddedRegion region = gridProv.getGriddedRegion();
			Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs = Maps.newHashMap();
			Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs = Maps.newHashMap();
			
			for (int i=0; i<region.getNumLocations(); i++) {
				nodeSubSeisMFDs.put(i, gridProv.getMFD_SubSeisOnFault(i));
				nodeUnassociatedMFDs.put(i, gridProv.getMFD_Unassociated(i));
			}
			
			fileBased = new GridSourceFileReader(region, nodeSubSeisMFDs, nodeUnassociatedMFDs);
		}
		
		fileBased.writeGriddedSeisFile(file);
	}
	
	/**
	 * Loads grid sources from the given file
	 * @param file
	 * @return
	 * @throws IOException
	 * @throws DocumentException
	 */
	public static GridSourceFileReader fromFile(File file) throws IOException, DocumentException {
		Document doc = XMLUtils.loadDocument(file);
		return fromXMLMetadata(doc.getRootElement());
	}
	
	/**
	 * Loads grid sources from the given input stream
	 * @param is
	 * @return
	 * @throws DocumentException
	 */
	public static GridSourceFileReader fromInputStream(InputStream is) throws DocumentException {
		Document doc = XMLUtils.loadDocument(is);
		return fromXMLMetadata(doc.getRootElement());
	}
	
	/**
	 * Loads grid sources from the given XML root element
	 * @param root
	 * @return
	 */
	public static GridSourceFileReader fromXMLMetadata(Element root) {
		Element regionEl = root.element(GriddedRegion.XML_METADATA_NAME);
		
		GriddedRegion region = GriddedRegion.fromXMLMetadata(regionEl);
		
		Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs = Maps.newHashMap();
		Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs = Maps.newHashMap();
		
		Element nodeListEl = root.element(NODE_MFD_LIST_EL_NAME);
		int numNodes = Integer.parseInt(nodeListEl.attributeValue("num"));
		
		if (region.getNodeCount() == 7637 && (numNodes == 7636 || region.getName().startsWith("RELM")))
			region = loadU3RegionJSON();
		
		Iterator<Element> nodeElIt = nodeListEl.elementIterator(NODE_MFD_ITEM_EL_NAME);
		
		while (nodeElIt.hasNext()) {
			Element nodeEl = nodeElIt.next();
			
			int index = Integer.parseInt(nodeEl.attributeValue("index"));
			
			nodeSubSeisMFDs.put(index, loadMFD(nodeEl.element(SUB_SIZE_MFD_EL_NAME)));
			nodeUnassociatedMFDs.put(index, loadMFD(nodeEl.element(UNASSOCIATED_MFD_EL_NAME)));
		}
		
		Preconditions.checkState(nodeSubSeisMFDs.size() == numNodes, "Num MFDs inconsistant with number listed in XML file");
		
		return new GridSourceFileReader(region, nodeSubSeisMFDs, nodeUnassociatedMFDs);
	}
	
	private static IncrementalMagFreqDist loadMFD(Element funcEl) {
		if (funcEl == null)
			return null;
		
		EvenlyDiscretizedFunc func =
				(EvenlyDiscretizedFunc)AbstractDiscretizedFunc.fromXMLMetadata(funcEl);
		
		return U3FaultSystemIO.asIncr(func);
	}
	
	public static void main(String[] args) throws IOException, DocumentException {
		File dataDir = new File("/home/kevin/OpenSHA/UCERF3/fss_csvs");
//		File solFile = new File(dataDir, "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_2_MEAN_BRANCH_AVG_SOL.zip");
		File solFile = new File(dataDir, "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_TRUE_HAZARD_MEAN_SOL.zip");
		File outputFile = new File(dataDir, solFile.getName().replaceAll(".zip", "")+"_grid_sources.xml");
		U3FaultSystemSolution fss = U3FaultSystemIO.loadSol(solFile);
		writeGriddedSeisFile(outputFile, fss.requireModule(MFDGridSourceProvider.class));
////		File fssFile = new File("/tmp/FM3_1_ZENGBB_Shaw09Mod_DsrTap_CharConst_M5Rate8.7_MMaxOff7.6_" +
////				"NoFix_SpatSeisU3_VarPaleo0.6_VarSmoothPaleoSect1000_VarSectNuclMFDWt0.01_sol.zip");
//		File fssFile = new File("/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/"
//				+ "InversionSolutions/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip");
//		InversionFaultSystemSolution ivfss = FaultSystemIO.loadInvSol(fssFile);
//		GridSourceProvider sourceProv = ivfss.getGridSourceProvider();
//		System.out.println("Saving");
////		File gridSourcesFile = new File("/tmp/grid_sources.xml");
////		writeGriddedSeisFile(gridSourcesFile, sourceProv);
////		
////		System.out.println("Loading");
////		GridSourceFileReader reader = fromFile(gridSourcesFile);
//		
//		File gridSourcesRegionFile = new File("/tmp/grid_sources_reg.xml");
//		File gridSourcesBinFile = new File("/tmp/grid_sources.bin");
//		writeGriddedSeisBinFile(gridSourcesBinFile, gridSourcesRegionFile, sourceProv, 0d);
//		
//		System.out.println("Loading");
//		GridSourceFileReader reader = fromBinFile(gridSourcesBinFile, gridSourcesRegionFile);
//		System.out.println("DONE");
//		
//		for (int i=0; i<sourceProv.getGriddedRegion().getNumLocations(); i++) {
//			IncrementalMagFreqDist nodeSubSeisMFD = sourceProv.getNodeSubSeisMFD(i);
//			IncrementalMagFreqDist nodeUnassociatedMFD = sourceProv.getNodeUnassociatedMFD(i);
//			
//			if (nodeSubSeisMFD == null || nodeSubSeisMFD.getMaxY() == 0) {
//				Preconditions.checkState(reader.getNodeSubSeisMFD(i) == null);
//			} else {
//				Preconditions.checkNotNull(reader.getNodeSubSeisMFD(i), i+". Was supposed to be size "+nodeSubSeisMFD.size()
//						+" tot "+(float)nodeSubSeisMFD.getTotalIncrRate()+", was null");
//				Preconditions.checkState((float)nodeSubSeisMFD.getTotalIncrRate() ==
//						(float)reader.getNodeSubSeisMFD(i).getTotalIncrRate());
//			}
//			if (nodeUnassociatedMFD == null || nodeUnassociatedMFD.getMaxY() == 0) {
//				Preconditions.checkState(reader.getNodeUnassociatedMFD(i) == null);
//			} else {
//				Preconditions.checkNotNull(reader.getNodeUnassociatedMFD(i));
//				Preconditions.checkState((float)nodeUnassociatedMFD.getTotalIncrRate() ==
//						(float)reader.getNodeUnassociatedMFD(i).getTotalIncrRate());
//			}
//		}
//		System.out.println("Validated");
	}
	
	@Override
	public double getFracStrikeSlip(int idx) {
		return fracStrikeSlip[idx];
	}


	@Override
	public double getFracReverse(int idx) {
		return fracReverse[idx];
	}


	@Override
	public double getFracNormal(int idx) {
		return fracNormal[idx];
	}
	
	private void initFocalMechGrids() {
		GridReader gRead;
		gRead = new GridReader("StrikeSlipWts.txt");
		fracStrikeSlip = gRead.getValues();
		gRead = new GridReader("ReverseWts.txt");
		fracReverse = gRead.getValues();
		gRead = new GridReader("NormalWts.txt");
		fracNormal = gRead.getValues();
	}

	@Override
	public MFDGridSourceProvider newInstance(Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs,
			Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs, double[] fracStrikeSlip, double[] fracNormal,
			double[] fracReverse, TectonicRegionType[] trts) {
		return new AbstractGridSourceProvider.Precomputed(getGriddedRegion(), nodeSubSeisMFDs, nodeUnassociatedMFDs,
				fracStrikeSlip, fracNormal, fracReverse, trts);
	}

}
