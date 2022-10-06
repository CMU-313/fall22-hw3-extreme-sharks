'use strict';

/**
 * Document view content controller.
 */
angular.module('docs').controller('DocumentViewReviews', function ($scope, $rootScope, $stateParams, Restangular, $dialog, $state, Upload, $translate, $uibModal) {
    $scope.ratings = [
        {category: 'GPA', value: 2},
        {category: 'GPA', value: 3},
        {category: 'GPA', value: 5},
        {category: 'Experience', value: 5},
        {category: 'GRE', value: 1},
        {category: 'Extracurriculars', value: 2},
        {category: 'Extracurriculars', value: 4},
    ];


    $scope.averages = {};
    $scope.ratings.forEach(function(rating) {
        if (!$scope.averages.hasOwnProperty(rating.category)) {
            $scope.averages[rating.category] = { count: 0, sum: 0 }
        }
        $scope.averages[rating.category].count += 1
        $scope.averages[rating.category].sum += rating.value
    });

    console.log($scope.averages)


});