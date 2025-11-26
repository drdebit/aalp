(ns assertion-dag.example
  (:gen-class)
  (:require [loom.graph :as lg]))

;; Business Formation
(def ucc
  {:event
   {:has-identifier "State-UCC"
    :date "2024-01-01"
    :is-asserted-by "State Legislature"
    :reference "State Code Title 11"
    :allows {:event
             {:includes 
              {:has-counterparty []
               :provides :is-denominated-in-unit
               :receives :is-denominated-in-unit}}}}})

(def state-code-204
  {:event
   {:has-identifier "State-Code-14-11-204"
    :date "2021-05-01"
    :is-asserted-by "State Legislature"
    :reference "State Code Title 14, Chapter 11, Section 204"}})

(def business-formation
  {:event
   {:has-identifier "BusinessFormation-001"
    :date "2025-01-10"
    :is-asserted-by "SP"
    :is-allowed-by "State-Code-14-11-204"
    :has-counterparty "Secretary of State"
    :provides {:is-denominated-in-unit {:is-denominated-in-monetary-unit :USD}
               :has-quantity 100}
    :allows {:event {:includes
                     {:has-counterparty []
                      :provides {:is-denominated-in-unit
                                 {:is-denominated-in-physical-unit :printed-t-shirt}}
                      :receives {:is-denominated-in-unit
                                 {:is-denominated-in-monetary-unit :USD}}}}}
    :expects
    {:has-confidence-level 1.0
     :event ;; :event here could be optional.
     {:date "2026-01-10"
      :has-identifier "Obl-Renewal-2026"
      :is-required-by "State-Code-14-11-204"
      :includes 
      {:has-counterparty "Secretary of State"
       :provides
       {:has-quantity 100
        :is-denominated-in-unit {:is-denominated-in-monetary-unit :USD}}}}}}})

(def sp-hire
  {:event
   {:has-identifier "SPHire"
    :date "2025-01-10"
    :is-asserted-by "SP"
    :is-allowed-by "BusinessFormation-001"
    :has-counterparty "SP"
    :allows {:event
             {:includes
              {:consumes
               [{:is-denominated-in-unit {:is-denominated-in-time-unit :hour}}
                {:is-denominated-in-unit {:is-denominated-in-effort-unit
                                          {:is-denominated-in-employee-commitment "SPHire"}}}]}}}
    :expects ;; expects here because not earned yet so not strictly "required". But could think about this.
    {:has-confidence-level 1.0
     :event
     {:date "2025-01-24"
      :has-identifier "SPSalary-001"
      :includes 
      {:provides
       {:has-quantity 1500
        :is-denominated-in-unit {:is-denominated-in-monetary-unit :USD}}}}}}})

;; Membership Issuance
;; Note that for simplicity's sake I decided not to log a "member" here because there was no interesting identifying information. In the future for dividends and such I would reference this event directly as the "source".
(def equity-issuance
  {:event
   {:has-identifier "MembershipIssuance-001"
    :date "2025-01-15"
    :is-asserted-by "SP"
    :has-counterparty "ParentMember"
    :is-allowed-by "BusinessFormation-001"
    :receives {:has-quantity 20000
               :is-denominated-in-unit {:is-denominated-in-monetary-unit :USD}}
    :provides {:has-quantity 20
               :is-denominated-in-unit {:is-denominated-in :ownership-percentage}}
    :requires
    {:is-required-by "BusinessFormation-001"
     :has-identifier "ReportRequirement-001"
     :event
     {:date "2026-01-15"
      :has-identifier "AnnualReport-001"
      :includes {:has-counterparty "ParentMember"
                 :reports ["..."]}}}}})

;; Equipment Acquisition
;; Same here; the event is the machine and I'll reference that. I can mention that the seller can certainly get its own event entry though and probably would.
(def machine-purchase
  {:event
   {:has-identifier "EquipmentPurchase-001"
    :date "2025-02-01"
    :asserted-by "SP"
    :has-counterparty "Vendor-A"
    :is-allowed-by "State UCC"
    :receives {:has-quantity 1
               :is-denominated-in-unit {:is-denominated-in-physical-unit :t-shirt-printer}}
    :provides {:has-quantity 12000
               :is-denominated-in-unit {:is-denominated-in-monetary-unit :USD}}
    :allows {:event
             {:includes
              {:creates {:is-denominated-in-unit
                         {:is-denominated-in-physical-unit
                          :printed-t-shirt}
                         :has-quantity 1}
               :consumes [{:is-denominated-in-unit
                           {:is-denominated-in-physical-unit
                            :unprinted-t-shirt}
                           :has-quantity 1}
                          {:is-denominated-in-unit
                           {:is-denominated-in-physical-unit
                            :printing-ink-cartridge}
                           :has-quantity 1/100}
                          {:is-denominated-in-unit {:is-denominated-in-time-unit :hour}
                           :has-quantity 1/60}
                          {:is-denominated-in-unit
                           {:is-denominated-in-effort-unit :is-denominated-in-employee-commitment}
                           :has-quantity 1}]}}
             :is-restricted-by {:has-quantity 10000}}
    :requires ;; this could be :expects if you think of maintenance as optional.
    {:event
     {:date "2025-03-01"
      :has-identifier "MachineMaintenance-001"
      :includes
      {:provides
       [{:has-quantity 4
         :is-denominated-in-unit {:is-denominated-in-time-unit :hour}}
        {:has-quantity 1
         :is-denominated-in-effort-unit :is-denominated-in-employee-commitment}]}}}}})

;; Inventory Acquisition
;; Blank t-shirts
(def t-shirt-vendor
  {:event
   {:has-identifier "TShirtVendor"
    :date "2025-02-05"
    :is-asserted-by "SP"
    :allows {:event
             {:includes
              {:provides {:is-denominated-in-unit {:is-denominated-in-monetary-unit :USD}}}
              :receives {:is-denominated-in-unit
                         {:is-denominated-in-physical-unit :unprinted-t-shirt}}}}}})

(def t-shirt-purchase
  {:event
   {:has-identifier "TShirtPurchase-001"
    :date "2025-02-05"
    :is-asserted-by "SP"
    :has-counterparty "TShirtVendor"
    :provides {:is-denominated-in-unit {:is-denominated-in-monetary-unit :USD}
               :has-quantity 600}
    :receives {:is-denominated-in-unit
               {:is-denominated-in-physical-unit :unprinted-t-shirt}
               :has-quantity 200}}})
;; We don't record "allows" here because an unprinted t-shirt 

;; Ink cartridges
(def ink-vendor
  {:event
   {:has-identifier "InkVendor"
    :date "2025-02-05"
    :is-asserted-by "SP"
    :allows {:event
             {:includes
              {:provides {:is-denominated-in-unit {:is-denominated-in-monetary-unit :USD}}
               :receives {:is-denominated-in-unit
                          {:is-denominated-in-physical-unit :printing-ink-cartridge}}}}}}})

;; Ink contract allows for purchase at a certain price and for returns of any cartridges purchased within a month. Here, we only record the first allowance because the second allowance is triggered by purchase and does not yet exist. 
(def ink-contract
  {:event
   {:has-identifier "InkContract-001"
    :date "2025-02-05"
    :is-asserted-by "SP"
    :has-counterparty "InkVendor"
    :allows {:event
             {:includes
              {:provides {:is-denominated-in-unit {:is-denominated-in-monetary-unit :USD}
                          :has-quantity 2}
               :receives {:is-denominated-in-unit
                          {:is-denominated-in-physical-unit :printing-ink-cartridge}
                          :has-quantity 1}}}
             :is-restricted-by {:date {:less-than "2026-02-06"}}}}})

;; Here we record both the purchase and the right to return.
(def ink-purchase
  {:event
   {:has-identifier "InkPurchase-001"
    :date "2025-02-06"
    :is-asserted-by "SP"
    :has-counterparty "InkVendor" ;; not strictly necessary to note because of the reference to the contract below.
    :is-allowed-by "InkContract-001"
    :provides {:is-denominated-in-unit {:is-denominated-in-monetary-unit :USD}
               :has-quantity 100}
    :receives {:is-denominated-in-unit
               {:is-denominated-in-physical-unit :printing-ink-cartridge}
               :has-quantity 200}
    :allows {:event
             {:includes
              {:provides {:is-denominated-in-unit
                          {:is-denominated-in-physical-unit {:printing-ink-cartridge "InkPurchase-001"}}
                          :has-quantity 1}
               :receives {:is-denominated-in-unit {:is-denominated-in-monetary-unit :USD}
                          :has-quantity 2}}}
             :is-restricted-by {:date "2025-03-07"}}}})

;; Development of Printing Designs
(def copyright-law
  {:event
   {:has-identifier "Title-17-USC"
    :date "2020-01-01"
    :is-asserted-by "US Congress"
    :reference "Title 17, United States Code"
    :allows {:event {:includes {:is-protected-by "Title-17-USC"}}}}})

(def design-creation-example
  {:event
   {:has-identifier "DesignCreation-001"
    :date "2025-02-08"
    :is-asserted-by "SP"
    :is-protected-by "Title-17-USC"
    :consumes [{:has-quantity 8
                :is-denominated-in-unit {:is-denominated-in-time-unit :hour}}
               {:is-denominated-in-unit {:is-denominated-in-effort-unit
                                         {:is-denominated-in-employee-commitment "SPHire"}}}]
    :allows {:event
             {:includes
              {:creates
               {:is-denominated-in-unit
                {:is-denominated-in-physical-unit {:printed-t-shirt "Design-001"}}}}}}}})

;; Production and Sale
(def t-shirt-print-example
  {:event
   {:has-identifier "Printed-001"
    :date "2025-02-10"
    :is-asserted-by "SP"
    :is-allowed-by "EquipmentPurchase-001"
    :creates {:is-denominated-in-unit
              {:is-denominated-in-physical-unit {:printed-t-shirt "Design-001"}}
              :has-quantity 1}
    :consumes [{:is-denominated-in-unit
                {:is-denominated-in-physical-unit {:unprinted-t-shirt
                                                   "TShirtPurchase-001"}}
                :has-quantity 1}
               {:is-denominated-in-unit
                {:is-denominated-in-physical-unit {:printing-ink-cartridge
                                                   "InkPurchase-001"}}
                :has-quantity 1/100}
               {:is-denominated-in-unit {:is-denominated-in-time-unit :hour}
                :has-quantity 1/60}
               {:is-denominated-in-unit {:is-denominated-in-effort-unit
                                         {:is-denominated-in-employee-commitment "SPHire"}}}]
    ;; Note that this expectation comes from SP's forecasted sale price; if they hadn't a particular intended sale price, they could simply log the sale later.
    :expects
    {:has-confidence-level 0.5
     :has-identifier "ExpectedSale-001"
     :event 
     {:includes {:provides {:is-denominated-in-unit
                            {:is-denominated-in-physical-unit {:printed-t-shirt "Design-001"}}
                            :has-quantity 1}
                 :receives {:is-denominated-in-unit {:is-denominated-in-monetary-unit :USD}
                            :has-quantity 25}}}}}})

(def t-shirt-sale-example
  {:event
   {:has-identifier "Sale-001"
    :date "2025-02-20"
    :is-asserted-by "SP"
    :has-counterparty "Customer-A"
    :modifies {:fulfills "ExpectedSale-001"}
    :provides {:is-denominated-in-unit
               {:is-denominated-in-physical-unit {:printed-t-shirt "Design-001"}}
               :has-quantity 1}
    :receives {:is-denominated-in-unit {:is-denominated-in-monetary-unit :USD}
               :has-quantity 25}}})
;; :modifies and :fulfills serve as a kind of shorthand for the event that includes the transfer.
;; In this case I chose to include the details but theoretically I don't have to.

;; Recurring Sales Agreement
(def sales-agreement
  {:event
   {:has-identifier "SalesAgreement-001"
    :date "2025-03-01"
    :is-asserted-by "SP"
    :has-counterparty "RegularCustomer-001"
    :requires
    [{:event
      {:has-identifier "AgreedSale-001"
       :date "2025-04-01"
       :has-counterparty "RegularCustomer-001"
       :includes
       {:provides
        [{:is-denominated-in-unit
          {:is-denominated-in-physical-unit {:printed-t-shirt "Design-001"}}
          :has-quantity 50}
         {:is-denominated-in-unit
          {:is-denominated-in-physical-unit {:printed-t-shirt "Design-002"}}
          :has-quantity 50}
         {:is-denominated-in-unit
          {:is-denominated-in-physical-unit {:printed-t-shirt "Design-003"}}
          :has-quantity 50}]
        :allows {:event
                 {:has-identifier "CashReceipt-001"
                  :receives {:is-denominated-in-unit {:is-denominated-in-monetary-unit :USD}
                             :has-quantity 3750}}}}}}
     {:event
      {:has-identifier "AgreedSale-002"
       :date "2025-05-01"
       :has-counterparty "..."}}]}})

;; Performance Reporting and Analysis
(def asc-606
  {:event
   {:has-identifier "ASC-606"
    :date "2014-05-28"
    :is-asserted-by "Financial Accounting Standards Board"
    :reference "ASC 606"
    :allows
    {:event
     {:collects
      {:includes
       {:event
        {:includes
         [{:provides {:is-denominated-in-unit :is-denominated-in-physical-unit}}
          {:receives {:is-denominated-in-unit {:is-denominated-in-monetary-unit
                                               :monetary-unit}
                      :has-quantity :quantity}}]}}
       :excludes
       {:event
        {:includes
         [{:requires {:includes {:provides {:is-denominated-in-unit :is-denominated-in-physical-unit}}}}
          {:expects {:includes {:provides {:is-denominated-in-unit :is-denominated-in-physical-unit}}}}
          {:allows {:includes {:provides {:is-denominated-in-unit :is-denominated-in-physical-unit}}}}]}}}
      :reports {:is-denominated-in-unit {:is-denominated-in-monetary-unit :monetary-unit}
                :has-quantity {:sum :quantity}}}}}})

(def accrual-revenue-calc
  {:event
   {:has-identifier "AccrualRevenue"
    :date "2025-12-31"
    :is-asserted-by "SP"
    :is-allowed-by "ASC-606"
    :collects
    {:includes
     {:event
      {:includes
       [{:provides {:is-denominated-in-unit
                    {:is-denominated-in-physical-unit :printed-t-shirt}}}
        {:receives {:is-denominated-in-unit
                    {:is-denominated-in-monetary-unit :USD}
                    :has-quantity :quantity}}]}}
     :excludes
     {:event
      {:includes
       [{:requires {:includes {:provides {:is-denominated-in-unit
                                          {:is-denominated-in-physical-unit :printed-t-shirt}}}}}
        {:expects {:includes {:provides {:is-denominated-in-unit
                                         {:is-denominated-in-physical-unit :printed-t-shirt}}}}}
        {:allows {:includes {:provides {:is-denominated-in-unit
                                        {:is-denominated-in-physical-unit :printed-t-shirt}}}}}]}}}
    :reports {:is-denominated-in-unit {:is-denominated-in-monetary-unit :USD}
                :has-quantity {:sum :quantity}}}})

(def annual-report
  {:event
   {:has-identifier "AnnualReport-001"
    :date "2026-01-15"
    :has-counterparty "ParentMember"
    :is-asserted-by "SP"
    :modifies {:fulfills "ReportRequirement-001"}
    :reports ["AccrualRevenue" "..."]
    :requires
    {:is-required-by "BusinessFormation-001"
     :has-identifier "ReportRequirement-002"
     :event
     {:date "2027-01-15"
      :has-identifier "AnnualReport-002"
      :includes {:has-counterparty "ParentMember"
                 :reports ["AccrualRevenue" "..."]}}}}})

(def irs
  {:event
   {:has-identifier "InternalRevenueService"
    :date "1953-07-09"
    :is-asserted-by "U.S. Department of the Treasury"
    :reference "Treasury Order 150-29"
    :requires
    {:event
     {:includes
      {:reports ["CashRevenue" "..."]}}}}})

(def irc-451
  {:event
   {:has-identifier "IRC-451"
    :date "1954-08-16"
    :is-asserted-by "InternalRevenueService"
    :reference "IRC §451"
    :requires ;; note the :requires here, taxes are not optional.
    {:event
     {:includes
      {:event
       {:includes
        [{:provides {:is-denominated-in-unit :is-denominated-in-physical-unit}}
         {:receives {:is-denominated-in-unit :is-denominated-in-monetary-unit
                     :has-quantity :quantity}}]}}
      :excludes
      {:event
       {:includes
        [{:requires {:includes {:receives {:is-denominated-in-unit :is-denominated-in-monetary-unit}}}}
         {:expects {:includes {:receives {:is-denominated-in-unit :is-denominated-in-monetary-unit}}}}
         {:allows {:includes {:receives {:is-denominated-in-unit :is-denominated-in-monetary-unit}}}}]}}
      :reports {:is-denominated-in-unit :is-denominated-in-monetary-unit
                :has-quantity {:sums :quantity}}}}}})

(def cash-revenue-calc
  {:event
   {:has-identifier "CashRevenue"
    :date "2025-12-31"
    :is-asserted-by "SP"
    :includes
    {:event
     {:includes
      [{:provides {:is-denominated-in-unit
                   {:is-denominated-in-physical-unit :printed-t-shirt}}}
       {:receives {:is-denominated-in-unit
                   {:is-denominated-in-monetary-unit :USD}
                   :has-quantity :quantity}}]}}
    :excludes
    {:event
     {:includes
      [{:requires {:includes {:receives {:is-denominated-in-unit
                                         {:is-denominated-in-monetary-unit :USD}}}}}
       {:expects {:includes {:receives {:is-denominated-in-unit
                                        {:is-denominated-in-monetary-unit :USD}}}}}
       {:allows {:includes {:receives {:is-denominated-in-unit
                                       {:is-denominated-in-monetary-unit :USD}}}}}]}}
    :reports {:is-denominated-in-unit {:is-denominated-in-monetary-unit :USD}
              :has-quantity {:sums :quantity}}}})


(def tax-return
  {:event
   {:has-identifier "TaxReturn-001"
    :date "2026-04-15"
    :is-asserted-by "SP"
    :has-counterparty "InternalRevenueService"
    :is-required-by "InternalRevenueService"
    :reports ["CashRevenue" "..."]
    :expects
    {:has-confidence-level 1.0
     :event
     {:date "2026-04-15"
      :has-identifier "TaxReturn-002"
      :has-counterparty "InternalRevenueService"
      :is-required-by "InternalRevenueService"
      :includes
      {:reports ["CashRevenue"]}}}}})

;; Environmental Reporting
(def env-report-request
  {:event
   {:date "2026-03-11"
    :has-identifier "CustomerRequestForEnvReport"
    :is-asserted-by "SP"
    :is-requested-by "ConsciousCustomer"
    :requires
    {:has-identifier "ElectricEmissionsInformationRequest"
     :event
     {:is-asserted-by "LocalElectric"
      :includes
      {:reports {:equals [{:is-denominated-in-unit
                           {:is-denominated-in-energy-unit :kWh}}
                          {:is-denominated-in-unit
                           {:is-denominated-in-physical-unit
                            {:carbon-emissions :pound}}}]}}}}
    :expects
    {:has-confidence-level 1.0
     :has-identifier "RequestedEnvReport-001"
     :event
     {:includes
      {:consumes [{:is-denominated-in-unit
                   {:is-denominated-in-effort-unit
                    {:is-denominated-in-employee-commitment "SPHire"}}}
                  {:is-denominated-in-unit {:is-denominated-in-time-unit :hour}}]
       :reports ["EmissionsPerShirt"]}}}}})

(def emissions-per-kWh
  {:event
   {:date "2026-03-16"
    :has-identifier "EmissionsFromElectric"
    :is-asserted-by "LocalElectric"
    :modifies {:fulfills "ElectricEmissionsInformationRequest"}
    :reports {:equals [{:is-denominated-in-unit
                        {:is-denominated-in-energy-unit :kWh}
                        :has-quantity 1}
                       {:is-denominated-in-unit
                        {:is-denominated-in-physical-unit
                         {:carbon-emissions :pound}}
                        :has-quantity 0.855}]}}})

(def emissions-per-shirt-calc
  {:event
   {:date "2026-04-01"
    :has-identifier "EmissionsPerShirt"
    :is-asserted-by "SP"
    :collects
    {:includes 
     {:event
      {:includes [{:receives {:is-denominated-in-unit
                              {:is-denominated-in-energy-unit :kWh}
                              :has-quantity :kWhs}}
                  {:creates {:is-denominated-in-unit
                             {:is-denominated-in-physical-unit :printed-t-shirt}
                             :has-quantity :printed-t-shirts}}
                  {:reports {:equals
                             [{:is-denominated-in-unit
                               {:is-denominated-in-energy-unit :kWh}
                               :has-quantity :unit-kWh}
                              {:is-denominated-in-unit
                               {:is-denominated-in-physical-unit
                                {:carbon-emissions :pound}}
                               :has-quantity :unit-emissions}]}}]}}
     :excludes
     {:event
      {:includes
       [{:requires {:includes "..."}}
        {:expects {:includes "..."}}
        {:allows {:includes "..."}}]}}
     :reports
     {:multiplies [:unit-emissions
                   {:divides [{:sums :kWhs}
                              {:sums :printed-t-shirts}]}]}}}})

(def env-report
  {:event
   {:date "2026-04-01"
    :has-identifier "EnvReport-001"
    :is-asserted-by "SP"
    :has-counterparty "ConsciousCustomer"
    :modifies {:fulfills "RequestedEnvReport-001"}
    :reports ["EmissionsPerShirt"]
    :consumes [{:is-denominated-in-unit
                   {:is-denominated-in-effort-unit
                    {:is-denominated-in-employee-commitment "SPHire"}}}
               {:is-denominated-in-unit {:is-denominated-in-time-unit :hour}
                :has-quantity 2}]}})
