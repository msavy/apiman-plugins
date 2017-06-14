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
package io.apiman.plugins.auth3scale.util.report.batchedreporter;

import io.apiman.gateway.engine.async.IAsyncHandler;
import io.apiman.plugins.auth3scale.authrep.AuthRepConstants;
import io.apiman.plugins.auth3scale.util.ParameterMap;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Marc Savy {@literal <msavy@redhat.com>}
 * @param <T> extends ReportData
 */
public class Reporter<T extends ReportData> {
    private final URI endpoint;
    private IAsyncHandler<Void> fullHandler;
    private IAsyncHandler<List<T>> flushHandler;

    protected final Map<Integer, ConcurrentLinkedQueue<T>> reportBuckets = new ConcurrentHashMap<>();
    protected static final int DEFAULT_LIST_CAPAC = 800;
    protected static final int FULL_TRIGGER_CAPAC = 500;
    protected static final int MAX_RECORDS = 1000;

    public Reporter(URI endpoint) {
        this.endpoint = endpoint;
    }

    public List<ReportToSend> encode() {
        List<ReportToSend> encodedReports = new ArrayList<>(reportBuckets.size());
        for (ConcurrentLinkedQueue<T> queue : reportBuckets.values()) {
            if (queue.isEmpty()) {
                continue;
            }
            // Get report
            T reportData = queue.poll();
            // 2 mandatory top-level items
            ParameterMap data = new ParameterMap();
            data.add(AuthRepConstants.SERVICE_TOKEN, reportData.getServiceToken());
            data.add(AuthRepConstants.SERVICE_ID, reportData.getServiceId());
            // Rest is array of transactions
            List<ParameterMap> transactions = new ArrayList<>(); // TODO approximate - size() is O(n) on linkedqueue, so don't use that.
            int i = 0;
            do {
                transactions.add(reportData.toParameterMap());
                i++;
                reportData = queue.poll();
            } while (reportData != null && i < MAX_RECORDS);
            // Get underlying array.
            data.add(AuthRepConstants.TRANSACTIONS, transactions.toArray(new ParameterMap[0]));
            // System.out.println("data about to be encoded... " + reportData);
            encodedReports.add(new ReportToSendImpl(endpoint, data));
        }
        return encodedReports;
    }

    public Reporter<T> addRecord(T record) {
        ConcurrentLinkedQueue<T> reportGroup = reportBuckets.computeIfAbsent(record.bucketId(), k -> new ConcurrentLinkedQueue<>());
        reportGroup.add(record);
        // This is just approximate, we don't care whether it's somewhat out.
        if (reportGroup.size() >= FULL_TRIGGER_CAPAC) {
            full();
        }
        return this;
    }

    public Reporter<T> flushHandler(IAsyncHandler<List<T>> flushHandler) {
        this.flushHandler = flushHandler;
        return this;
    }

    public Reporter<T> setFullHandler(IAsyncHandler<Void> fullHandler) {
        this.fullHandler = fullHandler;
        return this;
    }

    protected void full() {
        fullHandler.handle((Void) null);
    }

    private static final class ReportToSendImpl implements ReportToSend {
        private final URI endpoint;
        private final ParameterMap data;
        private IAsyncHandler<Void> flushHandler;

        ReportToSendImpl(URI endpoint,
                ParameterMap data,
                IAsyncHandler<ParameterMap> flushHandler) {
            this.endpoint = endpoint;
            this.data = data;
            this.flushHandler = flushHandler;
        }

        @Override
        public String getData() {
            return data.encode();
        }

        @Override
        public String getEncoding() {
            return "application/x-www-form-urlencoded"; //$NON-NLS-1$
        }

        @Override
        public URI getEndpoint() {
            return endpoint;
        }

        @Override
        public void flushed() {

        }
    }

}
