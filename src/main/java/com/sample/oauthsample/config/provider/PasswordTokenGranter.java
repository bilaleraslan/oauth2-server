package com.sample.oauthsample.config.provider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.exceptions.InvalidGrantException;
import org.springframework.security.oauth2.common.exceptions.OAuth2Exception;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.provider.ClientDetails;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.OAuth2Request;
import org.springframework.security.oauth2.provider.TokenRequest;
import org.springframework.security.oauth2.provider.token.AbstractTokenGranter;

public class PasswordTokenGranter extends AbstractTokenGranter {
	private static final String GRANT_TYPE = "password-sms";
	private static final GrantedAuthority PRE_AUTH = new SimpleGrantedAuthority("PRE_AUTH");

	private final AuthenticationManager authenticationManager;

	public PasswordTokenGranter(AuthorizationServerEndpointsConfigurer endpointsConfigurer,
			AuthenticationManager authenticationManager) {
		super(endpointsConfigurer.getTokenServices(), endpointsConfigurer.getClientDetailsService(),
				endpointsConfigurer.getOAuth2RequestFactory(), GRANT_TYPE);
		this.authenticationManager = authenticationManager;
	}

	@Override
	protected OAuth2Authentication getOAuth2Authentication(ClientDetails client, TokenRequest tokenRequest) {
		Map<String, String> parameters = new LinkedHashMap<>(tokenRequest.getRequestParameters());
		String username = parameters.get("username");
		String password = parameters.get("password");
		parameters.remove("password");
		Authentication userAuth = new UsernamePasswordAuthenticationToken(username, password);
		((AbstractAuthenticationToken) userAuth).setDetails(parameters);

		try {
			userAuth = this.authenticationManager.authenticate(userAuth);
		} catch (AccountStatusException | BadCredentialsException e) {
			throw new InvalidGrantException(e.getMessage());
		}

		if (userAuth != null && userAuth.isAuthenticated()) {
			OAuth2Request storedOAuth2Request = this.getRequestFactory().createOAuth2Request(client, tokenRequest);
			if (isEnabled(username)) {
				userAuth = new UsernamePasswordAuthenticationToken(username, password, Collections.singleton(PRE_AUTH));
				OAuth2AccessToken accessToken = getTokenServices()
						.createAccessToken(new OAuth2Authentication(storedOAuth2Request, userAuth));
				throw new MfaRequiredException(accessToken.getValue());
			}
			return new OAuth2Authentication(storedOAuth2Request, userAuth);
		} else {
			throw new InvalidGrantException("Could not authenticate user: " + username);
		}
	}

	public static class MfaRequiredException extends OAuth2Exception {

		public MfaRequiredException(String mfaToken) {
			super("Multi-factor authentication required");
			this.addAdditionalInformation("mfa_token", mfaToken);
		}

		public String getOAuth2ErrorCode() {
			return "mfa_required";
		}

		public int getHttpErrorCode() {
			return 403;
		}
	}

	private boolean isEnabled(String username) {
		return username.equals("bb");
	}
}
