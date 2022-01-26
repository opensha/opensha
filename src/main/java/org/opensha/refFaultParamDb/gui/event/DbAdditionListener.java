package org.opensha.refFaultParamDb.gui.event;

/**
 * <p>Title: DbAddtionListener.java </p>
 * <p>Description: Db Addetion Listener listens for the events whenever additional
 * information has been added to the database. This interface will typically be
 * implemented by GUIs so that parameters can be updated as soon as new information is
 * available in the database. </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface  DbAdditionListener {
  public void dbAdditionSuccessful(DbAdditionSuccessEvent event);
}
