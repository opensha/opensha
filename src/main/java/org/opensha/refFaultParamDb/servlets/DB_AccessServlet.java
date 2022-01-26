package org.opensha.refFaultParamDb.servlets;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.rowset.CachedRowSet;

import oracle.spatial.geometry.JGeometry;

import org.opensha.commons.util.MailUtil;
import org.opensha.commons.util.MailUtil.MailProps;
import org.opensha.refFaultParamDb.dao.db.ContributorDB_DAO;
import org.opensha.refFaultParamDb.dao.db.DB_ConnectionPool;
import org.opensha.refFaultParamDb.dao.db.DB_AccessAPI;
import org.opensha.refFaultParamDb.dao.db.PrefFaultSectionDataDB_DAO;
import org.opensha.refFaultParamDb.dao.db.ServerDB_Access;
import org.opensha.refFaultParamDb.dao.db.SpatialQueryResult;
import org.opensha.refFaultParamDb.dao.exception.DBConnectException;

/**
 * <p>Title: DB_AccessServlet</p>
 *
 * <p>Description: Creates a two-tier database connection pool that can be shared.</p>
 *
 * @author Edward (Ned) Field, Vipin Gupta, Nitin Gupta
 * @version 1.0
 */
public class DB_AccessServlet extends HttpServlet{
	//GOLDEN: jdbc:oracle:thin:@gldwebdb.cr.usgs.gov:1521:EQCATS
	//PASADENA: jdbc:oracle:thin:@iron.gps.caltech.edu:1521:irondb
	
	/**
	 * these are the opterations allowed for an unauthenticated user
	 */
	private static ArrayList<String> allowedGuestOperations;
	
	static {
		allowedGuestOperations = new ArrayList<String>();
		allowedGuestOperations.add(DB_AccessAPI.RESET_PASSWORD);
		allowedGuestOperations.add(DB_AccessAPI.SELECT_QUERY);
		allowedGuestOperations.add(DB_AccessAPI.SELECT_QUERY_SPATIAL);
	}
	
	private MailProps props = null;

	private final static String CONNECT_FAILURE_MSG = "Connection to the database server failed.\nCheck username/password or try again later";
	private final static String PROP_NAME = "DbConnectionPropertiesFileName";
	private DB_AccessAPI myBroker;
	private ContributorDB_DAO contributorDAO;
	private PrefFaultSectionDataDB_DAO prefDataDAO;
	public void init() throws ServletException {
		try {
			Properties p = new Properties();
			String fileName = getInitParameter(PROP_NAME);
			p.load(new FileInputStream(fileName));
			String dbDriver = (String) p.get("dbDriver");
			String dbServer = (String) p.get("dbServer");
			int minConns = Integer.parseInt( (String) p.get("minConns"));
			int maxConns = Integer.parseInt( (String) p.get("maxConns"));
			String logFileString = (String) p.get("logFileString");
			double maxConnTime =
				(new Double( (String) p.get("maxConnTime"))).doubleValue();
			String usrName = (String) p.get("userName");
			String password = (String)p.get("password");
			myBroker = new
			DB_ConnectionPool(dbDriver, dbServer, usrName, password,
					minConns, maxConns, logFileString, maxConnTime);
			contributorDAO = new ContributorDB_DAO(myBroker);
			
			String emailFileName = getInitParameter(RefFaultDB_UpdateEmailServlet.CONFIG_NAME);
			props = MailUtil.loadMailPropsFromFile(emailFileName);
		}
		catch (FileNotFoundException f) {f.printStackTrace();}
		catch (IOException e) {e.printStackTrace();}
		catch (Exception e) { e.printStackTrace(); }
	}

	/**
	 *
	 * @param request HttpServletRequest
	 * @param response HttpServletResponse
	 * @throws ServletException
	 * @throws IOException
	 */
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws
	ServletException, IOException {
		
		if (myBroker == null)
			init();

		// get an input stream from the applet
		ObjectInputStream inputFromApp = new ObjectInputStream(request.
				getInputStream());
		ObjectOutputStream outputToApp = new ObjectOutputStream(response.
				getOutputStream());
		try {
			// get the username
			String user = (String)inputFromApp.readObject();
			// get the password
			String pass = (String)inputFromApp.readObject();
			//receiving the name of the Function to be performed
			String functionToPerform = (String) inputFromApp.readObject();
			
//			System.out.println("DB_AccessServlet: handling requst '"+functionToPerform
//					+"' from user: "+user+" (pass is null? "+(pass == null)+")");
			
			boolean valid;
			if (user == null || pass == null || user.isEmpty() || pass.isEmpty()) {
				valid = false;
			} else {
				valid = contributorDAO.isContributorValid(user, pass);
				
				if (!valid)
					System.out.println("DB_AccessServlet: attempting to connect with invalid credentials: "+user);
			}
			
			// if this isn't a valid contributor and it's not a guest operation, throw an error
			if (!valid && !allowedGuestOperations.contains(functionToPerform)) {
				inputFromApp.close();
				DBConnectException exception =  new DBConnectException(CONNECT_FAILURE_MSG);
				outputToApp.writeObject(exception);
				outputToApp.close();
				return;
			}

			//receiving the query
			Object query = inputFromApp.readObject();

			/**
			 * Checking the type of function that needs to be performed in the database
			 */
			//getting the sequence number from Data table
			if(functionToPerform.equals(DB_AccessAPI.SEQUENCE_NUMBER)){
				int seqNo = myBroker.getNextSequenceNumber((String)query);
				outputToApp.writeObject(new Integer(seqNo));
			}
			//inserting new data in the table
			else if(functionToPerform.equals(DB_AccessAPI.INSERT_UPDATE_QUERY)){
//				System.out.println("DB Servlet Insert:\n"+query);
				if (query instanceof ArrayList) {
					ArrayList<String> sqls = (ArrayList<String>)query;
					Boolean rollbackOnFail = (Boolean)inputFromApp.readObject();
					int[] ret = myBroker.insertUpdateOrDeleteBatch(sqls, rollbackOnFail);
					outputToApp.writeObject(ret);
				} else {
					int key = myBroker.insertUpdateOrDeleteData((String)query);
					outputToApp.writeObject(new Integer(key));
				}
				
			}
			// inserting spatial data into database
			else if(functionToPerform.equals(DB_AccessAPI.INSERT_UPDATE_SPATIAL)){
				ArrayList<JGeometry> geomteryObjectList = (ArrayList<JGeometry>)inputFromApp.readObject();
				int key = myBroker.insertUpdateOrDeleteData((String)query, geomteryObjectList);
				outputToApp.writeObject(new Integer(key));
			}
			//reading the data form the database
			else if(functionToPerform.equals(DB_AccessAPI.SELECT_QUERY)){
				CachedRowSet resultSet= myBroker.queryData((String)query);
				outputToApp.writeObject(resultSet);
			}
			//reading the data form the database
			else if(functionToPerform.equals(DB_AccessAPI.SELECT_QUERY_SPATIAL)){
				String sqlWithNoSaptialColumnNames = (String)inputFromApp.readObject();
				ArrayList<String> geomteryObjectList = (ArrayList<String>)inputFromApp.readObject();
				SpatialQueryResult resultSet= myBroker.queryData((String)query,
						sqlWithNoSaptialColumnNames, geomteryObjectList);
				outputToApp.writeObject(resultSet);
			}
			// reset the password
			else if(functionToPerform.equalsIgnoreCase(DB_AccessAPI.RESET_PASSWORD)) {
				String email = (String)query;
				String randomPass = ContributorDB_DAO.getRandomPassword();
				String encr = ContributorDB_DAO.getEnryptedPassword(randomPass);
				System.out.println("New random encr pw: " + encr);
				query = "update "+ContributorDB_DAO.TABLE_NAME+" set "+ContributorDB_DAO.PASSWORD+"= '"+
				encr+"' where "+ContributorDB_DAO.EMAIL+"='"+email+"'";
				int key = myBroker.insertUpdateOrDeleteData((String)query);
				props.setEmailTo(email);
				props.setEmailSubject("Login information in CA Ref Fault Param GUI");
				String userName = contributorDAO.getContributorByEmail(email).getName();
				String emailMessage = "Account info - "+"\n"+
				"user name: "+userName+"\n"+
				"Password: "+randomPass+"\n";
				if (key > 0) {
					MailUtil.sendMail(props,emailMessage);
				}
				outputToApp.writeObject(new Integer(key));
			} else if(functionToPerform.equalsIgnoreCase(ServerDB_Access.UPDATE_ALL_PREF_DATA)) {
				if (prefDataDAO == null)
					prefDataDAO = new PrefFaultSectionDataDB_DAO(myBroker);
				prefDataDAO.rePopulatePrefDataTable();
				outputToApp.writeObject(new Boolean(true));
			}
			inputFromApp.close();
			outputToApp.close();
		}
		catch(SQLException e){
			outputToApp.writeObject(e);
			outputToApp.close();
		}
		catch (ClassNotFoundException ex) {
			ex.printStackTrace();
		}
		catch (IOException ex) {
			ex.printStackTrace();
		}catch(Exception ex) {
			ex.printStackTrace();
		}
	}



	//Process the HTTP Post request
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws
	ServletException, IOException {
		// call the doPost method
		doGet(request, response);
	}

}
