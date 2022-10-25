package org.opensha.refFaultParamDb.gui.event;

import java.util.EventObject;

/**
 * <p>Title: DbAdditionSuccessEvent.java </p>
 * <p>Description: This event is thrown whenenver new information is added
 * to the database using a GUI component. The listeners need to implement
 * the DbAdditionListener interface to listen to these events </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class DbAdditionSuccessEvent extends EventObject {
  public Object value; // VO object of the data inserted into the database

  /**
   * Constructor
   * @param source - component which created this event and sent to listeners
   * @param value -  object which contains values inserted into the database
   */
  public DbAdditionSuccessEvent(Object source, Object value) {
    super(source);
    this.value = value;
  }

  /**
   * Get the value. This object holds the values inserted into the database
   * @return
   */
  public Object getValue() {
    return this.value;
  }

}
