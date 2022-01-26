package org.opensha.refFaultParamDb.excelToDatabase;

import java.util.ArrayList;

import org.opensha.refFaultParamDb.dao.db.DB_AccessAPI;
import org.opensha.refFaultParamDb.dao.db.DB_ConnectionPool;
import org.opensha.refFaultParamDb.dao.db.ReferenceDB_DAO;
import org.opensha.refFaultParamDb.dao.db.ServerDB_Access;
import org.opensha.refFaultParamDb.gui.infotools.SessionInfo;
import org.opensha.refFaultParamDb.vo.Reference;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class ReadReferencesFromGoldenDB {
  private final static DB_AccessAPI dbConnPas= new DB_ConnectionPool();
  private final static DB_AccessAPI dbConnGolden = new ServerDB_Access(null);

  public ReadReferencesFromGoldenDB() {
  }

  public static void main(String[] args) {
    SessionInfo.setUserName("");
    SessionInfo.setPassword("");
    ReferenceDB_DAO refGoldenDAO = new ReferenceDB_DAO(dbConnGolden);
    ArrayList refList = refGoldenDAO.getAllReferences();
    ReferenceDB_DAO refPasDAO = new ReferenceDB_DAO(dbConnPas);
    for(int i=0; i<refList.size(); ++i) {
      System.out.println(i+" of "+refList.size());
      try {
        refPasDAO.addReference( (Reference) refList.get(i));
      }catch(Exception e) {
        System.out.println("Error in "+i);
      }
    }
  }

}
