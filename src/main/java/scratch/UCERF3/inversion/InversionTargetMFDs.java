package scratch.UCERF3.inversion;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.SubSeismoOnFaultMFDs;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.griddedSeismicity.GridSourceProvider;
import scratch.UCERF3.utils.MFD_InversionConstraint;

public abstract class InversionTargetMFDs implements ArchivableModule, SubModule<FaultSystemRupSet> {
	
	private FaultSystemRupSet rupSet;

	public InversionTargetMFDs(FaultSystemRupSet rupSet) {
		this.rupSet = rupSet;
	}

	/**
	 * Total MFD for the entire region, including any gridded seismicity.
	 * 
	 * @return total MFD
	 */
	public abstract IncrementalMagFreqDist getTotalRegionalMFD();

	/**
	 * Total MFD for on-fault supra-seismogenic ruptures (i.e., those represented by the {@link FaultSystemRupSet}
	 * 
	 * @return total on-fault supra-seismogenic MFD
	 */
	public abstract IncrementalMagFreqDist getTotalOnFaultSupraSeisMFD();

	/**
	 * Total MFD across all on-fault sub-seismogenic ruptures. These ruptures are not represented by the
	 * {@link FaultSystemRupSet}, and are instead part of the {@link GridSourceProvider}'s sub-seismogenic on-fault
	 * gridded seismicity MFDs.
	 * 
	 * @return total sub-seismogenic on fault MFD
	 */
	public abstract IncrementalMagFreqDist getTotalOnFaultSubSeisMFD();

	/**
	 * Total MFD for gridded seismicity ruptures that are not associated with any fault (i.e., not sub-seismogenic on fault)
	 * 
	 * @return MFD for off-fault gridded seismicity ruptures
	 */
	public abstract IncrementalMagFreqDist getTrulyOffFaultMFD();

	/**
	 * This returns MFD constraints to be used in an inversion. They may be broken down into individual sub-regions
	 * (e.g., northern and southern CA for UCERF3), and should be reduced for both off-fault and sub-seismogenic ruptures.
	 * 
	 * @return MFD constraints
	 */
	public abstract List<? extends MFD_InversionConstraint> getMFD_Constraints();
	
	/**
	 * This returns target sub-seismogenic MFDs for each fault section, and it's contents are implementation-dependent
	 * 
	 * @return sub-seismogenic MFDs for each section in the {@link FaultSystemRupSet}
	 */
	public abstract SubSeismoOnFaultMFDs getOnFaultSubSeisMFDs();

	/**
	 * This returns the sum of getTotalSubSeismoOnFaultMFD() and getTrulyOffFaultMFD()
	 * 
	 * @return total gridded seismicity MFD
	 */
	public IncrementalMagFreqDist getTotalGriddedSeisMFD() {
		IncrementalMagFreqDist subSeismo = getTotalOnFaultSubSeisMFD();
		IncrementalMagFreqDist offFault = getTrulyOffFaultMFD();
		if (subSeismo == null && offFault == null)
			return null;
		if (subSeismo == null)
			return offFault;
		if (offFault == null)
			return subSeismo;
		SummedMagFreqDist totGridSeisMFD = new SummedMagFreqDist(subSeismo.getMinX(), subSeismo.getMaxX(), subSeismo.size());
		totGridSeisMFD.addIncrementalMagFreqDist(subSeismo);
		totGridSeisMFD.addIncrementalMagFreqDist(offFault);
		totGridSeisMFD.setName("InversionTargetMFDs.getTotalGriddedSeisMFD()");
		return totGridSeisMFD;
	}

	/**
	 * Total on fault MFD, including both sub- and supra-seismogenic ruptures
	 * 
	 * @return total on-fault MFD (both sub- and supra-seismogenic)
	 */
	public IncrementalMagFreqDist getTotalOnFaultMFD() {
		IncrementalMagFreqDist subSeismo = getTotalOnFaultSubSeisMFD();
		IncrementalMagFreqDist supraSeismo = getTotalOnFaultSupraSeisMFD();
		if (subSeismo == null && supraSeismo == null)
			return supraSeismo;
		if (subSeismo == null)
			return supraSeismo;
		if (supraSeismo == null)
			return subSeismo;
		SummedMagFreqDist totOnMFD = new SummedMagFreqDist(subSeismo.getMinX(), subSeismo.getMaxX(), subSeismo.size());
		totOnMFD.addIncrementalMagFreqDist(subSeismo);
		totOnMFD.addIncrementalMagFreqDist(supraSeismo);
		totOnMFD.setName("InversionTargetMFDs.getTotalOnFaultMFD()");
		return totOnMFD;
	}

	@Override
	public void setParent(FaultSystemRupSet parent) throws IllegalStateException {
		this.rupSet = parent;
	}

	@Override
	public FaultSystemRupSet getParent() {
		return rupSet;
	}
	
	public static class Precomputed extends InversionTargetMFDs implements ArchivableModule {

		private static TypeAdapter<IncrementalMagFreqDist> mfdAdapter = new IncrementalMagFreqDist.Adapter();
		private static TypeAdapter<MFD_InversionConstraint> mfdConstraintAdapter = new MFD_InversionConstraint.Adapter();
		
		private IncrementalMagFreqDist totalRegionalMFD;
		private IncrementalMagFreqDist onFaultSupraSeisMFD;
		private IncrementalMagFreqDist onFaultSubSeisMFD;
		private IncrementalMagFreqDist trulyOffFaultMFD;
		private ImmutableList<? extends MFD_InversionConstraint> mfdConstraints;
		private SubSeismoOnFaultMFDs subSeismoOnFaultMFDs;
		
		private Precomputed() {
			super(null);
		}
		
		public Precomputed(InversionTargetMFDs targetMFDs) {
			this(targetMFDs.rupSet, targetMFDs.getTotalRegionalMFD(), targetMFDs.getTotalOnFaultSupraSeisMFD(),
					targetMFDs.getTotalOnFaultSubSeisMFD(), targetMFDs.getTrulyOffFaultMFD(),
					targetMFDs.getMFD_Constraints(), targetMFDs.getOnFaultSubSeisMFDs());
		}

		public Precomputed(FaultSystemRupSet rupSet, IncrementalMagFreqDist totalRegionalMFD,
				IncrementalMagFreqDist onFaultSupraSeisMFD, IncrementalMagFreqDist onFaultSubSeisMFD,
				IncrementalMagFreqDist trulyOffFaultMFD, List<? extends MFD_InversionConstraint> mfdConstraints,
				SubSeismoOnFaultMFDs subSeismoOnFaultMFDs) {
			super(rupSet);
			this.totalRegionalMFD = totalRegionalMFD;
			this.onFaultSupraSeisMFD = onFaultSupraSeisMFD;
			this.onFaultSubSeisMFD = onFaultSubSeisMFD;
			this.trulyOffFaultMFD = trulyOffFaultMFD;
			this.subSeismoOnFaultMFDs = subSeismoOnFaultMFDs;
			this.mfdConstraints = mfdConstraints == null ? null : ImmutableList.copyOf(mfdConstraints);
		}

		@Override
		public String getName() {
			return "Inversion Target MFDs";
		}

		@Override
		public SubModule<FaultSystemRupSet> copy(FaultSystemRupSet newParent) throws IllegalStateException {
			FaultSystemRupSet rupSet = getParent();
			Preconditions.checkState(rupSet == null || rupSet.isEquivalentTo(newParent));
			return new Precomputed(newParent, totalRegionalMFD, onFaultSupraSeisMFD, onFaultSubSeisMFD,
					trulyOffFaultMFD, mfdConstraints, subSeismoOnFaultMFDs);
		}

		private void writeToJSON(JsonWriter out) throws IOException {
			out.beginObject();
			
			out.name("totalRegionalMFD");
			mfdAdapter.write(out, totalRegionalMFD);
			
			out.name("onFaultSupraSeisMFD");
			mfdAdapter.write(out, onFaultSupraSeisMFD);
			
			out.name("onFaultSubSeisMFD");
			mfdAdapter.write(out, onFaultSubSeisMFD);
			
			out.name("trulyOffFaultMFD");
			mfdAdapter.write(out, trulyOffFaultMFD);
			
			out.name("mfdConstraints");
			if (mfdConstraints == null) {
				out.nullValue();
			} else {
				out.beginArray();
				for (MFD_InversionConstraint constraint : mfdConstraints)
					mfdConstraintAdapter.write(out, constraint);
				out.endArray();
			}
			
			out.endObject();
		}

		private void initFromJSON(JsonReader in) throws IOException {
			in.beginObject();
			
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "totalRegionalMFD":
					totalRegionalMFD = mfdAdapter.read(in);
					break;
				case "onFaultSupraSeisMFD":
					onFaultSupraSeisMFD = mfdAdapter.read(in);
					break;
				case "onFaultSubSeisMFD":
					onFaultSubSeisMFD = mfdAdapter.read(in);
					break;
				case "trulyOffFaultMFD":
					trulyOffFaultMFD = mfdAdapter.read(in);
					break;
				case "mfdConstraints":
					if (in.peek() == JsonToken.NULL) {
						in.nextNull();
						mfdConstraints = null;
					} else {
						ImmutableList.Builder<MFD_InversionConstraint> builder = ImmutableList.builder();
						in.beginArray();
						while (in.hasNext())
							builder.add(mfdConstraintAdapter.read(in));
						in.endArray();
						mfdConstraints = builder.build();
					}
					break;

				default:
					in.skipValue();
					break;
				}
			}
			
			in.endObject();
		}

		@Override
		public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
			FileBackedModule.initEntry(zout, entryPrefix, "inversion_target_mfds.json");
			BufferedOutputStream out = new BufferedOutputStream(zout);
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			OutputStreamWriter writer = new OutputStreamWriter(out);
			writeToJSON(gson.newJsonWriter(writer));
			writer.flush();
			out.flush();
			zout.closeEntry();
			
			if (subSeismoOnFaultMFDs != null)
				subSeismoOnFaultMFDs.writeToArchive(zout, entryPrefix);
		}

		@Override
		public void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
			BufferedInputStream in = FileBackedModule.getInputStream(zip, entryPrefix, "inversion_target_mfds.json");
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			InputStreamReader reader = new InputStreamReader(in);
			initFromJSON(gson.newJsonReader(reader));
			
			String subSeisName = ArchivableModule.getEntryName(entryPrefix, SubSeismoOnFaultMFDs.DATA_FILE_NAME);
			ZipEntry subSeismoEntry = zip.getEntry(subSeisName);
			if (subSeismoEntry != null) {
				CSVFile<String> csv = CSVFile.readStream(zip.getInputStream(subSeismoEntry), false);
				subSeismoOnFaultMFDs = SubSeismoOnFaultMFDs.fromCSV(csv);
			}
		}

		@Override
		public final IncrementalMagFreqDist getTotalRegionalMFD() {
			return totalRegionalMFD;
		}

		@Override
		public final IncrementalMagFreqDist getTotalOnFaultSupraSeisMFD() {
			return onFaultSupraSeisMFD;
		}

		@Override
		public final IncrementalMagFreqDist getTrulyOffFaultMFD() {
			return trulyOffFaultMFD;
		}

		@Override
		public final IncrementalMagFreqDist getTotalOnFaultSubSeisMFD() {
			return onFaultSubSeisMFD;
		}

		@Override
		public final List<? extends MFD_InversionConstraint> getMFD_Constraints() {
			return mfdConstraints;
		}
		
		@Override
		public final SubSeismoOnFaultMFDs getOnFaultSubSeisMFDs() {
			return subSeismoOnFaultMFDs;
		}
		
	}

}