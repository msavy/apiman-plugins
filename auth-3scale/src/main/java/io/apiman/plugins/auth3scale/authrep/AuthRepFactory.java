package io.apiman.plugins.auth3scale.authrep;

import io.apiman.gateway.engine.beans.ApiRequest;
import io.apiman.gateway.engine.beans.ApiResponse;
import io.apiman.gateway.engine.policy.IPolicyContext;
import io.apiman.plugins.auth3scale.authrep.executors.ApiKeyAuthExecutor;
import io.apiman.plugins.auth3scale.util.report.batchedreporter.ApiKeyAuthReporter;
import io.apiman.plugins.auth3scale.util.report.batchedreporter.BatchedReporter;

/**
 * @author Marc Savy {@literal <msavy@redhat.com>}
 */
public class AuthRepFactory { 
	
	private ApiKeyAuthReporter apiKeyAuthReporter;

	public AuthRepFactory(BatchedReporter batchedReporter) {
		apiKeyAuthReporter = new ApiKeyAuthReporter();
		batchedReporter.addReporter(apiKeyAuthReporter);		
	}
    
    public AuthRepExecutor<?> createAuth(ApiRequest request, IPolicyContext context) {
    	return new ApiKeyAuthExecutor(request, context);
    }
    
    public AuthRepExecutor<?> createRep(ApiResponse response, ApiRequest request, IPolicyContext context) {
    	return new ApiKeyAuthExecutor(response, request, context).setReporter(apiKeyAuthReporter);
    }
}
