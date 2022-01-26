package org.opensha.refFaultParamDb.data;
/**
 * <p>Title: ExactTime.java </p>
 * <p>Description: This class hold the exact time. This time can be a event time
 * or a start/end time in a timespan</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class ExactTime extends TimeAPI {
  private int year, month, day, hour, minute, second;
  private String era;
  private boolean isNow;

  /**
   *
   * @param year
   * @param month
   * @param day
   * @param hour
   * @param minute
   * @param second
   * @param era
   */
  public ExactTime(int year, int month, int day, int hour, int minute, int second, String era,
                   boolean isNow) {
    // adjust month as gregorian calendar months go from 0 to 11
    this(year, month, day, hour, minute, second,isNow);
    setEra(era);
  }

  /**
   * Set whether this represents NOW
   * @param isNow
   */
  public void setIsNow(boolean isNow) {
    this.isNow = isNow;
  }

  /**
   * If this represesent NOW.
   * Returns true if it represents NOW
   * @return
   */
  public boolean getIsNow() {
    return this.isNow;
  }

  public String toString() {
    return "Year="+year+" "+ era+"\n"+
        "Month="+month+"\n"+
        "Day="+day+"\n"+
        "Hour="+hour+"\n"+
        "Minute="+minute+"\n"+
        "Second="+second+"\n"+
        "IsNow="+this.isNow+"\n"+
        super.toString();
  }


  /**
   *
   * @param year
   * @param month
   * @param day
   * @param hour
   * @param minute
   * @param second
   */
  public ExactTime(int year, int month, int day, int hour, int minute, int second, boolean isNow) {
   this.year = year;
   this.month = month;
   this.day = day;
   this.hour = hour;
   this.minute = minute;
   this.second = second;
   this.setIsNow(isNow);
  }

  public String getEra() {
    return era;
  }

  public void setEra(String era) {
    this.era = era;
  }

  public int getYear() {
    return this.year;
  }
  public int getMonth() {
     // adjust month as gregorian calendar months go from 0 to 11
    return this.month;
  }
  public int getDay() {
    return this.day;
  }
  public int getHour() {
    return this.hour;
  }
  public int getMinute() {
    return this.month;
  }
  public int getSecond() {
    return this.second;
  }

}
