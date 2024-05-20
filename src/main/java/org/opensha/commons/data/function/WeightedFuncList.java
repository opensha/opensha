package org.opensha.commons.data.function;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.calc.FractileCurveCalculator;

import com.google.common.collect.Lists;

/**
 * <p>Title: WeightedFuncList</p>
 * <p>Description: This class stores the epistemic lists with their uncertainties.
 * This class provides the collective info. for whole list. One can give a
 * arbitrary list for which fractiles have to be calculated.
 * </p>
 * @author : Nitin Gupta
 * @created September 9, 2004.
 * @version 1.0
 */

public class WeightedFuncList {

	//relative Wts for each
	private List<Double> relativeWts = new ArrayList<>();

	//Discretized list of functions for individual curves
	private XY_DataSetList functionList = new XY_DataSetList();

	//Discretized list of function for each fractile calculated
	private XY_DataSetList fractileList = new XY_DataSetList();

	//Discrrtized function to store the Mean function
	private AbstractXY_DataSet meanFunction;


	// Error Strings to be dispalyed
	private final static String ERROR_WEIGHTS =
		"Error! Number of weights should be equal to number of curves";

	private FractileCurveCalculator fractileCalc;

	/**
	 * list of fractions at which we need to calculate the fractiles
	 */
	private List<Double> fractionList = new ArrayList<>();

	//checks if mean fractile was calculated or not.
	private boolean isMeanFractileCalculated = false;

	//weighted function info
	private String info=null;

	public WeightedFuncList() {}

	/**
	 * Adds the list of relative weights and functionList to the existing list of
	 * relative wt list and list of function.
	 * It does not remove the existing dataset but adds new data to it.
	 * The size of relative Weight list should be equal to the number of functions
	 * in the functionList,
	 * @param relWts : ArrayList of doubles for the relative wts of each function
	 * in the list.
	 * @param funcList : List of individual functions
	 */
	public void addList(List<Double> relWts, XY_DataSetList funcList){

		int size = relWts.size();
		if(size != funcList.size()){
			throw new RuntimeException(ERROR_WEIGHTS);
		}

		//if the size of both list are same then add them to their corresponding lists.
		for(int i=0;i<size;++i){
			XY_DataSet function = funcList.get(i);
			if(isFuncAllowed(function))
				functionList.add(function);
			else
				throw new RuntimeException("Function not allowed");
			relativeWts.add(relWts.get(i));
		}
		//if person has already calculated the fractiles and then adding more functions to the
		//existing list then we need to calculate the fractiles again and remove the existing
		//list of fractiles. We recompute fractiles for all the functions in the list if
		//any new function is added.
		if(fractileList.size() >0){
			//creating new instance of the fraction list
			ArrayList<Double> list = new ArrayList<>();
			//adding all the contents of the existing fraction list to a new list becuase
			//we will clear the existing list.
			list.addAll(fractionList);
			addFractiles(list);
		}
		//if mean has already been calculated for the existing function list then on addition of
		//new function will result in automatic re-computation mean fractile.
		if(isMeanFractileCalculated)
			addMean();
		//creating the info string for the weighted function list
		setInfoForWeightedFunctionList();
	}

	/**
	 * this function sets the info in the Discretized Weighted Function List
	 */
	private void setInfoForWeightedFunctionList(){
		String funcListInfo = functionList.size()+
		" functions with relative weights: ";
		for(int i=0;i<relativeWts.size();++i)
			funcListInfo +=(Double)relativeWts.get(i)+", ";

		funcListInfo = funcListInfo.substring(0,funcListInfo.length()-2)+"\n";
		functionList.setInfo(funcListInfo);
//		int size = functionList.size();
		//setting the info for each function in the list
		//for(int i=0;i<size;++i)
		//functionList.get(i).setInfo("Dataset-"+(i+1)+" of "+size+"  with relative wt: "+
		//                          (Double)relativeWts.get(i));
	}

	/**
	 * Add Discretizedfunction and relative weight to the existing list of discretized functions
	 * and relative wt list.
	 *
	 * Note: If one has weighted list to be added, the rather adding one weighted function at
	 * a time , use the function addList() function to add the whole list, which will expediate
	 * the computation time.
	 * @param relWt
	 * @param func
	 */
	public void add(double relWt,DiscretizedFunc func){
		relativeWts.add(Double.valueOf(relWt));
		if(isFuncAllowed(func))
			functionList.add(func);
		else
			throw new RuntimeException("Function not allowed");

		//if person has already calculated the fractiles and then adding more functions to the
		//existing list then we need to calculate the fractiles again and remove the existing
		//list of fractiles. We recompute fractiles for all the functions in the list if
		//any new function is added.
		if(fractileList.size() >0){
			//creating new instance of the fraction list
			ArrayList<Double> list = new ArrayList<>();
			//adding all the contents of the existing fraction list to a new list becuase
			//we will clear the existing list.
			list.addAll(fractionList);
			addFractiles(list);
		}
		//if mean has already been calculated for the existing function list then on addition of
		//new function will result in automatic re-computation mean fractile.
		if(isMeanFractileCalculated)
			addMean();
		//creating the info string for the weighted function list
		setInfoForWeightedFunctionList();

	}



	/**
	 * Makes sure that each function has equal number of numPoints otherwise it will
	 * return false
	 * @param function
	 * @return
	 */
	public boolean isFuncAllowed(XY_DataSet function){
		// check  that all curves in list have same number of X values
		int listSize= functionList.size();
		if(listSize !=0){
			int numPoints = ((DiscretizedFunc)functionList.get(0)).size();
			if(function.size()!=numPoints)
				return false;
		}
		return true;
	}


	/**
	 * sets the fractile curve calculation
	 */
	private void setFractileCurveCalcuations(){
		if(fractileCalc ==null)
			fractileCalc = new FractileCurveCalculator(functionList,relativeWts);
		else
			fractileCalc.set(functionList,relativeWts);
	}


	/**
	 * This function saves the fraction for which fractile has to be computed.
	 * It then adds this calculated fractile in a DiscretizedFunctionList
	 * @param fraction
	 */
	public void addFractile(double fraction){
		addFractiles(Lists.newArrayList(fraction));
	}

	/**
	 * This function saves the list of fraction list for which fractile needed to be calculated.
	 * It then adds this calculated fractiles in a DiscretizedFunctionList.
	 * It will clear the existing fractile list, if it exists.
	 * @param fractionList: List of fraction (Doubles) for which we need to compute
	 * fractile.
	 */
	public void addFractiles(List<Double> list){
		int size  = list.size();
		//clearing the fractile list
		fractileList.clear();
		fractionList.clear();
		setFractileCurveCalcuations();
		for(int i=0;i<size;++i){
			fractionList.add(list.get(i));
			double fraction = ((Double)list.get(i)).doubleValue();
			fractileList.add(fractileCalc.getFractile(fraction));
		}
		//setting the fractiles info.
		setFractilesInfo();
	}



	/**
	 * Calculates mean fractile
	 * @return the mean fractile from the list of functions.
	 */
	public void addMean(){
		setFractileCurveCalcuations();
		meanFunction = fractileCalc.getMeanCurve();
		isMeanFractileCalculated = true;
		String meanInfo = "Mean\n";
		meanFunction.setInfo(meanInfo);
	}


	/**
	 * This function returns the weighted functions list without the
	 * fractile and mean functions.
	 * @return
	 */
	public XY_DataSetList getWeightedFunctionList(){
		return functionList;
	}

	/**
	 * This function returns the list of function for which fractiles were computed.
	 * @return
	 */
	public XY_DataSetList getFractileList(){
		if(fractileList.size() >0)
			return fractileList;
		return null;
	}

	/**
	 * This method returns list of values for which fractiles are computed
	 * @return
	 */
	public List<Double> getFractileValuesList(){
		return fractionList;
	}


	/**
	 *
	 * @return the mean fractile function if it was computed
	 */
	public AbstractXY_DataSet getMean(){
		if(isMeanFractileCalculated)
			return meanFunction;
		return null;
	}


	/**
	 *
	 * @return the relative weights array associated with each function in the list.
	 */
	public List<Double> getRelativeWtList(){
		return relativeWts;
	}

	/**
	 *
	 * @return the number of functions in the list with relative wts associated with them
	 */
	public int getNumWeightedFunctions(){
		return functionList.size();
	}

	/**
	 *
	 * @return total number of functions for which fractile was computed
	 * This number return does not take into account if mean fractile was calculated.
	 */
	public int getNumFractileFunctions(){
		return fractileList.size();
	}


	/**
	 *
	 * It clears all the fraction list for which fractiles were computed.
	 * It also clears teh fractile list.
	 *
	 * Once this function has been called, user has to give a new list of fraction
	 * for which fractiles are to be computed.
	 */
	public void removeAllFractiles(){
		fractionList.clear();
		fractileList.clear();
	}


	/**
	 * Remove the mean fractile and sets it to null.
	 */
	public void removeMean(){
		if(isMeanFractileCalculated){
			meanFunction= null;
			isMeanFractileCalculated = false;
		}
	}

	/**
	 * Clears the weighted functions in DiscretizedFunction List and also clears their
	 * associated relative weight list.
	 */
	public void clearWeightedFunctionList(){
		relativeWts.clear();
		functionList.clear();
	}


	/**
	 *
	 * @return boolean. true if mean curve was calculated and false if not.
	 */
	public boolean isMeanFunctionCalculated(){
		return isMeanFractileCalculated;
	}

	/**
	 * Sets the info related to this weighted function list
	 * @param info
	 */
	public void setInfo(String info){
		this.info = info;
	}

	/**
	 *
	 * @return the info associated with this weighted function list, otherwise
	 * return null.
	 */
	public String getInfo(){
		return info;
	}

	/**
	 * Sets the metadata for fractiles in the Weighted function list
	 */
	private void setFractilesInfo(){
		//creating and setting the info for the fractileList
		String fractileInfo = "Fractiles: ";
		for(int i=0;i<fractionList.size();++i)
			fractileInfo +=(Double)fractionList.get(i)+", ";

		fractileInfo = fractileInfo.substring(0,fractileInfo.length()-2)+"\n";
		fractileList.setInfo(fractileInfo);

//		int size = fractileList.size();
		//setting the info for each function in the list
		// for(int i=0;i<size;++i)
		// fractileList.get(i).setInfo("Fractile: "+(Double)fractionList.get(i));
	}

	/**
	 * Gets the metadata for fractiles in the Weighted function list
	 * @return
	 */
	public String getFractileInfo(){
		return fractileList.getInfo();
	}


	/**
	 *
	 * @return the info about number of functions in
	 * weighted function list and their weights.
	 */
	public String getFunctionTraceInfo(){
		return functionList.getInfo();
	}

	/**
	 *
	 * @return the info about the mean function in weighted function list.
	 */
	public String getMeanFunctionInfo(){
		return meanFunction.getInfo();
	}
}
