/**
 * 
 */
package org.sb.mailrelay;

import java.io.IOException;
import java.security.Provider;
import java.security.Security;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.URLName;

import com.sun.mail.smtp.SMTPTransport;

/**
 * @author sam
 *
 */
public class OauthProvider extends Provider {

	private static final long serialVersionUID = 1L;

	static {
		Security.addProvider(new OauthProvider());
	}

	private OauthProvider() {
		super("Google OAuth2 Provider", 1.0, "Provides the XOAUTH2 SASL Mechanism");
		put("SaslClientFactory.XOAUTH2", OauthSaslClientFactory.class.getName());
	}

	/**
	 * Installs the OAuth2 SASL provider. This must be called exactly once
	 * before calling other methods on this class.
	 */

	/**
	 * Connects and authenticates to an SMTP server with OAuth2. You must have
	 * called {@code initialize}.
	 *
	 * @param host
	 *            Hostname of the smtp server, for example {@code
	 *     smtp.googlemail.com}.
	 * @param port
	 *            Port of the smtp server, for example 587.
	 * @param userEmail
	 *            Email address of the user to authenticate, for example
	 *            {@code oauth@gmail.com}.
	 * @param oauthToken
	 *            The user's OAuth token.
	 * @param debug
	 *            Whether to enable debug logging on the connection.
	 *
	 * @return An authenticated SMTPTransport that can be used for SMTP
	 *         operations.
	 */
	static SMTPTransport connectToSmtp(String host, int port, String userEmail, String oauthToken, boolean debug)
			throws IOException {
		Properties props = new Properties();
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.starttls.required", "true");
		props.put("mail.smtp.sasl.enable", "true");
		props.put("mail.smtp.sasl.mechanisms", "XOAUTH2");
		props.put(OauthSaslClientFactory.OAUTH_TOKEN_PROP, oauthToken);
		Session session = Session.getInstance(props);
		session.setDebug(debug);

		final URLName unusedUrlName = null;
		SMTPTransport transport = new SMTPTransport(session, unusedUrlName);
		// If the password is non-null, SMTP tries to do AUTH LOGIN.
		final String emptyPassword = "";
		try {
			transport.connect(host, port, userEmail, emptyPassword);
		} catch (MessagingException e) {
			// TODO Auto-generated catch block
			throw new IOException(e);
		}

		return transport;
	}
}
