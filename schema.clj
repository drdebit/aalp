(ns assertion-dag.schema
  (:require [loom.graph :as lg])
  (:gen-class))

;; Define the core DAG structure of the Assertive Accounting schema
(def schema-dag
  (-> (lg/digraph)
      ;; Event Context
      (lg/add-edges [:event :has-identifier]
                    [:event :date]
                    [:event :is-asserted-by]
                    [:event :reference])

      ;; Relations to other assertions
      (lg/add-edges [:event :is-allowed-by]
                    [:event :allows]
                    [:event :requires]
                    [:event :expects]
                    [:event :is-required-by]
                    [:event :is-restricted-by]
                    [:event :is-requested-by]
                    [:event :includes]
                    [:event :excludes]
                    [:event :reports]
                    [:event :consumes]
                    [:event :creates])

      ;; Counterparties and Exchanges
      (lg/add-edges [:event :has-counterparty]
                    [:has-counterparty :provides]
                    [:has-counterparty :receives]
                    [:has-counterparty :reports])

      ;; Resource Quantities and Top-level Denominations
      (lg/add-edges [:provides :has-quantity]
                    [:provides :is-denominated-in-unit]
                    [:receives :has-quantity]
                    [:receives :is-denominated-in-unit]
                    [:consumes :has-quantity]
                    [:consumes :is-denominated-in-unit]
                    [:creates :has-quantity]
                    [:creates :is-denominated-in-unit])

      ;; Denomination Hierarchies
      (lg/add-edges [:is-denominated-in-unit :is-denominated-in-monetary-unit]
                    [:is-denominated-in-monetary-unit :USD]
                    [:is-denominated-in-unit :is-denominated-in-physical-unit]
                    [:is-denominated-in-physical-unit :unprinted-t-shirt]
                    [:is-denominated-in-physical-unit :printed-t-shirt]
                    [:is-denominated-in-physical-unit :carbon-emissions]
                    [:carbon-emissions :pound]
                    [:is-denominated-in-unit :is-denominated-in-effort-unit]
                    [:is-denominated-in-effort-unit :is-denominated-in-employee-commitment]
                    [:is-denominated-in-unit :is-denominated-in-intellectual-unit]
                    [:is-denominated-in-intellectual-unit :printable-t-shirt-design]
                    [:is-denominated-in-unit :is-denominated-in-time-unit]
                    [:is-denominated-in-unit :is-denominated-in-energy-unit]
                    [:is-denominated-in-energy-unit :kWh])

      ;; Obligation/Entitlement/Requirement Expectations
      (lg/add-edges [:expects :has-confidence-level])

      ;; Capacity and Expectation
      (lg/add-edges [:allows :event]
                    [:allows :is-restricted-by])

      ;; State Updates
      (lg/add-edges [:modifies :fulfills])

      ;; IP Creation
      (lg/add-edges [:creates :has-identifier]
                    [:creates :consumes])))


