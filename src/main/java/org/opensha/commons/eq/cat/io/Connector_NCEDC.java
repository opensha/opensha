package org.opensha.commons.eq.cat.io;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

import org.apache.commons.io.IOUtils;

/**
 * Add comments here
 *
 * 
 * @author Peter Powers
 * @version $Id: Connector_NCEDC.java 7478 2011-02-15 04:56:25Z pmpowers $
 * 
 */
public class Connector_NCEDC {

    public static void main(String[] args) {
        
        //NCSN test
        // <FORM METHOD="POST" ACTION="http://www.ncedc.org/cgi-bin/catalog-search2.pl">
        // <INPUT TYPE="radio" NAME="format" VALUE="ncread" CHECKED>
        // <INPUT TYPE="radio" NAME="format" VALUE="ncraw">
        // <INPUT TYPE="radio" NAME="format" VALUE="ncphraw">
        // <INPUT TYPE="radio" NAME="format" VALUE="ncfpraw">
        
        // <INPUT NAME="mintime" SIZE=22 VALUE="2002/01/01,00:00:00"> yyyy/mm/dd,HH:MM:SS    UTC
        // <INPUT NAME="maxtime" SIZE=22>
        
        // <INPUT NAME="minmag" SIZE=5 VALUE="3.0"> 
        // <INPUT NAME="maxmag" SIZE=5>
        
        // <INPUT NAME="mindepth" SIZE=7>
        // <INPUT NAME="maxdepth" SIZE=7>
        
        // <INPUT NAME="minlat" SIZE=8>   
        // <INPUT NAME="maxlat" SIZE=8>
        
        // <INPUT NAME="minlon" SIZE=8>   
        // <INPUT NAME="maxlon" SIZE=8> 
        
        // <INPUT TYPE="radio" NAME="etype" VALUE="E" CHECKED>Earthquakes
        // <INPUT TYPE="radio" NAME="etype" VALUE="B">Blasts (Quarry or Nuclear)
        // <INPUT TYPE="radio" NAME="etype" VALUE="A">All Events

        // Include Events with no reported Magnitude:
        // <INPUT TYPE="checkbox" NAME="no_mag" VALUE="no_mag">
        
        // <INPUT TYPE=RADIO NAME=outputloc VALUE=web CHECKED>
        // <INPUT TYPE=RADIO NAME=outputloc VALUE=ftp>
        // <INPUT NAME="searchlimit" SIZE=10 VALUE=10000>
        
        // <INPUT TYPE="submit" VALUE="Submit request">
        // <INPUT TYPE="reset" VALUE="Reset Fields To Default Values">

        OutputStreamWriter wr = null;
        BufferedReader rd = null;
        
        try {
            // Construct data
            String data =
                URLEncoder.encode("format", "UTF-8") + "=" + 
                URLEncoder.encode("ncfpraw", "UTF-8") + "&" +
                
                URLEncoder.encode("mintime", "UTF-8") + "=" + 
                URLEncoder.encode("2002/01/01,00:00:00", "UTF-8") + "&" +
                URLEncoder.encode("maxtime", "UTF-8") + "=" + 
                URLEncoder.encode("2004/01/01,00:00:00", "UTF-8") + "&" +
                URLEncoder.encode("minmag", "UTF-8") + "=" + 
                URLEncoder.encode("4.8", "UTF-8")  + "&" +
        
                URLEncoder.encode("etype", "UTF-8") + "=" + 
                URLEncoder.encode("E", "UTF-8") + "&" +
                URLEncoder.encode("searchlimit", "UTF-8") + "=" + 
                URLEncoder.encode("50", "UTF-8");
                
            // Send data
            URL url = new URL("http://www.ncedc.org/cgi-bin/catalog-search2.pl");
            URLConnection conn = url.openConnection();
            //if 
            conn.setDoOutput(true);
            wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(data);
            wr.flush();
        
            // Get the response
            rd = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            
            String line;
            
            while (rd.ready()) {
                rd.readLine();
            }
            while ((line = rd.readLine()) != null) {
                System.out.println(rd.ready());
                System.out.println(line);
                
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
        } finally {
            IOUtils.closeQuietly(wr);
            IOUtils.closeQuietly(rd);
        }
    }
}
