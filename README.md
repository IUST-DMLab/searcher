Core Search Module for FarsBase Semantic Search
==========

### Introduction
This repository contains the the core index and search modules for Semantic Search in FarsBase.

The library is invoked by the [search-services](https://github.com/IUST-DMLab/search-services) web service that translates a JSON request and runs searcher. search-services is, in turn, invoked by [search-ui](https://github.com/IUST-DMLab/search-ui), the Web User Interface for the search service. Search-UI is accessible at: [http://farsbase.net](http://farsbase.net/search/html/index.html).

## Dependencies
This project makes use of several dependencies: When in doubt, please cross-check with the respective projects:

* FarsBase [resource-extractor](https://github.com/IUST-DMLab/resource-extractor) version 1.6.1
* FarsBase [utils](https://github.com/IUST-DMLab/utils) version 0.3.0
* FarsBase [search-normalizer](https://github.com/IUST-DMLab/search-normalizer) version 1.0
* FarsBase [virtuoso-connector](https://github.com/IUST-DMLab/virtuoso-connector) version 0.2.5

## License
The source code of this repo is published under the [Apache License Version 2.0](https://github.com/AKSW/jena-sparql-api/blob/master/LICENSE).

