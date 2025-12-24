package org.opensha.sha.calc.IM_EventSet;

import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * Writes sites from memory into files.
 * <p>
 * Takes a ParameterList of site data and uses the <code>SiteTranslator</code> map to
 * validate which parameters are applicable and writes in the specified order.
 * </p>
 */
public class SiteFileWriter {
    private final boolean lonFirst;
    private final ArrayList<String> siteDataTypes;
    private static final String SEP = " ";

    /**
     * Writes site data to files.
     * <p>
     * The file coordinates and ordered site data are determined at initialization.
     * </p>
     * @param lonFirst coordinates are written with longitude first if true
     * @param siteDataTypes ordered list of data to write for each site
     */
    public SiteFileWriter(boolean lonFirst, ArrayList<String> siteDataTypes) {
        this.lonFirst = lonFirst;
        this.siteDataTypes = siteDataTypes;
    }

    /**
     * Writes the given location and parameter data to the specified file.
     * @param locs
     * @param siteDataParams
     * @param file
     * @throws IOException
     */
    public void writeFile(ArrayList<Location> locs, ArrayList<ParameterList> siteDataParams, File file) throws IOException {
        if (locs.size() != siteDataParams.size()) {
           throw new RuntimeException("locs.size != siteDataParams.size");
        }

        try (FileWriter fileWriter = new FileWriter(file)) {
            // Write header
            fileWriter.write(SiteFileLoader.COM + SEP);
            if (lonFirst)
                fileWriter.write("Lon" + SEP + "Lat");
            else
                fileWriter.write("Lat" + SEP + "Lon");
            for (String dataType : siteDataTypes) {
                String label = dataType;
                if (label.equals(SiteData.TYPE_DEPTH_TO_1_0))
                    label = "Z1.0";
                else if (label.equals(SiteData.TYPE_DEPTH_TO_2_5))
                    label = "Z2.5";
                if (label.contains(SEP))
                    fileWriter.write(SEP + "\"" + label + "\"");
                else
                    fileWriter.write(SEP + label);
            }
            fileWriter.write("\n");
            // Write data rows
            for (int i = 0; i < locs.size(); i++) {
                ArrayList<Object> line = new ArrayList<>();
                Location loc = locs.get(i);
                if (lonFirst) {
                    line.add(loc.getLongitude());
                    line.add(loc.getLatitude());
                } else {
                    line.add(loc.getLatitude());
                    line.add(loc.getLongitude());
                }

                ParameterList siteData = siteDataParams.get(i);
                for (String siteDataType : siteDataTypes) {
                    // Cannot use SiteTranslator map as it is not a 1-1 mapping.
                    // if (SiteTranslator.DATA_TYPE_PARAM_NAME_MAP.isValidMapping(siteDataType, paramName)
                    if (siteDataType.equals(SiteData.TYPE_VS30) && siteData.containsParameter(Vs30_Param.NAME)) {
                       line.add(siteData.getValue(Vs30_Param.NAME));
                    } else if (siteDataType.equals(SiteData.TYPE_DEPTH_TO_2_5) && siteData.containsParameter(DepthTo2pt5kmPerSecParam.NAME)) {
                        line.add(siteData.getValue(DepthTo2pt5kmPerSecParam.NAME));
                    } else if (siteDataType.equals(SiteData.TYPE_DEPTH_TO_1_0) && siteData.containsParameter(DepthTo1pt0kmPerSecParam.NAME)) {
                        Double val = (Double) siteData.getValue(DepthTo1pt0kmPerSecParam.NAME);
                        if (val != null) val /= 1000;
                        line.add(val);
                    }
                }
                if (line.size() != (siteDataTypes.size()+2)) {
                    throw new RuntimeException("Site data values written mismatch requested types");
                }

                fileWriter.write(line.stream().map(String::valueOf).collect(Collectors.joining(SEP)));
                fileWriter.write("\n");
            }
        }
    }
}
