package org.opensha.commons.util.http;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

public class StaticPasswordAuthenticator extends Authenticator {
	
	String username;
	char[] password;
	
	public StaticPasswordAuthenticator(String username, char[] password) {
		this.username = username;
		this.password = password;
	}
	
	public PasswordAuthentication getPasswordAuthentication () {
        return new PasswordAuthentication (username, password);
    }

}
