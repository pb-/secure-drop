nrepl:
	clojure -A:nrepl
.PHONY: nrepl

jar:
	mkdir -p classes
	clj -e "(compile 'securedrop)"
	clojure -A:uberdeps --main-class securedrop
.PHONY: jar
