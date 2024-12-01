the following line fails to work for a range response that attempts to assign the header value as an int/long. the the line will attempt to create a `seq` from the int/long - which fails

- note - httpkit works without modification
- fix - cast the header-value to a string

https://github.com/ring-clojure/ring/blob/dde9716e23f85497df4ea0deba148fd22ac99c6c/ring-servlet/src/ring/util/servlet.clj#L66
