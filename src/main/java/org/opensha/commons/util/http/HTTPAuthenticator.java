package org.opensha.commons.util.http;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

import org.opensha.commons.gui.UserAuthDialog;


public class HTTPAuthenticator extends Authenticator {
	
	UserAuthDialog auth;
	
	public HTTPAuthenticator() {
		super();
		
		auth = new UserAuthDialog(null, false);
	}
	
	public UserAuthDialog getDialog() {
		return auth;
	}

    public PasswordAuthentication getPasswordAuthentication () {
    	auth.setVisible(true);
    	if (auth.isCanceled())
    		return null;
        return new PasswordAuthentication (auth.getUsername(), auth.getPassword());
    }
}
