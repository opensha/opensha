package org.opensha.refFaultParamDb.gui.infotools;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.net.URLConnection;

import org.opensha.refFaultParamDb.servlets.RefFaultDB_UpdateEmailServlet;

/**
 * <p>Title: ConnectToEmailServlet.java </p>
 * <p>Description: Connect to email servlet to email whenever an addition/deletion/update
 * is done to the database. </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class ConnectToEmailServlet {

  /**
   * Send email to database curator whenever a data is addded/removed/updated
   * from the database.
   *
   * @param message
   */
  public final static void sendEmail(String message) {
    try {
      URL emailServlet = new URL(RefFaultDB_UpdateEmailServlet.SERVLET_ADDRESS);

      URLConnection servletConnection = emailServlet.openConnection();

      // inform the connection that we will send output and accept input
      servletConnection.setDoInput(true);
      servletConnection.setDoOutput(true);
      // Don't use a cached version of URL connection.
      servletConnection.setUseCaches(false);
      servletConnection.setDefaultUseCaches(false);
      // Specify the content type that we will send binary data
      servletConnection.setRequestProperty("Content-Type",
                                           "application/octet-stream");
      ObjectOutputStream toServlet = new
          ObjectOutputStream(servletConnection.getOutputStream());
      //sending the email message
      toServlet.writeObject(message);
      toServlet.flush();
      toServlet.close();

      ObjectInputStream fromServlet = new
          ObjectInputStream(servletConnection.getInputStream());

      String outputFromServlet = (String) fromServlet.readObject();
      fromServlet.close();
    }catch(Exception e) {
      e.printStackTrace();
    }
  }
}
