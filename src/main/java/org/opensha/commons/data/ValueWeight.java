package org.opensha.commons.data;


/**
 * ValueWeight : This class allows to have a value and a weight can be assigned to that value.
 * for example: A rupture rate can be assigned and we  can assign a weight to that value of rate
 * @author vipingupta
 *
 */
public class ValueWeight {
	private final static String C = "ValueWeight";
	private double value = Double.NaN;  // value
	private double weight = Double.NaN; // weight associated with this value

	/**
	 * Default constructor
	 *
	 */
	public ValueWeight() { }

	/**
	 * Set the value and weight
	 * @param value
	 * @param weight
	 */
	public ValueWeight(double value, double weight) {
		setValue(value);
		setWeight(weight);
	}


	/**
	 * Get value
	 * @return
	 */
	public double getValue() {
		return value;
	}

	/**
	 * Set value
	 * @param value
	 */
	public void setValue(double value) {
		this.value = value;
	}

	/**
	 * Get weight
	 * @return
	 */
	public double getWeight() {
		return weight;
	}

	/**
	 * Set weight
	 * @param weight
	 */
	public void setWeight(double weight) {
		this.weight = weight;
	}

	/**
	 * clone
	 */
	public Object clone() {
		ValueWeight valWeight = new ValueWeight(this.value, this.weight);
		return valWeight;
	}

	/**
     *  Compares the values to if this is less than, equal to, or greater than
     *  the comparing objects. Weight is irrelevant in this case
     *
     * @param  obj                     The object to compare this to
     * @return                         -1 if this value < obj value, 0 if equal,
     *      +1 if this value > obj value
     * @exception  ClassCastException  Is thrown if the comparing object is not
     *      a ValueWeight.
     */
    public int compareTo( Object obj ) throws ClassCastException {

        String S = C + ":compareTo(): ";

        if ( !( obj instanceof ValueWeight ) ) {
            throw new ClassCastException( S + "Object not a ValueWeight, unable to compare" );
        }
        ValueWeight param = ( ValueWeight ) obj;
        Double thisVal = new Double(value);

        return thisVal.compareTo( new Double(param.value) );
    }
}
