package scratch.UCERF3.utils;
import org.dom4j.Element;
import java.io.IOException;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.Region;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import com.google.common.base.Preconditions;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * This class extends MFD_InversionConstraint (having an MFD and a Region) with an EvenlyDiscretized function 
 * containing weights.
 * 
 * @author chrisbc
 *
 */
@JsonAdapter(MFD_InversionConstraint.Adapter.class)
public class MFD_WeightedInversionConstraint extends MFD_InversionConstraint {
	
	public static final String XML_METADATA_NAME = "MFD_WeightedInversionConstraint";
	
	EvenlyDiscretizedFunc weights;
	
	public MFD_WeightedInversionConstraint(IncrementalMagFreqDist mfd, Region region, EvenlyDiscretizedFunc weights) {
		super(mfd, region);
		this.weights=weights;
		validateDiscretization();
	}
	
	private void validateDiscretization() {
		Preconditions.checkState(mfd.getMinX() == weights.getMinX(), "minX of mfd and weight objects must be equal", mfd.getMinX(), weights.getMinX());
		Preconditions.checkState(mfd.getMaxX() == weights.getMaxX(), "maxX of mfd and weight objects must be equal", mfd.getMaxX(), weights.getMaxX());
		Preconditions.checkState(mfd.size() == weights.size(), "size of mfd and weight objects must be equal", mfd.size(), weights.size());
	}
	
	public void setWeights(EvenlyDiscretizedFunc weights) {
		this.weights=weights;
		validateDiscretization();
	}
	
	
	public EvenlyDiscretizedFunc getWeights() {
		return weights;
	}
	
	@Deprecated
	@Override
	public Element toXMLMetadata(Element root) {
		throw new UnsupportedOperationException("No more XML, sorry");
	}

}
