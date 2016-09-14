package io.apiman.plugins.auth3scale.authrep;

/**
 * @author Marc Savy {@literal <msavy@redhat.com>}
 */
public interface AuthRepConstants {
    String USER_KEY = "user_key";
    String SERVICE_ID = "service_id";
    String PROVIDER_KEY = "provider_key";
	String REFERRER = "referer"; // Yes, misspelt.
	String USER_ID = "user_id"; // User ID is different from user_key - it is for rate limiting purposes...
}
