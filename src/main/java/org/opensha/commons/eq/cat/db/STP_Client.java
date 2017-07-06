package org.opensha.commons.eq.cat.db;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.commons.io.IOUtils;
import org.opensha.commons.eq.cat.MutableCatalog;

/**
 * STP connection manager.
 * 
 * @author Peter Powers
 * @version $Id: STP_Client.java 7478 2011-02-15 04:56:25Z pmpowers $
 */
public class STP_Client {

    private STP_Server server;
    private Socket socket;
    private PrintWriter send;
    private BufferedReader receive;
    private boolean debug;
    
    /**
     * Configures a new client to request data from the server provided.
     * @param server to fetch catalog data from
     * @param debug whether or not to print stp responses to stdout
     */
    public STP_Client(STP_Server server, boolean debug) {
        this.server = server;
        this.debug = debug;
    }
    
    /**
     * Get a catalog from the server.
     * 
     * @param request object
     * @return an Temblor earthquake catalog
     * @throws IOException if a connection error occurs or if the request is
     *      canceled while connection is still live
     * @throws UnknownHostException if the server is not accepting connections
     */
    public MutableCatalog getCatalog(STP_Request request) throws IOException,
            UnknownHostException {
        return getCatalog(request.toString());
    }
    
    /**
     * Get a catalog from the server. See the
     * <a href="http://www.scecdc.scec.org/STP/stp.html">STP Guide</a>
     * for details on request formatting.
     * 
     * @param request parameter string
     * @return an Temblor earthquake catalog
     * @throws IOException if a connection or transfer error occurs or if the 
     *      request is canceled while connection is still live
     * @throws UnknownHostException if the server is not accepting connections
     */
    public MutableCatalog getCatalog(String request) throws IOException,
            UnknownHostException {
        
        STP_Reader reader;
        DataOutputStream dout = null;
        String response;

        try {
            // init reader
            reader = new STP_Reader(server.getMaxEvents());
            
            // set up connection
            socket = new Socket(server.getAddress(), server.getPort());
            verboseOut("Socket: " + socket);
            socket.setSoTimeout(server.getTimeout());
            verboseOut("Timeout: " + server.getTimeout());
            send = new PrintWriter(socket.getOutputStream(), true);
            verboseOut("Output stream: " + send);
            receive = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            verboseOut("Input stream: " + receive);
            String pwd = server.getPassword();
            send.println(server.getPassword());
            verboseOut("Password >> : " + pwd);
            
            // validate connection
            response = receive.readLine();
            verboseOut("  <<" + response);
            if (response == null || !response.equals("CONNECTED")) {
                throw new IOException("STP connection rejected: " + response);
            }
            
            // establish byte order
            dout = new DataOutputStream(socket.getOutputStream());
            dout.writeInt(2);
            dout.flush();
            verboseOut("Byte order >> : " + 2);
            
            // read messages
            do {
                response = receive.readLine();
                verboseOut("  <<" + response);
            } while (response != null && !response.equals("OVER"));

            // Set max events (STP default is 100)
            String maxMessage = "SET NEVNTMAX " + server.getMaxEvents();
            send.println(maxMessage);
            verboseOut("Max events >> : " + maxMessage);
            
            // read messages
            do { 
                response = receive.readLine();
                verboseOut("  <<" + response);
            } while (response != null && !response.equals("OVER"));

            // send query
            String requestMod = request;
            requestMod += (request.endsWith("\n")) ? "" : "\n";
            send.flush();
            send.println(requestMod);
            verboseOut("Request >> : " + requestMod);
            
            // read message start
            response = receive.readLine();
            verboseOut("  <<" + response);

            // read events
            response = receive.readLine();
            while (!response.startsWith("#")) {
                verboseOut("  <<" + response);
                if (response.startsWith(" ")) {
                    reader.parseLine(response);
                }
                response = receive.readLine();
            }
            
            verboseOut("  <<" + response);
            verboseOut("====== Done ======");
            return reader.getCatalog();

        } catch (UnknownHostException uhe) {
            verboseOut("STP error (UH): " + uhe.getMessage());
            throw uhe;
        } catch (IOException ioe) { // cathing IO and UnkownHost
            verboseOut("STP error (IO): " + ioe.getMessage());
            throw ioe;
        } finally {
            if (socket != null) {
                socket.close();
                socket = null;
            }
            IOUtils.closeQuietly(send);
            IOUtils.closeQuietly(receive);
            IOUtils.closeQuietly(dout);
            send = null;
            receive = null;
        }
    }
    
    /**
     * Cancels the current catalog request if the underlying server 
     * connection is still live.
     */
    public void cancelRequest() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignore) {
        }
    }
    
    private void verboseOut(String message) {
        if (debug) System.out.println(message);
    }
}
