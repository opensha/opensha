package org.opensha.sha.gui.servlets.user_auth_db;

/**
 * <p>Title: OpenSHA_UsersVO.java </p>
 * <p>Description: This class will contain the information for interaction
 * with the OpenSHA new usrs database.</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author Vipin Gupta, Nitin Gupta
 * @date Nov 15, 2004
 * @version 1.0
 */

public class OpenSHA_UsersVO {
  public final static char NOT_APPROVED = 'N';
  public final static char APPROVED = 'Y';
  public final static String ROLE_ADMIN = "admin";
  public final static String ROLE_USER = "user";

  // fields which exist in the database as well
  private String first_name="", last_name="", email="", phone_number="", organization="", username="", password="";
  private String creation_date="", approval_date="", modification_date="";
  private char approved=NOT_APPROVED;
  private String role=ROLE_USER;

  // get the value of the columns in the table
  public String getFirstName() { return first_name; }
  public String getLastName() { return last_name; }
  public String getEmail() { return email; }
  public String getPhone() { return phone_number; }
  public String getOrganization() { return organization; }
  public String getUsername() { return username; }
  public char getApprovalStatus() { return approved; }
  public String getCreationDate() { return this.creation_date; }
  public String getApprovalDate() { return this.approval_date; }
  public String getModificationDate() { return this.modification_date; }
  public String getPassword() { return this.password; }
  public String getRole() { return role; }

  // get the value of the columns in the table
  public void setFirstName(String str) {  first_name = str; }
  public void setLastName(String str) {  last_name = str ; }
  public void setEmail(String str) {  email = str; }
  public void setPhone(String str) {  phone_number = str; }
  public void setOrganization(String str) {   organization = str; }
  public void setUsername(String str) {  username = str; }
  public void setPassword(String str) {  password= str; }
  public void setApprovalStatus(char approvalStatus) { approved = approvalStatus; }
  public void setCreationDate(String date) { creation_date = date; }
  public void setApprovalDate(String date) { approval_date = date; }
  public void setModificationDate(String date) { modification_date = date; }
  public void setRole(String role) { this.role = role; }

}
