(ns angular-phonecat-re-frame.prod
  (:require [angular-phonecat-re-frame.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
