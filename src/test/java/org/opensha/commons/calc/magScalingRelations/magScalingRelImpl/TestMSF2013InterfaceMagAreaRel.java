package org.opensha.commons.calc.magScalingRelations.magScalingRelImpl;

import static org.junit.Assert.*;

import org.junit.Test;


public class TestMSF2013InterfaceMagAreaRel {

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
        MSF2013InterfaceMagAreaRel MSF = new MSF2013InterfaceMagAreaRel();

        assertEquals(6.839561868, MSF.getMedianMag(1000), 0.01);
        assertEquals(7.538531873, MSF.getMedianMag(5000), 0.01);
        assertEquals(8.140591864, MSF.getMedianMag(20000), 0.01);
        assertEquals(9.140591864, MSF.getMedianMag(200000), 0.01);

    }


    @Test
    public void testGetMedianArea() {
        MSF2013InterfaceMagAreaRel MSF = new MSF2013InterfaceMagAreaRel();

        assertEquals(1446.898718, MSF.getMedianArea(7.0), 0.001);
        assertEquals(4575.495491, MSF.getMedianArea(7.5), 0.001);
        assertEquals(14468.98718, MSF.getMedianArea(8.0), 0.001);
        assertEquals(45754.95491, MSF.getMedianArea(8.5), 0.001);
        assertEquals(144689.8718, MSF.getMedianArea(9.0), 0.001);


    }
}
