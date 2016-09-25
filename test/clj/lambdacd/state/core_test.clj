(ns lambdacd.state.core-test
  (:require [clojure.test :refer :all]
            [shrubbery.core :refer [mock received?]]
            [lambdacd.state.core :as s]
            [lambdacd.testsupport.data :refer [some-ctx-with]]
            [lambdacd.state.protocols :as state-protocols]
            [lambdacd.internal.pipeline-state :as legacy-pipeline-state]))

(def some-build-number 42)
(def some-step-id [0])
(def some-step-result {:foo :bat})
(def some-structure {:some :structure})
(def some-pipeline-def `(foo))

(deftest consume-step-result-update-test
  (testing "that calls to a StepResultUpdateConsumer will just pass through"
    (let [component (mock state-protocols/StepResultUpdateConsumer)]
      (s/consume-step-result-update (some-ctx-with :pipeline-state-component component)
                                    some-build-number some-step-id some-step-result)
      (is (received? component state-protocols/consume-step-result-update [some-build-number some-step-id some-step-result]))))
  (testing "that calls to a legacy PipelineStateComponent be mapped to that method"
    (let [component (mock legacy-pipeline-state/PipelineStateComponent)]
      (s/consume-step-result-update (some-ctx-with :pipeline-state-component component)
                                    some-build-number some-step-id some-step-result)
      (is (received? component legacy-pipeline-state/update [some-build-number some-step-id some-step-result])))))

(deftest consume-pipeline-structure-test
  (testing "that calls to a PipelineStructureConsumer will just pass through"
    (let [component (mock state-protocols/PipelineStructureConsumer)]
      (s/consume-pipeline-structure (some-ctx-with :pipeline-state-component component)
                                    some-build-number some-structure)
      (is (received? component state-protocols/consume-pipeline-structure [some-build-number some-structure]))))
  (testing "that calls to a legacy PipelineStateComponent are ignored"
    (let [component (mock legacy-pipeline-state/PipelineStateComponent)]
      (s/consume-pipeline-structure (some-ctx-with :pipeline-state-component component)
                                    some-build-number some-structure))))

(deftest next-build-number-test
  (testing "that calls to a BuildNumberSource will just pass through"
    (let [component (mock state-protocols/NextBuildNumberSource)]
      (s/next-build-number (some-ctx-with :pipeline-state-component component))
      (is (received? component state-protocols/next-build-number []))))
  (testing "that calls to a legacy PipelineStateComponent be mapped to that method"
    (let [component (mock legacy-pipeline-state/PipelineStateComponent)]
      (s/next-build-number (some-ctx-with :pipeline-state-component component))
      (is (received? component legacy-pipeline-state/next-build-number [])))))

(deftest all-build-numbers-test
  (testing "that calls to QueryAllBuildNumbersSource will just pass through"
    (let [component (mock state-protocols/QueryAllBuildNumbersSource)]
      (s/all-build-numbers (some-ctx-with :pipeline-state-component component))
      (is (received? component state-protocols/all-build-numbers []))))
  (testing "compatibility with PipelineStateComponent"
    (testing "an empty state"
      (let [component (mock legacy-pipeline-state/PipelineStateComponent {:get-all {}})]
        (is (= [] (s/all-build-numbers (some-ctx-with :pipeline-state-component component))))))
    (testing "state with builds returns sorted list"
      (let [component (mock legacy-pipeline-state/PipelineStateComponent {:get-all {5 {}
                                                                                    1 {}
                                                                                    2 {}}})]
        (is (= [1 2 5] (s/all-build-numbers (some-ctx-with :pipeline-state-component component))))))))

(deftest get-build-test
  (testing "that calls to QueryBuildSource will just pass through"
    (let [component (mock state-protocols/QueryBuildSource {:get-build {:some :state}})]
      (is (= {:some :state} (s/get-build (some-ctx-with :pipeline-state-component component) 1)))
      (is (received? component state-protocols/get-build [1]))))
  (testing "compatibility with PipelineStateComponent"
    (testing "an empty state"
      (let [component (mock legacy-pipeline-state/PipelineStateComponent {:get-all {}})]
        (is (= {:step-results nil} (s/get-build (some-ctx-with :pipeline-state-component component) 1)))))
    (testing "state with builds returns sorted list"
      (let [component (mock legacy-pipeline-state/PipelineStateComponent {:get-all {1 {:some :build}}})]
        (is (= {:step-results {:some :build}} (s/get-build (some-ctx-with :pipeline-state-component component) 1)))))))

(deftest get-step-results
  (testing "that it returns step-results"
    (with-redefs [s/get-build (constantly {:step-results {:step :results}})]
      (is (= {:step :results} (s/get-step-results nil nil))))))

(deftest get-step-result-test
  (testing "that we can get a simple step-result"
    (with-redefs [s/get-build (constantly {:step-results {[2 1] {:step :result}}})]
      (is (= {:step :result} (s/get-step-result nil 1 [2 1]))))))

(deftest get-pipeline-structure-test
  (testing "that we get the pipeline structure if it is contained in the build"
    (with-redefs [s/get-build (constantly {:pipeline-structure some-structure})]
      (is (= some-structure (s/get-pipeline-structure nil some-build-number)))))
  (testing "that we get the current pipeline structure if the build result doesn't contain it (for compatibility reasons)"
    (with-redefs [s/get-build (constantly {:no :structure-here})]
      (is (= [{:name             "foo"
               :type             :unknown
               :has-dependencies false
               :step-id          `(1)}] (s/get-pipeline-structure (some-ctx-with :pipeline-def some-pipeline-def) some-build-number))))))

