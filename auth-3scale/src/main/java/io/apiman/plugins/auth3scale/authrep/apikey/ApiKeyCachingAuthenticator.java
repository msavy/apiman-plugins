/*
 * Copyright 2016 JBoss Inc
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
package io.apiman.plugins.auth3scale.authrep.apikey;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import com.google.common.util.concurrent.UncheckedExecutionException;
import io.apiman.gateway.engine.beans.ApiRequest;

/**
 * @author Marc Savy {@literal <msavy@redhat.com>}
 */
public class ApiKeyCachingAuthenticator {
    private static final int CAPACITY = 10_000_000;

    private Cache<Integer, Boolean> lruCache = CacheBuilder.newBuilder()
            .initialCapacity(CAPACITY) // TODO sensible capacity?
            .expireAfterWrite(10, TimeUnit.MINUTES) // TODO async?
            .maximumSize(CAPACITY) // LRU capacity
//            .concurrencyLevel(4) TODO
            .build();

    boolean isAuthCached(ApiRequest serviceRequest, String apiKey) {
        try {
            return lruCache.get(getCacheKey(serviceRequest.getApiId(), apiKey,
                    hashArray(serviceRequest)), () -> false); // TODO cache routematcher result into request?
        } catch (ExecutionException e) {
            throw new UncheckedExecutionException(e);
        }
    }

    ApiKeyCachingAuthenticator cache(ApiRequest serviceRequest, String apiKey) {
        lruCache.put(getCacheKey(serviceRequest.getApiId(), apiKey,
                hashArray(serviceRequest)), true);
        return this;
    }

    public ApiKeyCachingAuthenticator invalidate(ApiRequest serviceRequest, String apiKey) { // TODO invalidate will be with what apikey..?
        lruCache.invalidate(getCacheKey(serviceRequest.getApiId(), apiKey,
                hashArray(serviceRequest))); // TODO optmise
        return this;
    }

    private int getCacheKey(Object... objects) {
        return Objects.hash(objects);
    }

    private int hashArray(ApiRequest req) {
        return Arrays.hashCode(req.getApi().getRouteMatcher().match(req.getDestination()));
    }
}
