/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.oauth2;

import org.forgerock.common.UserStore;
import org.forgerock.oauth2.core.AccessToken;
import org.forgerock.oauth2.core.ClientRegistration;
import org.forgerock.oauth2.core.OAuth2Request;
import org.forgerock.oauth2.core.ScopeValidator;
import org.forgerock.oauth2.core.Token;
import org.forgerock.oauth2.core.exceptions.InvalidClientException;
import org.forgerock.oauth2.core.exceptions.ServerException;
import org.forgerock.oauth2.core.exceptions.UnauthorizedClientException;
import org.forgerock.openidconnect.OpenIDTokenIssuer;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @since 12.0.0
 */
@Singleton
public class ScopeValidatorImpl implements ScopeValidator {

    private static Map<String, Object> scopeToUserUserProfileAttributes;

    static {
        scopeToUserUserProfileAttributes = new HashMap<String, Object>();
        scopeToUserUserProfileAttributes.put("email","mail");
        scopeToUserUserProfileAttributes.put("address", "postaladdress");
        scopeToUserUserProfileAttributes.put("phone", "telephonenumber");

        Map<String, Object> profileSet = new HashMap<String, Object>();
        profileSet.put("name", "cn");
        profileSet.put("given_name", "givenname");
        profileSet.put("family_name", "sn");
        profileSet.put("locale", "preferredlocale");
        profileSet.put("zoneinfo", "preferredtimezone");

        scopeToUserUserProfileAttributes.put("profile", profileSet);
    }

    private final UserStore userStore;
    private final OpenIDTokenIssuer openIDTokenIssuer;

    @Inject
    public ScopeValidatorImpl(final UserStore userStore, final OpenIDTokenIssuer openIDTokenIssuer) {
        this.userStore = userStore;
        this.openIDTokenIssuer = openIDTokenIssuer;
    }

    public Set<String> validateAuthorizationScope(ClientRegistration clientRegistration, Set<String> scope) {
        if (scope == null || scope.isEmpty()) {
            return clientRegistration.getDefaultScopes();
        }

        Set<String> scopes = new HashSet<String>(clientRegistration.getAllowedScopes());
        scopes.retainAll(scope);
        return scopes;
    }

    public Set<String> validateAccessTokenScope(ClientRegistration clientRegistration, Set<String> scope, OAuth2Request request) {
        if (scope == null || scope.isEmpty()) {
            return clientRegistration.getDefaultScopes();
        }

        Set<String> scopes = new HashSet<String>(clientRegistration.getAllowedScopes());
        scopes.retainAll(scope);
        return scopes;
    }

    public Set<String> validateRefreshTokenScope(ClientRegistration clientRegistration, Set<String> requestedScope, Set<String> tokenScope, OAuth2Request request) {

        if (requestedScope == null || requestedScope.isEmpty()) {
            return tokenScope;
        }

        Set<String> scopes = new HashSet<String>(tokenScope);
        scopes.retainAll(requestedScope);
        return scopes;
    }

    public Map<String, Object> getUserInfo(AccessToken token, OAuth2Request request) throws UnauthorizedClientException {

        Set<String> scopes = token.getScope();
        Map<String,Object> response = new HashMap<String, Object>();
        final ResourceOwnerImpl user = userStore.get(token.getResourceOwnerId());

        //add the subject identifier to the response
        response.put("sub", token.getResourceOwnerId());
        response.put("updated_at", user.getModifiedTimestamp());
        for(String scope: scopes){

            //get the attribute associated with the scope
            Object attribute = scopeToUserUserProfileAttributes.get(scope);
            if (attribute == null) {
                // Invalid Scope in token scope
            } else if (attribute instanceof String) {
                response.put(scope, user.getAttribute((String) attribute));
            } else if (attribute instanceof Map) {
                //the attribute is a collection of attributes
                //for example profile can be address, email, etc...
                if (attribute != null && !((Map<String,String>) attribute).isEmpty()){
                    for (Map.Entry<String, String> entry: ((Map<String, String>) attribute).entrySet()){
                        response.put(entry.getKey(), user.getAttribute(entry.getValue()));
                    }
                }
            }
        }

        return response;
    }

    public Map<String, Object> evaluateScope(AccessToken accessToken) {
        Map<String, Object> map = new HashMap<String, Object>();
        Set<String> scopes = accessToken.getScope();
        String resourceOwner = accessToken.getResourceOwnerId();

        if (resourceOwner != null && scopes != null && !scopes.isEmpty()) {
            final ResourceOwnerImpl user = userStore.get(resourceOwner);
            if (user != null) {
                for (String scope : scopes) {
                    String value = user.getAttribute(scope);
                    map.put(scope, value);
                }
            }
        }

        return map;
    }

    public Map<String, String> additionalDataToReturnFromAuthorizeEndpoint(Map<String, Token> tokens, OAuth2Request request) {
        return Collections.emptyMap();
    }

    public void additionalDataToReturnFromTokenEndpoint(AccessToken accessToken, OAuth2Request request) throws ServerException, InvalidClientException {
        final Set<String> scope = accessToken.getScope();
        if (scope != null && scope.contains("openid")) {
            final Map.Entry<String, String> tokenEntry = openIDTokenIssuer.issueToken(accessToken, request);
            if (tokenEntry != null) {
                accessToken.addExtraData(tokenEntry.getKey(), tokenEntry.getValue());
            }
        }
    }
}
