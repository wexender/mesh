package com.gentics.mesh.core.verticle.node;

import static com.gentics.mesh.core.data.relationship.Permission.CREATE_PERM;
import static com.gentics.mesh.core.data.relationship.Permission.DELETE_PERM;
import static com.gentics.mesh.core.data.relationship.Permission.READ_PERM;
import static com.gentics.mesh.demo.DemoDataProvider.PROJECT_NAME;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import io.vertx.core.Future;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.gentics.mesh.api.common.PagingInfo;
import com.gentics.mesh.core.AbstractRestVerticle;
import com.gentics.mesh.core.data.NodeFieldContainer;
import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.core.data.root.NodeRoot;
import com.gentics.mesh.core.data.service.ServerSchemaStorage;
import com.gentics.mesh.core.rest.common.GenericMessageResponse;
import com.gentics.mesh.core.rest.error.HttpStatusCodeErrorException;
import com.gentics.mesh.core.rest.node.NodeCreateRequest;
import com.gentics.mesh.core.rest.node.NodeListResponse;
import com.gentics.mesh.core.rest.node.NodeRequestParameters;
import com.gentics.mesh.core.rest.node.NodeResponse;
import com.gentics.mesh.core.rest.node.NodeUpdateRequest;
import com.gentics.mesh.core.rest.node.field.HTMLField;
import com.gentics.mesh.core.rest.node.field.StringField;
import com.gentics.mesh.core.rest.node.field.impl.StringFieldImpl;
import com.gentics.mesh.core.rest.schema.SchemaReference;
import com.gentics.mesh.core.verticle.project.ProjectNodeVerticle;
import com.gentics.mesh.test.AbstractRestVerticleTest;
import com.gentics.mesh.util.BlueprintTransaction;
import com.gentics.mesh.util.FieldUtil;

public class ProjectNodeVerticleTest extends AbstractRestVerticleTest {

	@Autowired
	private ProjectNodeVerticle verticle;

	@Autowired
	private ServerSchemaStorage schemaStorage;

	private NodeRoot nodeRoot;

	@Before
	public void setup() throws Exception {
		super.setupVerticleTest();
		nodeRoot = boot.nodeRoot();
	}

	@Override
	public AbstractRestVerticle getVerticle() {
		return verticle;
	}

	// Create tests

	@Test
	public void testCreateNodeWithBogusLanguageCode() throws HttpStatusCodeErrorException, Exception {

		NodeCreateRequest request = new NodeCreateRequest();
		SchemaReference schemaReference = new SchemaReference();
		schemaReference.setName("content");
		schemaReference.setUuid(data().getSchemaContainer("content").getUuid());
		request.setLanguage("BOGUS");
		request.getFields().put("name", FieldUtil.createStringField("some name"));
		request.getFields().put("filename", FieldUtil.createStringField("new-page.html"));
		request.getFields().put("content", FieldUtil.createStringField("Blessed mealtime again!"));

		request.setSchema(schemaReference);
		request.setParentNodeUuid(data().getFolder("news").getUuid());

		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, request);
		latchFor(future);
		expectException(future, BAD_REQUEST, "node_no_language_found", "BOGUS");
	}

	@Test
	public void testCreateNode() throws Exception {

		Node parentNode = data().getFolder("news");
		assertNotNull(parentNode);
		assertNotNull(parentNode.getUuid());

		NodeCreateRequest request = new NodeCreateRequest();
		request.setSchema(new SchemaReference("content", data().getSchemaContainer("content").getUuid()));
		request.setLanguage("en");
		request.getFields().put("title", FieldUtil.createStringField("some title"));
		request.getFields().put("name", FieldUtil.createStringField("some name"));
		request.getFields().put("filename", FieldUtil.createStringField("new-page.html"));
		request.getFields().put("content", FieldUtil.createStringField("Blessed mealtime again!"));

		request.setParentNodeUuid(parentNode.getUuid());

		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, request);
		latchFor(future);
		assertSuccess(future);
		NodeResponse restNode = future.result();
		test.assertMeshNode(request, restNode);

	}

	@Test
	public void testCreateReadDeleteNode() throws Exception {
		NodeCreateRequest request = new NodeCreateRequest();
		SchemaReference schemaReference = new SchemaReference();
		schemaReference.setName("content");
		schemaReference.setUuid(data().getSchemaContainer("content").getUuid());
		request.setSchema(schemaReference);

		request.setLanguage("de");
		request.getFields().put("name", FieldUtil.createStringField("some name"));
		request.getFields().put("title", FieldUtil.createStringField("Title"));
		request.getFields().put("filename", FieldUtil.createStringField("new-page.html"));
		request.getFields().put("content", FieldUtil.createStringField("Blessed mealtime again!"));
		request.setParentNodeUuid(data().getFolder("news").getUuid());

		// Create node
		NodeRequestParameters parameters = new NodeRequestParameters();
		parameters.setLanguages("de");
		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, request, parameters);
		latchFor(future);
		assertSuccess(future);
		NodeResponse restNode = future.result();
		test.assertMeshNode(request, restNode);

		nodeRoot.findByUuid(restNode.getUuid(), rh -> {
			Node node = rh.result();
			assertNotNull(node);
			test.assertMeshNode(request, node);
			// Load the node again
			Future<NodeResponse> future2 = getClient().findNodeByUuid(PROJECT_NAME, restNode.getUuid(), parameters);
				latchFor(future2);
				assertSuccess(future2);
				NodeResponse restNode2 = future2.result();
				test.assertMeshNode(node, restNode2);

				// Delete the node
				Future<GenericMessageResponse> deleteFut = getClient().deleteNode(PROJECT_NAME, restNode2.getUuid());
				latchFor(deleteFut);
				assertSuccess(deleteFut);
				expectMessageResponse("node_deleted", deleteFut, restNode2.getUuid());
				data().getMeshRoot().getNodeRoot().findByUuid(restNode2.getUuid(), rh2 -> {
					assertNull("The node should have been deleted.", rh2.result());
				});
			});

	}

	@Test
	public void testCreateNodeWithMissingParentNodeUuid() throws Exception {

		NodeCreateRequest request = new NodeCreateRequest();
		SchemaReference schemaReference = new SchemaReference();
		schemaReference.setName("node");
		request.setSchema(schemaReference);
		request.getFields().put("name", FieldUtil.createStringField("some name"));
		request.getFields().put("filename", FieldUtil.createStringField("new-page.html"));
		request.getFields().put("content", FieldUtil.createStringField("Blessed mealtime again!"));
		request.setSchema(new SchemaReference("content", data().getSchemaContainer("content").getUuid()));

		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, request);
		latchFor(future);
		expectException(future, BAD_REQUEST, "node_missing_parentnode_field");

	}

	@Test
	public void testCreateNodeWithMissingPermission() throws Exception {

		// Revoke create perm
		info.getRole().revokePermissions(data().getFolder("news"), CREATE_PERM);

		NodeCreateRequest request = new NodeCreateRequest();
		SchemaReference schemaReference = new SchemaReference("content", data().getSchemaContainer("content").getUuid());
		request.setSchema(schemaReference);
		request.getFields().put("name", FieldUtil.createStringField("some name"));
		request.getFields().put("filename", FieldUtil.createStringField("new-page.html"));
		request.getFields().put("content", FieldUtil.createStringField("Blessed mealtime again!"));
		request.setSchema(new SchemaReference("content", data().getSchemaContainer("content").getUuid()));

		request.setParentNodeUuid(data().getFolder("news").getUuid());

		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, request);
		latchFor(future);
		expectException(future, FORBIDDEN, "error_missing_perm", data().getFolder("news").getUuid());
	}

	// Read tests

	/**
	 * Test default paging parameters
	 * 
	 * @throws Exception
	 */
	@Test
	public void testReadNodesDefaultPaging() throws Exception {

		Future<NodeListResponse> future = getClient().findNodes(PROJECT_NAME);
		latchFor(future);
		assertSuccess(future);
		NodeListResponse restResponse = future.result();
		assertNotNull(restResponse);
		assertEquals(25, restResponse.getMetainfo().getPerPage());
		assertEquals(1, restResponse.getMetainfo().getCurrentPage());
		assertEquals(25, restResponse.getData().size());
	}

	@Test
	public void testReadNodes() throws Exception {

		Node parentNode = data().getFolder("2015");
		// Don't grant permissions to the no perm node. We want to make sure that this one will not be listed.
		Node noPermNode = parentNode.create();
		noPermNode.setCreator(info.getUser());
		assertNotNull(noPermNode.getUuid());

		int perPage = 11;
		Future<NodeListResponse> future = getClient().findNodes(PROJECT_NAME, new PagingInfo(3, perPage));
		latchFor(future);
		assertSuccess(future);
		NodeListResponse restResponse = future.result();
		assertEquals(perPage, restResponse.getData().size());

		// Extra Nodes + permitted node
		int totalNodes = data().getNodeCount();
		int totalPages = (int) Math.ceil(totalNodes / (double) perPage);
		assertEquals("The response did not contain the correct amount of items", perPage, restResponse.getData().size());
		assertEquals(3, restResponse.getMetainfo().getCurrentPage());
		assertEquals(totalNodes, restResponse.getMetainfo().getTotalCount());
		assertEquals(totalPages, restResponse.getMetainfo().getPageCount());
		assertEquals(perPage, restResponse.getMetainfo().getPerPage());

		List<NodeResponse> allNodes = new ArrayList<>();
		for (int page = 1; page <= totalPages; page++) {
			Future<NodeListResponse> pageFuture = getClient().findNodes(PROJECT_NAME, new PagingInfo(page, perPage));
			latchFor(pageFuture);
			assertSuccess(pageFuture);
			restResponse = pageFuture.result();
			allNodes.addAll(restResponse.getData());
		}
		assertEquals("Somehow not all users were loaded when loading all pages.", totalNodes, allNodes.size());

		// Verify that the no_perm_node is not part of the response
		final String noPermNodeUUID = noPermNode.getUuid();
		List<NodeResponse> filteredUserList = allNodes.parallelStream().filter(restNode -> restNode.getUuid().equals(noPermNodeUUID))
				.collect(Collectors.toList());
		assertTrue("The no perm node should not be part of the list since no permissions were added.", filteredUserList.size() == 0);

		Future<NodeListResponse> pageFuture = getClient().findNodes(PROJECT_NAME, new PagingInfo(-1, 25));
		latchFor(pageFuture);
		expectException(pageFuture, BAD_REQUEST, "error_invalid_paging_parameters");

		pageFuture = getClient().findNodes(PROJECT_NAME, new PagingInfo(0, 25));
		latchFor(pageFuture);
		expectException(pageFuture, BAD_REQUEST, "error_invalid_paging_parameters");

		pageFuture = getClient().findNodes(PROJECT_NAME, new PagingInfo(1, 0));
		latchFor(pageFuture);
		expectException(pageFuture, BAD_REQUEST, "error_invalid_paging_parameters");

		pageFuture = getClient().findNodes(PROJECT_NAME, new PagingInfo(1, -1));
		latchFor(pageFuture);
		expectException(pageFuture, BAD_REQUEST, "error_invalid_paging_parameters");

		pageFuture = getClient().findNodes(PROJECT_NAME, new PagingInfo(4242, 25));
		latchFor(pageFuture);
		assertSuccess(pageFuture);
		NodeListResponse list = pageFuture.result();
		assertEquals(4242, list.getMetainfo().getCurrentPage());
		assertEquals(0, list.getData().size());
		assertEquals(25, list.getMetainfo().getPerPage());
		assertEquals(3, list.getMetainfo().getPageCount());
		assertEquals(data().getNodeCount(), list.getMetainfo().getTotalCount());

	}

	@Test
	public void testReadNodesWithoutPermissions() throws Exception {

		// TODO add node that has no perms and check the response
		Future<NodeListResponse> future = getClient().findNodes(PROJECT_NAME, new PagingInfo());
		latchFor(future);
		assertSuccess(future);
		NodeListResponse restResponse = future.result();

		int nElements = restResponse.getData().size();
		assertEquals("The amount of elements in the list did not match the expected count", 25, nElements);
		assertEquals(1, restResponse.getMetainfo().getCurrentPage());
		assertEquals(3, restResponse.getMetainfo().getPageCount());
		assertEquals(25, restResponse.getMetainfo().getPerPage());
		assertEquals(data().getNodeCount(), restResponse.getMetainfo().getTotalCount());
	}

	@Test
	public void testReadNodeByUUID() throws Exception {

		getClient().getClientSchemaStorage().addSchema(data().getSchemaContainer("folder").getSchema());

		Node node = data().getFolder("2015");
		assertNotNull(node);
		assertNotNull(node.getUuid());

		Future<NodeResponse> future = getClient().findNodeByUuid(PROJECT_NAME, node.getUuid());
		latchFor(future);
		assertSuccess(future);
		test.assertMeshNode(node, future.result());
	}

	@Test
	public void testReadNodeByUUIDSingleLanguage() throws Exception {
		getClient().getClientSchemaStorage().addSchema(data().getSchemaContainer("folder").getSchema());
		Node node = data().getFolder("products");

		NodeRequestParameters parameters = new NodeRequestParameters();
		parameters.setLanguages("de");
		Future<NodeResponse> future = getClient().findNodeByUuid(PROJECT_NAME, node.getUuid(), parameters);
		latchFor(future);
		assertSuccess(future);
		NodeResponse restNode = future.result();
		test.assertMeshNode(node, restNode);

		String nameText = ((StringFieldImpl) restNode.getFields().get("name")).getString();
		assertEquals("Produkte", nameText);
	}

	@Test
	public void testReadNodeWithBogusLanguageCode() throws Exception {

		Node node = data().getFolder("2015");
		assertNotNull(node);
		assertNotNull(node.getUuid());

		NodeRequestParameters parameters = new NodeRequestParameters();
		parameters.setLanguages("blabla", "edgsdg");

		Future<NodeResponse> future = getClient().findNodeByUuid(PROJECT_NAME, node.getUuid(), parameters);
		latchFor(future);
		expectException(future, BAD_REQUEST, "error_language_not_found", "blabla");

	}

	@Test
	public void testReadNodeByUUIDWithoutPermission() throws Exception {
		Node node = data().getFolder("2015");
		try (BlueprintTransaction tx = new BlueprintTransaction(fg)) {
			info.getRole().revokePermissions(node, READ_PERM);
			tx.success();
		}
		Future<NodeResponse> future = getClient().findNodeByUuid(PROJECT_NAME, node.getUuid());
		latchFor(future);
		expectException(future, FORBIDDEN, "error_missing_perm", node.getUuid());
	}

	@Test
	public void testReadNodeByBogusUUID() throws Exception {

		Future<NodeResponse> future = getClient().findNodeByUuid(PROJECT_NAME, "bogusUUID");
		latchFor(future);
		expectException(future, NOT_FOUND, "object_not_found_for_uuid", "bogusUUID");
	}

	@Test
	public void testReadNodeByInvalidUUID() throws Exception {
		String uuid = "dde8ba06bb7211e4897631a9ce2772f5";

		Future<NodeResponse> future = getClient().findNodeByUuid(PROJECT_NAME, uuid);
		latchFor(future);
		expectException(future, NOT_FOUND, "object_not_found_for_uuid", uuid);

	}

	// Update

	@Test
	public void testUpdateNode() throws HttpStatusCodeErrorException, Exception {
		Node node = data().getFolder("2015");
		NodeUpdateRequest request = new NodeUpdateRequest();
		SchemaReference schemaReference = new SchemaReference();
		schemaReference.setName("folder");
		schemaReference.setUuid(data().getSchemaContainer("folder").getUuid());
		request.setSchema(schemaReference);
		request.setLanguage("en");

		assertEquals("2015", node.getFieldContainer(data().getEnglish()).getString("name").getString());

		final String newName = "english renamed name";
		request.getFields().put("name", FieldUtil.createStringField(newName));
		// final String newFilename = "new-name.html";
		// request.getFields().put("filename", FieldUtil.createStringField(newFilename));
		// final String newContent = "english renamed content!";
		// request.getFields().put("content", FieldUtil.createStringField(newContent));

		NodeRequestParameters parameters = new NodeRequestParameters();
		parameters.setLanguages("de", "en");
		Future<NodeResponse> future = getClient().updateNode(PROJECT_NAME, node.getUuid(), request, parameters);
		latchFor(future);
		assertSuccess(future);
		NodeResponse restNode = future.result();
		assertNotNull(restNode);
		// assertEquals(newFilename, ((StringField) restNode.getFields().get("filename")).getText());
		assertEquals(newName, node.getFieldContainer(data().getEnglish()).getString("name").getString());
		assertEquals(newName, ((StringField) restNode.getFields().get("name")).getString());

		// assertEquals(newContent, ((HTMLField) restNode.getFields().get("content")).getHTML());
	}

	@Test
	public void testUpdateNodeWithExtraField() throws UnknownHostException, InterruptedException {

		Node parentNode = data().getFolder("news");
		assertNotNull(parentNode);
		assertNotNull(parentNode.getUuid());

		NodeCreateRequest request = new NodeCreateRequest();
		request.setSchema(new SchemaReference("content", data().getSchemaContainer("content").getUuid()));
		request.setLanguage("en");
		request.getFields().put("title", FieldUtil.createStringField("some title"));
		request.getFields().put("extrafield", FieldUtil.createStringField("some extra field value"));
		request.getFields().put("name", FieldUtil.createStringField("some name"));
		request.getFields().put("filename", FieldUtil.createStringField("new-page.html"));
		request.getFields().put("content", FieldUtil.createStringField("Blessed mealtime again!"));

		request.setParentNodeUuid(parentNode.getUuid());

		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, request);
		latchFor(future);
		expectException(future, BAD_REQUEST, "NYD", "???");
		NodeResponse restNode = future.result();
		test.assertMeshNode(request, restNode);

	}

	@Test
	public void testUpdateNodeWithMissingField() throws UnknownHostException, InterruptedException {
		Node parentNode = data().getFolder("news");
		assertNotNull(parentNode);
		assertNotNull(parentNode.getUuid());

		NodeCreateRequest request = new NodeCreateRequest();
		request.setSchema(new SchemaReference("content", data().getSchemaContainer("content").getUuid()));
		request.setLanguage("en");
		// title field is missing
		request.getFields().put("name", FieldUtil.createStringField("some name"));
		request.getFields().put("filename", FieldUtil.createStringField("new-page.html"));
		request.getFields().put("content", FieldUtil.createStringField("Blessed mealtime again!"));

		request.setParentNodeUuid(parentNode.getUuid());

		Future<NodeResponse> future = getClient().createNode(PROJECT_NAME, request);
		latchFor(future);
		expectException(future, BAD_REQUEST, "NYD", "???");
		NodeResponse restNode = future.result();
		test.assertMeshNode(request, restNode);
	}

	@Test
	public void testUpdateNodeWithExtraJson() throws HttpStatusCodeErrorException, Exception {
		NodeUpdateRequest request = new NodeUpdateRequest();
		SchemaReference schemaReference = new SchemaReference();
		schemaReference.setName("content");
		request.setSchema(schemaReference);
		final String newFilename = "new-name.html";
		final String newName = "english renamed name";
		final String newContent = "english renamed content!";

		request.getFields().put("name", FieldUtil.createStringField(newName));
		request.getFields().put("filename", FieldUtil.createStringField(newFilename));
		request.getFields().put("content", FieldUtil.createStringField(newContent));

		Node node = data().getFolder("2015");

		NodeRequestParameters parameters = new NodeRequestParameters();
		parameters.setLanguages("de", "en");
		Future<NodeResponse> future = getClient().updateNode(PROJECT_NAME, node.getUuid(), request, parameters);
		latchFor(future);
		assertSuccess(future);

		NodeResponse restNode = future.result();

		assertEquals(newFilename, ((StringField) restNode.getFields().get("filename")).getString());
		assertEquals(newName, ((StringField) restNode.getFields().get("name")).getString());
		assertEquals(newContent, ((HTMLField) restNode.getFields().get("content")).getHTML());

		// TODO Reload and update
		NodeFieldContainer englishContainer = node.getOrCreateFieldContainer(data().getEnglish());
		assertEquals(newFilename, englishContainer.getString("displayName").getString());
		assertEquals(newName, englishContainer.getString("name").getString());
		assertEquals(newContent, englishContainer.getString("content").getString());

	}

	// Delete

	@Test
	public void testDeleteNode() throws Exception {

		Node node = data().getFolder("2015");
		String uuid = node.getUuid();
		Future<GenericMessageResponse> future = getClient().deleteNode(PROJECT_NAME, uuid);
		latchFor(future);
		assertSuccess(future);

		expectMessageResponse("node_deleted", future, uuid);
		nodeRoot.findByUuid(uuid, rh -> {
			assertNull(rh.result());	
		});
	}

	@Test
	public void testDeleteNodeWithNoPerm() throws Exception {

		String uuid = data().getFolder("2015").getUuid();
		try (BlueprintTransaction tx = new BlueprintTransaction(fg)) {
			Node node = data().getFolder("2015");
			info.getRole().revokePermissions(node, DELETE_PERM);
			tx.success();
		}

		Future<GenericMessageResponse> future = getClient().deleteNode(PROJECT_NAME, uuid);
		latchFor(future);

		expectException(future, FORBIDDEN, "error_missing_perm", uuid);
		nodeRoot.findByUuid(uuid, rh -> {
			assertNotNull(rh.result());	
		});
	}
}