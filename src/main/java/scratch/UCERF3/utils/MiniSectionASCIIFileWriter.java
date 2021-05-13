package scratch.UCERF3.utils;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.opensha.commons.geo.Location;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;
import scratch.UCERF3.utils.DeformationModelFileParser.DeformationSection;

public class MiniSectionASCIIFileWriter {

	public static void main(String[] args) throws IOException {
		// this writes out minisections for a given DM/FM in our standard ASCII fault section
		// format. Aseismicity/coupling coefficients are set just as in DeformationModelFetcher
		// for subsections, with the exception of custom tapers and custom Parkfield/Creeping/Mendocino.

//		DeformationModels dm = DeformationModels.ZENGBB;
//		FaultModels fm = FaultModels.FM3_1;
		
		File outputDir = new File("/tmp/minisect_dm_files");
		
		for (FaultModels fm : FaultModels.values()) {
			if (fm.getRelativeWeight(null) == 0)
				continue;
			
			for (DeformationModels dm : DeformationModels.values()) {
				if (dm.getRelativeWeight(null) == 0)
					continue;
				
				writeMiniSectASCIIFiles(fm, dm, outputDir);
			}
		}
	}
	
	public static void writeMiniSectASCIIFiles(FaultModels fm, DeformationModels dm, File outputDir) throws IOException {
		if (!outputDir.exists())
			outputDir.mkdir();
		
		String prefix = fm.encodeChoiceString()+"_"+dm.encodeChoiceString();
		File outputFile = new File(outputDir, prefix+"_minis.txt");
		File reducedOutputFile = new File(outputDir, prefix+"_minis_reduced.txt");
		File avgOutputFile = new File(outputDir, prefix+"_avg.txt");
		
		// load FM
		List<FaultSection> fmSects = fm.fetchFaultSections();
		// load DM
		Map<Integer, DeformationSection> dmSects = DeformationModelFileParser.load(dm.getDataFileURL(fm));
		// compute moment reductions for DMs
		DeformationModelFileParser.applyMomentReductions(
				dmSects, DeformationModelFetcher.MOMENT_REDUCTION_MAX);
		
		List<FaultSection> miniSects = Lists.newArrayList();
		
		List<FaultSection> avgSects = Lists.newArrayList();
		
		int index = 0;
		
		double defaultAseismicityValue = InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE;
		
		for (FaultSection parentSect : fmSects) {
			Integer parentID = parentSect.getSectionId();
			DeformationSection dmSect = dmSects.get(parentID);
			Preconditions.checkNotNull(dmSect);
			
			// split into minisections
			Preconditions.checkState(dmSect.getLocsAsTrace().size() == parentSect.getFaultTrace().size());
			
			double avgSlip = DeformationModelFetcher.getLengthBasedAverage(
					parentSect.getFaultTrace(), dmSect.getSlips());
			double avgRake = FaultUtils.getLengthBasedAngleAverage(
					parentSect.getFaultTrace(), dmSect.getRakes());
			
			FaultSection avgSect = parentSect.clone();
			avgSect.setAveSlipRate(avgSlip);
			avgSect.setAveRake(avgRake);
			avgSects.add(avgSect);
			
			for (int i=0; i<dmSect.getLocs1().size(); i++) {
				Location loc1 = dmSect.getLocs1().get(i);
				Location loc2 = dmSect.getLocs2().get(i);
				double rake = dmSect.getRakes().get(i);
				double slip = dmSect.getSlips().get(i);
				
				String name = parentSect.getName()+", Minisection "+i;
				
				FaultSection newSect = parentSect.clone();
				newSect.setParentSectionId(parentID);
				newSect.setParentSectionName(parentSect.getName());
				newSect.setSectionId(index++);
				newSect.setAveSlipRate(slip);
				newSect.setAveRake(rake);
				newSect.setSectionName(name);
				
				// deal with creep
				if (dmSect.getMomentReductions() != null) {
					// DM has moment reductions
					double aseismicityFactor, couplingCoeff;
					double momentReductionFactor = dmSect.getMomentReductions().get(i);
					// we apply moment reductions as aseismic recutions up to the MOMENT_REDUCTION_THRESHOLD
					if (momentReductionFactor<=DeformationModelFetcher.MOMENT_REDUCTION_THRESHOLD) {
						// just aseismicity
						aseismicityFactor = momentReductionFactor;
						couplingCoeff = 1.0;
					} else {
						// above the threshold, split between aseis and coupling coeff
						aseismicityFactor = DeformationModelFetcher.MOMENT_REDUCTION_THRESHOLD;
						double slipRateReduction = (momentReductionFactor
								-DeformationModelFetcher.MOMENT_REDUCTION_THRESHOLD)/(1-aseismicityFactor);
						couplingCoeff = 1.0 - slipRateReduction;
					}
					newSect.setAseismicSlipFactor(aseismicityFactor);
					newSect.setCouplingCoeff(couplingCoeff);
				} else {
					// if we had an aseismicity value in UCERF2 (non zero), then keep that as recommended by Tim Dawson
					// via e-mail 3/2/12 (subject: Moment Rate Reductions). Otherwise, set it to the default value.
					if (newSect.getAseismicSlipFactor() == 0)
						newSect.setAseismicSlipFactor(defaultAseismicityValue);
				}
				
				FaultTrace miniTrace = new FaultTrace(name);
				miniTrace.add(loc1);
				miniTrace.add(loc2);
				((FaultSectionPrefData)newSect).setFaultTrace(miniTrace);
				
				miniSects.add(newSect);
			}
		}
		
		DateFormat df = new SimpleDateFormat("yyyy_MM_dd");
		String dateStr = df.format(new Date());
		
		List<String> metaData = Lists.newArrayList();
		metaData.add("Minisections for:");
		metaData.add("FM: "+fm);
		metaData.add("DM: "+dm);
		metaData.add("Generated on: "+dateStr);
		metaData.add("Generated by: "+ClassUtils.getClassNameWithoutPackage(MiniSectionASCIIFileWriter.class));
		metaData.add("");
		metaData.add("This file contains fault mini-sections with slip rates and rakes from the given");
		metaData.add("deformation model. Aseismicity factors/coupling coefficients are set from the UCERF3");
		metaData.add("creep table, EXCEPT they lack any UCERF3 special cases that were applied on the");
		metaData.add("sub section level:");
		metaData.add("\tCustom aseis/coupling on Parkfield/Creeping section");
		metaData.add("\tCustom taper for overlapping faults: Cerro Prieto/Imperial and Maacama/Rodgers Creek.");
		metaData.add("\tCustom coupling coef. of 0.15 on Mendocino west of the triple junction");
		FaultSectionDataWriter.writeSectionsToFile(miniSects, metaData, outputFile, false);
		metaData.add("");
		metaData.add("This file also contains reduced slip rates and upper seismogenic depths.");
		FaultSectionDataWriter.writeSectionsToFile(miniSects, metaData, reducedOutputFile, true);
		
		metaData = Lists.newArrayList();
		metaData.add("Averaged sections for:");
		metaData.add("FM: "+fm);
		metaData.add("DM: "+dm);
		metaData.add("Generated on: "+dateStr);
		metaData.add("Generated by: "+ClassUtils.getClassNameWithoutPackage(MiniSectionASCIIFileWriter.class));
		metaData.add("");
		metaData.add("This file contains fault sections with slip rates and rakes averaged among every");
		metaData.add("minisection from the deformation model. Averages are weighted by minisection");
		metaData.add("length. DISREGARD aseismicity values and coupling coefficients as they do not");
		metaData.add("apply to these files.");
		FaultSectionDataWriter.writeSectionsToFile(avgSects, metaData, avgOutputFile, false);
	}

}
