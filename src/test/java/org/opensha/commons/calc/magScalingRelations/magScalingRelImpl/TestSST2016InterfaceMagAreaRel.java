package org.opensha.commons.calc.magScalingRelations.magScalingRelImpl;

import static org.junit.Assert.*;

import org.junit.Test;


public class TestSST2016InterfaceMagAreaRel {

    /**
     * @BeforeClass public static void setUpBeforeClass() throws Exception {
     * InputStream csvdata = TestAH2017InterfaceBilinearMagAreaRel.class.getResourceAsStream("fixtures/AH2017InterfaceBilinear.csv");
     * CSVFile<String> csv = CSVFile.readStream(csvdata, false);
     * <p>
     * <p>
     * }
     **/

    @Test
    public void testGetMedianMag() {
        SST2016InterfaceMagAreaRel SST = new SST2016InterfaceMagAreaRel();

        assertEquals(6.7186934, SST.getMedianMag(1000), 0.01);
        assertEquals(7.417663405, SST.getMedianMag(5000), 0.01);
        assertEquals(8.019723396, SST.getMedianMag(20000), 0.01);
        assertEquals(9.019723396, SST.getMedianMag(200000), 0.01);

    }


    @Test
    public void testGetMedianArea() {
        SST2016InterfaceMagAreaRel SST = new SST2016InterfaceMagAreaRel();

        assertEquals(1911.202037, SST.getMedianArea(7.0), 0.001);
        assertEquals(6043.751507, SST.getMedianArea(7.5), 0.001);
        assertEquals(19112.02037, SST.getMedianArea(8.0), 0.001);
        assertEquals(60437.51507, SST.getMedianArea(8.5), 0.001);
        assertEquals(191120.2037, SST.getMedianArea(9.0), 0.001);

    }
}
