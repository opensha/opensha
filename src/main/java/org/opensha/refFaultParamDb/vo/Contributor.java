package org.opensha.refFaultParamDb.vo;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.opensha.commons.metadata.XMLSaveable;

/**
 * <p>Title: Contributor.java </p>
 * <p>Description: This class has information about contributors</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class Contributor  implements java.io.Serializable, XMLSaveable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public static final String XML_METADATA_NAME = "Contributor";

	private int id=-1; // contributor ID
	private String name; // contributor name
	private String firstName;
	private String lastName;
	private String email;

	public Contributor() {
	}

	public Contributor(int id, String name) {
		setId(id);
		setName(name);
	}

	public Contributor(String name) {
		setName(name);
	}

	public int getId() { return id; }
	public String getName() { return this.name; }

	public void setId(int contributorId) {
		this.id = contributorId;
	}
	public void setName(String contributorName) {
		this.name=contributorName;
	}
	public String getEmail() {
		return email;
	}
	public String getFirstName() {
		return firstName;
	}
	public String getLastName() {
		return lastName;
	}
	public void setLastName(String lastName) {
		this.lastName = lastName;
	}
	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public Element toXMLMetadata(Element root) {

		Element el = root.addElement(XML_METADATA_NAME);

		el.addAttribute("id", id + "");
		el.addAttribute("name", name);
		el.addAttribute("email", email);
		el.addAttribute("firstName", firstName);
		el.addAttribute("lastName", lastName);

		return root;
	}

	public static Contributor fromXMLMetadata(Element el) {
		Contributor cont = new Contributor();

		Attribute idAtt = el.attribute("id");
		if (idAtt != null) {
			int id = Integer.parseInt(idAtt.getValue());
			cont.setId(id);
		}
		Attribute nameAtt = el.attribute("name");
		if (nameAtt != null) {
			cont.setName(nameAtt.getValue());
		}
		Attribute emailAtt = el.attribute("email");
		if (emailAtt != null) {
			cont.setEmail(emailAtt.getValue());
		}
		Attribute firstNameAtt = el.attribute("firstName");
		if (firstNameAtt != null) {
			cont.setFirstName(firstNameAtt.getValue());
		}
		Attribute lastNameAtt = el.attribute("lastName");
		if (lastNameAtt != null) {
			cont.setLastName(lastNameAtt.getValue());
		}

		return cont;
	}
}
