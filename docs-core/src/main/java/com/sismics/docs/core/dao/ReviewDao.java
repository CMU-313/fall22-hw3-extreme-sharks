package com.sismics.docs.core.dao;

import com.sismics.docs.core.model.jpa.Review;
import com.sismics.docs.core.model.jpa.Route;
import com.sismics.docs.core.model.jpa.RouteModel;
import com.sismics.util.context.ThreadLocalContext;
import org.apache.commons.lang.NotImplementedException;

import javax.persistence.EntityManager;
import java.util.*;

/**
 * Review DAO.
 *
 * @author Nicolas Ettlin
 */
public class ReviewDao {
    /**
     * Creates a new review.
     *
     * @param review The review
     *
     * @return New ID
     */
    public String create(Review review) {
        // Create the UUID
        review.setId(UUID.randomUUID().toString());

        // Create the review
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        em.persist(review);

        return review.getId();
    }

    /**
     * Returns the list of all route models.
     *
     * @return Map containing the reviews for each route on the document with a resume review step
     */
    public Map<Route, List<Review>> findByDocument(String documentId) {
        throw new NotImplementedException();
    }
}
