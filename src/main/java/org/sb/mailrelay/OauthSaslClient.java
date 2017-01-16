/**
 * 
 */
package org.sb.mailrelay;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

/**
 * @author sam
 *
 */
class OauthSaslClient implements SaslClient {

	private static final Logger log = Logger.getLogger(OauthSaslClient.class.getPackage().getName());
	
	private final String oauthToken;
	private final CallbackHandler callbackHandler;

	private boolean isComplete = false;

	/**
	 * Creates a new instance of the OAuth2SaslClient. This will ordinarily only
	 * be called from OAuth2SaslClientFactory.
	 */
	public OauthSaslClient(String oauthToken, CallbackHandler callbackHandler) {
		this.oauthToken = oauthToken;
		this.callbackHandler = callbackHandler;
	}

	public String getMechanismName() {
		return "XOAUTH2";
	}

	public boolean hasInitialResponse() {
		return true;
	}

	public byte[] evaluateChallenge(byte[] challenge) throws SaslException {
		if (isComplete) {
			// Empty final response from server, just ignore it.
			log.fine(() -> "Complete .. ignoring challenge");
			return new byte[] {};
		}

		log.fine(() -> "Evauating challenge");

		NameCallback nameCallback = new NameCallback("Enter name");
		Callback[] callbacks = new Callback[] { nameCallback };
		try {
			callbackHandler.handle(callbacks);
		} catch (UnsupportedCallbackException e) {
			log.log(Level.SEVERE, "Unsupported callback", e);
			throw new SaslException("Unsupported callback: " + e);
		} catch (IOException e) {
			log.log(Level.SEVERE, "Failed to execute callback", e);
			throw new SaslException("Failed to execute callback: " + e);
		}
		String email = nameCallback.getName();

		byte[] response = String.format("user=%s\1auth=Bearer %s\1\1", email, oauthToken).getBytes();
		isComplete = true;
		return response;
	}

	public boolean isComplete() {
		return isComplete;
	}

	public byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException {
		throw new IllegalStateException();
	}

	public byte[] wrap(byte[] outgoing, int offset, int len) throws SaslException {
		throw new IllegalStateException();
	}

	public Object getNegotiatedProperty(String propName) {
		if (!isComplete()) {
			throw new IllegalStateException();
		}
		return null;
	}

	public void dispose() throws SaslException {
	}
}
