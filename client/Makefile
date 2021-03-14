nrepl:
	clojure -A:nrepl
.PHONY: nrepl

client: jar
	cat scripts/stub.sh target/client.jar > target/client
	chmod a+x target/client
.PHONY: client

jar:
	mkdir -p classes
	clj -e "(compile 'securedrop)"
	clojure -A:uberdeps --main-class securedrop
.PHONY: jar
