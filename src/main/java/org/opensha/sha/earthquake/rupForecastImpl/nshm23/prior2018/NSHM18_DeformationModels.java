package org.opensha.sha.earthquake.rupForecastImpl.nshm23.prior2018;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jfree.data.Range;
import org.opensha.commons.calc.magScalingRelations.MagLengthRelationship;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.util.SubSectionBuilder;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_DeformationModels;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum NSHM18_DeformationModels implements RupSetDeformationModel {
	// TODO somehow honor slip-weight-exceptions.json?
	GEOL("Geologic", "GEO", 0.8d),
	BIRD("Bird", "BIRD", 0.1d),
	ZENG("Zeng", "ZENG", 0.1d),
	BRANCH_AVERAGED("Branch Averaged", "BrAvg", 0d);
	
	public static boolean APPLY_ACTIVITY_PROBABILITY = true;
	
	static final String NSHM18_DM_PATH = "/data/erf/nshm18/def_models/deformation-model-data.json";
	static final String ACTIVITY_PROB_PATH = "/data/erf/nshm18/special_cases/activity-probability.json";

	private String name;
	private String shortName;
	private double weight;

	private NSHM18_DeformationModels(String name, String shortName, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return name();
	}

	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public boolean isApplicableTo(RupSetFaultModel faultModel) {
		if (!(faultModel instanceof NSHM18_FaultModels))
			return false;
		if (faultModel != NSHM18_FaultModels.NSHM18_WUS_NoCA)
			// if we're including CA, can only be BA or geologic
			return this == BRANCH_AVERAGED || this == GEOL;
		return true;
	}
	
	static class DefModelRecord {
		int id;
		String name;
		String state;
		Double length;
		Double magnitude;
		Map<String, DefModelSlipRecord> rates;
	}
	
	@JsonAdapter(SlipRecordAdapter.class)
	static class DefModelSlipRecord {
		Double slip_rate;
		Double rake;
		Double gr_a_value;
		Double single_rate;
	}
	
	private static class SlipRecordAdapter extends TypeAdapter<DefModelSlipRecord> {

		@Override
		public void write(JsonWriter out, DefModelSlipRecord value) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public DefModelSlipRecord read(JsonReader in) throws IOException {
			in.beginObject();
			
			DefModelSlipRecord rec = new DefModelSlipRecord();
			
			while (in.hasNext()) {
				String name = in.nextName();
				if (in.peek() == JsonToken.NULL) {
					in.skipValue();
					continue;
				}
				switch (name) {
				case "slip-rate":
					rec.slip_rate = in.nextDouble();
					break;
				case "rake":
					rec.rake = in.nextDouble();
					break;
				case "gr-a-value":
					rec.gr_a_value = in.nextDouble();
					break;
				case "single-rate":
					rec.single_rate = in.nextDouble();
					break;

				default:
					System.err.println("Skipping unexpected key: "+name);
					in.skipValue();
					break;
				}
			}
			
			in.endObject();
			
			return rec;
		}
		
	}
	
	@Override
	public List<? extends FaultSection> build(RupSetFaultModel faultModel) throws IOException {
		Preconditions.checkState(isApplicableTo(faultModel), "%s is not applicable to %s", name, faultModel.getName());
		List<? extends FaultSection> sectsOutsideCA = buildFullSects(NSHM18_FaultModels.NSHM18_WUS_NoCA);
		
		List<FaultSection> subsectsOutsideCA = SubSectionBuilder.buildSubSects(sectsOutsideCA);
		
		List<FaultSection> fullList;
		if (faultModel == NSHM18_FaultModels.NSHM18_WUS_PlusU3_FM_3p1) {
			// add UCERF3
			List<? extends FaultSection> u3SubSects;
			if (this == BRANCH_AVERAGED)
				u3SubSects = DeformationModels.MEAN_UCERF3.build(FaultModels.FM3_1);
			else if (this == GEOL)
				u3SubSects = DeformationModels.GEOLOGIC.build(FaultModels.FM3_1);
			else
				throw new IllegalStateException("Can only build stitched NSHM18 w/ U3 DM for geologic or branch averaged");
			
			fullList = new ArrayList<>(subsectsOutsideCA);
			
			for (FaultSection subsect : u3SubSects) {
				if (subsect.getParentSectionId() == 721 || subsect.getParentSectionId() == 719)
					continue;
				int newID = fullList.size();
				subsect = subsect.clone();
				subsect.setSectionId(newID);
				fullList.add(subsect);
			}
		} else {
			Preconditions.checkState(faultModel == NSHM18_FaultModels.NSHM18_WUS_NoCA);
			fullList = subsectsOutsideCA;
		}
		
		NSHM23_DeformationModels.applyStdDevDefaults(fullList);
		
		return fullList;
	}

	@Override
	public List<? extends FaultSection> build(RupSetFaultModel faultModel, int minPerFault, double ddwFract,
			double fixedLen) throws IOException {
		Preconditions.checkState(minPerFault == 2, "minPerFault must be 2 for NSHM18");
		Preconditions.checkState(ddwFract == 0.5, "ddwFract must be 0.5 for NSHM18");
		Preconditions.checkState(!(fixedLen > 0d), "fixedLen must be NaN for NSHM18");
		return build(faultModel);
	}

	@Override
	public List<? extends FaultSection> buildForSubsects(RupSetFaultModel faultModel,
			List<? extends FaultSection> subSects) throws IOException {
		throw new UnsupportedOperationException("Not supported, NSHM18/UCERF3 must build the subsections");
	}
	
	static class ActivityProbRecord {
		int id;
		String name;
		String state;
		Double probability;
	}
	
	private Map<Integer, ActivityProbRecord> activityProbabilities;
	
	private synchronized Map<Integer, ActivityProbRecord> getActivityProbabilities() {
		if (activityProbabilities != null)
			return activityProbabilities;
		Map<Integer, ActivityProbRecord> activityProbabilities = new HashMap<>();
		
		Reader reader = new BufferedReader(new InputStreamReader(
				NSHM18_DeformationModels.class.getResourceAsStream(ACTIVITY_PROB_PATH)));
		Preconditions.checkNotNull(reader, "Activity probability file not found: %s", NSHM18_DM_PATH);
		
		Gson gson = new GsonBuilder().create();
		
		List<ActivityProbRecord> recs = gson.fromJson(reader,
						TypeToken.getParameterized(List.class, ActivityProbRecord.class).getType());
		for (ActivityProbRecord rec : recs)
			activityProbabilities.put(rec.id, rec);
		
		this.activityProbabilities = activityProbabilities;
		return activityProbabilities;
	}
	
	public List<? extends FaultSection> buildFullSects(RupSetFaultModel faultModel) throws IOException {
		Preconditions.checkState(isApplicableTo(faultModel), "DM/FM mismatch");
		Reader dmReader = new BufferedReader(new InputStreamReader(
				NSHM18_DeformationModels.class.getResourceAsStream(NSHM18_DM_PATH)));
		Preconditions.checkNotNull(dmReader, "Deformation model file not found: %s", NSHM18_DM_PATH);
		
		Gson gson = new GsonBuilder().create();
		
		List<DefModelRecord> records = gson.fromJson(dmReader,
				TypeToken.getParameterized(List.class, DefModelRecord.class).getType());
		
		System.out.println("Loaded "+records.size()+" deformation model records");
		
		Map<Integer, DefModelRecord> recordMap = new HashMap<>();
		for (DefModelRecord record : records)
			recordMap.put(record.id, record);
		Preconditions.checkState(recordMap.size() == records.size());
		
		Map<Integer, ActivityProbRecord> activityProbabilities =
				APPLY_ACTIVITY_PROBABILITY ? getActivityProbabilities() : null;
		
		List<? extends FaultSection> origSects = faultModel.getFaultSections();
		List<GeoJSONFaultSection> modSects = new ArrayList<>();
		int numNonZero = 0;
		for (FaultSection sect : origSects) {
			GeoJSONFaultSection modSect = new GeoJSONFaultSection(sect);
			DefModelRecord rec = recordMap.get(sect.getSectionId());
			if (rec == null) {
				System.err.println("WARNING: no matching deformation model record for id="
						+sect.getSectionId()+", name="+sect.getSectionName());
				continue;
			}
//			Preconditions.checkNotNull(rec, "No matching deformation model record for id=%s, name=%s",
//					sect.getSectionId(), sect.getSectionName());
			DefModelSlipRecord slipRec = rec.rates.get(shortName);
			// if it's branch averaged, this is null and the averaging will be applied
			
			double rake = sect.getAveRake();
			Preconditions.checkState((float)rake == -180f || (float)rake == -90f || (float)rake == 0f
					|| (float)rake == 90f || (float)rake == 180f,
					"Unexpected geologic rake: %s", rake);
			boolean slipIsVertical = (float)rake == -90f || (float)rake == 90f;

//			Preconditions.checkNotNull(slipRec, "No matching deformation model %s rate for id=%s, name=%s",
			//					shortName, sect.getSectionId(), sect.getSectionName());
			double slipRate;
			if (slipRec == null || slipRec.slip_rate == null) {
				if (rec.name.startsWith("Seattle")) {
					if (rec.name.contains("east") || rec.name.contains("middle"))
						slipRate = 0.08;
					else if (rec.name.contains("north") || rec.name.contains("south"))
						slipRate = 0.17;
					else
						throw new IllegalStateException("Unexpected Seattle: "+rec.name);
					System.err.println("WARNING: applying hardcoded NSHM23 draft slip rate for "+rec.name+": "+(float)slipRate);
					slipIsVertical = false;
				} else {
					// use other slip rates
					double sumOtherWeights = 0d;
					double rateWeightSum = 0d;
					for (NSHM18_DeformationModels dm : values()) {
						DefModelSlipRecord oSlipRec = rec.rates.get(dm.shortName);
						if (oSlipRec != null && oSlipRec.slip_rate != null && oSlipRec.slip_rate > 0d) {
							sumOtherWeights += dm.weight;
							rateWeightSum += oSlipRec.slip_rate * dm.weight;
						}
					}
					if (sumOtherWeights > 0d)
						rateWeightSum /= sumOtherWeights;
					if (this != BRANCH_AVERAGED)
						System.err.println("WARNING: no "+shortName+" slip rate for id="+sect.getSectionId()+", name="
								+sect.getSectionName()+", setting to weight average of other branch choices: "+(float)rateWeightSum);
					slipRate = rateWeightSum;
					
					// TODO: don't use DM rake
					// TODO: if rake is -90 or 90, they are vertical slip rates
				}
			} else {
				//				Preconditions.checkNotNull(slipRec.slip_rate, "No %s slip rate for id=%s, name=%s",
				//				shortName, sect.getSectionId(), sect.getSectionName());
				//		Preconditions.checkNotNull(slipRec.rake, "No %s rake for id=%s, name=%s",
				//				shortName, sect.getSectionId(), sect.getSectionName());
				slipRate = slipRec.slip_rate;
				// Peter says DM rakes were never actually used
//				if (slipRec.rake == null) {
//					System.err.println("WARNING: no "+shortName+" rake for id="
//							+sect.getSectionId()+", name="+sect.getSectionName()+", leaving at "+(float)sect.getAveRake());
//				} else {
//					modSect.setAveRake(slipRec.rake);
//				}
			}
			
			Preconditions.checkState(Double.isFinite(slipRate), "Bad slip rate for section %s. %s: %s",
					sect.getSectionId(), sect.getSectionName(), slipRate);
			if (slipIsVertical) {
				// need to convert form vertical slip rate to on-plane slip rate
				double origSlipRate = slipRate;
				slipRate = slipRate / Math.sin(Math.toRadians(sect.getAveDip()));
				Preconditions.checkState(Double.isFinite(slipRate), "Bad slip rate after on-plane conversion for section %s. %s: %s. Vertical slip: %s",
						sect.getSectionId(), sect.getSectionName(), slipRate, origSlipRate);
			}
			if (activityProbabilities != null && activityProbabilities.containsKey(sect.getSectionId())) {
				ActivityProbRecord probs = activityProbabilities.get(sect.getSectionId());
				System.out.println("Applying activity probability of "+probs.probability+" for fault "+probs.id+", name "+sect.getSectionName());
				slipRate *= probs.probability;
			}
			modSect.setAveSlipRate(slipRate);
			
			if (modSect.getOrigAveSlipRate() > 0d) {
				numNonZero++;
				modSect.setSlipRateStdDev(Math.max(NSHM23_DeformationModels.STD_DEV_FLOOR, 0.5*modSect.getOrigAveSlipRate()));
			} else {
				modSect.setSlipRateStdDev(NSHM23_DeformationModels.STD_DEV_FLOOR);
			}
			
			modSects.add(modSect);
		}
		
		System.out.println("Built "+modSects.size()+" sections (of "+origSects.size()+" original), "+numNonZero+" have slip rates > 0");
		
		return modSects;
	}
	
	private static void plotML_Comparison(NSHM18_FaultModels fm, MagLengthRelationship ml) throws IOException {
		Map<Integer, FaultSection> sects = fm.getFaultSectionIDMap();
		
		Reader dmReader = new BufferedReader(new InputStreamReader(
				NSHM18_DeformationModels.class.getResourceAsStream(NSHM18_DM_PATH)));
		Preconditions.checkNotNull(dmReader, "Deformation model file not found: %s", NSHM18_DM_PATH);
		
		Gson gson = new GsonBuilder().create();
		
		List<DefModelRecord> records = gson.fromJson(dmReader,
				TypeToken.getParameterized(List.class, DefModelRecord.class).getType());
		
		System.out.println("Loaded "+records.size()+" deformation model records");
		
		DefaultXY_DataSet scatter = new DefaultXY_DataSet();
		
		for (DefModelRecord record : records) {
			FaultSection sect = sects.get(record.id);
			if (!NSHM18_FaultModels.STATES.contains(record.state))
				continue;
			if (sect == null) {
				System.err.println("WARNING: no matching sect for record "+record.id+", "+record.name+" ("+record.state+")");
				continue;
			}
			if (record.length == null || record.magnitude == null)
				continue;
//			ml.setRake(sect.getAveRake());
			double calcMag = ml.getMedianMag(record.length);
//			double calcMag = ml.getMedianMag(sect.getFaultTrace().getTraceLength());
			if (Math.abs(calcMag - record.magnitude) > 0.05 && record.magnitude < 7.49) {
				System.out.println(record.id+". "+record.name);
				System.out.println("\tJSON length="+record.length.floatValue()+"\tmag="+record.magnitude.floatValue());
				System.out.println("\tCalc mag w/ JSON length: "+(float)ml.getMedianMag(record.length));
				System.out.println("\tCalc mag w/ our length ("+(float)sect.getFaultTrace().getTraceLength()+"): "
						+(float)ml.getMedianMag(sect.getFaultTrace().getTraceLength()));
			}
			scatter.set(record.magnitude, calcMag);
		}
		
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		Range range = new Range(5d, 9d);
		
		DefaultXY_DataSet line = new DefaultXY_DataSet();
		line.set(range.getLowerBound(), range.getLowerBound());
		line.set(range.getUpperBound(), range.getUpperBound());
		
		funcs.add(line);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));
		
		funcs.add(scatter);
		chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 2f, Color.BLACK));
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		
		PlotSpec spec = new PlotSpec(funcs, chars, "M-L comparison", "Deformation Model JSON Characteristic Magnitude", ml.getName());
		gp.drawGraphPanel(spec, false, false, range, range);
		
		PlotUtils.writePlots(new File("/tmp"), "ml_scatter", gp, 800, false, true, false, false);
	}
	
	public static void main(String[] args) throws IOException {
		System.out.println("Building GEOLOGIC");
		GEOL.build(NSHM18_FaultModels.NSHM18_WUS_NoCA);
		System.out.println("Building ZENG");
		ZENG.build(NSHM18_FaultModels.NSHM18_WUS_NoCA);
		System.out.println("Building BIRD");
		BIRD.build(NSHM18_FaultModels.NSHM18_WUS_NoCA);
		System.out.println("Building BA");
		BRANCH_AVERAGED.build(NSHM18_FaultModels.NSHM18_WUS_NoCA);
		
//		plotML_Comparison(NSHM18_FaultModels.NSHM18_WUS_NoCA, new WC1994_MagLengthRelationship());
	}

}
