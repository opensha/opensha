package org.opensha.sha.gui.infoTools;


import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import org.opensha.commons.geo.LocationList;
import org.opensha.sha.gui.servlets.siteEffect.BasinDepthClass;
import org.opensha.sha.gui.servlets.siteEffect.WillsSiteClass;

/**
 * <p>Title: ConnectToCVM</p>
 * <p>Description: This class connects to the CVM servlets to get the values for the
 * WillsSiteClass and SCEC Basin Depth</p>
 * @author : Edward (Ned) Field, Nitin Gupta and Vipin Gupta
 * @version 1.0
 */
@Deprecated
public final class ConnectToCVM {


  /**
   * Gets the Wills et al. Site Type (2000) Map for each gridded site from the file on the local computer
   * @param lonMin
   * @param lonMax
   * @param latMin
   * @param latMax
   * @param gridSpacing
   * @param fileName : Name of the Wills Site Class file to be read from the local computer
   * @return
   * @throws Exception
   */
  public static ArrayList getWillsSiteType(double minLon,double maxLon,double minLat,double maxLat,
                              double gridSpacing, String fileName) {

    //creating the objct for the Wills Site Class
    WillsSiteClass willsSiteClass = new  WillsSiteClass(minLon, maxLon, minLat, maxLat, gridSpacing,fileName);
    return willsSiteClass.getWillsSiteClass();
  }



  /**
   * Gets the Basin Depth Values for each gridded site from the file on the local computer
   * @param lonMin
   * @param lonMax
   * @param latMin
   * @param latMax
   * @param gridSpacing
   * @param fileName : Name of the Basin Depth file to be read from the local computer
   * @return
   * @throws Exception
   */
  public static ArrayList getBasinDepth(double minLon,double maxLon,double minLat,double maxLat,
                              double gridSpacing, String fileName) {

    //creating the object for the Basin Depth Class
    BasinDepthClass basinDepthClass = new  BasinDepthClass(minLon, maxLon, minLat, maxLat, gridSpacing,fileName);
    return basinDepthClass.getBasinDepth();
  }


  /**
   * Gets the Wills et al. Site Type (2000) Map Web Service from the CVM servlet
   */
  public static ArrayList getWillsSiteTypeFromCVM (double lonMin,double lonMax,double latMin,double latMax,
                              double gridSpacing) throws Exception{
    ArrayList vs30 = null;
    // if we want to the paramter from the servlet


    // make connection with servlet
    URL cvmServlet = new URL("http://gravity.usc.edu/OpenSHA/servlet/WillsSiteClassServlet");
    URLConnection servletConnection = cvmServlet.openConnection();

    servletConnection.setDoOutput(true);

    // Don't use a cached version of URL connection.
    servletConnection.setUseCaches (false);
    servletConnection.setDefaultUseCaches (false);

    // Specify the content type that we will send binary data
    servletConnection.setRequestProperty ("Content-Type", "application/octet-stream");

    // send the student object to the servlet using serialization
    ObjectOutputStream outputToServlet = new ObjectOutputStream(servletConnection.getOutputStream());


    outputToServlet.writeObject(Double.valueOf(lonMin));
    outputToServlet.writeObject(Double.valueOf(lonMax));
    outputToServlet.writeObject(Double.valueOf(latMin));
    outputToServlet.writeObject(Double.valueOf(latMax));
    outputToServlet.writeObject(Double.valueOf(gridSpacing));

    outputToServlet.flush();
    outputToServlet.close();

    // now read the connection again to get the vs30 as sent by the servlet
    ObjectInputStream ois=new ObjectInputStream(servletConnection.getInputStream());
    //ArrayList of Wills Site Class Values translated from the Vs30 Values.
    vs30=(ArrayList)ois.readObject();
    ois.close();

    return vs30;
  }



  /**
   * Gets the Wills et al. Site Type (2000) Map Web Service from the CVM servlet
   */
  public static ArrayList getWillsSiteTypeFromCVM (LocationList locList) throws Exception{
    ArrayList willsSiteClass = null;

    // make connection with servlet
    URL cvmServlet = new URL("http://gravity.usc.edu/OpenSHA/servlet/WillsSiteClassForGriddedRegionServlet");
    URLConnection servletConnection = cvmServlet.openConnection();

    servletConnection.setDoOutput(true);

    // Don't use a cached version of URL connection.
    servletConnection.setUseCaches (false);
    servletConnection.setDefaultUseCaches (false);

    // Specify the content type that we will send binary data
    servletConnection.setRequestProperty ("Content-Type", "application/octet-stream");

    // send the student object to the servlet using serialization
    ObjectOutputStream outputToServlet = new ObjectOutputStream(servletConnection.getOutputStream());


    outputToServlet.writeObject(locList);

    outputToServlet.flush();
    outputToServlet.close();

    // now read the connection again to get the vs30 as sent by the servlet
    ObjectInputStream ois=new ObjectInputStream(servletConnection.getInputStream());
    //ArrayList of Wills Site Class Values translated from the Vs30 Values.
    willsSiteClass=(ArrayList)ois.readObject();
    ois.close();

    return willsSiteClass;
  }





  /**
   * Gets the Basin Depth from the CVM servlet
   */
  public static ArrayList getBasinDepthFromCVM(double lonMin,double lonMax,double latMin,double latMax,
                                    double gridSpacing) throws Exception{
    ArrayList basinDepth = null;
    // if we want to the paramter from the servlet

    // make connection with servlet
    URL cvmServlet = new URL("http://gravity.usc.edu/OpenSHA/servlet/SCEC_BasinDepthServlet");
    URLConnection servletConnection = cvmServlet.openConnection();

    servletConnection.setDoOutput(true);

    // Don't use a cached version of URL connection.
    servletConnection.setUseCaches (false);
    servletConnection.setDefaultUseCaches (false);

    // Specify the content type that we will send binary data
    servletConnection.setRequestProperty ("Content-Type", "application/octet-stream");

    // send the student object to the servlet using serialization
    ObjectOutputStream outputToServlet = new ObjectOutputStream(servletConnection.getOutputStream());

    outputToServlet.writeObject(Double.valueOf(lonMin));
    outputToServlet.writeObject(Double.valueOf(lonMax));
    outputToServlet.writeObject(Double.valueOf(latMin));
    outputToServlet.writeObject(Double.valueOf(latMax));
    outputToServlet.writeObject(Double.valueOf(gridSpacing));

    outputToServlet.flush();
    outputToServlet.close();

    // now read the connection again to get the vs30 as sent by the servlet
    ObjectInputStream ois=new ObjectInputStream(servletConnection.getInputStream());

    //vectors of Basin Depth for each gridded site
    basinDepth=(ArrayList)ois.readObject();
    ois.close();


    return basinDepth;
 }


 /**
  * Gets the Basin Depth from the CVM servlet
  */
 public static ArrayList getBasinDepthFromCVM(LocationList locList)
     throws Exception{
   ArrayList basinDepth = null;
   // if we want to the paramter from the servlet

   // make connection with servlet
   URL cvmServlet = new URL("http://gravity.usc.edu/OpenSHA/servlet/SCEC_BasinDepthForGriddedRegionServlet");
   URLConnection servletConnection = cvmServlet.openConnection();

   servletConnection.setDoOutput(true);
   // Don't use a cached version of URL connection.
   servletConnection.setUseCaches (false);
   servletConnection.setDefaultUseCaches (false);

   // Specify the content type that we will send binary data
   servletConnection.setRequestProperty ("Content-Type", "application/octet-stream");

   // send the student object to the servlet using serialization
   ObjectOutputStream outputToServlet = new ObjectOutputStream(servletConnection.getOutputStream());

   outputToServlet.writeObject(locList);

   outputToServlet.flush();
   outputToServlet.close();

   // now read the connection again to get the vs30 as sent by the servlet
   ObjectInputStream ois=new ObjectInputStream(servletConnection.getInputStream());

   //vectors of Basin Depth for each gridded site
   basinDepth=(ArrayList)ois.readObject();
   ois.close();
   return basinDepth;
 }


}
