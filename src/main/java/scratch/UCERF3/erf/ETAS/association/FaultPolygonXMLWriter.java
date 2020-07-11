package scratch.UCERF3.erf.ETAS.association;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.Element;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.XMLUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.UCERF3_DataUtils;

public class FaultPolygonXMLWriter {

	public static void main(String[] args) throws IOException {
		double[] buffers = { 1, 2, 4, 6, 8, 10, 12 };
		File outputDir = new File("/tmp");
		FaultModels[] fms = { FaultModels.FM3_1, FaultModels.FM3_2 };
		DeformationModels dm = DeformationModels.GEOLOGIC;
		
		for (FaultModels fm : fms) {
			List<? extends FaultSection> sects = new DeformationModelFetcher(
					fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, 0.1).getSubSectionList();
			
			for (double buffer : buffers) {
				FaultPolyMgr polyMgr = FaultPolyMgr.create(sects, buffer);
				
				Document doc = XMLUtils.createDocumentWithRoot();
				Element root = doc.getRootElement();
				
				for (FaultSection sect : sects) {
					Region poly = polyMgr.getPoly(sect.getSectionId());
					Preconditions.checkNotNull(poly, "Null poly for sect %s", sect);
					poly.setName(sect.getName());
					poly.toXMLMetadata(root);
				}
				
				XMLUtils.writeDocumentToFile(new File(outputDir, fm.encodeChoiceString()+"_sub_sects_"+(int)buffer+"km_buffer.xml"), doc);
			}
			
			// now do geologic polygons
			sects = fm.fetchFaultSections();
			Document doc = XMLUtils.createDocumentWithRoot();
			Element root = doc.getRootElement();
			
			for (FaultSection sect : sects) {
				Region poly = sect.getZonePolygon();
				if (poly == null) {
					System.out.println("WARNING: no polygon for section "+sect.getName());
					continue;
				}
				poly.setName(sect.getName());
				poly.toXMLMetadata(root);
			}
			
			XMLUtils.writeDocumentToFile(new File(outputDir, fm.encodeChoiceString()+"_sects_geologic.xml"), doc);
		}
	}

}
