package com.gentics.mesh.search;

import static com.gentics.mesh.test.TestDataProvider.PROJECT_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.util.Date;

import org.codehaus.jettison.json.JSONException;
import org.junit.Test;

import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.core.rest.node.NodeListResponse;
import com.gentics.mesh.core.rest.node.NodeResponse;
import com.gentics.mesh.graphdb.NoTx;
import com.gentics.mesh.parameter.impl.PagingParametersImpl;
import com.gentics.mesh.parameter.impl.VersioningParameters;

public class NodeSearchEndpointFTest extends AbstractNodeSearchEndpointTest {

	@Test
	public void testSearchAndSort() throws Exception {
		try (NoTx noTx = db.noTx()) {
			fullIndex();
		}

		String json = "{";
		json += "				\"sort\" : {";
		json += "			      \"created\" : {\"order\" : \"asc\"}";
		json += "			    },";
		json += "			    \"query\":{";
		json += "			        \"bool\" : {";
		json += "			            \"must\" : {";
		json += "			                \"term\" : { \"schema.name\" : \"content\" }";
		json += "			            }";
		json += "			        }";
		json += "			    }";
		json += "			}";

		String search = json;
		NodeListResponse response = call(() -> getClient().searchNodes(PROJECT_NAME, search, new VersioningParameters().draft()));
		assertNotNull(response);
		assertFalse(response.getData().isEmpty());

		long lastCreated = 0;
		for (NodeResponse nodeResponse : response.getData()) {
			Date date = Date.from(Instant.parse(nodeResponse.getCreated()));
			if (lastCreated > date.getTime()) {
				fail("Found entry that was not sorted by create timestamp. Last entry: {" + lastCreated + "} current entry: {"
						+ nodeResponse.getCreated() + "}");
			} else {
				lastCreated = date.getTime();
			}
			assertEquals("content", nodeResponse.getSchema().getName());
		}
	}

	@Test
	public void testSearchContent() throws Exception {
		try (NoTx noTx = db.noTx()) {
			fullIndex();
		}

		NodeListResponse response = call(() -> getClient().searchNodes(PROJECT_NAME, getSimpleQuery("the"),
				new PagingParametersImpl().setPage(1).setPerPage(2), new VersioningParameters().draft()));
		assertEquals(1, response.getData().size());
		assertEquals(1, response.getMetainfo().getTotalCount());
		for (NodeResponse nodeResponse : response.getData()) {
			assertNotNull(nodeResponse);
			assertNotNull(nodeResponse.getUuid());
		}

	}

	/**
	 * Test searching for a node which is only persisted in the search index but no longer in the graph.
	 * 
	 * @throws InterruptedException
	 * @throws JSONException
	 */
	@Test
	public void testSearchMissingVertex() throws Exception {
		try (NoTx noTx = db.noTx()) {
			fullIndex();

			Node node = content("honda nr");
			node.remove();
			NodeListResponse response = call(() -> getClient().searchNodes(getSimpleQuery("the"), new PagingParametersImpl().setPage(1).setPerPage(2)));
			assertEquals(0, response.getData().size());
			assertEquals(0, response.getMetainfo().getTotalCount());
		}
	}

}