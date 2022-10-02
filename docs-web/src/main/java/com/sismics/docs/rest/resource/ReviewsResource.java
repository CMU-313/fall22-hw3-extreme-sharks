package com.sismics.docs.rest.resource;

import javax.ws.rs.Path;

/**
 * Reviews REST resources.
 * 
 * @author Ziqi Ding
 */
@Path("/reviews")
public class ReviewsResource {
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
     * @apiSuccess {Object[]} ratings Ratings
     * @apiPermission user
     * @apiVersion 1.5.0
     *
     * @param documentId Document ID
     * @return Response
     */
}
