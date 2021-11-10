package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.opensha.commons.data.ShortNamed;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ConstraintRange;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import cern.colt.matrix.tdouble.DoubleMatrix2D;

/**
 * Abstract class for an inversion constraint.
 * <br>
 * Note on serialization: Default Gson serialization will be attempted to load/save constraints to/from JSON. Custom
 * TypeAdpaters can be registered via the {@link JsonAdapter} annotation. If the constraint relies on a
 * {@link FaultSystemRupSet} or its modules, those fields must be transient and can be populated after deserialization
 * by overriding {@link #setRuptureSet(FaultSystemRupSet)}.
 * 
 * @author kevin
 */
@JsonAdapter(InversionConstraint.Adapter.class)
public abstract class InversionConstraint implements ShortNamed {
	
	private transient boolean quickGetsSets = true;
	private transient String name;
	private transient String shortName;
	protected transient double weight;
	protected transient boolean inequality;
	protected transient ConstraintWeightingType weightingType;
	
	/**
	 * Constructor that sets the names, weight, and inequality flag. These values will be serialized externally and should
	 * not be stored separately in the subclass. Constraint weighting type is set to {@link ConstraintWeightingType.UNNORMALIZED}.
	 * 
	 * @param name
	 * @param shortName
	 * @param weight
	 * @param inequality
	 */
	protected InversionConstraint(String name, String shortName, double weight, boolean inequality) {
		this(name, shortName, weight, inequality, ConstraintWeightingType.UNNORMALIZED);
	}
	
	/**
	 * Constructor that sets the names, weight, and inequality flag. These values will be serialized externally and should
	 * not be stored separately in the subclass.
	 * 
	 * @param name
	 * @param shortName
	 * @param weight
	 * @param inequality
	 * @param weightingType;
	 */
	protected InversionConstraint(String name, String shortName, double weight, boolean inequality, ConstraintWeightingType weightingType) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
		this.inequality = inequality;
		this.weightingType = weightingType;
	}
	
	/**
	 * @return the number of rows in the A matrix/d vector for this constraint
	 */
	public abstract int getNumRows();
	
	/**
	 * 
	 * @param startIndex
	 * @return range for this constraint
	 */
	public ConstraintRange getRange(int startIndex) {
		return new ConstraintRange(name, shortName, startIndex, startIndex+getNumRows(), inequality, weight, weightingType);
	}
	
	/**
	 * @return true if this is an inequality constraint (A_ineq, d_ineq), else a regular
	 * equality constraint
	 */
	public final boolean isInequality() {
		return inequality;
	}
	
	/**
	 * Sets the weight, by which data fit misfits are multiplied, for this constraint
	 * 
	 * @param weight
	 */
	public void setWeight(double weight) {
		this.weight = weight;
	}
	
	/**
	 * Gets the weight, by which data fit misfits are multiplied, for this constraint
	 * 
	 * @return weight
	 */
	public final double getWeight() {
		return weight;
	}
	
	/**
	 * Gets the constraint weighting type, usefult for interpreting misfits, for this constraint
	 * 
	 * @return constraint weighting type
	 */
	public final ConstraintWeightingType getWeightingType() {
		return weightingType;
	}
	
	@Override
	public final String getName() {
		return name;
	}

	@Override
	public final String getShortName() {
		return shortName;
	}

	/**
	 * Encodes this constraint into the given A matrix and d vector, beginning at the
	 * given starting row and ending before (startRow+getNumRows())
	 * @param A
	 * @param d
	 * @param startRow
	 * @return number of non-zero elements added
	 */
	public abstract long encode(DoubleMatrix2D A, double[] d, int startRow);
	
	/**
	 * Utility method to set a value in the given A matrix, respecting the quickGetsSets value
	 * 
	 * @param A
	 * @param row
	 * @param col
	 * @param val
	 */
	protected void setA(DoubleMatrix2D A, int row, int col, double val) {
		if (quickGetsSets)
			A.setQuick(row, col, val);
		else
			A.set(row, col, val);
	}
	
	/**
	 * Utility method to get a value in the given A matrix, respecting the quickGetsSets value
	 * 
	 * @param A
	 * @param row
	 * @param col
	 * @return value at that location
	 */
	protected double getA(DoubleMatrix2D A, int row, int col) {
		if (quickGetsSets)
			return A.getQuick(row, col);
		return A.get(row, col);
	}
	
	/**
	 * Utility method to add a value in the given A matrix, respecting the quickGetsSets value
	 * 
	 * @param A
	 * @param row
	 * @param col
	 * @param val
	 * @return true if the previous value was nonzero
	 */
	protected boolean addA(DoubleMatrix2D A, int row, int col, double val) {
		double prevVal = getA(A, row, col);
		if (quickGetsSets)
			A.setQuick(row, col, val+prevVal);
		else
			A.set(row, col, val+prevVal);
		return prevVal != 0d;
	}
	
	/**
	 * Sets whether or not we should use quick set/get methods on the A matrix. The quick
	 * versions of these methods are faster, but don't do any input validation (range checking)
	 * @param quickGetsSets
	 */
	public void setQuickGetSets(boolean quickGetsSets) {
		this.quickGetsSets = quickGetsSets;
	}
	
	/**
	 * Configure (or re-configure) the constraint for the given rupture set. Default implementation does nothing,
	 * override if the constraint depends on the rupture set
	 * <br>
	 * This method will be called to set the rupture set after JSON deserialization.
	 * 
	 * @param rupSet
	 */
	public void setRuptureSet(FaultSystemRupSet rupSet) {}
	
	public static class Adapter extends TypeAdapter<InversionConstraint> {
		
		private Gson gson;
		private FaultSystemRupSet rupSet;
		
		public Adapter() {
			this(null);
		}
		
		public Adapter(FaultSystemRupSet rupSet) {
			this.rupSet = rupSet;
			gson = new GsonBuilder().registerTypeHierarchyAdapter(
					FaultSystemRupSet.class, new RupSetInterceptorAdaptor())
					.serializeSpecialFloatingPointValues().create();
		}

		@Override
		public void write(JsonWriter out, InversionConstraint value) throws IOException {
			out.beginObject();
			out.name("type").value(value.getClass().getName());
			out.name("name").value(value.getName());
			out.name("shortName").value(value.getShortName());
			out.name("inequality").value(value.isInequality());
			out.name("weight").value(value.getWeight());
			if (value.weightingType != null)
				out.name("weightingType").value(value.weightingType.name());
			out.name("data");
//			System.out.println("Writing "+value.getName()+" ("+value.getClass().getName()+")");
			gson.toJson(value, value.getClass(), out);
			out.endObject();
		}

		@SuppressWarnings("unchecked")
		@Override
		public InversionConstraint read(JsonReader in) throws IOException {
			Class<? extends InversionConstraint> type = null;
			String name = null;
			String shortName = null;
			Boolean inequality = null;
			Double weight = null;
			ConstraintWeightingType weightingType = null;
			InversionConstraint constraint = null;
			
			in.beginObject();
			
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "type":
					String className = in.nextString();
					try {
						type = (Class<? extends InversionConstraint>) Class.forName(className);
					} catch (Exception e) {
						System.err.println("WARNING: Could not locate constraint class, will use stub instead: "+className);
					}
					break;
				case "name":
					name = in.nextString();
					break;
				case "shortName":
					shortName = in.nextString();
					break;
				case "inequality":
					inequality = in.nextBoolean();
					break;
				case "weight":
					weight = in.nextDouble();
					break;
				case "weightingType":
					weightingType = ConstraintWeightingType.valueOf(in.nextString());
					break;
				case "data":
					if (type == null) {
						in.skipValue();
						break;
					}
					constraint = gson.fromJson(in, type);
					if (rupSet != null)
						constraint.setRuptureSet(rupSet);
					break;

				default:
					in.skipValue();
					break;
				}
			}
			
			Preconditions.checkNotNull(weight, "Weight not supplied in constraint JSON");
			Preconditions.checkNotNull(inequality, "Inequality boolean not supplied in constraint JSON");
			
			if (constraint == null) {
				// configure stub
				constraint = new ConstraintStub(name, shortName, weight, inequality, weightingType); 
			} else {
				// set transient properties, exactly as existed when this was created
				constraint.weight = weight;
				constraint.inequality = inequality;
				constraint.name = name;
				constraint.shortName = shortName;
				if (weightingType != null)
					constraint.weightingType = weightingType;
			}
			
			in.endObject();
			return constraint;
		}
		
	}
	
	private static class RupSetInterceptorAdaptor extends TypeAdapter<FaultSystemRupSet> {

		@Override
		public void write(JsonWriter out, FaultSystemRupSet value) throws IOException {
			throw new IllegalStateException("Attempting to serialize a FaultSystemRupSet, which should never happen. "
					+ "Constraint should declare the rupture set to be transient and override the setRuptureSet(...) "
					+ "method to get the rupture set.");
		}

		@Override
		public FaultSystemRupSet read(JsonReader in) throws IOException {
			throw new IllegalStateException("Attempting to deserialize a FaultSystemRupSet, which should never happen. "
					+ "Constraint should declare the rupture set to be transient and override the setRuptureSet(...) "
					+ "method to get the rupture set.");
		}
		
	}
	
	private static class ConstraintStub extends InversionConstraint {

		protected ConstraintStub(String name, String shortName, double weight, boolean inequality,
				ConstraintWeightingType weightingType) {
			super(name, shortName, weight, inequality, weightingType);
		}

		@Override
		public int getNumRows() {
			throw new UnsupportedOperationException("Cannot call getNumRows() on a constraint stub "
					+ "(after deserialization failed, see earlier message)");
		}

		@Override
		public long encode(DoubleMatrix2D A, double[] d, int startRow) {
			throw new UnsupportedOperationException("Cannot call encode(...) on a constraint stub "
					+ "(after deserialization failed, see earlier message)");
		}
		
	}
	
	public static void writeConstraintsJSON(File jsonFile, List<InversionConstraint> constraints) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(jsonFile));
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		TypeToken<?> token = TypeToken.getParameterized(List.class, InversionConstraint.class);
		gson.toJson(constraints, token.getType(), writer);
		writer.flush();
		writer.close();
	}
	
	public static List<InversionConstraint> loadConstraintsJSON(File jsonFile, FaultSystemRupSet rupSet) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(jsonFile));
		Gson gson = new GsonBuilder().registerTypeAdapter(InversionConstraint.class, new Adapter(rupSet)).create();
		TypeToken<?> token = TypeToken.getParameterized(List.class, InversionConstraint.class);
		return gson.fromJson(reader, token.getType());
	}

}