(ns full.json
  #?(:clj (:require
            [cheshire.core :as json]
            [cheshire.generate :as json-gen]
            [camelsnake.core :refer :all]
            [full.core.sugar :refer :all]
            [full.core.time :refer :all]
            [clojure.java.io :as io]
            [clojure.walk :refer [postwalk]])
     :cljs (:require
             [camel-snake-kebab.core :refer [->camelCase ->kebab-case-keyword]]
             [camel-snake-kebab.extras :refer [transform-keys]]
             [goog.json :as goog-json]
             [goog.json.Serializer]
             [clojure.walk :refer [postwalk]]))
  #?(:clj (:import (org.joda.time DateTime LocalDate))))

(defn get-preserved-keys
  ":preserve-keys takes a vector of paths (as in multiple get-in).
  Child keys of those won't be kebabized or transformed
  to keywords. so it will retain whatever casing is in them."
  [json-map key-fn preserve-keys]
  (into {} (for [pres-key preserve-keys
                 :let [pres-val (get json-map pres-key)]
                 :when pres-val]
             [(key-fn pres-key) pres-val])))

(defn- convert-map-keys [json-map key-fn preserve-keys]
  (if (map? json-map)
    (let [preserved-map (get-preserved-keys json-map key-fn preserve-keys)
          walk-fn (fn [[k v]] [(key-fn k) v])
          kebabized-json (postwalk (fn [x] (if (map? x) (into {} (map walk-fn x)) x)) json-map)]
      (merge kebabized-json preserved-map))
    json-map))

(defn convert-keys [json-map key-fn preserve-keys]
  (if (map? json-map)
    (convert-map-keys json-map key-fn preserve-keys)
    (map #(convert-map-keys % key-fn preserve-keys) json-map)))

#?(:clj
   (do
     (json-gen/add-encoder DateTime
                           (fn [c jsonGenerator] (.writeString jsonGenerator (dt->iso-ts c))))

     (json-gen/add-encoder LocalDate
                           (fn [c jsonGenerator] (.writeString jsonGenerator (d->iso-d c))))

     (json-gen/add-encoder Character
                           (fn [c jsonGenerator] (.writeString jsonGenerator (str c))))

     (defn read-json
       "Parses a JSON string and returns a hash-map"
       [raw & {:keys [json-key-fn preserve-keys]
               :or {preserve-keys []}}]
       (let [json-key-fn (or json-key-fn ->kebab-case-keyword)]
         (when raw
           (if (not-empty preserve-keys)
             (-> (json/parse-string raw)
                 (convert-keys json-key-fn preserve-keys))
             (json/parse-string raw json-key-fn)))))

     (defn write-json [obj & {:keys [json-key-fn pretty]
                              :or {json-key-fn ->camelCase}}]
       (json/generate-string obj {:key-fn json-key-fn :pretty pretty})))

   :cljs
   (do
     (defn read-json
       [raw & {:keys [json-key-fn preserve-keys]
               :or {preserve-keys []}}]
       (let [json-key-fn (or json-key-fn ->kebab-case-keyword)]
         (when raw
           (if (not-empty preserve-keys)
             (some-> (goog-json/parse raw)
                     (js->clj)
                     (convert-keys json-key-fn preserve-keys))
             (some-> raw
                     (goog-json/parse raw)
                     (js->clj)
                     (cond->> json-key-fn (transform-keys json-key-fn)))))))

     (defn write-json [obj & {:keys [json-key-fn]
                              :or {json-key-fn ->camelCase}}]
       (-> obj
           (cond->> json-key-fn (transform-keys json-key-fn))
           clj->js
           (->> (.serialize (goog.json.Serializer.)))))))

#?(:clj
   (do
     (defn slurp-json [path]
       (read-json (slurp path)))

     (defn slurp-json-resource [path]
       (slurp-json (io/resource path)))))
