package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture;

import com.google.common.base.Preconditions;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.TextBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCache;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;

import java.io.*;

public class StiffnessCalcModule implements TextBackedModule, SubModule<FaultSystemRupSet> {

    // stiffness calculation constants
    double lameLambda = 3e4;
    double lameMu = 3e4;
    double coeffOfFriction = 0.5;
    // stiffness grid spacing, increase if it's taking too long
    double stiffGridSpacing = 2d;

    File cacheDir = new File("/tmp");
    int stiffnessCacheSize = 0;

    FaultSystemRupSet rupSet;
    File stiffnessCacheFile;
    public SubSectStiffnessCalculator stiffnessCalc;
    AggregatedStiffnessCache stiffnessCache;

    public StiffnessCalcModule() {
    }

    public StiffnessCalcModule(FaultSystemRupSet rupSet, double stiffGridSpacing) {
        this.rupSet = rupSet;
        this.stiffGridSpacing = stiffGridSpacing;
        createStiffnessCalc();
    }

    public SubSectStiffnessCalculator getStiffnessCalculator() {
        return stiffnessCalc;
    }

    protected void createStiffnessCalc() {

        stiffnessCalc = new SubSectStiffnessCalculator(
                rupSet.getFaultSectionDataList(),
                stiffGridSpacing,
                lameLambda,
                lameMu,
                coeffOfFriction,
                SubSectStiffnessCalculator.PatchAlignment.FILL_OVERLAP,
                1d);
        stiffnessCache = stiffnessCalc.getAggregationCache(SubSectStiffnessCalculator.StiffnessType.CFF);

        if ((cacheDir != null && cacheDir.exists()) || stiffnessCacheFile != null) {
            if (stiffnessCacheFile == null) {
                stiffnessCacheFile = new File(cacheDir, stiffnessCache.getCacheFileName());
            } else {
                Preconditions.checkState(stiffnessCacheFile.exists());
            }
            stiffnessCacheSize = 0;
            if (stiffnessCacheFile.exists()) {
                try {
                    stiffnessCacheSize = stiffnessCache.loadCacheFile(stiffnessCacheFile);
                } catch (IOException e) {
                    System.err.println("WARNING: exception loading previous cache");
                    e.printStackTrace();
                }
            } else {
                System.out.println("Will cache to: " + stiffnessCacheFile.getAbsolutePath());
            }
        }
    }

    public void checkUpdateStiffnessCache() {
        if (stiffnessCacheFile != null && stiffnessCacheSize < stiffnessCache.calcCacheSize()) {
            // we've calculated new Coulomb values, write out new cache files
            System.out.println("Writing stiffness cache to " + stiffnessCacheFile.getAbsolutePath());
            try {
                stiffnessCache.writeCacheFile(stiffnessCacheFile);
                stiffnessCacheSize = stiffnessCache.calcCacheSize();
                System.out.println("DONE writing stiffness cache");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    final static String PARAMETER_HEADER = "lameLambda, lameMu, coeffOfFriction, gridSpacing";

    @Override
    public String getText() {
        checkUpdateStiffnessCache();

        StringBuilder builder = new StringBuilder();
        builder.append(PARAMETER_HEADER).append('\n');
        builder.append(lameLambda + "," + lameMu + "," + coeffOfFriction + "," + stiffGridSpacing + "\n");
        builder.append("---cache follows ---\n");

        try {
            BufferedReader reader = new BufferedReader(new FileReader(stiffnessCacheFile));
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            reader.close();
        } catch (IOException x) {
            throw new RuntimeException(x);
        }

        return builder.toString();
    }

    @Override
    public void setText(String text) {
        String[] lines = text.split("\n");

        Preconditions.checkArgument(lines[0].equals(PARAMETER_HEADER));
        String[] parameters = lines[1].split(",");
        Preconditions.checkArgument(parameters.length == 4);
        lameLambda = Double.parseDouble(parameters[0]);
        lameMu = Double.parseDouble(parameters[1]);
        coeffOfFriction = Double.parseDouble(parameters[2]);
        stiffGridSpacing = Double.parseDouble(parameters[3]);

        try {
            stiffnessCacheFile = File.createTempFile("cff", ".csv");
            stiffnessCacheFile.deleteOnExit();
            System.out.println("Writing temporary stiffness cache to " + stiffnessCacheFile.getAbsolutePath());
            BufferedWriter writer = new BufferedWriter(new FileWriter(stiffnessCacheFile));
            for (int i = 3; i < lines.length; i++) {
                writer.write(lines[i]);
                writer.write('\n');
            }
            writer.close();

        } catch (IOException x) {
            throw new RuntimeException(x);
        }

        createStiffnessCalc();
    }


    @Override
    public String getFileName() {
        return "StiffnessModule.txt";
    }

    @Override
    public String getName() {
        return "StiffnessModule";
    }

    @Override
    public void setParent(FaultSystemRupSet parent) throws IllegalStateException {
        if (this.rupSet != null)
            Preconditions.checkState(rupSet.getNumRuptures() == parent.getNumRuptures());
        this.rupSet = parent;
    }

    @Override
    public FaultSystemRupSet getParent() {
        return rupSet;
    }

    @Override
    public SubModule<FaultSystemRupSet> copy(FaultSystemRupSet newParent) throws IllegalStateException {
        Preconditions.checkState(rupSet.getNumSections() == newParent.getNumSections());

        StiffnessCalcModule module = new StiffnessCalcModule();
        module.lameLambda = lameLambda;
        module.lameMu = lameMu;
        module.coeffOfFriction = coeffOfFriction;
        module.stiffGridSpacing = stiffGridSpacing;

        module.cacheDir = cacheDir;
        module.stiffnessCacheSize = stiffnessCacheSize;
        module.rupSet = rupSet;
        module.stiffnessCacheFile = stiffnessCacheFile;
        module.stiffnessCalc = stiffnessCalc;
        module.stiffnessCache = stiffnessCache;
        return module;
    }
}
