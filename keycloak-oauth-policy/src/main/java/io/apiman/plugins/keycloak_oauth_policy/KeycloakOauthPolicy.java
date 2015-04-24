/*
 * Copyright 2015 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apiman.plugins.keycloak_oauth_policy;

import io.apiman.gateway.engine.async.IAsyncResult;
import io.apiman.gateway.engine.async.IAsyncResultHandler;
import io.apiman.gateway.engine.beans.PolicyFailure;
import io.apiman.gateway.engine.beans.ServiceRequest;
import io.apiman.gateway.engine.components.ISharedStateComponent;
import io.apiman.gateway.engine.policies.AbstractMappedPolicy;
import io.apiman.gateway.engine.policy.IPolicyChain;
import io.apiman.gateway.engine.policy.IPolicyContext;
import io.apiman.plugins.keycloak_oauth_policy.beans.ApplicationRoleMapping;
import io.apiman.plugins.keycloak_oauth_policy.beans.ForwardAuthInfo;
import io.apiman.plugins.keycloak_oauth_policy.beans.KeycloakOauthConfigBean;
import io.apiman.plugins.keycloak_oauth_policy.failures.PolicyFailureFactory;
import io.apiman.plugins.keycloak_oauth_policy.util.Holder;

import org.apache.commons.lang.StringUtils;
import org.keycloak.RSATokenVerifier;
import org.keycloak.VerificationException;
import org.keycloak.constants.KerberosConstants;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessToken.Access;

/**
 * A Keycloak OAuth policy.
 *
 * @author Marc Savy <msavy@redhat.com>
 */
public class KeycloakOauthPolicy extends AbstractMappedPolicy<KeycloakOauthConfigBean> {

    private static final String AUTHORIZATION_KEY = "Authorization"; //$NON-NLS-1$
    private static final String ACCESS_TOKEN_QUERY_KEY = "access_token"; //$NON-NLS-1$
    private static final String BEARER = "Bearer "; //$NON-NLS-1$
    private static final String NEGOTIATE = "Negotiate "; //$NON-NLS-1$
    private final PolicyFailureFactory failureFactory = new PolicyFailureFactory();

    /**
     * @see io.apiman.gateway.engine.policies.AbstractMappedPolicy#getConfigurationClass()
     */
    @Override
    protected Class<KeycloakOauthConfigBean> getConfigurationClass() {
        return KeycloakOauthConfigBean.class;
    }

    /**
     * @see io.apiman.gateway.engine.policies.AbstractMappedPolicy#doApply(io.apiman.gateway.engine.beans.ServiceRequest,
     *      io.apiman.gateway.engine.policy.IPolicyContext, java.lang.Object,
     *      io.apiman.gateway.engine.policy.IPolicyChain)
     */
    @Override
    protected void doApply(final ServiceRequest request, final IPolicyContext context,
            final KeycloakOauthConfigBean config, final IPolicyChain<ServiceRequest> chain) {

        final String rawToken = getRawAuthToken(request);
        final Holder<Boolean> successStatus = new Holder<>(true);

        if (rawToken == null) {
            if (config.getRequireOauth()) {
                doFailure(successStatus, chain, failureFactory.noAuthenticationProvided(context));
            } else {
                chain.doApply(request);
            }
        } else if (doTokenAuth(successStatus, request, context, config, chain, rawToken).getValue()) {
            // Transport security check
            if (config.getRequireTransportSecurity() && !request.isTransportSecure()) {
                // If we've detected a situation where we should blacklist a
                // token
                if (config.getBlacklistUnsafeTokens()) {
                    blacklistToken(context, rawToken, new IAsyncResultHandler<Void>() {

                        @Override
                        public void handle(IAsyncResult<Void> result) {
                            if (result.isError()) {
                                throwError(successStatus, chain, result.getError());
                            }
                        }
                    });
                }

                doFailure(successStatus, chain, failureFactory.noTransportSecurity(context));
                return;
            }

            // If enabled we check against the blacklist
            if (config.getBlacklistUnsafeTokens()) {
                isBlacklistedToken(context, rawToken, new IAsyncResultHandler<Boolean>() {

                    @Override
                    public void handle(IAsyncResult<Boolean> result) {
                        if (result.isError()) {
                            throwError(successStatus, chain, result.getError());
                        } else if (result.getResult()) {
                            doFailure(successStatus, chain, failureFactory.blacklistedToken(context));
                        } else {
                            chain.doApply(request);
                        }
                    }
                });
            } else {
                if (successStatus.getValue())
                    chain.doApply(request);
            }
        }
    }

    private void doFailure(Holder<Boolean> isFailedHolder, IPolicyChain<?> chain, PolicyFailure failure) {
        chain.doFailure(failure);
        isFailedHolder.setValue(false);
    }

    private void throwError(Holder<Boolean> isFailedHolder, IPolicyChain<?> chain, Throwable error) {
        chain.throwError(error);
        isFailedHolder.setValue(false);
    }

    private Holder<Boolean> doTokenAuth(Holder<Boolean> isFailedHolder, ServiceRequest request,
            IPolicyContext context, KeycloakOauthConfigBean config, IPolicyChain<ServiceRequest> chain,
            String rawToken) {
        try {
            AccessToken parsedToken = RSATokenVerifier.verifyToken(rawToken, config.getRealmCertificate()
                    .getPublicKey(), config.getRealm());

            delegateKerberosTicket(request, config, parsedToken);
            forwardHeaders(request, config, rawToken, parsedToken);
            stripAuthTokens(request, config);

            if (doTokenRoleAuth(config, parsedToken)) {
                return isFailedHolder.setValue(true);
            } else {
                doFailure(isFailedHolder, chain, failureFactory.doesNotHoldRequiredRoles(context));
            }

        } catch (VerificationException e) {
            chain.doFailure(failureFactory.verificationException(context, e));
        }
        return isFailedHolder.setValue(false);
    }

    private void delegateKerberosTicket(ServiceRequest request, KeycloakOauthConfigBean config, AccessToken parsedToken) {
        String serializedGssCredential = (String) parsedToken.getOtherClaims()
                .get(KerberosConstants.GSS_DELEGATION_CREDENTIAL);

        if (config.getDelegateKerberosTicket()) {
            request.getHeaders().put(AUTHORIZATION_KEY, NEGOTIATE + serializedGssCredential);
        }
    }

    private boolean doTokenRoleAuth(KeycloakOauthConfigBean config, AccessToken parsedToken) {
        boolean result = true;

        if (config.getApplicationRoleMappings().size() > 0) {
            for (ApplicationRoleMapping role : config.getApplicationRoleMappings()) {
                Access access = parsedToken.getResourceAccess(role.getApplication());

                if (access != null) {
                    // System.out.println(access.getRoles());
                    // System.out.println(role.getRequiredRoles());
                    // System.out.println(access.getRoles().containsAll(role.getRequiredRoles()));
                    result = result && access.getRoles().containsAll(role.getRequiredRoles());
                    if (!result)
                        return false;
                }
            }
        }
        // System.out.println("config.getRealmRoleMappings() " + config.getRealmRoleMappings());
        // System.out.println("parsedToken.getRealmAccess().getRoles() + parsedToken.getRealmAccess().getRoles());
        if (config.getRealmRoleMappings().size() > 0)
            result = result && parsedToken.getRealmAccess().getRoles().containsAll(config.getRealmRoleMappings());

        return result;
    }

    private String getRawAuthToken(ServiceRequest request) {
        String rawToken = StringUtils.strip(request.getHeaders().get(AUTHORIZATION_KEY));

        if (rawToken != null && StringUtils.startsWith(rawToken, BEARER)) {
            rawToken = StringUtils.removeStart(rawToken, BEARER);
        } else {
            rawToken = request.getQueryParams().get(ACCESS_TOKEN_QUERY_KEY);
        }

        return rawToken;
    }

    private void stripAuthTokens(ServiceRequest request, KeycloakOauthConfigBean config) {
        if (config.getStripTokens()) {
            request.getHeaders().remove(AUTHORIZATION_KEY);
            request.getQueryParams().remove(ACCESS_TOKEN_QUERY_KEY);
        }
    }

    private void forwardHeaders(ServiceRequest request, KeycloakOauthConfigBean config, String rawToken,
            AccessToken parsedToken) {
        if (config.getForwardAuthInfo().size() == 0)
            return;

        for (ForwardAuthInfo entry : config.getForwardAuthInfo()) {
            String fieldValue = null;

            switch (entry.getField()) {
            case ACCESS_TOKEN:
                fieldValue = rawToken;
            case EMAIL:
                fieldValue = parsedToken.getEmail();
            case NAME:
                fieldValue = parsedToken.getName();
            case SUBJECT:
                fieldValue = parsedToken.getSubject();
            case USERNAME:
                fieldValue = parsedToken.getPreferredUsername();
            }
            request.getHeaders().put(entry.getHeader(), fieldValue);
        }
    }

    private void isBlacklistedToken(IPolicyContext context, String rawToken,
            final IAsyncResultHandler<Boolean> resultHandler) {
        ISharedStateComponent dataStore = getDataStore(context);
        dataStore.<Boolean> getProperty("apiman-keycloak-blacklist", rawToken, false, //$NON-NLS-1$
                resultHandler);
    }

    private void blacklistToken(IPolicyContext context, String rawToken,
            final IAsyncResultHandler<Void> resultHandler) {
        ISharedStateComponent dataStore = getDataStore(context);
        dataStore.<Boolean> setProperty("apiman-keycloak-blacklist", rawToken, true, //$NON-NLS-1$
                resultHandler);
    }

    private ISharedStateComponent getDataStore(IPolicyContext context) {
        return context.getComponent(ISharedStateComponent.class);
    }
}
