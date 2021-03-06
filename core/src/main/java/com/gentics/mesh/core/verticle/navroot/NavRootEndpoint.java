package com.gentics.mesh.core.verticle.navroot;

import static com.gentics.mesh.http.HttpConstants.APPLICATION_JSON;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.vertx.core.http.HttpMethod.GET;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.mesh.cli.BootstrapInitializer;
import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.context.impl.InternalRoutingActionContextImpl;
import com.gentics.mesh.core.AbstractProjectEndpoint;
import com.gentics.mesh.etc.RouterStorage;
import com.gentics.mesh.parameter.impl.NavigationParametersImpl;
import com.gentics.mesh.rest.EndpointRoute;

/**
 * Endpoint which returns navigation responses for a given webroot path.
 */	
@Singleton
public class NavRootEndpoint extends AbstractProjectEndpoint {

	private NavRootHandler handler;

	public NavRootEndpoint() {
		super("navroot", null, null);
	}

	@Inject
	public NavRootEndpoint(BootstrapInitializer boot, RouterStorage routerStorage, NavRootHandler handler) {
		super("navroot", boot, routerStorage);
		this.handler = handler;
	}

	@Override
	public String getDescription() {
		return "Provides an endpoint which can be used to retrieve a navigation response";
	}

	@Override
	public void registerEndPoints() {
		secureAll();
		addPathHandler();
	}

	private void addPathHandler() {
		EndpointRoute endpoint = createEndpoint();
		endpoint.pathRegex("\\/(.*)");
		endpoint.method(GET);
		endpoint.description("Return a navigation for the node which is located using the given path.");
		endpoint.setRAMLPath("/{path}");
		endpoint.addUriParameter("path", "Webroot path to the node language variation.", "someFolder/somePage.html");
		endpoint.addQueryParameters(NavigationParametersImpl.class);
		endpoint.produces(APPLICATION_JSON);
		endpoint.exampleResponse(OK, nodeExamples.getNavigationResponse(), "Loaded navigation.");
		endpoint.handler(rc -> {
			InternalActionContext ac = new InternalRoutingActionContextImpl(rc);
			String path = ac.getParameter("param0");
			handler.handleGetPath(ac, path);
		});
	}
}
