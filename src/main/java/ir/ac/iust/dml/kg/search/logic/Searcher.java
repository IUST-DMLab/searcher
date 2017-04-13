package ir.ac.iust.dml.kg.search.logic;

import ir.ac.iust.dml.kg.search.logic.data.SearchResult;

public class Searcher {

    public SearchResult search(String keyword) {
        switch (keyword.length() % 3) {
            case 0:
                return FakeLogic.oneEntity();
            case 1:
                return FakeLogic.oneEntityAndBreadcrumb();
            default:
                return FakeLogic.list();
        }
    }
}
