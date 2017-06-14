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

package io.apiman.plugins.auth3scale.authrep.strategies;

import io.apiman.gateway.engine.async.IAsyncResultHandler;
import io.apiman.gateway.engine.beans.ApiRequest;
import io.apiman.gateway.engine.policy.IPolicyContext;
import io.apiman.gateway.engine.vertx.polling.fetchers.threescale.beans.Content;

/**
 *  First leg is the same as {@link StandardAuth}.
 *
 * @author Marc Savy {@literal <marc@rhymewithgravy.com>}
 */
public class BatchedAuth extends StandardAuth {
    public static final BatchedAuthCache HEURISTIC_CACHE = new BatchedAuthCache();
    private Content config;
    private ApiRequest request;
    private IPolicyContext context;
    private Object[] keyElems;

    public BatchedAuth(Content config, ApiRequest request, IPolicyContext context) {
        super(config, request, context);
        this.config = config;
        this.request = request;
        this.context = context;
    }

    @Override
    public StandardAuth setKeyElems(Object... keyElems) {
        this.keyElems = keyElems;
        return this;
    }

    @Override
    public StandardAuth auth(IAsyncResultHandler<Void> resultHandler) {
        // A cache entry at this level implies that we are forcing standard auth.
        // because a batch was recently flushed and we want to try to ensure we
        // catch the rate limiting state updates as quickly as possible.
        // TODO make integer to force N number of async authreps after flush
        if (HEURISTIC_CACHE.isAuthCached(config, request, keyElems)) {
            super.doBlockingAuthRep(resultHandler);
        } else {
            super.auth(resultHandler);
        }
        return this;
    }
}
