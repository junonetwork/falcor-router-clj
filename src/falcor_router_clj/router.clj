(ns falcor-router-clj.router
  (:require [clojure.core.match :refer [match]]))

(defn key?
  [key]
  (or (string? key) (number? key)))


(defn range?
  [falcor-range]
  (match falcor-range
         { :from _ :to _ } true
         { :to _ } true
         (_ :guard integer?) true
         :else false))


(defn literal?
  [literal]
  (partial = literal))


(defn test-pattern
  [pattern-key key-set]
  (let [key-setv (if (sequential? key-set) key-set (vector key-set))]
    (group-by pattern-key key-setv)))


;; TODO - this could be rewritten as a reduction over the pattern
(defn match-path-set
  ([pattern path-set]
   (if (< (count path-set) (count pattern))
     ;; failed pattern match: path-set too short
     {:unmatched [path-set] :matched [] :remaining []}
     (loop [[pattern-key & rest-pattern] pattern
            [key-set & rest-path-set] path-set
            previous-path-set []
            parsed {:unmatched [] :matched []}]
       (let [{matched true unmatched false} (test-pattern pattern-key key-set)]
         (cond
           ;; failed pattern match: key-set doesn't match pattern
           (empty? matched) {:unmatched [path-set] :matched [] :remaining []}
           ;; successful pattern match
           (empty? rest-pattern) (cond-> parsed
                                   (not (empty? unmatched)) (update :unmatched
                                                                    conj
                                                                    (concat previous-path-set
                                                                            unmatched
                                                                            rest-path-set))
                                   true (update :matched conj matched)
                                   true (assoc :remaining (or rest-path-set [])))
           ;; successful key-set match
           :else (recur rest-pattern
                        rest-path-set
                        (conj previous-path-set key-set)
                        (cond-> parsed
                          (not (empty? unmatched)) (update :unmatched
                                                           conj
                                                           (concat previous-path-set
                                                                   unmatched
                                                                   rest-path-set))
                          true (update :matched conj matched)))))))))


;; (defn is-equal
;;   [actual expected]
;;   (if-not (= actual expected)
;;     (throw (AssertionError. (str "Expected:\t" expected "\n\nActual:\t" actual)))))

(defn merge-parsed
  [parsed-path {:keys [matched unmatched remaining]}]
  (let [new-match {:paths matched :remaining remaining}]
    (cond-> parsed-path
      true (update :unmatched concat unmatched)
      (not (empty? matched)) (update :matched conj new-match))))


(defn match-path-sets
  ([pattern path-sets] (match-path-sets pattern path-sets {}))
  ([pattern [path-set & path-sets] parsed]
   (let [new-parsed (merge-parsed parsed (match-path-set pattern path-set))]
     (if (empty? path-sets)
       new-parsed
       (recur pattern path-sets new-parsed)))))


(defn query-route
  [{:keys [pattern handler]}
   path-sets]
  (let [parsed (match-path-sets pattern path-sets)
        query (handler (map :paths (:matched parsed)))]
    (assoc parsed :query query)))


(defn router
  [routes]
  (fn [path-sets]
    (reduce (fn [result route]
              (let [{:keys [unmatched query]} (query-route route (:unmatched result))]
                (cond-> result
                  true (assoc :unmatched unmatched)
                  (not (nil? query)) (update :queries conj query)
                  (empty? unmatched) reduced)))
            {:unmatched path-sets :queries []}
            routes)))


;; (defn get-resources
;;   [[ids predicates ranges]]
;;   (str ids predicates ranges))

;; (defn get-search
;;   [[query search-ranges predicates predicate-ranges]]
;;   (str query search-ranges predicates predicate-ranges))

;; (def routes [{:pattern [(literal? "resource") key? key? range?] :handler get-resources}
;;              {:pattern [(literal? "search") key? range? key? range?] :handler get-search}
;;              {:pattern [(literal? "search") key? range?] :handler get-search}])

;; (def path-sets [["resource" ["one" "two"] "label" 0]
;;                 ["search" "QUERY" {:to 10} ["label" "age"] 0]])

;; ((router routes) path-sets)
