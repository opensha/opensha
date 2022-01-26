package org.opensha.refFaultParamDb.excelToDatabase;

import java.util.ArrayList;
import java.util.StringTokenizer;

import org.opensha.refFaultParamDb.dao.db.DB_ConnectionPool;
import org.opensha.refFaultParamDb.dao.db.PaleoSiteDB_DAO;
import org.opensha.refFaultParamDb.dao.db.ReferenceDB_DAO;
import org.opensha.refFaultParamDb.vo.PaleoSite;
import org.opensha.refFaultParamDb.vo.PaleoSitePublication;

/**
 * <p>Title: ReadSitesFile.java </p>
 * <p>Description: This program reads the sites file and puts the sites into the database
 * </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class ReadSitesFile {
  private final static String FILENAME = "org\\opensha\\refFaultParamDb\\PaleoSites.txt";
  private PaleoSiteDB_DAO paleoSiteDAO = new PaleoSiteDB_DAO(DB_ConnectionPool.getDB2ReadOnlyConn());
  private ReferenceDB_DAO referenceDAO = new ReferenceDB_DAO(DB_ConnectionPool.getDB2ReadOnlyConn());
  private final static String COMMENTS_DEFAULT = "Site Information provided by Chris Wills from Excel file";
  private final static String STRAND_DEFAULT = "Unknown";
  private final static String SITE_TYPE_DEFAULT = "Unknown";
  private final static float  SITE_ELEVATION_DEFAULT = Float.NaN;
  private final static int REFERENCE_ID_DEFAULT = 6530; // id for WGCEP-2007 reference
  private final static String NONAME_SITENAME="no name";
  private final static int FAULT_SECTION_ID = 1;
  public ReadSitesFile() {
    try {
     ArrayList referencesList = org.opensha.commons.util.FileUtils.loadFile(FILENAME);
     String line;
     StringTokenizer tokenizer;
     for (int i = 0; i < referencesList.size(); ++i) {
       line = (String)referencesList.get(i);
       // make the paleoSite VO
       PaleoSite paleoSite = new PaleoSite();
       tokenizer = new StringTokenizer(line,"|");
       paleoSite.setOldSiteId(tokenizer.nextToken().trim());
       paleoSite.setSiteName(tokenizer.nextToken().trim());
       if(paleoSite.getSiteName().equalsIgnoreCase(NONAME_SITENAME))
         paleoSite.setSiteName(" ");
       paleoSite.setFaultSectionNameId(tokenizer.nextToken().trim(), FAULT_SECTION_ID);
       String references = tokenizer.nextToken().trim();
       paleoSite.setSiteLon1(Float.parseFloat(tokenizer.nextToken().trim()));
       paleoSite.setSiteLat1(Float.parseFloat(tokenizer.nextToken().trim()));
       paleoSite.setSiteElevation1(SITE_ELEVATION_DEFAULT);
       paleoSite.setSiteLon2(paleoSite.getSiteLon1());
       paleoSite.setSiteLat2(paleoSite.getSiteLat1());
       paleoSite.setSiteElevation2(SITE_ELEVATION_DEFAULT);
       paleoSite.setGeneralComments(COMMENTS_DEFAULT);
       ArrayList siteTypeNames = new ArrayList();
       siteTypeNames.add(SITE_TYPE_DEFAULT);
       PaleoSitePublication paleoSitePub = new PaleoSitePublication();
       paleoSitePub.setRepresentativeStrandName(STRAND_DEFAULT);
       paleoSitePub.setSiteTypeNames(siteTypeNames);
       paleoSitePub.setReference(referenceDAO.getReference(this.REFERENCE_ID_DEFAULT));
       ArrayList paleoSitePubList = new ArrayList();
       paleoSitePubList.add(paleoSitePub);
       paleoSite.setPaleoSitePubList(paleoSitePubList);
       //for(int j=0; j< referenceList.size(); ++j) {
       //  String rf = (String)referenceList.get(j);
       //  Reference ref = referenceDAO.getReference(rf);
       //  if(ref==null) {
       //    System.out.println(rf + " does not exist");
       //  }
       //}
      paleoSiteDAO.addPaleoSite(paleoSite);
     }
   }catch(Exception e) {
     e.printStackTrace();
   }

  }

}
