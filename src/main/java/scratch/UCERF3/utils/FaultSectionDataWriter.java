/**
 * 
 */
package scratch.UCERF3.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.refFaultParamDb.gui.infotools.GUI_Utils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;

import com.google.common.collect.Lists;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;

/**
 * @author Ned Field
 *
 */
public class FaultSectionDataWriter {
	
	private static final boolean INCLUDE_CC = true;

	/**
	 * 
	 * @param subSectionPrefDataList
	 * @param metaData - each String in this list will have a "# " and "\n" added to the beginning and ending, respectively
	 * @param filePathAndName
	 * @throws IOException 
	 */
	public final static void writeSectionsToFile(List<? extends FaultSection> subSectionPrefDataList, 
			List<String> metaData, String filePathAndName) throws IOException {
		writeSectionsToFile(subSectionPrefDataList, metaData, new File(filePathAndName), false);
	}
	
	/**
	 * 
	 * @param subSectionPrefDataList
	 * @param metaData - each String in this list will have a "# " and "\n" added to the beginning and ending, respectively
	 * @param file
	 */
	public final static void writeSectionsToFile(List<? extends FaultSection> subSectionPrefDataList, 
			List<String> metaData, File file, boolean applyReductions) throws IOException {
		FileWriter fw = new FileWriter(file);
		fw.write(getSectionsASCII(subSectionPrefDataList, metaData, applyReductions).toString());
		fw.close();
	}

	/**
	 * 
	 * @param subSectionPrefDataList
	 * @param metaData - each String in this list will have a "# " and "\n" added to the beginning and ending, respectively
	 * @param filePathAndName
	 */
	public final static StringBuffer getSectionsASCII(List<? extends FaultSection> subSectionPrefDataList, 
			List<String> metaData, boolean applyReductions) {
		StringBuffer buff = new StringBuffer();
		if (metaData != null && !metaData.isEmpty()) {
			String header1 = "# ******** MetaData **************\n";
			for(String metaDataLine: metaData)
				header1 += "# "+metaDataLine+"\n";
			buff.append(header1);
		}
		String reducedStr = "";
		if (applyReductions)
			reducedStr = "Reduced ";
		String header2 = "# ******** Data Format ***********\n"+ 
				"# Section Index\n"+
				"# Section Name\n"+
				"# Parent Section ID\n"+
				"# Parent Section Name\n"+
				"# Ave "+reducedStr+"Upper Seis Depth (km)\n"+
				"# Ave Lower Seis Depth (km)\n"+
				"# Ave Dip (degrees)\n"+
				"# Ave Dip Direction\n"+
				"# Ave "+reducedStr+"Long Term Slip Rate\n"+
				"# Ave Aseismic Slip Factor\n";
		if (INCLUDE_CC)
			header2 += "# Coupling Coefficient\n";
		header2 += 
				"# Ave Rake\n"+
				"# Trace Length (derivative value) (km)\n"+
				"# Num Trace Points\n"+
				"# lat1 lon1\n"+
				"# lat2 lon2\n"+
				"# etc for all trace points\n"+
				"# ********************************\n";
		buff.append(header2);
		for(int i=0; i<subSectionPrefDataList.size(); i++) {
			FaultSection sectData = subSectionPrefDataList.get(i);
			FaultTrace faultTrace = sectData.getFaultTrace(); 
			String str =  sectData.getSectionId()+"\n"+sectData.getSectionName()+"\n"+
					getValue(sectData.getParentSectionId())+"\n"+
					getValue(sectData.getParentSectionName())+"\n";
			if (applyReductions)
				str += getValue(sectData.getReducedAveUpperDepth())+"\n";
			else
				str += getValue(sectData.getOrigAveUpperDepth())+"\n";
			str += 	getValue(sectData.getAveLowerDepth())+"\n"+
					getValue(sectData.getAveDip()) +"\n"+
					getValue(sectData.getDipDirection())+"\n";
			if (applyReductions)
				str += getValue(sectData.getReducedAveSlipRate())+"\n";
			else
				str += getValue(sectData.getOrigAveSlipRate())+"\n";
			str += 	getValue(sectData.getAseismicSlipFactor())+"\n";
			if (INCLUDE_CC)
				str += getValue(sectData.getCouplingCoeff())+"\n";
			str += 	getValue(sectData.getAveRake())+"\n"+
					getValue(faultTrace.getTraceLength())+"\n"+
					faultTrace.getNumLocations()+"\n";
			// write all the point on the fault section trace
			for(int j=0; j<faultTrace.getNumLocations(); ++j)
				str+=(float)faultTrace.get(j).getLatitude()+"\t"+(float)faultTrace.get(j).getLongitude()+"\n";
			buff.append(str);
		}
		return buff;
	}

	private final static  String getValue(double val) {
		if(Double.isNaN(val)) return "Not Available";
		else return GUI_Utils.decimalFormat.format(val);
	}

	private final static  String getValue(int val) {
		if(val == -1) return "Not Available";
		else return Integer.toString(val);
	}


	private final static String getValue(String val) {
		if(val==null || val.equalsIgnoreCase("")) return "Not Available";
		else return val;
	}

	/**
	 * This writes the rupture sections to an ASCII file
	 * @param filePathAndName
	 * @throws IOException 
	 */
	public static void writeRupsToFiles(String filePathAndName, FaultSystemRupSet rupSet) throws IOException {
		FileWriter fw = new FileWriter(filePathAndName);
		fw.write(getRupsASCII(rupSet).toString());
		fw.close();
	}

	/**
	 * This writes the rupture sections to an ASCII file
	 * @param filePathAndName
	 */
	public static StringBuffer getRupsASCII(FaultSystemRupSet rupSet) {
		return getRupsASCII(rupSet, null);
	}

	/**
	 * This writes the rupture sections to an ASCII file
	 * @param filePathAndName
	 */
	public static StringBuffer getRupsASCII(FaultSystemRupSet rupSet, FaultSystemSolution sol) {
		StringBuffer buff = new StringBuffer();
		buff.append("rupID\tclusterID\trupInClustID\tmag\t");
		if (sol != null)
			buff.append("rate\t");
		buff.append("numSectIDs\tsect1_ID\tsect2_ID\t...\n");	// header
		
		List<List<Integer>> rupsForClusters = Lists.newArrayList();
		
		if (rupSet instanceof InversionFaultSystemRupSet) {
			InversionFaultSystemRupSet invRupSet = (InversionFaultSystemRupSet)rupSet;
			// cluster based
			for(int c=0;c<invRupSet.getNumClusters();c++)
				rupsForClusters.add(invRupSet.getRupturesForCluster(c));
		} else {
			// treat it as one cluster
			List<Integer> rups = Lists.newArrayList();
			for (int r=0; r<rupSet.getNumRuptures(); r++)
				rups.add(r);
			rupsForClusters.add(rups);
		}
		
		for(int c=0;c<rupsForClusters.size();c++) {
			List<Integer> rups = rupsForClusters.get(c);
			//				  ArrayList<ArrayList<Integer>>  rups = rupSet.getCluster(c).getSectionIndicesForRuptures();
			for(int i=0; i<rups.size(); i++) {
				//					  ArrayList<Integer> rup = rups.get(r);
				int rupIndex = rups.get(i);
				List<Integer> sections = rupSet.getSectionsIndicesForRup(rupIndex);
				String line = Integer.toString(rupIndex)+"\t"+Integer.toString(c)+"\t"+Integer.toString(rupIndex)+"\t"+
						+(float)rupSet.getMagForRup(rupIndex);
				if (sol != null)
					line += "\t"+sol.getRateForRup(rupIndex);
				line += "\t"+sections.size();
				for(Integer sectID: sections) {
					line += "\t"+sectID;
				}
				line += "\n";
				buff.append(line);
				rupIndex+=1;
			}				  
		}
		return buff;
	}

}
