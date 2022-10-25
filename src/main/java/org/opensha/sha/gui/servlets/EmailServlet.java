package org.opensha.sha.gui.servlets;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opensha.commons.util.MailUtil;

/**
 * <p>Title: EmailServlet </p>
 * <p>Description: This servlet sends email to the system maintainer if application
 * crashes and user submits the bug report using the Bug reporting window that
 * pops up. </p>
 * @author : Nitin Gupta & Vipin Gupta
 * @version 1.0
 */

public class EmailServlet extends HttpServlet {

	//static Strings to send the mails
	private static final String TO = "vgupta@usc.edu, kmilner@usc.edu";
	private static final String HOST = "email.usc.edu";

	//Process the HTTP Get request
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws
	ServletException, IOException {
		
		System.out.println("EmailServlet: Handling GET");

		try {

			// get an input stream from the applet
			ObjectInputStream inputFromApplet = new ObjectInputStream(request.
					getInputStream());


			//get the email address from the applet
			String email = (String) inputFromApplet.readObject();

			//getting the email content from the aplication
			String emailMessage = (String) inputFromApplet.readObject();
			MailUtil.sendMail(HOST,email,TO,"Exception in Application",emailMessage);
			// report to the user whether the operation was successful or not
			// get an ouput stream from the applet
			ObjectOutputStream outputToApplet = new ObjectOutputStream(response.
					getOutputStream());
			outputToApplet.writeObject("Email Sent");
			outputToApplet.close();

		}
		catch (Exception e) {
			// report to the user whether the operation was successful or not
			e.printStackTrace();
		}
	}



	//Process the HTTP Post request
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws
	ServletException, IOException {
		// call the doPost method
		doGet(request, response);
	}

}
