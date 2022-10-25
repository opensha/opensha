package org.opensha.refFaultParamDb.gui.infotools;

import org.opensha.refFaultParamDb.dao.db.ContributorDB_DAO;
import org.opensha.refFaultParamDb.dao.db.DB_ConnectionPool;
import org.opensha.refFaultParamDb.vo.Contributor;

/**
 * <p>Title: SessionInfo.java </p>
 * <p>Description: Saves  the information about the current session like username,
 * password, contributorId, etc. </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class SessionInfo {
	private static String userName;
	private static String password;
	private static Contributor contributor;

	/**
	 * Set the username typed by the user in the login window. This username is used
	 * for db connection
	 * @param userName
	 */
	public static void setUserName(String userName) {
		SessionInfo.userName = userName;
	}
	/**
	 * Get the username to be used for DB connection
	 * @return
	 */
	public static String getUserName() { return userName; }
	/**
	 * Set the password for making the database connection
	 * @param password
	 */
	public static void setPassword(String password) {
		SessionInfo.password = password;
	}
	/**
	 * Get the password
	 * @return
	 */
	public static String getPassword() { return password; }

	/**
	 * Get the encrypted password
	 * @return
	 */
	public static String getEncryptedPassword() { return ContributorDB_DAO.getEnryptedPassword(password); }

	/**
	 * Get the contributor info from database based on username and password
	 */
	public static void setContributorInfo() {
		ContributorDB_DAO contributorDAO = new ContributorDB_DAO(DB_ConnectionPool.getLatestReadOnlyConn());
		contributor = contributorDAO.getValidatedContributor(userName, password);
	}

	/**
	 * Get the contributor info
	 *
	 * @return
	 */
	public static Contributor getContributor() {
		return contributor;
	}

}
