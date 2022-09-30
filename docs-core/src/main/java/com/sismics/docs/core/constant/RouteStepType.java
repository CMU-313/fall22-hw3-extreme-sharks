package com.sismics.docs.core.constant;

/**
 * Route step types.
 *
 * @author bgamard 
 */
public enum RouteStepType {
    /**
     * Approval step with 2 choices.
     */
    APPROVE,
    
    /**
     * Simple validation step, no possible choice.
     */
    VALIDATE,

    /**
     * The reviewer gives a rating for several dimensions of a resume.
     */
    RESUME_REVIEW,
}
