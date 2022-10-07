package com.sismics.docs.rest;

import java.util.*;

import com.google.common.base.Predicate;
import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;
import com.sismics.docs.core.dao.ReviewDao;
import com.sismics.docs.core.model.jpa.Review;
import com.sismics.docs.core.model.jpa.Route;
import com.sismics.docs.core.util.DirectoryUtil;
import com.sismics.util.context.ThreadLocalContext;
import com.sismics.util.filter.TokenBasedSecurityFilter;
import com.sismics.util.jpa.EMF;


import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;
import org.joda.time.format.DateTimeFormat;
import org.junit.Assert;
import org.junit.Test;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.persistence.EntityManager;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.InputStream;
import java.util.Date;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class TestReviewsResource extends BaseJerseyTest {
    /**
     * Test the reviews resource.
     * 
     * @throws Exception e
     * 
     * @author Ziqi Ding
     */

    private String token = null;
    
    @Test
    public void testDocumentResource() throws Exception {
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
                "[{\"category\":\"GRE\",\"value\":4},{\"category\":\"GPA\",\"value\":3.5},{\"category\":\"Skills\",\"value\":5},{\"category\":\"Experience\",\"value\":4.5},{\"category\":\"Extracurriculars\",\"value\":4}]",
                "Looks good."
        );

        // Test: do the workflow with two review steps
        startWorkflow(documentId, twoReviewStepsRouteModelId);
        validateWorkflowStep(
                documentId,
                "REVIEWED",
                "[{\"category\":\"GRE\",\"value\":1},{\"category\":\"GPA\",\"value\":2},{\"category\":\"Skills\",\"value\":3},{\"category\":\"Experience\",\"value\":4},{\"category\":\"Extracurriculars\",\"value\":5}]",
                "This is horrible!"
        );
        validateWorkflowStep(
                documentId,
                "REVIEWED",
                "[{\"category\":\"GRE\",\"value\":5},{\"category\":\"GPA\",\"value\":4},{\"category\":\"Skills\",\"value\":3},{\"category\":\"Experience\",\"value\":2},{\"category\":\"Extracurriculars\",\"value\":1}]",
                "Good candidate."
        );

        // Test: do a non-review workflow
        startWorkflow(documentId, noReviewStepsRouteModelId);
        validateWorkflowStep(documentId, "VALIDATED", null, null);

        // Test: start a review workflow without completing it
        startWorkflow(documentId, oneReviewStepRouteModelId);

        json = target().path("/reviews/" + documentId)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, token)
                .get(JsonObject.class);
        // Verifying the one-step workflow
        JsonArray workflows = json.getJsonArray("workflows");
        Assert.assertEquals(3, workflows.size());
        JsonObject workflow1 = workflows.getJsonObject(2);
        Assert.assertEquals("Single review workflow", workflow1.getString("name"));
        JsonArray ratings1 = workflow1.getJsonArray("ratings");
        Assert.assertEquals(5, ratings1.size());
        Assert.assertEquals("GRE", ratings1.getJsonObject(0).getString("category")); // First rating
        Assert.assertEquals(4, ratings1.getJsonObject(0).getJsonNumber("value").doubleValue(),0);
        Assert.assertEquals("GPA", ratings1.getJsonObject(1).getString("category"));
        Assert.assertEquals(3.5, ratings1.getJsonObject(1).getJsonNumber("value").doubleValue(),0);
        Assert.assertEquals("Skills", ratings1.getJsonObject(2).getString("category"));
        Assert.assertEquals(5, ratings1.getJsonObject(2).getJsonNumber("value").doubleValue(),0);
        Assert.assertEquals("Experience", ratings1.getJsonObject(3).getString("category"));
        Assert.assertEquals(4.5, ratings1.getJsonObject(3).getJsonNumber("value").doubleValue(),0);
        Assert.assertEquals("Extracurriculars", ratings1.getJsonObject(4).getString("category"));
        Assert.assertEquals(4, ratings1.getJsonObject(4).getJsonNumber("value").doubleValue(),0);
        
        JsonArray comments1 = workflow1.getJsonArray("comments");
        Assert.assertEquals(1, comments1.size());
        String author1 = comments1.getJsonObject(0).getString("author");
        Assert.assertEquals("admin", author1);
        String contents1 = comments1.getJsonObject(0).getString("contents");
        Assert.assertEquals("Looks good.", contents1);


        // Verifying the two-step workflow
        JsonObject workflow2 = workflows.getJsonObject(1);
        Assert.assertEquals("Two reviews workflow", workflow2.getString("name"));
        JsonArray ratings = workflow2.getJsonArray("ratings");
        Assert.assertEquals(10, ratings.size());
        Assert.assertEquals("GRE", ratings.getJsonObject(0).getString("category")); // First rating
        Assert.assertEquals(1, ratings.getJsonObject(0).getJsonNumber("value").doubleValue(),0);
        Assert.assertEquals("GPA", ratings.getJsonObject(1).getString("category"));
        Assert.assertEquals(2, ratings.getJsonObject(1).getJsonNumber("value").doubleValue(),0);
        Assert.assertEquals("Skills", ratings.getJsonObject(2).getString("category"));
        Assert.assertEquals(3, ratings.getJsonObject(2).getJsonNumber("value").doubleValue(),0);
        Assert.assertEquals("Experience", ratings.getJsonObject(3).getString("category"));
        Assert.assertEquals(4, ratings.getJsonObject(3).getJsonNumber("value").doubleValue(),0);
        Assert.assertEquals("Extracurriculars", ratings.getJsonObject(4).getString("category"));
        Assert.assertEquals(5, ratings.getJsonObject(4).getJsonNumber("value").doubleValue(),0);
        Assert.assertEquals("GRE", ratings.getJsonObject(5).getString("category")); // First rating
        Assert.assertEquals(5, ratings.getJsonObject(5).getJsonNumber("value").doubleValue(),0);
        Assert.assertEquals("GPA", ratings.getJsonObject(6).getString("category"));
        Assert.assertEquals(4, ratings.getJsonObject(6).getJsonNumber("value").doubleValue(),0);
        Assert.assertEquals("Skills", ratings.getJsonObject(7).getString("category"));
        Assert.assertEquals(3, ratings.getJsonObject(7).getJsonNumber("value").doubleValue(),0);
        Assert.assertEquals("Experience", ratings.getJsonObject(8).getString("category"));
        Assert.assertEquals(2, ratings.getJsonObject(8).getJsonNumber("value").doubleValue(),0);
        Assert.assertEquals("Extracurriculars", ratings.getJsonObject(9).getString("category"));
        Assert.assertEquals(1, ratings.getJsonObject(9).getJsonNumber("value").doubleValue(),0);
        
        JsonArray comments2 = workflow2.getJsonArray("comments");
        Assert.assertEquals(2, comments2.size());
        String author2_1 = comments2.getJsonObject(0).getString("author");
        Assert.assertEquals("admin", author2_1);
        String contents2_1 = comments2.getJsonObject(0).getString("contents");
        Assert.assertEquals("This is horrible!", contents2_1);
        String author2_2 = comments2.getJsonObject(1).getString("author");
        Assert.assertEquals("admin", author2_2);
        String contents2_2 = comments2.getJsonObject(1).getString("contents");
        Assert.assertEquals("Good candidate.", contents2_2);

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
    private void validateWorkflowStep(String documentId, String transition, String ratings, String comments) {
        Form form = new Form()
                .param("documentId", documentId)
                .param("transition", transition);

        if (ratings != null) {
            form = form.param("ratings", ratings);
        }
        if (comments != null) {
            form = form.param("comment", comments);
        }

        Response response = target().path("/route/validate").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, token)
                .post(Entity.form(form));
        Assert.assertEquals(Response.Status.OK, Response.Status.fromStatusCode(response.getStatus()));
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


