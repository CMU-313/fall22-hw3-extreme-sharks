package com.sismics.docs.rest.resource;

import java.util.*;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.sismics.docs.core.constant.PermType;
import com.sismics.docs.core.dao.DocumentDao;
import com.sismics.docs.core.dao.ReviewDao;
import com.sismics.docs.core.dao.dto.DocumentDto;
import com.sismics.docs.core.model.jpa.Review;
import com.sismics.docs.core.model.jpa.Route;
import com.sismics.util.JsonUtil;

/**
 * Reviews REST resources.
 * 
 * @author Ziqi Ding
 */
@Path("/reviews")
public class ReviewsResource extends BaseResource {
    /**
     * Returns the reviews for a document.
     *
     * @api {get} /reviews/:id Get the reviews for a document.
     * @apiName GetReview
     * @apiGroup Review
     * @apiParam {String} id Document ID
     * @apiSuccess {String} id ID
     * @apiSuccess {String} title Title
     * @apiSuccess {String} description Description
     * @apiSuccess {Object[]} workflows The workflow array that shows all the workflows and reviews
     * @apiSuccess {String} workflows.name The name of one of the workflows.
     * @apiSuccess {Object[]} workflows.ratings The ratings array that contains all the ratings for a workflow on the document
     * @apiSuccess {String} workflows.ratings.category The category that a specific rating belongs to
     * @apiSuccess {Number} workflows.ratings.value The value of a specific rating (Range: 1-5)
     * @apiSuccess {String[]} workflows.comments The comments array that contains all the comments for a workflow on the document
     * @apiPermission user
     * @apiVersion 1.5.0
     *
     * @param documentId Document ID
     * @return Response
     */
    @GET
    @Path("{id: [a-z0-9\\-]+}")
    public Response get(@PathParam("id") String documentId){
        authenticate();

        // Document info
        DocumentDao documentDao = new DocumentDao();
        DocumentDto documentDto = documentDao.getDocument(documentId, PermType.READ, getTargetIdList(null));

        // Review info
        ReviewDao reviewDao = new ReviewDao();
        Map<Route, List<Review>> reviews = reviewDao.findByDocument(documentId);
        if (reviews == null) {
            throw new NotFoundException();
        }
        List<String> comments = reviewDao.getComments(documentId);

        // Add basic document info
        JsonObjectBuilder reviewsJson = Json.createObjectBuilder()
                .add("id", documentDto.getId())
                .add("title", documentDto.getTitle())
                .add("description", JsonUtil.nullable(documentDto.getDescription()));

        
        JsonArrayBuilder workflowsJson = Json.createArrayBuilder();
        List<Review> ratings = new ArrayList<>();;

        // Iterate through the review aray to add ratings
        for (Map.Entry<Route, List<Review>> entry : reviews.entrySet()){
            ratings.addAll(entry.getValue());
        }
        JsonArrayBuilder ratingsJson = Json.createArrayBuilder();
        for (Review r : ratings){
            ratingsJson.add(Json.createObjectBuilder()
                                .add("category", r.getCategory())
                                .add("value", r.getValue()));
        }

        // Iterate through the reviews aray to add workflow
        for (Map.Entry<Route, List<Review>> entry : reviews.entrySet()){
            Route route = entry.getKey();

            workflowsJson.add(Json.createObjectBuilder()
                            .add("name", route.getName())
                            .add("ratings", ratingsJson));
        }

        // Iterate through the comments array to add comments to workflow
        JsonArrayBuilder commentsJson = Json.createArrayBuilder();
        for (String c : comments){
            commentsJson.add(c);
        }
        workflowsJson.add(Json.createObjectBuilder()
                                .add("comments", commentsJson));

        return Response.ok().entity(reviewsJson.build()).build();
    }
    
}
