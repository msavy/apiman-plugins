/*
 * Copyright 2017 JBoss Inc
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

package io.apiman.plugins.auth3scale.authrep;

import io.apiman.common.logging.IApimanLogger;
import io.apiman.gateway.engine.beans.ApiRequest;
import io.apiman.gateway.engine.policy.IPolicyContext;
import io.apiman.gateway.engine.vertx.polling.fetchers.threescale.beans.Content;
import io.apiman.gateway.engine.vertx.polling.fetchers.threescale.beans.ProxyRule;
import io.apiman.plugins.auth3scale.util.ParameterMap;

import java.text.MessageFormat;

@SuppressWarnings("nls")
public abstract class AbstractAuthRepBase {
    private Content config;
    private ApiRequest request;
    private IApimanLogger logger;

    public AbstractAuthRepBase(Content config, ApiRequest request, IPolicyContext context) {
        this.config = config;
        this.request = request;
        this.logger = context.getLogger(AbstractAuthRepBase.class);
    }

    public abstract AbstractAuthRepBase setAuthRepStrategy();

    protected ParameterMap setIfNotNull(ParameterMap in, String k, String v) {
        if (v == null) {
            return in;
        }
        in.add(k, v);
        return in;
    }

    protected ParameterMap buildRepMetrics() {
        ParameterMap pm = new ParameterMap(); // TODO could be interesting to cache a partially built map and just replace values?

        int[] matches = config.getProxy().match(request.getDestination());
        if (matches.length > 0) {
            for (int matchIndex : matches) {
                // Get specific proxy rule that matches. (e.g. / or /foo/bar)
                ProxyRule proxyRule = config.getProxy().getProxyRules().get(matchIndex);
                // Name of the metric as defined in 3scale
                String metricName = proxyRule.getMetricSystemName();
                // Ensure the matching rule applies to the request's HTTP Method
                if (!proxyRule.getHttpMethod().equalsIgnoreCase(request.getType()))
                    continue;

                logger.trace("Matched rule {0}", proxyRule);
                if (pm.containsKey(metricName)) {
                    long newValue = pm.getLongValue(metricName) + proxyRule.getDelta(); // Increment delta.
                    pm.setLongValue(metricName, newValue);
                } else {
                    pm.setLongValue(metricName, proxyRule.getDelta()); // Otherwise value is delta.
                }
            }
        }
        return pm;
    }

    protected boolean hasRoutes(ApiRequest req) {
        return config.getProxy().match(req.getDestination()).length > 0;
    }

    protected String getIdentityElement(Content config, ApiRequest request, String canonicalName)  {
        // Manual for now as there's no mapping in the config.
        if (config.getProxy().getCredentialsLocation().equalsIgnoreCase("query")) {
            return request.getQueryParams().get(getElemFromConfig(config, canonicalName));
        } else { // Else let's assume header
            return request.getHeaders().get(getElemFromConfig(config, canonicalName));
        }
    }

    protected String getElemFromConfig(Content config, String canonicalName) {
        switch (config.getAuthType()) {
        case API_KEY:
            return config.getProxy().getAuthUserKey();
        case APP_ID:
            if (AuthRepConstants.APP_ID.equalsIgnoreCase(canonicalName))
                return config.getProxy().getAuthAppId();
            if (AuthRepConstants.APP_KEY.equalsIgnoreCase(canonicalName))
                return config.getProxy().getAuthAppKey();
        case OAUTH: // TODO
            return null;
        }
        throw new IllegalStateException(MessageFormat.format("Unrecognised auth identifier elements for {0} with {1}", config.getAuthType(), canonicalName));
    }

}
