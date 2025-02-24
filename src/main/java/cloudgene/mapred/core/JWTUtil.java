package cloudgene.mapred.core;

import java.util.HashMap;
import java.util.Map;

import org.restlet.Request;
import org.restlet.data.Parameter;
import org.restlet.util.Series;

import cloudgene.mapred.database.UserDao;
import genepi.db.Database;

public class JWTUtil {

	public static final String COOKIE_NAME = "cloudgene-token";

	public static long TOKEN_LIFETIME_MS = 24 * 60 * 60 * 1000;

	public static long TOKEN_LIFETIME_API_MS = 30L * 24L * 60L * 60L * 1000L;

	public JWTUtil() {
	}

	public static String createCookieToken(User user, String secretKey, String csrfToken) {

		Map<String, Object> playload = new HashMap<String, Object>();
		playload.put("username", user.getUsername());
		playload.put("name", user.getFullName());
		playload.put("mail", user.getMail());
		playload.put("api", false);
		playload.put("csrf", csrfToken);

		String token = JWT.generate(playload, secretKey, TOKEN_LIFETIME_MS);

		return token;
	}

	public static String createApiToken(User user, String secretKey) {

		Map<String, Object> playload = new HashMap<String, Object>();
		playload.put("username", user.getUsername());
		playload.put("name", user.getFullName());
		playload.put("mail", user.getMail());
		playload.put("api", true);
		String token = JWT.generate(playload, secretKey, TOKEN_LIFETIME_API_MS);

		return token;
	}

	public static User getUser(Database database, Map<String, Object> payload) {
		String username = payload.get("username").toString();
		if (username != null) {
			UserDao userDao = new UserDao(database);
			return userDao.findByUsername(username);
		} else {
			return null;
		}
	}

	public static boolean isApiToken(Map<String, Object> payload) {
		if (payload.containsKey("api")) {
			return (Boolean) payload.get("api");
		} else {
			return false;
		}
	}

	public static User getUserByRequest(Database database, Request request, String secretKey, boolean checkCsrf) {
		String token = request.getCookies().getFirstValue(COOKIE_NAME);

		// check cookie
		if (token != null) {

			// csrf token is needed!

			Series<Parameter> headers = (Series) request.getAttributes().get("org.restlet.http.headers");

			String csrfToken = headers.getFirstValue("X-CSRF-Token");
			if (csrfToken == null) {
				// IE 8 bug
				csrfToken = headers.getFirstValue("x-csrf-token");
			}

			// no csrf token available
			if (checkCsrf && csrfToken == null) {
				return null;
			}

			Map<String, Object> payload = JWT.validate(token, secretKey);

			if (payload != null) {

				// check csrf token
				if (!isApiToken(payload) && (!checkCsrf || csrfToken.equals(payload.get("csrf").toString()))) {
					User user = getUser(database, payload);
					return user;
				} else {
					// csrf token is invalid
					return null;
				}

			} else {

				// invalid access-token

				return null;
			}

		}

		return null;

	}

	public static User getUserByApiToken(Database database, Request request, String secretKey) {

		Map<String, Object> payload = JWT.validate(request, secretKey);

		if (payload != null) {

			// check if it is an api key
			if (isApiToken(payload)) {
				User user = getUser(database, payload);
				// check if api key is on users whitelist
				if (user != null && user.getApiToken().equals(payload.get("request-token").toString())) {
					return user;
				} else {
					return null;
				}
			} else {
				return null;
			}

		} else {

			// invalid access-token

			return null;

		}

	}

}
