package com.sismics.docs.core.dao;

import com.sismics.docs.core.model.jpa.Review;
import com.sismics.docs.core.model.jpa.Route;
import com.sismics.util.context.ThreadLocalContext;
import org.apache.commons.lang3.tuple.Pair;

import javax.persistence.EntityManager;
import javax.persistence.Query;
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
    @SuppressWarnings("unchecked")
    public Map<Route, List<Review>> findByDocument(String documentId) {
        EntityManager em = ThreadLocalContext.get().getEntityManager(); // NULL?
        Query q = em.createNativeQuery("select\n" +
                "    RTE_ID_C, RTE_IDDOCUMENT_C, RTE_NAME_C, RTE_CREATEDATE_D, RTE_DELETEDATE_D,\n" +
                "    REV_ID_C, REV_IDROUTESTEP_C, REV_CATEGORY_C, REV_VALUE_DBL \n" +
                "from T_ROUTE\n" +
                "inner join T_ROUTE_STEP\n" +
                "    on RTP_IDROUTE_C = RTE_ID_C\n" +
                "    and RTP_TYPE_C = 'RESUME_REVIEW'\n" +
                "    and RTP_DELETEDATE_D is null\n" +
                "left join T_REVIEW on REV_IDROUTESTEP_C = RTP_ID_C\n" +
                "where RTE_IDDOCUMENT_C = :documentId");
        q.setParameter("documentId", documentId);

        HashMap<String, Pair<Route, List<Review>>> byRouteId = new HashMap<>(); // route id → (route, reviews)
        for (Object[] row : (List<Object[]>)q.getResultList()) {
            int i = 0;

            // Create the route object
            String routeId = (String)row[i++];
            if (!byRouteId.containsKey(routeId)) {
                Route route = new Route();
                route.setId(routeId);
                route.setDocumentId((String)row[i++]);
                route.setName((String)row[i++]);
                route.setCreateDate((Date)row[i++]);
                route.setDeleteDate((Date)row[i++]);

                byRouteId.put(routeId, Pair.of(route, new LinkedList<>()));
            }

            // Create the review object
            i = 5;
            if (row[i] == null) continue; // workflow without reviews
            Review review = new Review();
            review.setId((String)row[i++]);
            review.setRouteStepId((String)row[i++]);
            review.setCategory((String)row[i++]);
            review.setValue((Double)row[i++]);

            byRouteId.get(routeId).getRight().add(review);
        }

        HashMap<Route, List<Review>> result = new HashMap<>();
        byRouteId.forEach((routeId, pair) -> result.put(pair.getLeft(), pair.getRight()));
        return result;
    }
}