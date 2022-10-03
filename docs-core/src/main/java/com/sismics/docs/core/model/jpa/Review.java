package com.sismics.docs.core.model.jpa;

import com.google.common.base.MoreObjects;
import com.sismics.docs.core.constant.AclType;
import com.sismics.docs.core.constant.PermType;

import javax.persistence.*;
import java.util.Date;

/**
 * Review entity.
 * 
 * @author Nicolas Ettlin
 */
@Entity
@Table(name = "T_REVIEW")
public class Review {
    /**
     * The review’s unique identifier.
     */
    @Id
    @Column(name = "REV_ID_C", length = 36)
    private String id;

    /**
     * The ID of the {@link RouteStep} this review belongs to.
     */
    @Column(name = "REV_IDROUTESTEP_C", nullable = false, length = 36)
    private String routeStepId;

    /**
     * The name of the category this review refers to.
     */
    @Column(name = "REV_CATEGORY_C", nullable = false, length = 36)
    private String category;

    /**
     * The value of this review (1–5).
     */
    @Column(name = "REV_VALUE_DBL", nullable = false)
    private double value;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRouteStepId() {
        return routeStepId;
    }

    public void setRouteStepId(String routeStepId) {
        this.routeStepId = routeStepId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("routeStepId", routeStepId)
                .add("category", category)
                .add("value", value)
                .toString();
    }
}
