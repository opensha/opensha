package scratch.UCERF3.utils;

import java.util.List;

import org.dom4j.Element;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.RegionUtils;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.SimpleFaultData;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

/**
 * This class contains an MFD and Region, used as a constraint in the Grand Inversion
 * @author field
 *
 */
public class MFD_InversionConstraint implements XMLSaveable {
	
	public static final String XML_METADATA_NAME = "MFD_InversionConstraint";
	
	IncrementalMagFreqDist mfd;
	Region region;
	
	
	public MFD_InversionConstraint(IncrementalMagFreqDist mfd, Region region) {
		this.mfd=mfd;
		this.region=region;
	}
	
	
	public void setMagFreqDist(IncrementalMagFreqDist mfd) {
		this.mfd=mfd;
	}
	
	
	public IncrementalMagFreqDist getMagFreqDist() {
		return mfd;
	}
	
	
	public void setRegion(Region region) {
		this.region=region;
	}
	
	
	public Region getRegion() {
		return region;
	}
	
	
	/**
	 * This returns the fraction of points inside the region from all the FaultSectionPrefData
	 * objects converted to a StirlingGriddedSurface with 1-km discretization.  Note that 
	 * aseismicity reduces area here.
	 * 
	 * @param faultSectPrefDataList
	 * @return
	 */
	public double getFractionInRegion(List<FaultSectionPrefData> faultSectPrefDataList) {
		if (region == null)
			return 1d;
		double numInside=0, totNum=0;
		double gridSpacing=1;  // in km
		for(FaultSectionPrefData data: faultSectPrefDataList) {
			StirlingGriddedSurface surf = data.getStirlingGriddedSurface(gridSpacing, false, true);
			double numPts = (double) surf.size();
			totNum += numPts;
			numInside += numPts*RegionUtils.getFractionInside(region, surf.getEvenlyDiscritizedListOfLocsOnSurface());
			data.getSimpleFaultData(true);
		}
		return numInside/totNum;
	}


	@Override
	public Element toXMLMetadata(Element root) {
		Element el = root.addElement(XML_METADATA_NAME);
		
		// must call this way to make sure we get the regular region, not a gridded
		if (region != null)
			region.toXMLMetadata(el, Region.XML_METADATA_NAME);
		mfd.toXMLMetadata(el);
		
		return root;
	}
	
	public static MFD_InversionConstraint fromXMLMetadata(Element constrEl) {
		Element regionEl = constrEl.element(Region.XML_METADATA_NAME);
		Region region = regionEl == null ? null : Region.fromXMLMetadata(regionEl);
		
		Element mfdEl = constrEl.element(IncrementalMagFreqDist.XML_METADATA_NAME);
		EvenlyDiscretizedFunc func = (EvenlyDiscretizedFunc) EvenlyDiscretizedFunc.fromXMLMetadata(mfdEl);
		IncrementalMagFreqDist mfd = new IncrementalMagFreqDist(func.getMinX(), func.size(), func.getDelta());
		for (int i=0; i<func.size(); i++) {
			mfd.set(i, func.getY(i));
		}
		
		return new MFD_InversionConstraint(mfd, region);
	}

}
