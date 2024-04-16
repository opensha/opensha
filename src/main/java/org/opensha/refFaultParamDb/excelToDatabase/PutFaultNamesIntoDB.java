package org.opensha.refFaultParamDb.excelToDatabase;

import java.util.HashMap;
import java.util.Iterator;

import org.opensha.refFaultParamDb.dao.db.DB_ConnectionPool;
import org.opensha.refFaultParamDb.dao.db.FaultDB_DAO;
import org.opensha.refFaultParamDb.vo.Fault;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class PutFaultNamesIntoDB {
  private final static HashMap faultNameIdMapping = new HashMap();
  private final static FaultDB_DAO faultDAO = new FaultDB_DAO(DB_ConnectionPool.getDB2ReadOnlyConn());

  public PutFaultNamesIntoDB() {
    Iterator it = faultNameIdMapping.keySet().iterator();
    while(it.hasNext()) {
      String faultName = (String)it.next();
      Integer faultId = (Integer)faultNameIdMapping.get(faultName);
      faultDAO.addFault(new Fault(faultId.intValue(),faultName) );
    }
  }

  static {
    faultNameIdMapping.put("Bartlett Springs fault system", Integer.valueOf(29));
    faultNameIdMapping.put("Black Mountain fault zone", Integer.valueOf(142));
    faultNameIdMapping.put("Calaveras fault zone", Integer.valueOf(54));
    faultNameIdMapping.put("Camp Rock-Emerson fault zone", Integer.valueOf(114));
    faultNameIdMapping.put("Cleghorn fault zone", Integer.valueOf(108));
    faultNameIdMapping.put("Concord fault", Integer.valueOf(38));
    faultNameIdMapping.put("Cordelia fault zone", Integer.valueOf(219));
    faultNameIdMapping.put("Elsinore fault zone", Integer.valueOf(126));
    faultNameIdMapping.put("Fish Lake Valley fault zone", Integer.valueOf(49));
    faultNameIdMapping.put("Fish Slough fault", Integer.valueOf(48));
    faultNameIdMapping.put("Garlock fault zone", Integer.valueOf(69));
    faultNameIdMapping.put("Green Valley fault", Integer.valueOf(37));
    faultNameIdMapping.put("Greenville fault zone", Integer.valueOf(53));
    faultNameIdMapping.put("Greenville fault zone", Integer.valueOf(53));
    faultNameIdMapping.put("Hayward fault zone", Integer.valueOf(55));
    faultNameIdMapping.put("Healdsburg fault", Integer.valueOf(31));
    faultNameIdMapping.put("Helendale-South Lockhart fault zone",
                           Integer.valueOf(110));
    faultNameIdMapping.put("Hilton Creek fault", Integer.valueOf(44));
    faultNameIdMapping.put("Hollywood fault", Integer.valueOf(102));
    faultNameIdMapping.put("Homestead Valley fault zone", Integer.valueOf(116));
    faultNameIdMapping.put("Honey Lake fault zone", Integer.valueOf(22));
    faultNameIdMapping.put("Hunting Creek-Berryessa fault system",
                           Integer.valueOf(35));
    faultNameIdMapping.put("Imperial fault", Integer.valueOf(132));
    faultNameIdMapping.put("Johnson Valley fault zone", Integer.valueOf(115));
    faultNameIdMapping.put("Lavic Lake fault", Integer.valueOf(351));
    faultNameIdMapping.put("Lenwood-Lockhart fault zone", Integer.valueOf(111));
    faultNameIdMapping.put("Little Lake fault zone", Integer.valueOf(72));
    faultNameIdMapping.put("Little Salmon fault zone", Integer.valueOf(15));
    faultNameIdMapping.put("Los Osos fault zone", Integer.valueOf(79));
    faultNameIdMapping.put("Maacama fault zone", Integer.valueOf(30));
    faultNameIdMapping.put("Mad River fault zone", Integer.valueOf(13));
    faultNameIdMapping.put("Malibu Coast fault zone", Integer.valueOf(99));
    faultNameIdMapping.put("Mesquite Lake fault", Integer.valueOf(123));
    faultNameIdMapping.put("Mohawk Valley fault zone", Integer.valueOf(25));
    faultNameIdMapping.put("Mono Lake fault", Integer.valueOf(41));
    faultNameIdMapping.put("Monte Vista-Shannon fault zone", Integer.valueOf(56));
    faultNameIdMapping.put("Newport-Inglewood-Rose Canyon fault zone",
                           Integer.valueOf(127));
    faultNameIdMapping.put("North Frontal thrust system", Integer.valueOf(109));
    faultNameIdMapping.put("Ortigalita fault zone", Integer.valueOf(52));
    faultNameIdMapping.put("Owens Valley fault zone", Integer.valueOf(51));
    faultNameIdMapping.put("Owl Lake fault", Integer.valueOf(70));
    faultNameIdMapping.put("Palos Verdes fault zone", Integer.valueOf(128));
    faultNameIdMapping.put("Panamint Valley fault zone", Integer.valueOf(67));
    faultNameIdMapping.put("Pisgah-Bullion fault zone", Integer.valueOf(122));
    faultNameIdMapping.put("Pleito fault zone", Integer.valueOf(76));
    faultNameIdMapping.put("Raymond fault", Integer.valueOf(103));
    faultNameIdMapping.put("Rodgers Creek fault", Integer.valueOf(32));
    faultNameIdMapping.put("San Andreas fault zone", Integer.valueOf(1));
    faultNameIdMapping.put("San Gabriel fault", Integer.valueOf(89));
    faultNameIdMapping.put("San Gregorio fault zone", Integer.valueOf(60));
    faultNameIdMapping.put("San Jacinto fault zone", Integer.valueOf(125));
    faultNameIdMapping.put("San Simeon fault", Integer.valueOf(80));
    faultNameIdMapping.put("Santa Monica fault", Integer.valueOf(101));
    faultNameIdMapping.put("Santa Ynez fault zone", Integer.valueOf(87));
    faultNameIdMapping.put("Sargent fault zone", Integer.valueOf(58));
    faultNameIdMapping.put("Sierra Madre fault zone", Integer.valueOf(105));
    faultNameIdMapping.put("Simi-Santa Rosa fault zone", Integer.valueOf(98));
    faultNameIdMapping.put("Zayante-Vergeles fault zone", Integer.valueOf(59));
  }


}
