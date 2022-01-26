package org.opensha.commons.calc;

import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.AbstractDiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSetList;

/**
 * <p>Title: FunctionListCalc</p>
 * <p>Description: This is the calculator for calculating mean and other
 * statistics for all the functions in a function list</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author Nitin Gupta , Vipin Gupta
 * @ date Dec 11, 2002
 * @version 1.0
 */

public class FunctionListCalc {

  /**
   * This function accepts the functionlist and returns another function after
   * calculating the mean of all the functions in this function list
   *
   * @param funcList  List conatining all the functins for which mean needs to be calculated
   * @return A function for mean of all the functions in the list
   */
  public static AbstractDiscretizedFunc getMean(XY_DataSetList funcList) {
    AbstractDiscretizedFunc meanFunc = new ArbitrarilyDiscretizedFunc();
    int numFunctions = funcList.size(); // number of functions in the list
    int numPoints; // number of x,y points
    if(numFunctions >= 1)  numPoints = funcList.get(0).size();
    else throw new RuntimeException("No function exists in functionlist to calculate mean");

    // now we need to iterate over all the points
    // here we assume that all the functions in the list have same number of x and y values

    // iterate over all points
    for(int i=0; i <numPoints; ++i) {
      double sum=0;
      // now iterate over all functions in the list
      for(int j=0; j<numFunctions; ++j)
        sum+=funcList.get(j).getY(i); // get the y value at this index
      // add the poin to the mean function
      meanFunc.set(funcList.get(0).getX(i),sum/numFunctions);
    }

    return meanFunc;
  }

}
