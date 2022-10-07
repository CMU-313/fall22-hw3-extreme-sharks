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

            // Overall
            function sum(array) {
                return array.reduce(function(a, b) { return a + b }, 0);
            }

            workflow.averages['Overall'] = {
                count: sum(Object.values(workflow.averages).map(a => a.count)),
                sum: sum(Object.values(workflow.averages).map(a => a.sum)),
            };

            return workflow;
        });
    });

});