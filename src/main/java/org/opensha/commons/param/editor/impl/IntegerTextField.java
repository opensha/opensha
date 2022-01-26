package org.opensha.commons.param.editor.impl;

import java.awt.Toolkit;
import java.text.ParseException;

import javax.swing.JTextField;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;

import org.opensha.commons.param.editor.document.IntegerPlainDocument;

/**
 * <b>Title:</b> IntegerTextField<p>
 *
 * <b>Description:</b> Special JTextField that only allows integers to be typed in. This
 * text field allows for a negative sign as the first character, only digits thereafter.<p>
 *
 * Note: This is a fairly complex GUI customization that relies upon an IntegerDocument model
 * to determine what types of characters are allowed for a integer number ( digits, - sign
 * in first location, etc. ) It is beyond the scope of this javadoc to explain it's use
 * fully. It is not necessary for programmers to understand the details. They can just
 * use it like a normal JTextField and just expect it to work. <p>
 *
 * Please consult Swing doucmentation for further details, specifically JTextField and
 * PlainDocument. It is required a programmer understands the Model View Component
 * design architeture to understand the relationship between JTextField and PlainDocument
 * ( and our corresponding IntegerTextField and IntegerDocument ).<p>
 *
 * @author Steven W. Rock
 * @version 1.0
 */
public class IntegerTextField extends JTextField
    implements IntegerPlainDocument.InsertErrorListener
{

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/** Class name for debugging. */
    protected final static String C = "IntegerTextField";
    /** If true print out debug statements. */
    protected final static boolean D = false;


    public IntegerTextField() { this(null, 0); }

    public IntegerTextField(String text) { this(text, 0); }

    public IntegerTextField(String text, int columns) {
        super(null, text, columns);
        IntegerPlainDocument doc = (IntegerPlainDocument) this.getDocument();
        doc.addInsertErrorListener(this);
    }

    public Integer getIntegerValue() throws ParseException {
	    return ((IntegerPlainDocument) this.getDocument()).getIntegerValue();
    }

    public void setValue(Integer integer) { this.setText(integer.toString()); }
    public void setValue(int i) { this.setText("" + i); }

    public void insertFailed(
        IntegerPlainDocument doc,
        int offset, String str,
        AttributeSet a, String reason
    ) { Toolkit.getDefaultToolkit().beep();  }

    protected Document createDefaultModel() { return new IntegerPlainDocument(); }
}
