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
     * @api {get} /reviews/:id Get a review
     * @apiName GetReview
     * @apiGroup Review
     * @apiParam {String} id Document ID
     * @apiParam {Booleans} files If true includes files information
     * @apiSuccess {String} id ID
     * @apiSuccess {String} title Title
     * @apiSuccess {String} description Description
     * @apiSuccess {Object[]} categories Categories
     * @apiSuccess {Object[]} ratings Ratings
     * @apiSuccess {Number} reviewers_count Number of reviewers
     * @apiError (client) NotFound Reviews not found
     * @apiPermission none
     * @apiVersion 1.5.0
     *
     * @param documentId Document ID
     * @return Response
     */
}
