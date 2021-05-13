package org.opensha.commons.calc.magScalingRelations.magScalingRelImpl;

import static org.junit.Assert.*;

import org.junit.Test;


public class TestAH2017InterfaceBilinearMagAreaRel {

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
        AH2017InterfaceBilinearMagAreaRel AH = new AH2017InterfaceBilinearMagAreaRel();

        assertEquals(8.131, AH.getMedianMag(19952.6231496888), 0.01);
        assertEquals(9.190, AH.getMedianMag(119952.6231496888), 0.01);
        //the digitized data on OpenQuake seems not correct! 

    }


    @Test
    public void testGetMedianArea() {
        AH2017InterfaceBilinearMagAreaRel AH = new AH2017InterfaceBilinearMagAreaRel();

        assertEquals(13803.84264602883, AH.getMedianArea(8.0), 0.001);
        assertEquals(104712.8548050898, AH.getMedianArea(9.0), 0.001);

    }
}
