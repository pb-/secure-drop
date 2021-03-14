nrepl:
	clojure -A:nrepl
.PHONY: nrepl

client: jar
	cat scripts/stub.sh target/client.jar > target/sdc
	chmod a+x target/sdc
.PHONY: client

jar:
	mkdir -p classes
	clj -e "(compile 'securedrop)"
	clojure -A:uberdeps --main-class securedrop
.PHONY: jar
