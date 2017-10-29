(ns terraform-clj.core
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.set :as set]))


(defn to-underscore [k]
  (str/replace (name k) \- \_))

(def ami "ami-62cd101b")
(def server-port 8080)
(def elb-name "terraform-asg-example")

(def iac {:provider :aws
          :region :eu-west-1
          :resources [{:type :aws-launch-configuration
                       :id "example"
                       :ami ami
                       :instance-type "t2.micro"
                       :tags [{:Name "identity"}]
                       :user-data (str "#!/bin/bash\n"
                                       "echo \"Hello, World\" > index.html\n"
                                       "nohup busybox httpd -f -p " server-port " &\n")
                       :vpc-security-group-ids ["${aws_security_group.instance.id}"]
                       :lifecycle {:create-before-destroy :true}}
                      {:type :aws-key-pair
                       :id "deployer"
                       :key-name "mykey"
                       :public-key (slurp "/Users/erik/.ssh/id_rsa.pub")}
                      {:type :aws-security-group
                       :id "instance"
                       :name "terraform-example-instance"
                       :ingress {:from-port server-port
                                 :to-port server-port
                                 :protocol :tcp
                                 :cidr-blocks ["0.0.0.0/0"]}
                       :lifecycle {:create-before-destroy :true}}
                      {:type :aws-security-group
                       :id "elb"
                       :name  "terraform-example-elb"
                       :ingress {:from-port 80
                                 :to_port 80
                                 :protocol :tcp
                                 :cidr-blocks ["0.0.0.0/0"]}
                       :egress {:from_port 0
                                :to_port 0
                                :protocol "-1"
                                :cidr_blocks ["0.0.0.0/0"]}}
                      {:type :aws-autoscaling-group
                       :id "example"
                       :availability-zones ["eu-west-1a"]
                       :launch-configuration "${aws_launch_configuration.example.id}"
                       :load_balancers [elb-name]
                       :min-size 2
                       :max-size 10
                       :tags [{:key "Name"
                               :value "terraform-asg-example"
                               :propagate-at-launch true}]}
                      {:type :aws-elb
                       :id "example"
                       :name elb-name
                       :security_groups ["${aws_security_group.elb.id}"]
                       :availability-zones ["eu-west-1a"]
                       :listener {:lb-port 80
                                  :lb-protocol :http
                                  :instance-port server-port
                                  :instance-protocol :http}
                       :health-check {:healthy-threshold 2
                                      :unhealthy-threshold 2
                                      :timeout 3
                                      :interval 30
                                      :target (str "HTTP:" server-port "/")}}]})

(defmulti build-resource :type)

(defmethod build-resource :aws-key-pair [{:keys [id] :as pair}]
  {:aws-key-payr {name (dissoc pair :id :type)}})

(defmethod build-resource :aws-instance [{:keys [name] :as instance}]
  {:aws-instance {name (dissoc instance :name :type)}})

(defmethod build-resource :aws-elb  [{:keys [id] :as elb}]
  {:aws-elb {id (dissoc elb :type :id)}})

(defmethod build-resource :aws-security-group [{:keys [id] :as group}]
  {:aws-security-group {id (dissoc group :type :id)}})

(defmethod build-resource :aws-launch-configuration [{:keys [id] :as conf}]
  {:aws-launch-configuration {id (-> conf
                                       (dissoc :id :type :tags)
                                       (set/rename-keys {:ami :image-id
                                                         :vpc-security-group-ids :security-groups}))}})

(defmethod build-resource :aws-autoscaling-group [{:keys [id] :as group}]
  {:aws-autoscaling-group {id (dissoc group :id :type)}})

(defn build-resources [resources]
  {:resource (->> resources
                   (map build-resource)
                   (apply merge-with conj))})

(defn build-provider [{:keys [provider region]}]
  {:provider {provider {:region region} }})

(defn build [conf]
  (let [provider (build-provider conf)
        resources (build-resources (:resources conf))]
    (json/encode (merge  provider resources) {:key-fn to-underscore})))

(defn ->json-file [filename json]
  (spit filename json))
