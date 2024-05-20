package org.opensha.refFaultParamDb.gui;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.impl.IntegerConstraint;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.impl.IntegerParameter;
import org.opensha.refFaultParamDb.data.ExactTime;
/**
 * <p>Title: ExactTimeGuiBean.java </p>
 * <p>Description: This GUI allows the user to enter all the information
 * so that user can enter all the information related to the Gregorian Calendar.</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class ExactTimeGuiBean extends ParameterListEditor{
    // Start-Time Parameters
    public final static String YEAR_PARAM_NAME = "Year";
    private IntegerParameter yearParam;
    private IntegerConstraint yearConstraint = new IntegerConstraint(0,Integer.MAX_VALUE);
    private final static Integer YEAR_PARAM_DEFAULT = Integer.valueOf(2005);
    public final static String MONTH_PARAM_NAME = "Month";
    private IntegerParameter monthParam;
    private IntegerConstraint monthConstraint = new IntegerConstraint(1,12);
    private final static Integer MONTH_PARAM_DEFAULT = Integer.valueOf(1);
    public final static String DAY_PARAM_NAME = "Day";
    private IntegerParameter dayParam;
    private final static Integer DAY_PARAM_DEFAULT = Integer.valueOf(1);
    private IntegerConstraint dayConstraint = new IntegerConstraint(1,31);
    public final static String HOUR_PARAM_NAME = "Hour";
    private IntegerParameter hourParam;
    private final static Integer HOUR_PARAM_DEFAULT = Integer.valueOf(0);
    private IntegerConstraint hourConstraint = new IntegerConstraint(0,59);
    public final static String MINUTE_PARAM_NAME = "Minute";
    private IntegerParameter minuteParam;
    private final static Integer MINUTE_PARAM_DEFAULT = Integer.valueOf(0);
    private IntegerConstraint minuteConstraint = new IntegerConstraint(0,59);
    public final static String SECOND_PARAM_NAME = "Second";
    private IntegerParameter secondParam;
    private final static Integer SECOND_PARAM_DEFAULT = Integer.valueOf(0);
    private IntegerConstraint secondConstraint = new IntegerConstraint(0,59);

  public ExactTimeGuiBean(String title) {
    initParamsList();
    this.addParameters();
    this.setTitle(title);
  }


  /**
   * Set the parameters for exact time
   * @param exactTime
   */
  public void setTime(ExactTime exactTime) {
    int year = exactTime.getYear();
    int month=exactTime.getMonth();
    int day=exactTime.getDay();
    int hour=exactTime.getHour();
    int minute=exactTime.getMinute();
    int second = exactTime.getSecond();

    // set year
    yearParam.setValue(Integer.valueOf(exactTime.getYear()));
    getParameterEditor(yearParam.getName()).refreshParamEditor();
    //set month
    if(month!=0){
      monthParam.setValue(Integer.valueOf(month));
      getParameterEditor(monthParam.getName()).refreshParamEditor();
    }
    //set day
    if(day!=0){
      dayParam.setValue(Integer.valueOf(day));
      getParameterEditor(dayParam.getName()).refreshParamEditor();
    }
    //set hour
    if(hour!=0){
      hourParam.setValue(Integer.valueOf(hour));
      getParameterEditor(hourParam.getName()).refreshParamEditor();
    }
    //set minute
    if(minute!=0){
      minuteParam.setValue(Integer.valueOf(minute));
      getParameterEditor(minuteParam.getName()).refreshParamEditor();
    }
     //set second
    if(second!=0){
      secondParam.setValue(Integer.valueOf(second));
      getParameterEditor(secondParam.getName()).refreshParamEditor();
    }

  }

  /**
   * Initialize the parameters
   */
  private void initParamsList() {
    parameterList = new ParameterList();
    yearParam = new IntegerParameter(YEAR_PARAM_NAME, yearConstraint, YEAR_PARAM_DEFAULT);
    monthConstraint.setNullAllowed(true);
    dayConstraint.setNullAllowed(true);
    hourConstraint.setNullAllowed(true);
    minuteConstraint.setNullAllowed(true);
    secondConstraint.setNullAllowed(true);
    monthParam = new IntegerParameter(MONTH_PARAM_NAME, monthConstraint, MONTH_PARAM_DEFAULT);
    dayParam = new IntegerParameter(DAY_PARAM_NAME, dayConstraint, DAY_PARAM_DEFAULT);
    hourParam = new IntegerParameter(HOUR_PARAM_NAME, hourConstraint,HOUR_PARAM_DEFAULT);
    minuteParam = new IntegerParameter(MINUTE_PARAM_NAME, minuteConstraint, MINUTE_PARAM_DEFAULT);
    secondParam = new IntegerParameter(SECOND_PARAM_NAME, secondConstraint, SECOND_PARAM_DEFAULT);
    parameterList.addParameter(yearParam);
    parameterList.addParameter(monthParam);
    parameterList.addParameter(dayParam);
    parameterList.addParameter(hourParam);
    parameterList.addParameter(minuteParam);
    parameterList.addParameter(secondParam);
  }

  /**
   * Return the exact time
   * @return
   */
  public ExactTime getExactTime() {
    int year =((Integer)yearParam.getValue()).intValue();
    int month=0, day=0, hour=0, minute=0, second=0;
    // month parameter value
    Integer monthParamVal = (Integer)monthParam.getValue();
    if(monthParamVal!=null) month = monthParamVal.intValue();
    // day parameter value
    Integer dayParamVal = (Integer)dayParam.getValue();
    if(dayParamVal!=null) day = dayParamVal.intValue();
    // hour parameter value
    Integer hourParamVal = (Integer)hourParam.getValue();
    if(hourParamVal!=null) hour = hourParamVal.intValue();
    // minute parameter value
    Integer minuteParamVal = (Integer)minuteParam.getValue();
    if(minuteParamVal!=null) minute = minuteParamVal.intValue();
    // second parameter value
    Integer secondParamVal = (Integer)secondParam.getValue();
    if(secondParamVal!=null) second = secondParamVal.intValue();
    ExactTime exactTime = new ExactTime(year, month, day, hour, minute, second, false);
    return exactTime;
  }

}
