package org.opensha.commons.gui;

import java.awt.Color;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

/**
 * <p>Title: TitledBorderPanel</p>
 *
 * <p>Description: This class creates a JPanel with a TitledBorder.
 * It allows user to specify the name and color for the Title.</p>
 * @author Nitin Gupta, vipin Gupta
 * @version 1.0
 */
public class TitledBorderPanel
    extends JPanel {

  //defines the Default Border color
  private static Color FORE_COLOR = new Color( 80, 80, 140 );
  //defines the Default Border Font for the Title
  private static Font DEFAULT_LABEL_FONT = new Font( "SansSerif", Font.BOLD, 12 );

  //creating a LineBorder for the Panel
  private Border border = BorderFactory.createLineBorder(FORE_COLOR);
  private TitledBorder titledBorder;

  /**
   * Creates a Panel with TitledBorder with dark blue color.
   * @param title String Title of the Panel
   */
  public TitledBorderPanel(String title) {
    titledBorder = new TitledBorder(border,title);
    titledBorder.setTitleColor(FORE_COLOR);
    titledBorder.setTitleFont(DEFAULT_LABEL_FONT);
    setBorder(titledBorder);
  }

  /**
   * Creates a Panel with TitledBorder.
   * @param title String Title of the Panel
   * @param titleColor Color Color of the Border around the Panel
   */
  public TitledBorderPanel(String title, Color titleColor) {
    titledBorder = new TitledBorder(border,title);
    titledBorder.setTitleColor(titleColor);
    titledBorder.setTitleFont(DEFAULT_LABEL_FONT);
    setBorder(titledBorder);
  }


}
