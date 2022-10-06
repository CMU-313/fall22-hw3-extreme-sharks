'use strict';

/**
 * Document view content controller.
 */
angular.module('docs').controller('DocumentViewReviews', function ($scope, $rootScope, $stateParams, Restangular, $dialog, $state, Upload, $translate, $uibModal) {
    $scope.reviewWorkflows = null;

    Restangular.one('reviews/' + $stateParams.id).get().then(function(data) {
        $scope.reviewWorkflows = data.workflows.map(function(workflow) {
            workflow.averages = {};

            workflow.ratings.forEach(function(rating) {
                if (!workflow.averages.hasOwnProperty(rating.category)) {
                    workflow.averages[rating.category] = { count: 0, sum: 0 }
                }
                workflow.averages[rating.category].count += 1
                workflow.averages[rating.category].sum += rating.value
            });

            return workflow;
        });
    });

});