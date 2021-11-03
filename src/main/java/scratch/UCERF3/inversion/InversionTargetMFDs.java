package scratch.UCERF3.inversion;

import java.io.IOException;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.SubSeismoOnFaultMFDs;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

/**
 * Old inversion target MFDs class. Has since been moved to org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs
 * but is temporarily partially kept to ensure that previous solutions deserialize.
 * 
 * TODO: delete
 * 
 * @author kevin
 *
 */
@Deprecated
abstract class InversionTargetMFDs {
	
	private InversionTargetMFDs() {}

	/**
	 * This exists for compatibility with previously serialized modules, will be deleted.
	 * 
	 * @author kevin
	 *
	 */
	@Deprecated
	public static class Precomputed extends org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs.Precomputed {
		
		private org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs.Precomputed precomputed;
		
		private Precomputed() {
			super();
		}
		
		public Precomputed(org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs targetMFDs) {
			super(targetMFDs);
		}

		@Override
		public Class<? extends ArchivableModule> getLoadingClass() {
			return org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs.Precomputed.class;
		}
		
	}

}