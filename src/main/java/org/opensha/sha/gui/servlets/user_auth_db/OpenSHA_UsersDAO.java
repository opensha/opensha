package org.opensha.sha.gui.servlets.user_auth_db;
import java.util.ArrayList;

public interface OpenSHA_UsersDAO extends java.io.Serializable {
  // Insert a new user to the database
   public boolean insertNewUser (OpenSHA_UsersVO newUserVO);
  // update a user info (change status  from Unapproved to approved, change password, etc)
   public void updateUser(OpenSHA_UsersVO updatedUserVO);
   // update the user password
   public void updatePassword(String email, String newPassword);
  // remove the user from the database
   public void deleteUser(String email);
   // checks whether username/password match is authorized to use the system. this makes sure that user is approved as well
   public OpenSHA_UsersVO getUserInfo(String userName, String password);
   // returns a list of all the users in the database
   public ArrayList getAllUsers();
   // returns a list of all the approved users in the database
   public ArrayList getApprovedUsers();
   // returns a list of unapproved usrs in the database
   public ArrayList getUnapprovedUsers();
   // check whther this username has already been used in the database
   public boolean isNameUsed(String userName);
   // get user info based on email address
   public OpenSHA_UsersVO getUser(String email);
   // reset the password for this user
   public void resetAndEmailPassword(String email);
   // email the username to the email address
   public void emailUserName(String email);
 // get the account statsu for user
   public boolean isUserAuthorized(OpenSHA_UsersVO userVO);

}
