;; # Build steps
;; This namespace contains the actual buildsteps that test, package and deploy your application.
;; They are used in the pipeline-definition you saw in todopipeline.pipeline

(ns todopipeline.steps
  (:require [lambdacd.shell :as shell]
            [lambdacd.execution :as execution]
            [lambdacd.git :as git]
            [lambdacd.manualtrigger :as manualtrigger]
            [lambdacd.util :as util]))

;; Let's define some constants
(def backend-repo "git@github.com:flosell/todo-backend-compojure.git")
(def frontend-repo "git@github.com:flosell/todo-backend-client.git")

;; This step does nothing more than to delegate to a library-function.
;; It's a function that just waits until something changes in the repo.
;; Once done, it returns and the build can go on
(defn wait-for-frontend-repo [_ ctx]
  (let [wait-result (git/wait-for-git ctx frontend-repo "master")
        frontend-revision (:revision wait-result)]
    {:frontend-revision frontend-revision
     :backend-revision "HEAD"
     :status :success}))

(defn wait-for-backend-repo [_ ctx]
  (let [wait-result (git/wait-for-git ctx backend-repo "master")
        backend-revision (:revision wait-result)]
    {:backend-revision backend-revision
     :frontend-revision "HEAD"
     :status :success}))

;; Define some nice syntactic sugar that lets us run arbitrary build-steps with a
;; repository checked out. The steps get executed with the folder where the repo
;; is checked out as :cwd argument.
;; The ```^{:display-type :container}``` is a hint for the UI to display the child-steps as well.
(defn ^{:display-type :container} with-frontend-git [& steps]
  (fn [args ctx]
    (git/checkout-and-execute frontend-repo (:frontend-revision args) args ctx steps)))

(defn ^{:display-type :container} with-backend-git [& steps]
  (fn [args ctx]
    (git/checkout-and-execute backend-repo (:backend-revision args) args ctx steps)))

;; The steps that do the real work testing, packaging, publishing our code.
;; They get the :cwd argument from the ```with-*-git steps``` we defined above.
(defn client-package [{cwd :cwd} ctx]
  (shell/bash ctx cwd
    "bower install"
    "./package.sh"
    "./publish.sh"))

(defn server-test [{cwd :cwd} ctx]
  (shell/bash ctx cwd
    "lein test"))

(defn server-package [{cwd :cwd} ctx]
  (shell/bash ctx cwd
    "lein uberjar"
    "./publish.sh"))

(defn server-deploy-ci [{cwd :cwd} ctx]
  (shell/bash ctx cwd "./deploy-server.sh backend_ci /tmp/mockrepo/server-snapshot.tar.gz"))

(defn client-deploy-ci [{cwd :cwd} ctx]
  (shell/bash ctx cwd "./deploy-frontend.sh frontend_ci /tmp/mockrepo/client-snapshot.tar.gz"))

;; This is just a step that shows you what output steps actually have (since you have only used library functions up to
;; here). It's just a map with some information. :status has a special meaning in the sense that it needs to be there
;; and be :success for the step to be treated as successful and for the build to continue.
;; all other data can be (more or less) arbitrary and will be passed on to the next build-step as input arguments.
;; more or less means that some values have special meaning for the UI to display things.
;; Check out the implementation of ```shell/bash``` if you want to know more.
(defn some-step-that-cant-be-reached [& _]
  { :some-info "hello world"
    :status :success})


;; Another step that just fails using bash.
;; We could have made a failing step easier as well by just returning ```{ :status :failure }```
(defn some-failing-step [_ ctx]
  (shell/bash ctx "/" "echo \"i am going to fail now...\"" "exit 1"))