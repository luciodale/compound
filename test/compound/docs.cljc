(ns compound.docs
  (:require  [clojure.test :as t]
             [lucid.publish :as publish]
             [lucid.publish.theme :as theme]
             [clojure.java.io :as io]
             [hara.io.project :as project]
             [hara.test :refer [fact facts throws-info]]
             [orchestra.spec.test :as st]
             [clojure.spec.alpha :as s]
             [clojure.spec.alpha :as s]))

(st/instrument)
(s/check-asserts true)

[[:chapter {:title "Getting started"}]]

(require '[compound.core :as c])

"We're gonna have to handle a lot of fruit today.
We should probably set up somewhere to store all the information about it."

(fact
  (c/compound {:primary-index-def {:key :id}}) =>
  
  {:primary-index-def {:key :id
                       :on-conflict :compound/replace},
   :primary-index {},
   :secondary-indexes-by-id {},
   :secondary-index-defs-by-id {}})

[[:chapter {:title "Operating on the compound"}]]


[[:section {:title "Adding"}]]

"This compound is a feeling a bit empty, time to add some data."

(fact
  (-> (c/compound {:primary-index-def {:key :id}})
      (c/add-items [{:id 1 :name "bananoes"}
                    {:id 2 :name "grapes"}
                    {:id 3 :name "tomatoes"}])) =>

  {:primary-index-def {:on-conflict :compound/replace, :key :id},
   :primary-index {1 {:id 1, :name "bananoes"},
                   2 {:id 2, :name "grapes"},
                   3 {:id 3, :name "tomatoes"}},
   :secondary-indexes-by-id {},
   :secondary-index-defs-by-id {}})

[[:section {:title "Removing"}]]

"Wait, what are bananoes?? Let's get rid of them."

(fact
  (-> (c/compound {:primary-index-def {:key :id}})
      (c/add-items [{:id 1 :name "bananoes"}
                    {:id 2 :name "grapes"}
                    {:id 3 :name "tomatoes"}])
      (c/remove-keys [1])) =>
  
  {:primary-index-def {:on-conflict :compound/replace, :key :id},
   :primary-index {3 {:id 3, :name "tomatoes"},
                   2 {:id 2, :name "grapes"}},
   :secondary-indexes-by-id {},
   :secondary-index-defs-by-id {}})

[[:section {:title "Reading"}]]

"Looking at the whole data structure every time is a bit visually distracting."

"Get all the indexes using `indexes-by-id`, and particular indexes using `index`"

(fact
  (-> (c/compound {:primary-index-def {:key :id}})
      (c/add-items [{:id 1 :name "bananoes"}
                    {:id 2 :name "grapes"}
                    {:id 3 :name "tomatoes"}])
      (c/remove-keys [1])
      (c/indexes-by-id)) =>

  {:id {3 {:id 3, :name "tomatoes"},
        2 {:id 2, :name "grapes"}}})

(fact
  (-> (c/compound {:primary-index-def {:key :id}})
      (c/add-items [{:id 1 :name "bananoes"}
                    {:id 2 :name "grapes"}
                    {:id 3 :name "tomatoes"}])
      (c/remove-keys [1])
      (c/index :id)) =>

  {3 {:id 3, :name "tomatoes"},
   2 {:id 2, :name "grapes"}})

"Because these built in indexes are just maps, we can operate on them using standard clojure functions"

(fact
  (-> (c/compound {:primary-index-def {:key :id}})
      (c/add-items [{:id 1 :name "bananoes"}
                    {:id 2 :name "grapes"}
                    {:id 3 :name "tomatoes"}])
      (c/remove-keys [1])
      (c/index :id)
      (get 2)) =>
  
  {:id 2, :name "grapes"})

"Isn't that just grapes!"

[[:chapter {:title "Secondary indexes"}]]

"Looking up fruit by id is all well and good, but what if we want to be able to find all fruits of a certain colour, to make a seasonal display? "

"We'll need to add a secondary index, on `:colour`."

(fact
  (-> (c/compound {:primary-index-def {:key :id}
                   :secondary-index-defs [{:key :colour}]})
      (c/add-items [{:id 1 :name "grapes" :colour "green"}
                    {:id 2 :name "bananas" :colour "yellow"}])
      (c/indexes-by-id)) =>
  
  {:colour
   {"green" #{{:id 1, :name "grapes", :colour "green"}},
    "yellow" #{{:id 2, :name "bananas", :colour "yellow"}}},
   :id
   {1 {:id 1, :name "grapes", :colour "green"},
    2 {:id 2, :name "bananas", :colour "yellow"}}})

"*Note: secondary indexes don't have to be added at construction time. Adding them later will index all of the items currently in the compound into the new secondary index.*"
(fact
  (-> (c/compound {:primary-index-def {:key :id}})
      (c/add-items [{:id 1 :name "grapes" :colour "green"}
                    {:id 2 :name "bananas" :colour "yellow"}])
      (c/add-secondary-index {:key :colour})
      (c/indexes-by-id)) =>
  
  {:colour
   {"green" #{{:id 1, :name "grapes", :colour "green"}},
    "yellow" #{{:id 2, :name "bananas", :colour "yellow"}}},
   :id
   {1 {:id 1, :name "grapes", :colour "green"},
    2 {:id 2, :name "bananas", :colour "yellow"}}})

"There are a bunch of different index types built in, to cover common use cases"

[[:section {:title "One to many"}]]

"The one to many index is used when there may be more than one item with the same `:key`. The index is a map with a set for each key value."

"*Note: this is also, the default secondary index type if the `:index-type` is not specified.*"

(fact
  (-> (c/compound {:primary-index-def {:key :id}
                   :secondary-index-defs [{:key :colour
                                           :index-type :compound/one-to-many}]})
      (c/add-items [{:id 1 :name "grapes" :colour "green"}
                    {:id 2 :name "bananas" :colour "yellow"}])
      (c/indexes-by-id)) =>
  
  {:colour
   {"green" #{{:id 1, :name "grapes", :colour "green"}},
    "yellow" #{{:id 2, :name "bananas", :colour "yellow"}}},
   :id
   {1 {:id 1, :name "grapes", :colour "green"},
    2 {:id 2, :name "bananas", :colour "yellow"}}})

[[:section {:title "One to one"}]]

"The one to one index is used when there can only be one item for each `:key`. An error is thrown if an item with a duplicate key is added without first removing the existing one"


(fact
  (-> (c/compound {:primary-index-def {:key :id}
                   :secondary-index-defs [{:key :name
                                           :index-type :compound/one-to-one}]})
      (c/add-items [{:id 1 :name "grapes" :colour "green"}
                    {:id 2 :name "bananas" :colour "yellow"}])
      (c/indexes-by-id)) =>
  
  {:name
   {"grapes" {:id 1, :name "grapes", :colour "green"},
    "bananas" {:id 2, :name "bananas", :colour "yellow"}},
   :id
   {1 {:id 1, :name "grapes", :colour "green"},
    2 {:id 2, :name "bananas", :colour "yellow"}}})


[[:section {:title "Many to one"}]]

"The many to one index is used when there an item's `:key` can have multiple values, but each value can occur at most once"

(fact
  (-> (c/compound {:primary-index-def {:key :id}
                   :secondary-index-defs [{:key :aka
                                           :index-type :compound/many-to-one}]})
      (c/add-items [{:id 1 :name "grapes" :colour "green" :aka #{"green bunches of joy"}}
                    {:id 2 :name "bananas" :colour "yellow" :aka #{"yellow boomerangs" "monkey nourishers"}}])
      (c/indexes-by-id)) =>

  {:aka
   {"monkey nourishers"
    {:id 2,
     :name "bananas",
     :colour "yellow",
     :aka #{"monkey nourishers" "yellow boomerangs"}},
    "yellow boomerangs"
    {:id 2,
     :name "bananas",
     :colour "yellow",
     :aka #{"monkey nourishers" "yellow boomerangs"}},
    "green bunches of joy"
    {:id 1, :name "grapes", :colour "green", :aka #{"green bunches of joy"}}},
   :id
   {1 {:id 1, :name "grapes", :colour "green", :aka #{"green bunches of joy"}},
    2
    {:id 2,
     :name "bananas",
     :colour "yellow",
     :aka #{"monkey nourishers" "yellow boomerangs"}}}})

[[:section {:title "Many to many"}]]

"The many to many index is used when there an item's `:key` can have multiple values, and each value can occur multiple times"

"What goes with cheese?"

(fact
  (-> (c/compound {:primary-index-def {:key :id}
                   :secondary-index-defs [{:key :goes-with
                                           :index-type :compound/many-to-many}]})
      (c/add-items [{:id 1 :name "grapes" :colour "green" :goes-with #{"cheese"}}
                    {:id 2 :name "figs" :colour "green" :goes-with #{"cheese" "ice cream"}}
                    {:id 3 :name "bananas" :colour "yellow" :goes-with #{"pancakes" "ice cream"}}])
      (c/index :goes-with)
      (get "cheese")) =>

  #{{:id 2, :name "figs", :colour "green", :goes-with #{"ice cream" "cheese"}}
    {:id 1, :name "grapes", :colour "green", :goes-with #{"cheese"}}})

"(the answer is not bananas)"

[[:section {:title "Composite indexes"}]]

"Composite indexes are useful when you want to index something by this *then* by *that*"

"They require `:keys`to be a sequence of keys"

[[:subsection {:title "One to many (composite)"}]]
(fact
  (-> (c/compound {:primary-index-def {:key :id}
                   :secondary-index-defs [{:keys [:colour :category]
                                           :index-type :compound/one-to-many-composite}]})
      (c/add-items [{:id 1 :name "grapes" :colour "green" :category "berries"}
                    {:id 2 :name "sloe" :colour "blue" :category "berries"}
                    {:id 3 :name "orange" :colour "orange" :category "citrus"}
                    {:id 4 :name "lemon" :colour "yellow" :category "citrus"}
                    {:id 5 :name "lime" :colour "green" :category "citrus"}])
      (c/index [:colour :category])) =>

  {"orange" {"citrus" #{{:id 3, :name "orange", :colour "orange", :category "citrus"}}},
   "green" {"berries" #{{:id 1, :name "grapes", :colour "green", :category "berries"}},
            "citrus" #{{:id 5, :name "lime", :colour "green", :category "citrus"}}},
   "yellow" {"citrus" #{{:id 4, :name "lemon", :colour "yellow", :category "citrus"}}},
   "blue" {"berries" #{{:id 2, :name "sloe", :colour "blue", :category "berries"}}}})

[[:subsection {:title "One to one (composite)"}]]
(fact
  (-> (c/compound {:primary-index-def {:key :id}
                   :secondary-index-defs [{:keys [:category :display-index]
                                           :index-type :compound/one-to-one-composite}]})
      (c/add-items [{:id 1 :name "grapes" :display-index 1 :category "berries"}
                    {:id 2 :name "sloe" :display-index 2 :category "berries"}
                    {:id 3 :name "orange" :display-index 1 :category "citrus"}
                    {:id 4 :name "lemon" :display-index 2 :category "citrus"}
                    {:id 5 :name "lime" :display-index 3 :category "citrus"}])
      (c/index [:category :display-index])) =>

  {"berries"
   {1 {:id 1, :name "grapes", :display-index 1, :category "berries"},
    2 {:id 2, :name "sloe", :display-index 2, :category "berries"}},
   "citrus"
   {2 {:id 4, :name "lemon", :display-index 2, :category "citrus"},
    3 {:id 5, :name "lime", :display-index 3, :category "citrus"},
    1 {:id 3, :name "orange", :display-index 1, :category "citrus"}}})

[[:chapter {:title "Handling conflict"}]]

"Sometimes we need more control over what happens when we add an item with a key that already exists. Compound some built in behaviour and an extension point for customisation."

[[:section {:title "Replace"}]]

"Using `:compound/replace` for `:on-conflict` will resolve conflicts by removing the previous item with that key from the primary index and all secondary indexes before adding the new item"

(fact
  (-> (c/compound {:primary-index-def {:key :id
                                       :on-conflict :compound/replace}
                   :secondary-index-defs [{:key :name}]})
      (c/add-items [{:id 1 :name "bananas"}
                    {:id 2 :name "grapes"}
                    {:id 3 :name "tomatoes"}
                    {:id 3 :name "oranges"}])
      (c/indexes-by-id)) =>

  {:name
   {"grapes" #{{:id 2, :name "grapes"}},
    "oranges" #{{:id 3, :name "oranges"}},
    "bananas" #{{:id 1, :name "bananas"}}},
   :id
   {1 {:id 1, :name "bananas"},
    2 {:id 2, :name "grapes"},
    3 {:id 3, :name "oranges"}}})

[[:section {:title "Throw"}]]

"Using `:compound/throw` for `:on-conflict` will throw an error if we try and add an item with a key that already exists"

(fact
  (-> (c/compound {:primary-index-def {:key :id
                                       :on-conflict :compound/throw}
                   :secondary-index-defs [{:key :name}]})
      (c/add-items [{:id 1 :name "bananas"}
                    {:id 2 :name "grapes"}
                    {:id 3 :name "tomatoes"}
                    {:id 3 :name "oranges"}])
      (c/indexes-by-id))
  => (throws-info {:existing-item {:id 3 :name "tomatoes"}
                   :new-item {:id 3 :name "oranges"}}))

[[:section {:title "Merge"}]]

"Using `:compound/merge` for `:on-conflict` will call `clojure.core/merge` on the previous item and the new item when the key already exists"

(fact
  (-> (c/compound {:primary-index-def {:key :id
                                       :on-conflict :compound/merge}
                   :secondary-index-defs [{:key :name}]})
      (c/add-items [{:id 1 :name "bananas"}
                    {:id 2 :name "grapes"}
                    {:id 3 :name "tomatoes"}
                    {:id 3 :colour "red"}])
      (c/indexes-by-id)) =>
  
  {:name
   {"grapes" #{{:id 2, :name "grapes"}},
    "bananas" #{{:id 1, :name "bananas"}},
    "tomatoes" #{{:id 3, :name "tomatoes", :colour "red"}}},
   :id
   {1 {:id 1, :name "bananas"},
    2 {:id 2, :name "grapes"},
    3 {:id 3, :name "tomatoes", :colour "red"}}})

[[:section {:title "Custom"}]]

"Custom conflict behaviour can also be defined. This is covered in the extension section."

[[:chapter {:title "Extension" :tag "Extension"}]]

[[:section {:title "Custom keys"}]]

"We want to index our fruit by an SKU, which is a string composite of the first letter of the category (capitalized) and the id padded with 3 zeroes (not really, but just imagine)"

"To do this, first implement the `compound.custom-key/custom-key-fn` multimethod to tell compound how to form the key from the item."

(require '[compound.custom-key :as cu])
(require '[clojure.string :as string])

(defmethod cu/custom-key-fn :sku
  [_ item]
  (let [{:keys [category id]} item]
    (str (string/upper-case (first category)) (format "%03d" id))))

"And then reference it in the index definition, using `:custom-key`"

(fact
  (-> (c/compound {:primary-index-def {:custom-key :sku
                                       :on-conflict :compound/merge}})
      (c/add-items [{:id 1 :name "bananas" :category "Long fruit"}
                    {:id 2 :name "grapes" :category "Small round fruit"}
                    {:id 3 :name "tomatoes" :category "Pretend fruit"}])
      (c/primary-index)) =>

  {"S002" {:id 2, :name "grapes", :category "Small round fruit"},
   "L001" {:id 1, :name "bananas", :category "Long fruit"},
   "P003" {:id 3, :name "tomatoes", :category "Pretend fruit"}})

"The built-in secondary indexes can have a custom keys too. It works the same way."

(fact
  (-> (c/compound {:primary-index-def {:key :id
                                       :on-conflict :compound/merge}
                   :secondary-index-defs [{:custom-key :sku}]})
      (c/add-items [{:id 1 :name "bananas" :category "Long fruit"}
                    {:id 2 :name "grapes" :category "Small round fruit"}
                    {:id 3 :name "tomatoes" :category "Pretend fruit"}])
      (c/index :sku)) =>

  {"S002" #{{:id 2, :name "grapes", :category "Small round fruit"}},
   "L001" #{{:id 1, :name "bananas", :category "Long fruit"}},
   "P003" #{{:id 3, :name "tomatoes", :category "Pretend fruit"}}})

[[:section {:title "Custom conflict behaviour" :tag "custom-conflict"}]]

"If the provided conflict behaviours aren't sufficient, override the `compound.core/on-conflict-fn` multimethod, to tell compound what to do when two items with the same key are found."

(defmethod c/on-conflict-fn :add-quantities
  [_ a b]
  (merge a b {:quantity (+ (get a :quantity) (get b :quantity))}))

"Using custom conflict behaviour to add quantities, we can do this:"

(fact
  (-> (c/compound {:primary-index-def {:key :id
                                       :on-conflict :add-quantities}})
      (c/add-items [{:id 1 :name "bananas" :category "Long fruit" :quantity 1}
                    {:id 1 :name "bananas" :category "Long fruit" :quantity 3}
                    {:id 2 :name "grapes" :category "Small round fruit" :quantity 4}
                    {:id 2 :name "grapes" :category "Small round fruit" :quantity 10}])
      (c/index :id)) =>

  {1 {:id 1, :name "bananas", :category "Long fruit", :quantity 4},
   2 {:id 2, :name "grapes", :category "Small round fruit", :quantity 14}})

[[:section {:title "Custom indexes"}]]

"Compound can be extended with completely custom indexes, for example if you know of a data structure that provides optimized access for the access pattern that you will use e.g. one of [Michal Marczyk's](https://github.com/michalmarczyk) excellent data structures"

"To extend, implement the following multimethods from the `compound.secondary-indexes` namespace."

"* `empty` - the initial value of the index
* `id` - to get a unique id from the index definition
* `add` - to add items to the index, called after items are added to the primary index
* `remove` - to remove items from the index, called when items are removed from the primary index

* `spec` - the spec for the index definition"

"To implement a custom index that indexes *all* attributes of a map"

(require '[compound.secondary-indexes :as csi])
(require '[clojure.spec.alpha :as s])

"Add a spec"

(s/def ::id keyword?)

(defmethod csi/spec :all
  [_]
  (s/keys :opt-un [::id]))

"The id for the index is the provided id, or `:all`"

(defmethod csi/id :all
  [index-def]
  (or (get index-def :id) :all))

"The index will start empty as a plain map"

(defmethod csi/empty :all
  [index-def]
  {})

"When items are added to primary index, store them in the index against every attribute"

(defmethod csi/add :all
  [index index-def added]
  (reduce
    (fn add-item [index item]
      (reduce-kv (fn add-attribute [index k v]
                   (update-in index [k v] (fnil conj #{}) item))
                 index
                 item))
    index
    added))

"When items are removed from the primary index, remove them against every attribute"

(defmethod csi/remove :all
  [index index-def removed]
  (reduce
    (fn remove-item [index item]
      (reduce-kv (fn remove-attribute [index k v]
                   (let [existing-items (get-in index [k v])
                         new-items (disj existing-items item)]
                     (if (empty? new-items)
                       (update index k dissoc v)
                       (assoc-in index [k v] new-items))))
                 index
                 item))
    index
    removed))

"Now we're ready to rock and roll. "

(fact
  (-> (c/compound {:primary-index-def {:key :id
                                       :on-conflict :compound/replace}
                   :secondary-index-defs [{:index-type :all}]})
      (c/add-items [{:id 1 :name "bananas"
                     :category "Long fruit"
                     :quantity 1
                     :ripe? true}
                    {:id 2
                     :name "grapes"
                     :category "Small round fruit"
                     :quantity 4
                     :variety :pinot-grigio}
                    {:id 3
                     :name "oranges"
                     :category "Medium round fruit"
                     :ripe? true
                     :quantity 4}])
      (c/remove-keys [1])
      (c/index :all)
      (get-in [:ripe? true])) =>

  #{{:id 3,
     :name "oranges",
     :category "Medium round fruit",
     :ripe? true,
     :quantity 4}})


[[:chapter {:title "Miscellaneous"}]]

[[:section {:title "Paths as keys"}]]
"The primary index and built in secondary indexes support using paths as keys.
This is helpful if you have a set of nested structures and want to use
a nested value as a key or part of one."

(fact
  (-> (c/compound {:primary-index-def {:key [:product :id]}})
      (c/add-items [{:product {:id 3
                                :name "apples"}
                     :quantity 4}
                    {:product {:id 4
                               :name "bananans"}
                     :quantity 500}])) =>
  {:primary-index-def {:on-conflict :compound/replace, :key [:product :id]},
   :primary-index
   {3 {:product {:id 3, :name "apples"}, :quantity 4},
    4 {:product {:id 4, :name "bananans"}, :quantity 500}},
   :secondary-indexes-by-id {},
   :secondary-index-defs-by-id {}})

(fact
  (-> (c/compound {:primary-index-def {:key [:product :id]}
                   :secondary-index-defs [{:keys [[:product :id] :delivery-date]
                                           :id :products-on-date
                                           :index-type :compound/one-to-one-composite}]})
      (c/add-items [{:product {:id 3
                               :name "apples"}
                     :delivery-date "1964-04-01"
                     :quantity 4}
                    {:product {:id 4
                               :name "bananans"}
                     :delivery-date "1964-04-01"
                     :quantity 500}
                    {:product {:id 4
                               :name "cherries"}
                     :delivery-date "1964-04-02"
                     :quantity 6}])) =>
  {:primary-index-def {:on-conflict :compound/replace, :key [:product :id]},
   :primary-index
   {3
    {:product {:id 3, :name "apples"}, :delivery-date "1964-04-01", :quantity 4},
    4
    {:product {:id 4, :name "cherries"},
     :delivery-date "1964-04-02",
     :quantity 6}},
   :secondary-indexes-by-id
   {:products-on-date
    {4
     {"1964-04-02"
      {:product {:id 4, :name "cherries"},
       :delivery-date "1964-04-02",
       :quantity 6}},
     3
     {"1964-04-01"
      {:product {:id 3, :name "apples"},
       :delivery-date "1964-04-01",
       :quantity 4}}}},
   :secondary-index-defs-by-id
   {:products-on-date
    {:index-type :compound/one-to-one-composite,
     :keys [[:product :id] :delivery-date],
     :id :products-on-date}}})

[[:section {:title "Diffing"}]]

"Some utility functions are provided to help diffing compounds.
This is useful if you have two sources of data which you need to synchronize,
e.g. client and server state"

(fact
  (c/diff (-> (c/compound {:primary-index-def {:key :id}})
              (c/add-items [{:id 1 :name "bananas" :category "Old fruit"}
                            {:id 2 :name "grapes" :category "New fruit"}
                            {:id 4 :name "strawberries" :category "Red fruit"}
                            {:id 5 :name "blueberries" :category "Blue fruit"}]))
          (-> (c/compound {:primary-index-def {:key :id}})
              (c/add-items [{:id 1 :name "bananas" :category "Long fruit"}
                            {:id 2 :name "grapes" :category "Small round fruit"}
                            {:id 3 :name "tomatoes" :category "Pretend fruit"}]))) =>
  {:inserts
   #{{:id 5, :name "blueberries", :category "Blue fruit"}
     {:id 4, :name "strawberries", :category "Red fruit"}},
   :updates
   #{{:source {:id 2, :name "grapes", :category "New fruit"},
      :target {:id 2, :name "grapes", :category "Small round fruit"}}
     {:source {:id 1, :name "bananas", :category "Old fruit"},
      :target {:id 1, :name "bananas", :category "Long fruit"}}},
   :deletes #{{:id 3, :name "tomatoes", :category "Pretend fruit"}}})

[[:section {:title "(experimental) Alternate structure"}]]

"You can get a flatter structure for a compound by passing in `:structure :compound/flat`
when creating it. "

"This makes using compounds in maps nicer because you don't have to switch between
`get` and `(c/index)` and can use `get-in` all the way through"

(fact
  (-> (c/compound {:primary-index-def {:key :id}
                   :secondary-index-defs [{:key :name}
                                          {:key :category}]
                   :structure :compound/flat})
      (c/add-items [{:id 1 :name "bananas" :category "Old fruit"}
                    {:id 2 :name "grapes" :category "New fruit"}
                    {:id 4 :name "strawberries" :category "Red fruit"}
                    {:id 5 :name "blueberries" :category "Blue fruit"}])
      (get-in [:category "Red fruit"]))
  => #{{:id 4, :name "strawberries", :category "Red fruit"}})

(comment
  (publish/publish-all))
