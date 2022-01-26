package org.opensha.commons.data.function;

import org.opensha.commons.param.ParameterList;


/**
 * <b>Title:</b> EvenlyDiscrFuncWithParams<p>
 * <b>Description:</b> Subclass of the EvenlyDiscretizedFunc that
 * also includes a ParameterList of paramters associated with the
 * function. Not much different but provides methods for dealing with
 * the parameter list and overides such methods as toString(),
 * equals(), etc. These extra methods are put in an interface
 * FuncWithParamsAPI. Therefore this class implements that
 * interface as well as the DiscretizedFuncAPI.<p>
 *
 * In the case of the IMRTesterApplet the parameters represent the
 * input argument values that went into calculating the IMR.<p>
 *
 * @see EvenlyDiscretizedFunc
 * @see FuncWithParams
 * @see AbstractDiscretizedFunc
 * @see DiscretizedFuncAPIFunc
 * @see ParameterList
 * @author Steven W. Rock
 * @version 1.0
 */

public class EvenlyDiscrFuncWithParams
    extends EvenlyDiscretizedFunc
    implements FuncWithParams
{

    /**
     * This parameter list is the set of parameters that went into
     * calculation this DiscretizedFunction. Useful for determining if two
     * data sets are the same, i.e. have the same x/y axis and the same
     * set of independent parameters. Bypasses the more numerically intensive
     * task of comparing each Point2D of two DiscretizedFunction2D.
     */
    protected ParameterList list = new ParameterList();



    // **********************
    /** @todo  Constructors */
    // **********************


    public EvenlyDiscrFuncWithParams(double min, int num, double delta) {
        super(min, num, delta);
    }

    public EvenlyDiscrFuncWithParams(double min, int num,double delta, ParameterList list) {
        super(min, num, delta);
        this.list = list;
    }




    // **********************************
    /** @todo  Data Accessors / Setters */
    // **********************************

    /**
     * This parameter list is the set of parameters that went into
     * calculation this DiscretizedFunction. Useful for determining if two
     * data sets are the same, i.e. have the same x/y axis and the same
     * set of independent parameters. Bypasses the more numerically intensive
     * task of comparing each Point2D of two DiscretizedFunction2D.
     */
    public ParameterList getParameterList(){ return list; }

    /** Set the parameter list from an external source */
    public void setParameterList(ParameterList list){ this.list = list; }

    /** Returns name/value pairs, separated with commas, as one string, usefule for legends, etc. */
    public String getParametersString(){ return list.toString(); }

    public String getInfo(){ return list.toString(); }

    /**
     * Returns true if two DefaultXYDiscretizedFunction2D
     * have the same independent parameters; name and value.
     */
    public boolean equalParameterNamesAndValues(FuncWithParams function){
        if( function.getParametersString().equals( getParametersString() ) ) return true;
        else return false;
    }

    /**
     * Returns true if the second function has the same named
     * parameters in it's list, values may be different.
     */
    public boolean equalParameterNames(FuncWithParams function){
        return function.getParameterList().equalNames( this.getParameterList() );
    }




    /**
     * Returns a copy of this and all points in this DiscretizedFunction.
     * Also clones each parameter in the parameter list.
     */
    public EvenlyDiscretizedFunc deepClone(){

        EvenlyDiscrFuncWithParams f = new EvenlyDiscrFuncWithParams(
             minX, num, delta
        );

        f.info = info;
        f.minX = minX;
        f.maxX = maxX;
        f.name = name;
        f.tolerance = tolerance;
        f.setXAxisName(this.getXAxisName());
        f.setYAxisName(this.getYAxisName());

        f.setParameterList( (ParameterList)getParameterList().clone() );

        for(int i = 0; i<num; i++)
            f.set(i, points[i]);

        return f;

    }

    /**
     * Determines if two functions are the same with respect to the parameters that
     * were used to calculate the function, NOT THAT EACH POINT IS THE SAME. This is used
     * by the DiscretizedFunction2DAPIList to determine if it should add a new function
     * to the list.
     */
    public boolean equals(FuncWithParams function){
        String S = C + ": equals():";
        return function.getParameterList().equals( this.list );
    }


    /**
     * Returns all the parameters associated with the function as one string with
     * no new lines, of the format:<P>
     * name = value, name2 = value2, etc.
     */
    public String toString(){ return this.list.toString(); }

}
