package org.opensha.commons.eq.stat;

import org.opensha.commons.util.DataUtils;


/**
 * Class provides methods to use and recover information from
 * the Modified Omori Law. The Modified Omori Law
 * describes the decay of aftershock rate following a mainshock
 * and has the form:<br/>
 * <br/>
 * <center><img src="doc-files/omori_law.jpg"></center><br/>
 * <br/>
 * where <b>K</b> is a magnitude dependent productivity constant, <b>c</b> is
 * small value of time, and <b>p</b> is a decay constant. <b>K</b> represents 
 * the rate of earthquake occurrence at <b>t</b>=1 day after a mainshock;
 * <b>c</b> reflects earthquake detection limits and also prevents the
 * Modified Omori Law from 'blowing up' at short times after an event; 
 * <b>p</b> controls the decay of the aftershock rate.<br/>
 * <br/>
 * Methods in this class make use of a probability density form of
 * the Omori Law (PDF):<br/>
 * <br/>
 * <center><img src="doc-files/omori_law_pdf.jpg"></center><br/>
 * <br/>
 * and its corresponding complementary cumulative distribution 
 * function:<br/>
 * <br/>
 * <center><img src="doc-files/omori_law_cdf.jpg"></center><br/>
 * <br/>
 * <br/>
 * <font color="#D05625"><i>Note:</i></font> This implementation is
 * only valid for values of <b>p</b>&gt;1. Attempts to use smaller values
 * will trigger an <code>IllegalArgumentException</code>. 
 * TODO: CDF based computations will work for p&ne;1 which
 * is useful for generating catalogs; should make modifications where
 * any p=1 is adjusted by some small increment (e.g. p=1.000001).<br/>
 * <br/>
 * <br/>
 * <b><i>References:</i></b>
 * <ul>
 *  <li>Utsu, T. (1969), Aftershocks and earthquake statistics (I) Some source 
 *      parameters which characterize an aftershock sequence and their 
 *      interrelations, <i>J. Fac. Sci. Hokkaido Univ., Ser. VII, 3</i>, 
 *      129-195.</li>
 *  <li>Utsu, T., Y. Ogata, and R. S. Matsu'ura (1995), The centenary of the 
 *      Omori Formula for a decay law of aftershock activity, <i>Journal of the 
 *      Physics of the Earth, 43</i>, 1-33.</li>
 * </ul>
 * <br/>
 * <br/>
 * @author Peter Powers
 * @version $Id: Omori.java 7478 2011-02-15 04:56:25Z pmpowers $
 * 
 */
public class Omori {    
    
    /** productivity/scaling numerator */
    private double K;
    /** offset/small constant prevents blowup at 0 */
    private double c;
    /** scaling exponent */
    private double p;

    /** convenience constants updated on init and set */
    private double p_one;
    private double one_p;

    /**
     * Constructs and initializes a new Omori's Law calculation container with
     * the specified constants.
     * 
     * @param K the productivity (usually a function of magnitude)
     * @param c a small value that prevents the Law from 
     * 'blowing-up' at short times
     * @param p the log-log slope of the time-aftershock rate relationship
     * @throws IllegalArgumentException if p&le;0
     */
    public Omori(double p, double c, double K) {
        setProductivity(K);
        setOffset(c);
        setP(p);
    }
    
    /**
     * Constructs and initializes a new Omori's Law calculation container with
     * the specified constants and a productivity value K=1.
     * 
     * @param c a small value that prevents the Law from 
     * 'blowing-up' at short times
     * @param p the log-log slope of the time-aftershock rate relationship
     * @throws IllegalArgumentException if p&le;0
     */
    public Omori( double p, double c) {
        this(p, c, 1.0);
    }
    
    /**
     * Computes the probability of an aftershock's occurrence at time t 
     * from the complementary CDF.
     * 
     * @param t time of event in days
     * @return the probability of occurrence
     */
    public double eventProbability(double t) {
        return Math.pow(1.0 + (t/c), one_p);
    }

    /**
     * Computes the maximum time of an event occurrence given a
     * probability using the complementary CDF.
     * 
     * @param probability probability of an event
     * @return the maximum time of occurrence (in days)
     */
    public double eventTime(double probability) {
        return (Math.pow(probability, 1.0/one_p) - 1.0) * c;
    }

    /**
     * Computes the mean number of aftershocks over the time interval
     * [0, +Inf]. (Integral of Omori Law over the interval).
     * 
     * @return the mean number of events
     */
    public int meanEvents() {
        return (int) Math.round(K * Math.pow(c, one_p) / p_one);
    }

    /**
     * Computes the mean number of aftershocks over the time interval
     * [0, t]. (Integral of Omori Law over the interval).
     * 
     * @param t time limit of calculation
     * @return the mean number of events up to time t
     */
    public int meanEvents(double t) {
        return (int) Math.round(K / one_p * (
            Math.pow(t+c, one_p) - 
            Math.pow(c, one_p) ));
    }

    /**
     * Computes the mean number of aftershocks over the time interval
     * [tMin, tMax]. Method uses the integral of Omori Law over the interval
     * for a mainshock that occurred at t=0.
     * 
     * @param tMin interval start time in days
     * @param tMax interval end time in days
     * @return the mean number of events over the interval
     */
    public int meanEvents(double tMin, double tMax) {
        return (int) Math.round(K / one_p * (
            Math.pow(tMax+c, one_p) - 
            Math.pow(tMin+c, one_p) ));
    }

    /**
     * Computes an intensity value at time t from the PDF of the power law.
     * 
     * @param t time at which to compute intensity in days
     * @return the intensity value
     */
    public double intensityValue(double t) {
        return p_one *  Math.pow(c, p_one) / Math.pow(t+c, p);
    }

    /**
     * Computes the time corresponding to a given intensity from
     * the PDF form of the power law.
     * 
     * @param i intensity for which to compute time
     * @return the time value
     */
    public double intensityInverse(double i) {
        return Math.pow((p_one *  Math.pow(c, p_one) / i), (1.0/p)) - c;
    }

    /**
     * Computes the intensity at t=0 from the PDF form of the power law.
     * 
     * @return the intensity value
     */
    public double intensityAtZero() {
        return p_one/c;
    }
    
    /**
     * Calculates the value of the complementary CDF at a given time.
     * 
     * @param t time at which to calculate value
     * @return the complementary CDF value [0,1]
     */
    private double ccdfValue(double t) {
        return Math.pow((t+c)/c,one_p);
    }
    
    /**
     * Generates a temporal sequence of events. Method uses the complementary 
     * CDF of the Omori Law to generate a random set of event times.
     * 
     * @param length of sequence in days
     * @return an array of event times in days
     */
    public double[] generateTimes(double length) {
        // determine the range of ccdf values - the maximum value will always
        // be 1; the minimum value is determined from the end time
        double ccdfMax = 1;
        double ccdfMin = ccdfValue(length);
        double ccdfDelta = ccdfMax - ccdfMin;
        // determine target sequence length and init with randoms
        int sequenceSize = meanEvents(length);
        double[] values = DataUtils.randomValues(sequenceSize);
        // calculate the ccdf values and convert to time, replacing values
        for (int i=0; i<sequenceSize; i++) {
            double ccdfValue = (values[i] * ccdfDelta) + ccdfMin;
            values[i] = eventTime(ccdfValue);
        }
        return values;
    }
    
    /**
     * Returns the offset value 'c'.
     * @return the 'c' value
     */
    public double getOffset() {
        return c;
    }
    
    /**
     * Sets the offset value 'c'.
     * @param c the value to set
     */
    public void setOffset(double c) {
        this.c = c;
    }
    
    /**
     * Returns the productivity value 'K'.
     * @return the 'K' value
     */
    public double getProductivity() {
        return K;
    }
    
    /**
     * Sets the productivity value 'K'.
     * @param K the value to set
     */
    public void setProductivity(double K) {
        this.K = K;
    }
    
    /**
     * Returns the scaling exponent value 'p'.
     * @return the 'p' value
     */
    public double getP() {
        return p;
    }
    
    /**
     * Sets the scaling exponent value 'p'. If p==1, p is set to 1.00000001.
     * @param p the value to set
     */
    public void setP(double p) {
        this.p = (p==1) ? 1.0000001 : p;
        p_one = p-1.0;
        one_p = 1.0-p;
    }
    
    @Override
    public String toString() {
        String s = 
            "Modified Omori Law:\n" +
            "   p=" + p + "\n" +
            "   c=" + c + "\n" +
            "   K=" + K + "\n";
        return s;
    }
    
    // TODO delete
    public static void main(String[] args) {    
        Omori mol = new Omori(0.9999999, 0.01, 100);
        //System.out.println(mol.meanEvents(10));
        System.out.println(mol.intensityValue(3));
    }

}
