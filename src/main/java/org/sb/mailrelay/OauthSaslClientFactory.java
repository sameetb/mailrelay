/**
 * 
 */
package org.sb.mailrelay;

import java.util.Arrays;
import java.util.Map;

import java.util.logging.Logger;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslClientFactory;

/**
 * @author sam
 *
 */
public class OauthSaslClientFactory implements SaslClientFactory {

	private static final Logger log = Logger.getLogger(OauthSaslClientFactory.class.getPackage().getName());

	public static final String OAUTH_TOKEN_PROP = "mail.imaps.sasl.mechanisms.oauth2.oauthToken";

	public SaslClient createSaslClient(String[] mechanisms, String authorizationId, String protocol, String serverName,
			Map<String, ?> props, CallbackHandler callbackHandler) {
		boolean matchedMechanism = false;
		for (int i = 0; i < mechanisms.length; ++i) {
			if ("XOAUTH2".equalsIgnoreCase(mechanisms[i])) {
				matchedMechanism = true;
				break;
			}
		}
		if (!matchedMechanism) {
			log.severe("Failed to match any mechanisms in " + Arrays.deepToString(mechanisms));
			return null;
		}
		return new OauthSaslClient((String) props.get(OAUTH_TOKEN_PROP), callbackHandler);
	}

	public String[] getMechanismNames(Map<String, ?> props) {
		return new String[] { "XOAUTH2" };
	}
}