package com.sismics.docs.rest;

import com.sismics.docs.core.dao.ReviewDao;
import com.sismics.docs.core.model.jpa.Review;
import com.sismics.docs.core.model.jpa.Route;
import com.sismics.util.context.ThreadLocalContext;
import com.sismics.util.filter.TokenBasedSecurityFilter;
import com.sismics.util.jpa.EMF;
import org.junit.Assert;
import org.junit.Test;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.persistence.EntityManager;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Test the route resource.
 *
 * @author bgamard, Nicolas Ettlin
 */
public class TestRouteResource extends BaseJerseyTest {
    private String token = null;

    /**
     * Test the route resource.
     */
    @Test
    public void testRouteResource() throws Exception {
        // Login route1
        clientUtil.createUser("route1");
        String route1Token = clientUtil.login("route1");

        // Login admin
        String adminToken = clientUtil.login("admin", "admin", false);

        // Change SMTP configuration to target Wiser
        target().path("/app/config_smtp").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("hostname", "localhost")
                        .param("port", "2500")
                        .param("from", "contact@sismicsdocs.com")
                ), JsonObject.class);

        // Add an ACL READ for route1 with admin on the default workflow
        target().path("/acl").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(new Form()
                        .param("source", "default-document-review")
                        .param("perm", "READ")
                        .param("target", "route1")
                        .param("type", "USER")), JsonObject.class);

        // Get all route models
        JsonObject json = target().path("/routemodel")
                .queryParam("sort_column", "2")
                .queryParam("asc", "false")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, route1Token)
                .get(JsonObject.class);
        JsonArray routeModels = json.getJsonArray("routemodels");
        Assert.assertEquals(1, routeModels.size());

        // Create a document
        json = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, route1Token)
                .put(Entity.form(new Form()
                        .param("title", "My super title document 1")
                        .param("language", "eng")), JsonObject.class);
        String document1Id = json.getString("id");

        // Start the default route on document 1
        json = target().path("/route/start").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, route1Token)
                .post(Entity.form(new Form()
                        .param("documentId", document1Id)
                        .param("routeModelId", routeModels.getJsonObject(0).getString("id"))), JsonObject.class);
        JsonObject step = json.getJsonObject("route_step");
        Assert.assertEquals("Check the document's metadata", step.getString("name"));
        Assert.assertTrue(popEmail().contains("workflow step"));

        // List all documents with route1
        json = target().path("/document/list")
                .queryParam("sort_column", 3)
                .queryParam("asc", true)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, route1Token)
                .get(JsonObject.class);
        JsonArray documents = json.getJsonArray("documents");
        Assert.assertEquals(1, documents.size());
        Assert.assertFalse(documents.getJsonObject(0).getBoolean("active_route"));

        // List all documents with admin
        json = target().path("/document/list")
                .queryParam("sort_column", 3)
                .queryParam("asc", true)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        documents = json.getJsonArray("documents");
        Assert.assertEquals(1, documents.size());
        Assert.assertTrue(documents.getJsonObject(0).getBoolean("active_route"));
        Assert.assertEquals("Check the document's metadata", documents.getJsonObject(0).getString("current_step_name"));

        // Get the route on document 1
        json = target().path("/route")
                .queryParam("documentId", document1Id)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, route1Token)
                .get(JsonObject.class);
        JsonArray routes = json.getJsonArray("routes");
        Assert.assertEquals(1, routes.size());
        JsonObject route = routes.getJsonObject(0);
        Assert.assertEquals("Document review", route.getString("name"));
        Assert.assertNotNull(route.getJsonNumber("create_date"));
        JsonArray steps = route.getJsonArray("steps");
        Assert.assertEquals(3, steps.size());
        step = steps.getJsonObject(0);
        Assert.assertEquals("Check the document's metadata", step.getString("name"));
        Assert.assertEquals("VALIDATE", step.getString("type"));
        Assert.assertTrue(step.isNull("comment"));
        Assert.assertTrue(step.isNull("end_date"));
        Assert.assertTrue(step.isNull("validator_username"));
        Assert.assertTrue(step.isNull("transition"));
        JsonObject target = step.getJsonObject("target");
        Assert.assertEquals("administrators", target.getString("id"));
        Assert.assertEquals("administrators", target.getString("name"));
        Assert.assertEquals("GROUP", target.getString("type"));

        // Get document 1 as route1
        json = target().path("/document/" + document1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, route1Token)
                .get(JsonObject.class);
        JsonObject routeStep = json.getJsonObject("route_step");
        Assert.assertNotNull(routeStep);
        Assert.assertFalse(routeStep.getBoolean("transitionable"));
        Assert.assertEquals("Check the document's metadata", routeStep.getString("name"));

        // Get document 1 as admin
        json = target().path("/document/" + document1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        routeStep = json.getJsonObject("route_step");
        Assert.assertNotNull(routeStep);
        Assert.assertTrue(routeStep.getBoolean("transitionable"));
        Assert.assertEquals("Check the document's metadata", routeStep.getString("name"));

        // Validate the current step with admin
        json = target().path("/route/validate").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("documentId", document1Id)
                        .param("transition", "VALIDATED")), JsonObject.class);
        step = json.getJsonObject("route_step");
        Assert.assertEquals("Add relevant files to the document", step.getString("name"));
        Assert.assertTrue(json.getBoolean("readable"));
        Assert.assertTrue(popEmail().contains("workflow step"));

        // Get the route on document 1
        json = target().path("/route")
                .queryParam("documentId", document1Id)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, route1Token)
                .get(JsonObject.class);
        routes = json.getJsonArray("routes");
        Assert.assertEquals(1, routes.size());
        route = routes.getJsonObject(0);
        Assert.assertNotNull(route.getJsonNumber("create_date"));
        steps = route.getJsonArray("steps");
        Assert.assertEquals(3, steps.size());
        step = steps.getJsonObject(0);
        Assert.assertEquals("VALIDATE", step.getString("type"));
        Assert.assertTrue(step.isNull("comment"));
        Assert.assertFalse(step.isNull("end_date"));
        Assert.assertEquals("admin", step.getString("validator_username"));
        Assert.assertEquals("VALIDATED", step.getString("transition"));

        // Get document 1 as admin
        json = target().path("/document/" + document1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        routeStep = json.getJsonObject("route_step");
        Assert.assertNotNull(routeStep);
        Assert.assertEquals("Add relevant files to the document", routeStep.getString("name"));

        // Validate the current step with admin
        json = target().path("/route/validate").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("documentId", document1Id)
                        .param("transition", "VALIDATED")
                        .param("comment", "OK")), JsonObject.class);
        step = json.getJsonObject("route_step");
        Assert.assertEquals("Approve the document", step.getString("name"));
        Assert.assertTrue(json.getBoolean("readable"));
        Assert.assertTrue(popEmail().contains("workflow step"));

        // Get the route on document 1
        json = target().path("/route")
                .queryParam("documentId", document1Id)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, route1Token)
                .get(JsonObject.class);
        routes = json.getJsonArray("routes");
        Assert.assertEquals(1, routes.size());
        route = routes.getJsonObject(0);
        Assert.assertNotNull(route.getJsonNumber("create_date"));
        steps = route.getJsonArray("steps");
        Assert.assertEquals(3, steps.size());
        step = steps.getJsonObject(1);
        Assert.assertEquals("VALIDATE", step.getString("type"));
        Assert.assertEquals("OK", step.getString("comment"));
        Assert.assertFalse(step.isNull("end_date"));
        Assert.assertEquals("admin", step.getString("validator_username"));
        Assert.assertEquals("VALIDATED", step.getString("transition"));

        // Get document 1 as admin
        json = target().path("/document/" + document1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        routeStep = json.getJsonObject("route_step");
        Assert.assertNotNull(routeStep);
        Assert.assertEquals("Approve the document", routeStep.getString("name"));

        // Validate the current step with admin
        json = target().path("/route/validate").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("documentId", document1Id)
                        .param("transition", "APPROVED")), JsonObject.class);
        Assert.assertFalse(json.containsKey("route_step"));
        Assert.assertTrue(json.getBoolean("readable")); // Admin can read everything
        Assert.assertNull(popEmail()); // Last step does not send any email

        // Get the route on document 1
        json = target().path("/route")
                .queryParam("documentId", document1Id)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, route1Token)
                .get(JsonObject.class);
        routes = json.getJsonArray("routes");
        Assert.assertEquals(1, routes.size());
        route = routes.getJsonObject(0);
        Assert.assertNotNull(route.getJsonNumber("create_date"));
        steps = route.getJsonArray("steps");
        Assert.assertEquals(3, steps.size());
        step = steps.getJsonObject(2);
        Assert.assertEquals("APPROVE", step.getString("type"));
        Assert.assertTrue(step.isNull("comment"));
        Assert.assertFalse(step.isNull("end_date"));
        Assert.assertEquals("admin", step.getString("validator_username"));
        Assert.assertEquals("APPROVED", step.getString("transition"));

        // Get document 1 as admin
        target().path("/document/" + document1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);

        // Get document 1 as route1
        json = target().path("/document/" + document1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, route1Token)
                .get(JsonObject.class);
        Assert.assertFalse(json.containsKey("route_step"));

        // List all documents with route1
        json = target().path("/document/list")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, route1Token)
                .get(JsonObject.class);
        documents = json.getJsonArray("documents");
        Assert.assertEquals(1, documents.size());
        Assert.assertFalse(documents.getJsonObject(0).getBoolean("active_route"));

        // List all documents with admin
        json = target().path("/document/list")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        documents = json.getJsonArray("documents");
        Assert.assertEquals(1, documents.size()); // Admin can read all documents

        // Start the default route on document 1
        target().path("/route/start").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, route1Token)
                .post(Entity.form(new Form()
                        .param("documentId", document1Id)
                        .param("routeModelId", routeModels.getJsonObject(0).getString("id"))), JsonObject.class);
        Assert.assertTrue(popEmail().contains("workflow step"));

        // Get document 1 as route1
        json = target().path("/document/" + document1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, route1Token)
                .get(JsonObject.class);
        Assert.assertTrue(json.containsKey("route_step"));

        // Get document 1 as admin
        Response response = target().path("/document/" + document1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get();
        Assert.assertEquals(Response.Status.OK, Response.Status.fromStatusCode(response.getStatus()));

        // List all documents with route1
        json = target().path("/document/list")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, route1Token)
                .get(JsonObject.class);
        documents = json.getJsonArray("documents");
        Assert.assertEquals(1, documents.size());
        Assert.assertFalse(documents.getJsonObject(0).getBoolean("active_route"));

        // List all documents with admin
        json = target().path("/document/list")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        documents = json.getJsonArray("documents");
        Assert.assertEquals(1, documents.size());
        Assert.assertTrue(documents.getJsonObject(0).getBoolean("active_route"));

        // Search documents with admin
        json = target().path("/document/list")
                .queryParam("search", "workflow:me")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        documents = json.getJsonArray("documents");
        Assert.assertEquals(1, documents.size());

        // Cancel the route on document 1
        target().path("/route")
                .queryParam("documentId", document1Id)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, route1Token)
                .delete(JsonObject.class);

        // Get document 1 as route1
        json = target().path("/document/" + document1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, route1Token)
                .get(JsonObject.class);
        Assert.assertFalse(json.containsKey("route_step"));

        // Get document 1 as admin
        target().path("/document/" + document1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class); // Admin can see all documents

        // List all documents with route1
        json = target().path("/document/list")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, route1Token)
                .get(JsonObject.class);
        documents = json.getJsonArray("documents");
        Assert.assertEquals(1, documents.size());
        Assert.assertFalse(documents.getJsonObject(0).getBoolean("active_route"));

        // List all documents with admin
        json = target().path("/document/list")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        documents = json.getJsonArray("documents");
        Assert.assertEquals(1, documents.size());
    }

    /**
     * Test tag actions on workflow step.
     */
    @Test
    public void testTagActions() {
        // Login admin
        String adminToken = clientUtil.login("admin", "admin", false);

        // Create an Approved tag
        JsonObject json = target().path("/tag").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(new Form()
                        .param("name", "Approved")
                        .param("color", "#ff0000")), JsonObject.class);
        String tagApprovedId = json.getString("id");

        // Create a Pending tag
        json = target().path("/tag").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(new Form()
                        .param("name", "Pending")
                        .param("color", "#ff0000")), JsonObject.class);
        String tagPendingId = json.getString("id");

        // Create a new route model with actions
        json = target().path("/routemodel").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(new Form()
                        .param("name", "Workflow action 1")
                        .param("steps", "[{\"type\":\"APPROVE\",\"transitions\":[{\"name\":\"APPROVED\",\"actions\":[{\"type\":\"ADD_TAG\",\"tag\":\"" + tagApprovedId + "\"}]},{\"name\":\"REJECTED\",\"actions\":[]}],\"target\":{\"name\":\"administrators\",\"type\":\"GROUP\"},\"name\":\"Check the document's metadata\"},{\"type\":\"VALIDATE\",\"transitions\":[{\"name\":\"VALIDATED\",\"actions\":[{\"type\":\"REMOVE_TAG\",\"tag\":\"" + tagPendingId + "\"}]}],\"target\":{\"name\":\"administrators\",\"type\":\"GROUP\"},\"name\":\"Check the document's metadata\"}]")), JsonObject.class);
        String routeModelId = json.getString("id");

        // Create a document
        json = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(new Form()
                        .param("title", "My super title document 1")
                        .param("description", "My super description for document 1")
                        .param("tags", tagPendingId)
                        .param("language", "eng")), JsonObject.class);
        String document1Id = json.getString("id");

        // Start the route on document 1
        json = target().path("/route/start").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("documentId", document1Id)
                        .param("routeModelId", routeModelId)), JsonObject.class);
        JsonObject step = json.getJsonObject("route_step");
        Assert.assertEquals("Check the document's metadata", step.getString("name"));

        // Check tags on document 1
        json = target().path("/document/" + document1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        JsonArray tags = json.getJsonArray("tags");
        Assert.assertEquals(1, tags.size());
        Assert.assertEquals(tagPendingId, tags.getJsonObject(0).getString("id"));

        // Validate the current step with admin
        target().path("/route/validate").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("documentId", document1Id)
                        .param("transition", "APPROVED")), JsonObject.class);

        // Check tags on document 1
        json = target().path("/document/" + document1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        tags = json.getJsonArray("tags");
        Assert.assertEquals(2, tags.size());
        Assert.assertEquals(tagApprovedId, tags.getJsonObject(0).getString("id"));
        Assert.assertEquals(tagPendingId, tags.getJsonObject(1).getString("id"));

        // Validate the current step with admin
        target().path("/route/validate").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("documentId", document1Id)
                        .param("transition", "VALIDATED")), JsonObject.class);

        // Check tags on document 1
        json = target().path("/document/" + document1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        tags = json.getJsonArray("tags");
        Assert.assertEquals(1, tags.size());
        Assert.assertEquals(tagApprovedId, tags.getJsonObject(0).getString("id"));

        // Create a document
        json = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .put(Entity.form(new Form()
                        .param("title", "My super title document 2")
                        .param("tags", tagPendingId)
                        .param("language", "eng")), JsonObject.class);
        String document2Id = json.getString("id");

        // Start the route on document 2
        json = target().path("/route/start").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("documentId", document2Id)
                        .param("routeModelId", routeModelId)), JsonObject.class);
        step = json.getJsonObject("route_step");
        Assert.assertEquals("Check the document's metadata", step.getString("name"));

        // Validate the current step with admin
        target().path("/route/validate").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("documentId", document2Id)
                        .param("transition", "REJECTED")), JsonObject.class);

        // Check tags on document 2
        json = target().path("/document/" + document2Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        tags = json.getJsonArray("tags");
        Assert.assertEquals(1, tags.size());
        Assert.assertEquals(tagPendingId, tags.getJsonObject(0).getString("id"));

        // Validate the current step with admin
        target().path("/route/validate").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .post(Entity.form(new Form()
                        .param("documentId", document2Id)
                        .param("transition", "VALIDATED")), JsonObject.class);

        // Check tags on document 2
        json = target().path("/document/" + document2Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .get(JsonObject.class);
        tags = json.getJsonArray("tags");
        Assert.assertEquals(0, tags.size());

        // Delete the documents
        target().path("/document/" + document1Id)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete(JsonObject.class);
        target().path("/document/" + document2Id)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete(JsonObject.class);

        // Delete the route model
        target().path("/routemodel/" + routeModelId)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, adminToken)
                .delete(JsonObject.class);
    }

    /**
     * Tests that we can add reviews to a document
     */
    @Test
    public void testValidateRouteWithResumeReview() {
        // Setup: Initialize the entity manager (needed so we can query the database from the test)
        EntityManager em = EMF.get().createEntityManager();
        ThreadLocalContext.get().setEntityManager(em);
        em.getTransaction().begin();

        // Setup: Get the access token
        token = clientUtil.login("admin", "admin", false);

        // Setup: create some workflows
        final String oneReviewStepRouteModelId = createRouteModel(
                "Single review workflow",
                "[{\"type\":\"RESUME_REVIEW\",\"transitions\":[{\"name\":\"REVIEWED\",\"actions\":[]}],\"target\":{\"name\":\"administrators\",\"type\":\"GROUP\"},\"name\":\"Please review the resume\"}]"
        );
        final String twoReviewStepsRouteModelId = createRouteModel(
                "Two reviews workflow",
                "[{\"type\":\"RESUME_REVIEW\",\"transitions\":[{\"name\":\"REVIEWED\",\"actions\":[]}],\"target\":{\"name\":\"administrators\",\"type\":\"GROUP\"},\"name\":\"Please review the resume for the first time\"},{\"type\":\"RESUME_REVIEW\",\"transitions\":[{\"name\":\"REVIEWED\",\"actions\":[]}],\"target\":{\"name\":\"administrators\",\"type\":\"GROUP\"},\"name\":\"Please review the resume for the second time\"}]"
        );
        final String noReviewStepsRouteModelId = createRouteModel(
                "No reviews workflow",
                "[{\"type\":\"VALIDATE\",\"transitions\":[{\"name\":\"VALIDATED\",\"actions\":[]}],\"target\":{\"name\":\"administrators\",\"type\":\"GROUP\"},\"name\":\"Please validate\"}]"
        );

        // Setup: create a document
        JsonObject json = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, token)
                .put(Entity.form(new Form()
                        .param("title", "Jane Doe")
                        .param("description", "")
                        .param("language", "eng")
                        .param("create_date", Long.toString(new Date().getTime()))
                ), JsonObject.class);
        String documentId = json.getString("id");
        Assert.assertNotNull(documentId);

        // Test: do the simple workflow on this document
        startWorkflow(documentId, oneReviewStepRouteModelId);
        validateWorkflowStep(
                documentId,
                "REVIEWED",
                "[{\"category\":\"GRE\",\"value\":4},{\"category\":\"GPA\",\"value\":3.5},{\"category\":\"Skills\",\"value\":5},{\"category\":\"Experience\",\"value\":4.5},{\"category\":\"Extracurriculars\",\"value\":4}]"
        );

        // Test: do the workflow with two review steps
        startWorkflow(documentId, twoReviewStepsRouteModelId);
        validateWorkflowStep(
                documentId,
                "REVIEWED",
                "[{\"category\":\"GRE\",\"value\":1},{\"category\":\"GPA\",\"value\":2},{\"category\":\"Skills\",\"value\":3},{\"category\":\"Experience\",\"value\":4},{\"category\":\"Extracurriculars\",\"value\":5}]"
        );
        validateWorkflowStep(
                documentId,
                "REVIEWED",
                "[{\"category\":\"GRE\",\"value\":5},{\"category\":\"GPA\",\"value\":4},{\"category\":\"Skills\",\"value\":3},{\"category\":\"Experience\",\"value\":2},{\"category\":\"Extracurriculars\",\"value\":1}]"
        );

        // Test: do a non-review workflow
        startWorkflow(documentId, noReviewStepsRouteModelId);
        validateWorkflowStep(documentId, "VALIDATED", null);

        // Test: start a review workflow without completing it
        startWorkflow(documentId, oneReviewStepRouteModelId);

        // Get the route and match it

        // Test: get the reviews from the database
        ReviewDao dao = new ReviewDao();
        Map<Route, List<Review>> results = dao.findByDocument(documentId);
        Assert.assertEquals(3, results.entrySet().size());

        // Test: the workflow with one reviews is there
        List<Review> firstWorkflowReviews = find(
                results,
                (route, reviews) -> route.getName().equals("Single review workflow") && reviews.size() == 5
        );

        Assert.assertNotNull(firstWorkflowReviews);
        Review review = find(firstWorkflowReviews, r -> r.getCategory().equals("GPA"));
        Assert.assertNotNull(review);
        Assert.assertEquals(3.5, review.getValue(), 0);

        // Test: the workflow with two reviews is there
        List<Review> secondWorkflowReviews = find(
                results,
                (route, reviews) -> route.getName().equals("Two reviews workflow")
        );
        Assert.assertNotNull(secondWorkflowReviews);
        Assert.assertEquals(10, secondWorkflowReviews.size());
        Assert.assertNotNull(find(secondWorkflowReviews, r -> r.getCategory().equals("Experience") && r.getValue() == 4));
        Assert.assertNotNull(find(secondWorkflowReviews, r -> r.getCategory().equals("Experience") && r.getValue() == 2));

        // Test: the non-review workflow is not there
        Assert.assertNull(find(results, (route, reviews) -> route.getName().equals("No reviews workflow")));

        // Test: The incomplete route is there
        Assert.assertNotNull(find(
                results,
                (route, reviews) -> route.getName().equals("Single review workflow") && reviews.size() == 0
        ));

        // Tear down: delete the workflows
        deleteRouteModel(oneReviewStepRouteModelId);
        deleteRouteModel(twoReviewStepsRouteModelId);
        deleteRouteModel(noReviewStepsRouteModelId);

        // Tear down: delete the document
        json = target().path("/document/" + documentId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, token)
                .delete(JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));
    }

    /**
     * Tests the validation of invalid route data
     */
    @Test
    public void testValidateRouteWithInvalidResumeReview() {
        // Setup: Get the access token
        token = clientUtil.login("admin", "admin", false);

        // Setup: create a workflow
        final String routeModelId = createRouteModel(
                "Single review workflow",
                "[{\"type\":\"RESUME_REVIEW\",\"transitions\":[{\"name\":\"REVIEWED\",\"actions\":[]}],\"target\":{\"name\":\"administrators\",\"type\":\"GROUP\"},\"name\":\"Please review the resume\"}]"
        );

        // Setup: create a document
        JsonObject json = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, token)
                .put(Entity.form(new Form()
                        .param("title", "Jane Doe")
                        .param("description", "")
                        .param("language", "eng")
                        .param("create_date", Long.toString(new Date().getTime()))
                ), JsonObject.class);
        String documentId = json.getString("id");
        Assert.assertNotNull(documentId);

        // Setup: start the workflow
        startWorkflow(documentId, routeModelId);

        // Test: invalid transition name
        invalidWorkflowValidation(
                documentId,
                "APPROVED",
                "[{\"category\": \"GRE\", \"value\": -1}]"
        );

        // Test: invalid JSON data
        invalidWorkflowValidation(
                documentId,
                "REVIEWED",
                "[{category: \"GRE\", value: 4}]"
        );

        // Test: invalid type
        invalidWorkflowValidation(
                documentId,
                "REVIEWED",
                "[{category: null, value: 4}]"
        );
        invalidWorkflowValidation(
                documentId,
                "REVIEWED",
                "[{category: \"GRE\", value: null}]"
        );
        invalidWorkflowValidation(
                documentId,
                "REVIEWED",
                "[{category: 42, value: 4}]"
        );
        invalidWorkflowValidation(
                documentId,
                "REVIEWED",
                "[{category: \"GRE\", value: \"4\"}]"
        );

        // Test: missing properties
        invalidWorkflowValidation(
                documentId,
                "REVIEWED",
                "[{\"value\": 4}]"
        );
        invalidWorkflowValidation(
                documentId,
                "REVIEWED",
                "[{\"category\": \"GRE\"}]"
        );

        // Test: invalid range
        invalidWorkflowValidation(
                documentId,
                "REVIEWED",
                "[{\"category\": \"GRE\", \"value\": -1}]"
        );
        invalidWorkflowValidation(
                documentId,
                "REVIEWED",
                "[{\"category\": \"GRE\", \"value\": Infinity}]"
        );

        // Tear down: delete the workflows
        deleteRouteModel(routeModelId);

        // Tear down: delete the document
        json = target().path("/document/" + documentId).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, token)
                .delete(JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));
    }

    /**
     * Tests that the workflow history get routes for reviews
     */
    @Test
    public void testGetRouteWithReview(){
        // Setup: Initialize the entity manager (needed so we can query the database from the test)
        EntityManager em = EMF.get().createEntityManager();
        ThreadLocalContext.get().setEntityManager(em);
        em.getTransaction().begin();

        // Setup: Get the access token
        token = clientUtil.login("admin", "admin", false);

        // Setup: create some workflows
        final String reviewStepRouteModelId = createRouteModel(
                "Single review workflow",
                "[{\"type\":\"RESUME_REVIEW\",\"transitions\":[{\"name\":\"REVIEWED\",\"actions\":[]}],\"target\":{\"name\":\"administrators\",\"type\":\"GROUP\"},\"name\":\"Please review the resume\"}]"
        );
        final String review2StepRouteModelId = createRouteModel(
                "Single review workflow 2",
                "[{\"type\":\"RESUME_REVIEW\",\"transitions\":[{\"name\":\"REVIEWED\",\"actions\":[]}],\"target\":{\"name\":\"administrators\",\"type\":\"GROUP\"},\"name\":\"Please review the resume 2\"}]"
        );

        // Setup: create a document
        JsonObject json = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, token)
                .put(Entity.form(new Form()
                        .param("title", "John Doe")
                        .param("description", "")
                        .param("language", "eng")
                        .param("create_date", Long.toString(new Date().getTime()))
                ), JsonObject.class);
        String document1Id = json.getString("id");
        Assert.assertNotNull(document1Id);

        //Start one review workflow
        startWorkflow(document1Id, reviewStepRouteModelId);

        // List all documents with admin
        json = target().path("/document/list")
                        .queryParam("sort_column", 3)
                        .queryParam("asc", true)
                        .request()
                        .cookie(TokenBasedSecurityFilter.COOKIE_NAME, token)
                        .get(JsonObject.class);

        JsonArray documents = json.getJsonArray("documents");
        // Check that workflow with review is reflected
        Assert.assertEquals(1, documents.size());

        //Get the document as route1
        json = target().path("/document/" + document1Id)
                        .request()
                        .cookie(TokenBasedSecurityFilter.COOKIE_NAME, token)
                        .get(JsonObject.class);
        Assert.assertTrue(json.containsKey("route_step"));
        Assert.assertEquals("Please review the resume", json.getJsonObject("route_step").getString("name"));
        Assert.assertEquals("RESUME_REVIEW", json.getJsonObject("route_step").getString("type"));

         // Get the route on the document
        json = target().path("/route")
                .queryParam("documentId", document1Id)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, token)
                .get(JsonObject.class);
        JsonArray routes = json.getJsonArray("routes");
        Assert.assertEquals(1, routes.size());
        JsonObject route = routes.getJsonObject(0);
        Assert.assertEquals("Single review workflow", route.getString("name"));
        Assert.assertNotNull(route.getJsonNumber("create_date"));
        JsonArray steps = route.getJsonArray("steps");
        Assert.assertEquals(1, steps.size());
        JsonObject step = steps.getJsonObject(0);
        Assert.assertEquals("Please review the resume", step.getString("name"));
        Assert.assertEquals("RESUME_REVIEW", step.getString("type"));
        Assert.assertTrue(step.isNull("comment"));
        Assert.assertTrue(step.isNull("end_date"));
        Assert.assertTrue(step.isNull("validator_username"));
        Assert.assertTrue(step.isNull("transition"));
        JsonObject target = step.getJsonObject("target");
        Assert.assertEquals("administrators", target.getString("id"));
        Assert.assertEquals("administrators", target.getString("name"));
        Assert.assertEquals("GROUP", target.getString("type"));

        //Validate the step
        validateWorkflowStep(
                document1Id,
                "REVIEWED",
                "[{\"category\":\"GRE\",\"value\":4},{\"category\":\"GPA\",\"value\":3.5},{\"category\":\"Skills\",\"value\":5},{\"category\":\"Experience\",\"value\":4.5},{\"category\":\"Extracurriculars\",\"value\":4}]"
        );
        // Get the route on the document after validation
        json = target().path("/route")
                .queryParam("documentId", document1Id)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, token)
                .get(JsonObject.class);
        routes = json.getJsonArray("routes");
        Assert.assertEquals(1, routes.size());
        route = routes.getJsonObject(0);
        Assert.assertEquals("Single review workflow", route.getString("name"));
        Assert.assertNotNull(route.getJsonNumber("create_date"));
        steps = route.getJsonArray("steps");
        Assert.assertEquals(1, steps.size());
        step = steps.getJsonObject(0);
        Assert.assertEquals("Please review the resume", step.getString("name"));
        Assert.assertEquals("RESUME_REVIEW", step.getString("type"));
        Assert.assertTrue(step.isNull("comment"));
        Assert.assertNotNull(step.getJsonNumber("end_date"));
        Assert.assertEquals("admin", step.getString("validator_username"));
        Assert.assertEquals("REVIEWED", step.getString("transition"));
        target = step.getJsonObject("target");
        Assert.assertEquals("administrators", target.getString("id"));
        Assert.assertEquals("administrators", target.getString("name"));
        Assert.assertEquals("GROUP", target.getString("type"));

        // Setup: create another document under admin
        json = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, token)
                .put(Entity.form(new Form()
                        .param("title", "Jane Doe")
                        .param("description", "")
                        .param("language", "eng")
                        .param("create_date", Long.toString(new Date().getTime()))
                ), JsonObject.class);
        String document2Id = json.getString("id");
        Assert.assertNotNull(document2Id);

        // Create a review route under document 2
        startWorkflow(document2Id, review2StepRouteModelId);

        // List all documents with admin
        json = target().path("/document/list")
                        .queryParam("sort_column", 3)
                        .queryParam("asc", true)
                        .request()
                        .cookie(TokenBasedSecurityFilter.COOKIE_NAME, token)
                        .get(JsonObject.class);
        documents = json.getJsonArray("documents");
        Assert.assertEquals(2, documents.size());

        //Get the route on document 2
        json = target().path("/route")
                .queryParam("documentId", document2Id)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, token)
                .get(JsonObject.class);
        routes = json.getJsonArray("routes");
        Assert.assertEquals(1, routes.size());
        route = routes.getJsonObject(0);
        Assert.assertEquals("Single review workflow 2", route.getString("name"));
        Assert.assertNotNull(route.getJsonNumber("create_date"));
        steps = route.getJsonArray("steps");
        Assert.assertEquals(1, steps.size());
        step = steps.getJsonObject(0);
        Assert.assertEquals("Please review the resume 2", step.getString("name"));
        Assert.assertEquals("RESUME_REVIEW", step.getString("type"));
        Assert.assertTrue(step.isNull("comment"));
        Assert.assertTrue(step.isNull("end_date"));
        Assert.assertTrue(step.isNull("validator_username"));
        Assert.assertTrue(step.isNull("transition"));
        target = step.getJsonObject("target");
        Assert.assertEquals("administrators", target.getString("id"));
        Assert.assertEquals("administrators", target.getString("name"));
        Assert.assertEquals("GROUP", target.getString("type"));


        // Tear down: delete the workflows
        deleteRouteModel(reviewStepRouteModelId);
        deleteRouteModel(review2StepRouteModelId);

        // Tear down: delete the documents
        json = target().path("/document/" + document1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, token)
                .delete(JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));
        
        json = target().path("/document/" + document2Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, token)
                .delete(JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));
    }

    /**
     * Creates a route model (workflow type).
     *
     * @param name  The name of the new route model.
     * @param steps The JSON-formatted steps of the route model.
     *
     * @return The ID of the new route model.
     */
    private String createRouteModel(String name, String steps) {
        JsonObject json = target().path("/routemodel").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, token)
                .put(Entity.form(new Form()
                        .param("name", name)
                        .param("steps", steps)
                ), JsonObject.class);
        String routeModelId = json.getString("id");
        Assert.assertNotNull(routeModelId);
        return routeModelId;
    }

    /**
     * Deletes a route model (workflow type).
     *
     * @param routeModelId The route model ID
     */
    private void deleteRouteModel(String routeModelId) {
        JsonObject json = target().path("/routemodel/" + routeModelId)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, token)
                .delete(JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));
    }

    /**
     * Starts a workflow on a given document.
     *
     * @param documentId   The document ID
     * @param routeModelId The route model ID.
     */
    private void startWorkflow(String documentId, String routeModelId) {
        JsonObject json = target().path("/route/start").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, token)
                .post(Entity.form(new Form()
                        .param("documentId", documentId)
                        .param("routeModelId", routeModelId)
                ), JsonObject.class);
        Assert.assertNotNull("ok", json.getJsonObject("route_step"));
    }

    /**
     * Validates a workflow step.
     *
     * @param documentId The document ID
     * @param transition The transition to use
     * @param ratings    The ratings JSON to send (can be null to send nothing)
     */
    private void validateWorkflowStep(String documentId, String transition, String ratings) {
        Form form = new Form()
                .param("documentId", documentId)
                .param("transition", transition);

        if (ratings != null) {
            form = form.param("ratings", ratings);
        }

        Response response = target().path("/route/validate").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, token)
                .post(Entity.form(form));
        Assert.assertEquals(Response.Status.OK, Response.Status.fromStatusCode(response.getStatus()));
        response.close();
    }

    /**
     * Asserts that the given workflow validation parameters cause an error.
     *
     * @param documentId The document ID
     * @param transition The transition to use
     * @param ratings    The ratings JSON to send
     */
    private void invalidWorkflowValidation(String documentId, String transition, String ratings) {
        Form form = new Form()
                .param("documentId", documentId)
                .param("transition", transition)
                .param("ratings", ratings);

        Response response = target().path("/route/validate").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, token)
                .post(Entity.form(form));
        Assert.assertEquals(Response.Status.BAD_REQUEST, Response.Status.fromStatusCode(response.getStatus()));
        response.close();
    }

    /**
     * Get the value of a map where the entry satisfies some predicate
     *
     * @param map       The map
     * @param predicate The predicate
     * @param <K>       The key type
     * @param <V>       The value type
     *
     * @return The matching value or null if there wasn’t exactly one entry matching the predicate
     */
    private <K, V> V find(Map<K, V> map, BiFunction<K, V, Boolean> predicate) {
        List<Map.Entry<K, V>> matches = map.entrySet().stream()
                .filter(entry -> predicate.apply(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        if (matches.size() != 1) {
            return null;
        }

        return matches.get(0).getValue();
    }

    /**
     * Get the value of a list that matches some predicate
     *
     * @param list      The list
     * @param predicate The predicate
     * @param <T>       The list elements type
     *
     * @return The matching value or null if there wasn’t exactly one element matching the predicate
     */
    private <T> T find(List<T> list, Predicate<T> predicate) {
        List<T> matches = list.stream().filter(predicate).collect(Collectors.toList());

        if (matches.size() != 1) {
            return null;
        }

        return matches.get(0);
    }
}