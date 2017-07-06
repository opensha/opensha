/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.commons.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * <p>Title: MailUtil.java </p>
 * <p>Description: Utility to send mail throough the program </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author Nitin Gupta, Vipin Gupta
 * @date May 4, 2004
 * @version 1.0
 */

public final class MailUtil {
	
	public static class MailProps {
		private String emailTo, smtpHost, emailSubject, emailFrom;
		private boolean isEmailEnabled;
		
		public MailProps(Properties p) {
			emailTo = (String) p.get("EmailTo");
			smtpHost = (String) p.get("SmtpHost");
			emailSubject =  (String) p.get("Subject");
			emailFrom =(String) p.get("EmailFrom");
			isEmailEnabled = Boolean.valueOf((String) p.get("EmailEnabled")).booleanValue();
		}
		
		public MailProps(String emailTo, String smtpHost, String emailSubject,
				String emailFrom, boolean isEmailEnabled) {
			super();
			this.emailTo = emailTo;
			this.smtpHost = smtpHost;
			this.emailSubject = emailSubject;
			this.emailFrom = emailFrom;
			this.isEmailEnabled = isEmailEnabled;
		}

		public String getEmailTo() {
			return emailTo;
		}

		public void setEmailTo(String emailTo) {
			this.emailTo = emailTo;
		}

		public String getSmtpHost() {
			return smtpHost;
		}

		public void setSmtpHost(String smtpHost) {
			this.smtpHost = smtpHost;
		}

		public String getEmailSubject() {
			return emailSubject;
		}

		public void setEmailSubject(String emailSubject) {
			this.emailSubject = emailSubject;
		}

		public String getEmailFrom() {
			return emailFrom;
		}

		public void setEmailFrom(String emailFrom) {
			this.emailFrom = emailFrom;
		}

		public boolean isEmailEnabled() {
			return isEmailEnabled;
		}

		public void setEmailEnabled(boolean isEmailEnabled) {
			this.isEmailEnabled = isEmailEnabled;
		}
	}
	
	public static MailProps loadMailPropsFromFile(String fileName) throws FileNotFoundException, IOException {
		Properties p = new Properties();
		p.load(new FileInputStream(fileName));
		return new MailProps(p);
	}

	/**
	 *
	 * @param host SMTP server from which mail needs to be sent
	 * @param from Email prefix of sender
	 * @param emailAddr email address of receiver
	 * @param mailSubject Email subject
	 * @param mailMessage Email body
	 */
	public static void sendMail(String host, String from,
			String to,
			String mailSubject,
			String mailMessage) {
		MailProps p = new MailProps(to, host, mailSubject, from, true);
		sendMail(p, mailMessage);
	}
	
	public static void sendMail(MailProps p, String mailMessage) {
		if (!p.isEmailEnabled)
			return;
		try {
			Properties props = System.getProperties();
			// Setup mail server
			props.put("mail.smtp.host", p.getSmtpHost());
			// Get session
			Session session = Session.getDefaultInstance(props, null);
			
			// Define message
			MimeMessage message = new MimeMessage(session);
			message.setFrom(new InternetAddress(p.getEmailFrom()));
			message.addRecipient(Message.RecipientType.TO, 
			  new InternetAddress(p.getEmailTo()));
			message.setSubject(p.getEmailSubject());
			message.setText(mailMessage);

			// Send message
			Transport.send(message);
			
//			// Create a new instance of SmtpClient.
//			SmtpClient smtp = new SmtpClient(host);
//			// Sets the originating e-mail address
//			smtp.from(from);
//			// Sets the recipients' e-mail address
//			smtp.to(emailAddr);
//			// Create an output stream to the connection
//			PrintStream msg = smtp.startMessage();
//			msg.println("To: " + emailAddr); // so mailers will display the recipient's e-mail address
//			msg.println("From: " + from); // so that mailers will display the sender's e-mail address
//			msg.println("Subject: " + mailSubject + "\n");
//			msg.println(mailMessage);
//
//			// Close the connection to the SMTP server and send the message out to the recipient
//			smtp.closeServer();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String args[]) {
//		sendMail("email.usc.edu", "kmilner@usc.edu", "kmilner@usc.edu", "testing?", "this is a test message\nnewline!");
	}

}
