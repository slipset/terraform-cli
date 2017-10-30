(ns example.config
  (:require [terraform-clj.core :as tf]))

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

(-> iac tf/build (tf/->json-file "myconfig.tf"))
