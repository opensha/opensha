package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.uncertainty.UncertainBoundedDiscretizedFunc;
import org.opensha.commons.data.uncertainty.UncertainBoundedIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
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

import scratch.UCERF3.utils.MFD_InversionConstraint;

/**
 * Module for magnitude frequency distribution used to constrain an inversion, and to facilitate building
 * a gridded seismicity module.
 * 
 * TODO averaging support
 * 
 * @author kevin
 *
 */
public abstract class InversionTargetMFDs implements ArchivableModule, SubModule<FaultSystemRupSet>,
BranchAverageableModule<InversionTargetMFDs> {
	
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
	 * Total MFD for on-fault supra-seismogenic ruptures (i.e., those represented by the {@link FaultSystemRupSet})
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
	public abstract List<? extends IncrementalMagFreqDist> getMFD_Constraints();
	
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
		private ImmutableList<? extends IncrementalMagFreqDist> mfdConstraints;
		private SubSeismoOnFaultMFDs subSeismoOnFaultMFDs;
		
		protected Precomputed() {
			super(null);
		}
		
		public Precomputed(InversionTargetMFDs targetMFDs) {
			this(targetMFDs.rupSet, targetMFDs.getTotalRegionalMFD(), targetMFDs.getTotalOnFaultSupraSeisMFD(),
					targetMFDs.getTotalOnFaultSubSeisMFD(), targetMFDs.getTrulyOffFaultMFD(),
					targetMFDs.getMFD_Constraints(), targetMFDs.getOnFaultSubSeisMFDs());
		}

		public Precomputed(FaultSystemRupSet rupSet, IncrementalMagFreqDist totalRegionalMFD,
				IncrementalMagFreqDist onFaultSupraSeisMFD, IncrementalMagFreqDist onFaultSubSeisMFD,
				IncrementalMagFreqDist trulyOffFaultMFD, List<? extends IncrementalMagFreqDist> mfdConstraints,
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
			
//			out.name("mfdConstraints"); // old name
			out.name("constraints"); // old name
			if (mfdConstraints == null) {
				out.nullValue();
			} else {
				out.beginArray();
				for (IncrementalMagFreqDist constraint : mfdConstraints)
					mfdAdapter.write(out, constraint);
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
					// old version
					if (in.peek() == JsonToken.NULL) {
						in.nextNull();
						mfdConstraints = null;
					} else {
						ImmutableList.Builder<IncrementalMagFreqDist> builder = ImmutableList.builder();
						in.beginArray();
						while (in.hasNext()) {
							MFD_InversionConstraint oldConstr = mfdConstraintAdapter.read(in);
							IncrementalMagFreqDist mfd = oldConstr.getMagFreqDist();
							mfd.setRegion(oldConstr.getRegion());
							builder.add(mfd);
						}
						in.endArray();
						mfdConstraints = builder.build();
					}
					break;
				case "constraints":
					if (in.peek() == JsonToken.NULL) {
						in.nextNull();
						mfdConstraints = null;
					} else {
						ImmutableList.Builder<IncrementalMagFreqDist> builder = ImmutableList.builder();
						in.beginArray();
						while (in.hasNext())
							builder.add(mfdAdapter.read(in));
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
		public final List<? extends IncrementalMagFreqDist> getMFD_Constraints() {
			return mfdConstraints;
		}
		
		@Override
		public final SubSeismoOnFaultMFDs getOnFaultSubSeisMFDs() {
			return subSeismoOnFaultMFDs;
		}

		@Override
		public AveragingAccumulator<InversionTargetMFDs> averagingAccumulator() {
			// TODO Auto-generated method stub
			return new AveragingAccumulator<InversionTargetMFDs>() {
				
				private IncrementalMagFreqDist totalRegionalMFD;
				private IncrementalMagFreqDist onFaultSupraSeisMFD;
				private IncrementalMagFreqDist onFaultSubSeisMFD;
				private IncrementalMagFreqDist trulyOffFaultMFD;
				private List<IncrementalMagFreqDist> mfdConstraints;
				private AveragingAccumulator<SubSeismoOnFaultMFDs> subSeismoOnFaultAccumulator;
				
				private boolean first = true;
				private double totWeight = 0d;

				@Override
				public Class<InversionTargetMFDs> getType() {
					return InversionTargetMFDs.class;
				}

				@Override
				public void process(InversionTargetMFDs module, double relWeight) {
					IncrementalMagFreqDist myTotReg = module.getTotalRegionalMFD();
					IncrementalMagFreqDist myOnFaultSupra = module.getTotalOnFaultSupraSeisMFD();
					IncrementalMagFreqDist myOnFaultSub = module.getTotalOnFaultSubSeisMFD();
					IncrementalMagFreqDist myTrulyOff = module.getTrulyOffFaultMFD();
					List<? extends IncrementalMagFreqDist> myConstraints = module.getMFD_Constraints();
					SubSeismoOnFaultMFDs mySubSeismoMFDs = module.getOnFaultSubSeisMFDs();
					if (first) {
						if (myTotReg != null)
							totalRegionalMFD = buildSameSize(myTotReg);
						if (myOnFaultSupra != null)
							onFaultSupraSeisMFD = buildSameSize(myOnFaultSupra);
						if (myOnFaultSub != null)
							onFaultSubSeisMFD = buildSameSize(myOnFaultSub);
						if (myTrulyOff != null)
							trulyOffFaultMFD = buildSameSize(myTrulyOff);
						if (myConstraints != null) {
							mfdConstraints = new ArrayList<>();
							for (IncrementalMagFreqDist constraint : myConstraints)
								mfdConstraints.add(buildSameSize(constraint));
						}
						if (mySubSeismoMFDs != null)
							subSeismoOnFaultAccumulator = mySubSeismoMFDs.averagingAccumulator();
						first = false;
					}
					
					averageInWeighted(totalRegionalMFD, myTotReg, "total regional", relWeight);
					averageInWeighted(onFaultSupraSeisMFD, myOnFaultSupra, "on fault supra", relWeight);
					averageInWeighted(onFaultSubSeisMFD, myOnFaultSub, "on fault sub", relWeight);
					averageInWeighted(trulyOffFaultMFD, myTrulyOff, "truly off", relWeight);
					
					if (myConstraints != null || mfdConstraints != null) {
						Preconditions.checkNotNull(myConstraints, "Some branches have MFD constraints and others don't");
						Preconditions.checkNotNull(mfdConstraints, "Some branches have MFD constraints and others don't");
						Preconditions.checkState(mfdConstraints.size() == myConstraints.size(),
								"MFD constraint count varies by branch");
						for (int i=0; i<myConstraints.size(); i++)
							averageInWeighted(mfdConstraints.get(i), myConstraints.get(i), "MFD constraint "+i, relWeight);
					}
					if (mySubSeismoMFDs != null || subSeismoOnFaultAccumulator != null) {
						Preconditions.checkNotNull(mySubSeismoMFDs,
								"Some branches have sub seismo MFDs and others don't");
						Preconditions.checkNotNull(subSeismoOnFaultAccumulator,
								"Some branches have sub seismo MFDs and others don't");
						subSeismoOnFaultAccumulator.process(mySubSeismoMFDs, relWeight);
					}
					
					totWeight += relWeight;
				}

				@Override
				public InversionTargetMFDs getAverage() {
					scaleToTotWeight(totalRegionalMFD, totWeight);
					scaleToTotWeight(onFaultSupraSeisMFD, totWeight);
					scaleToTotWeight(onFaultSubSeisMFD, totWeight);
					scaleToTotWeight(trulyOffFaultMFD, totWeight);
					if (mfdConstraints != null)
						for (IncrementalMagFreqDist constr : mfdConstraints)
							scaleToTotWeight(constr, totWeight);
					SubSeismoOnFaultMFDs subSeismoMFDs = null;
					if (subSeismoOnFaultAccumulator != null)
						subSeismoMFDs = subSeismoOnFaultAccumulator.getAverage();
					return new Precomputed(null, totalRegionalMFD, onFaultSupraSeisMFD, onFaultSubSeisMFD,
							trulyOffFaultMFD, mfdConstraints, subSeismoMFDs);
				}
			};
		}
		
		private static IncrementalMagFreqDist buildSameSize(IncrementalMagFreqDist ref) {
			IncrementalMagFreqDist ret = new IncrementalMagFreqDist(ref.getMinX(), ref.size(), ref.getDelta());
			ret.setName(ref.getName());
			ret.setRegion(ref.getRegion());
			if (ref instanceof UncertainBoundedIncrMagFreqDist) {
				UncertainBoundedIncrMagFreqDist bounded = (UncertainBoundedIncrMagFreqDist)ref;
				ret = new UncertainBoundedIncrMagFreqDist(ret, buildSameSize(bounded.getLower()),
						buildSameSize(bounded.getUpper()), bounded.getBoundType(),
						new EvenlyDiscretizedFunc(ref.getMinX(), ref.size(), ref.getDelta()));
			} else if (ref instanceof UncertainIncrMagFreqDist) {
				ret = new UncertainIncrMagFreqDist(ret,
						new EvenlyDiscretizedFunc(ref.getMinX(), ref.size(), ref.getDelta()));
			}
			return ret;
		}
		
		private static void averageInWeighted(IncrementalMagFreqDist dest, IncrementalMagFreqDist mfd, String type,
				double weight) {
			if (dest == null || mfd == null) {
				// make sure both are null
				Preconditions.checkState(dest == null, "InversionTargetMFDs: some branches have %s, others don't", type);
				Preconditions.checkState(mfd == null, "InversionTargetMFDs: some branches have %s, others don't", type);
				return;
			}
			// make sure gridding is the same
			Preconditions.checkState((float)dest.getMinX() == (float)mfd.getMinX(),
					"MFD minX mismatch for %s between branches", type);
			Preconditions.checkState((float)dest.getDelta() == (float)mfd.getDelta(),
					"MFD delta mismatch for %s between branches", type);
			Preconditions.checkState(dest.size() == mfd.size(), "MFD size mismatch for %s between branches", type);
			if (mfd.getRegion() != null || dest.getRegion() != null) {
				Preconditions.checkState(mfd.getRegion() != null,
						"Some branches have a region for %s and others don't", type);
				Preconditions.checkState(dest.getRegion() != null,
						"Some branches have a region for %s and others don't", type);
				Preconditions.checkState(mfd.getRegion().equalsRegion(dest.getRegion()),
						"Region mismatch across branches for %s", type);
			}
			// now actually average it in
			for (int i=0; i<mfd.size(); i++)
				dest.add(i, weight*mfd.getY(i));
			if (dest instanceof UncertainBoundedIncrMagFreqDist) {
				Preconditions.checkState(mfd instanceof UncertainBoundedIncrMagFreqDist,
						"Some branches have uncertainty bounds and others don't for %s", type);
				UncertainBoundedIncrMagFreqDist boundedDest = (UncertainBoundedIncrMagFreqDist)dest;
				UncertainBoundedIncrMagFreqDist boundedMFD = (UncertainBoundedIncrMagFreqDist)mfd;
				Preconditions.checkState(boundedDest.getBoundType() == boundedMFD.getBoundType(),
						"Bound type mismatch for %s", type);
				averageInWeighted(boundedDest.getLower(), boundedMFD.getLower(), type+" LOWER", weight);
				averageInWeighted(boundedDest.getUpper(), boundedMFD.getUpper(), type+" UPPER", weight);
			}
			if (dest instanceof UncertainIncrMagFreqDist) {
				Preconditions.checkState(mfd instanceof UncertainIncrMagFreqDist,
						"Some branches have uncertainties and others don't for %s", type);
				EvenlyDiscretizedFunc destStdDevs = ((UncertainIncrMagFreqDist)dest).getStdDevs();
				EvenlyDiscretizedFunc mfdStdDevs = ((UncertainIncrMagFreqDist)mfd).getStdDevs();
				for (int i=0; i<destStdDevs.size(); i++)
					destStdDevs.add(i, mfdStdDevs.getY(i)*weight);
			}
		}
		
		private static void scaleToTotWeight(EvenlyDiscretizedFunc dest, double totWeight) {
			if (dest == null)
				return;
			double scale = 1d/totWeight;
			for (int i=0; i<dest.size(); i++) {
				double val = dest.getY(i);
				if (val != 0d)
					dest.set(i, val*scale);
			}
			if (dest instanceof UncertainBoundedIncrMagFreqDist) {
				UncertainBoundedIncrMagFreqDist bounded = (UncertainBoundedIncrMagFreqDist)dest;
				scaleToTotWeight(bounded.getLower(), totWeight);
				scaleToTotWeight(bounded.getUpper(), totWeight);
			}
			if (dest instanceof UncertainIncrMagFreqDist)
				scaleToTotWeight(((UncertainIncrMagFreqDist)dest).getStdDevs(), totWeight);
		}
		
	}

}