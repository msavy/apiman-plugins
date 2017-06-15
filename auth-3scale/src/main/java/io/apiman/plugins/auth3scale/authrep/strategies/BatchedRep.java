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

import io.apiman.gateway.engine.beans.ApiRequest;
import io.apiman.gateway.engine.beans.ApiResponse;
import io.apiman.gateway.engine.policy.IPolicyContext;
import io.apiman.gateway.engine.vertx.polling.fetchers.threescale.beans.Content;
import io.apiman.plugins.auth3scale.authrep.AbstractAuthRepBase;
import io.apiman.plugins.auth3scale.authrep.AbstractRep;
import io.apiman.plugins.auth3scale.util.report.batchedreporter.ReportData;
import io.apiman.plugins.auth3scale.util.report.batchedreporter.Reporter;

public class BatchedRep extends AbstractRep {
    private Content config;
    private ApiRequest request;
    private ApiResponse response;
    private IPolicyContext context;
    private Object[] keyElems;
    private ReportData report;
    private Reporter<? super ReportData> reporter;

    private final StandardAuthCache standardCache;
    private final BatchedAuthCache heuristicCache;

    public BatchedRep(Content config,
            ApiRequest request,
            ApiResponse response,
            IPolicyContext context,
            StandardAuthCache standardCache,
            BatchedAuthCache heuristicCache) {
        super();
        this.config = config;
        this.request = request;
        this.response = response;
        this.context = context;
        this.standardCache = standardCache;
        this.heuristicCache = heuristicCache;
    }

    @Override
    public AbstractRep rep() {
        reporter.addRecord(report);

//        reporter.flushHandler(result -> {
//            // AUTH_CACHE.invalidate(config, request, );
//            ReportData entry = result.getResult().get(0);
//            standardCache.invalidate(config, entry.getRequest(), keyElems);
//        });
        return this;
    }

    @Override
    public AbstractAuthRepBase setKeyElems(Object... keyElems) {
        this.keyElems = keyElems;
        return this;
    }

    @Override
    public AbstractAuthRepBase setReport(ReportData report) {
        this.report = report;
        return this;
    }

}
