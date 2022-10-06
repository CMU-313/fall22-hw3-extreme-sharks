package com.sismics.docs.rest.resource;

import java.util.*;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import com.sismics.docs.core.dao.ReviewDao;
import com.sismics.docs.core.dao.ReviewDao.ReviewComment;
import com.sismics.docs.core.model.jpa.Review;
import com.sismics.docs.core.model.jpa.Route;

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
     * @apiSuccess {Object[]} workflows The workflow array that shows all the workflows and reviews
     * @apiSuccess {String} workflows.name The name of one of the workflows.
     * @apiSuccess {Object[]} workflows.ratings The ratings array that contains all the ratings for a workflow on the document
     * @apiSuccess {String} workflows.ratings.category The category that a specific rating belongs to
     * @apiSuccess {Number} workflows.ratings.value The value of a specific rating (Range: 1-5)
     * @apiSuccess {Object[]} workflows.comments The comments array that contains all the comments for a workflow on the document
     * @apiSuccess {Number} workflows.numRatings The number of ratings inside a workflow
     * @apiSuccess {String} workflows.comments.author The name of the author
     * @apiSuccess {String} workflows.comments.contents The contents of the comment
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
        
        // Review info
        ReviewDao reviewDao = new ReviewDao();
        Map<Route, List<Review>> reviews = reviewDao.findByDocument(documentId);
        if (reviews == null) {
            throw new NotFoundException();
        }

        // Add basic document info
        JsonObjectBuilder reviewsJson = Json.createObjectBuilder()
                .add("id", documentId);

        
        JsonArrayBuilder workflowsJson = Json.createArrayBuilder();

        // Iterate through the reviews aray to add workflow
        for (Map.Entry<Route, List<Review>> entry : reviews.entrySet()){
            Route route = entry.getKey();

            // get ratings
            JsonArrayBuilder ratingsJson = Json.createArrayBuilder();
            for (Review r : entry.getValue()){
                ratingsJson.add(Json.createObjectBuilder()
                                    .add("category", r.getCategory())
                                    .add("value", r.getValue()));
            }

            // get comments
            JsonArrayBuilder commentsJson = Json.createArrayBuilder();
            List<ReviewComment> comments = reviewDao.getComments(route.getId());
            for (ReviewComment c : comments){
                commentsJson.add(Json.createObjectBuilder()
                                    .add("author", c.author)
                                    .add("contents", c.contents));
            }
            
            // workflow jsons
            workflowsJson.add(Json.createObjectBuilder()
                            .add("name", route.getName())
                            .add("ratings", ratingsJson)
                            .add("numRatings", reviewDao.reviewsCount(route.getId()))
                            .add("comments", commentsJson));
        }

        reviewsJson.add("workflows", workflowsJson);

        return Response.ok().entity(reviewsJson.build()).build();
    }
    
}
