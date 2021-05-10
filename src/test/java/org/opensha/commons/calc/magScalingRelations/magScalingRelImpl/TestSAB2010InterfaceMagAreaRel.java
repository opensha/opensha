package org.opensha.commons.calc.magScalingRelations.magScalingRelImpl;

import static org.junit.Assert.*;

import org.junit.Test;


public class TestSAB2010InterfaceMagAreaRel {

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
        SAB2010InterfaceMagAreaRel SAB = new SAB2010InterfaceMagAreaRel();

        assertEquals(6.33, SAB.getMedianMag(172.18686), 0.01);
        assertEquals(7.14, SAB.getMedianMag(1541.70045), 0.01);
        assertEquals(7.94, SAB.getMedianMag(13803.84265), 0.01);

    }


    @Test
    public void testGetMedianArea() {
        SAB2010InterfaceMagAreaRel SAB = new SAB2010InterfaceMagAreaRel();

        assertEquals(172.18686, SAB.getMedianArea(6.0), 0.001);
        assertEquals(1541.70045, SAB.getMedianArea(7.0), 0.001);
        assertEquals(13803.84265, SAB.getMedianArea(8.0), 0.001);

    }
}
