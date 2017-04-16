package ir.ac.iust.dml.kg.search.logic;

import ir.ac.iust.dml.kg.resource.extractor.IResourceExtractor;
import ir.ac.iust.dml.kg.resource.extractor.IResourceReader;
import ir.ac.iust.dml.kg.resource.extractor.MatchedResource;
import ir.ac.iust.dml.kg.resource.extractor.readers.ResourceReaderFromKGStoreV1Service;
import ir.ac.iust.dml.kg.resource.extractor.tree.TreeResourceExtractor;
import ir.ac.iust.dml.kg.search.logic.data.ResultEntity;
import ir.ac.iust.dml.kg.search.logic.data.SearchResult;
import knowledgegraph.normalizer.PersianCharNormalizer;

import java.util.List;

public class Searcher {
    private final IResourceExtractor extractor;
    private final KGFetcher kgFetcher;
    private static final PersianCharNormalizer normalizer = new PersianCharNormalizer();

    public Searcher() throws Exception {
        extractor = setupNewExtractor();
        kgFetcher = new KGFetcher();
    }

    public SearchResult search(String keyword) {
        String queryText = normalizer.normalize(keyword);
        final SearchResult result = new SearchResult();
        try {
            List<MatchedResource> matchedResources = extractor.search(queryText, true);

            for (MatchedResource matchedResource : matchedResources) {

                try {


                    ResultEntity resultEntity = new ResultEntity();

                    resultEntity.setTitle(matchedResource.getResource().getLabel());
                    resultEntity.setSubtitle(kgFetcher.fetchLabel(matchedResource.getResource().getInstanceOf()));
                    resultEntity.setLink(matchedResource.getResource().getIri());
                    String wikiPage = kgFetcher.fetchWikiPage(matchedResource.getResource().getIri());
                    if (wikiPage != null)
                        resultEntity.setLink(wikiPage);

                    if (matchedResource.getResource().getType() != null) {
                        if (matchedResource.getResource().getType().equals("DatatypeProperty"))
                            resultEntity.setDescription("رابطه (خصیصه)");
                        if (matchedResource.getResource().getType().equals("Resource"))
                            resultEntity.setDescription("موجودیت");
                    }
                    if (matchedResource.getResource().getLabel() == null) {
                        resultEntity.setTitle(extractTitleFromIri(matchedResource.getResource().getIri()));
                    }

            /*if (resultEntity.getTitle() == null)
                resultEntity.setTitle("پاسخی یافت نشد");*/
                    result.getEntities().add(resultEntity);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;

        /*switch (keyword.length() % 3) {
            case 0:
                return FakeLogic.oneEntity();
            case 1:
                return FakeLogic.oneEntityAndBreadcrumb();
            default:
                return FakeLogic.list();
        }*/
    }

    private String extractTitleFromIri(String iri) {
        return iri.substring(iri.lastIndexOf("/") + 1).replace('_', ' ');
    }

    private static IResourceExtractor setupNewExtractor() throws Exception {
        IResourceExtractor extractor = new TreeResourceExtractor();
        try (IResourceReader reader = new ResourceReaderFromKGStoreV1Service("http://194.225.227.161:8091/")) {
            extractor.setup(reader, 1000000);
        }
        return extractor;
    }
}
