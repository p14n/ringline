(ns ringline.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [ringline.core :as core]))

(deftest sample-test
  (testing "Sample test to verify Kaocha is working"
    (is (= 1 1))
    (is (= "hello" "hello"))))

