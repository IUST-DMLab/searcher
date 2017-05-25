package ir.ac.iust.dml.kg.search.logic;

import ir.ac.iust.dml.kg.resource.extractor.IResourceExtractor;
import ir.ac.iust.dml.kg.resource.extractor.IResourceReader;
import ir.ac.iust.dml.kg.resource.extractor.MatchedResource;
import ir.ac.iust.dml.kg.resource.extractor.ResourceCache;
import ir.ac.iust.dml.kg.resource.extractor.tree.TreeResourceExtractor;
import ir.ac.iust.dml.kg.search.logic.data.ResultEntity;
import ir.ac.iust.dml.kg.search.logic.data.SearchResult;
import knowledgegraph.normalizer.PersianCharNormalizer;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
            List<MatchedResource> matchedResources = extractor.search(queryText, false);

            //Answering predicate-subject phrases
            try {
                List<MatchedResource> properties = matchedResources.stream()
                        .filter(mR -> mR.getResource() != null)
                        .filter(mR -> mR.getResource().getType() != null)
                        .filter(mR -> mR.getResource().getType().toString().contains("Property"))
                        .collect(Collectors.toList());
                List<MatchedResource> entities = matchedResources.stream()
                        .filter(mR -> !properties.contains(mR))
                        .collect(Collectors.toList());

                for (MatchedResource subjectMR : entities) {
                    for (MatchedResource propertyMR : properties) {
                        System.out.println("Trying combinatios for " + subjectMR.getResource().getIri() + "\t & \t" + propertyMR.getResource().getIri());
                        Map<String, String> objectLables = kgFetcher.fetchSubjPropObjQuery(subjectMR.getResource().getIri(), propertyMR.getResource().getIri());
                        for (Map.Entry<String, String> olEntry : objectLables.entrySet()) {
                            System.out.printf("Object: %s\t%s\n", olEntry.getKey(), olEntry.getValue());
                            ResultEntity resultEntity = new ResultEntity();
                            resultEntity.setLink(olEntry.getKey());
                            resultEntity.setTitle(olEntry.getValue());
                            resultEntity.setDescription("نتیجه‌ی گزاره‌ای");
                            result.getEntities().add(resultEntity);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }


            //Output individual entities
            for (MatchedResource matchedResource : matchedResources) {
                try {
                    ResultEntity resultEntity = matchedResourceToResultEntity(matchedResource);
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

    private ResultEntity matchedResourceToResultEntity(MatchedResource matchedResource) {
        ResultEntity resultEntity = new ResultEntity();
        resultEntity.setTitle(matchedResource.getResource().getLabel());
        resultEntity.setSubtitle(kgFetcher.fetchLabel(matchedResource.getResource().getInstanceOf()));
        resultEntity.setLink(matchedResource.getResource().getIri());
                    /*String wikiPage = kgFetcher.fetchWikiPage(matchedResource.getResource().getIri());
                    if (wikiPage != null)
                        resultEntity.setLink(wikiPage);*/

        if (matchedResource.getResource().getType() != null) {
            String type = "Type: " + matchedResource.getResource().getType().toString();
            if (matchedResource.getResource().getType().toString().contains("Property"))
                type = " (گزاره)";
            if (matchedResource.getResource().getType().toString().contains("Resource"))
                type = " (موجودیت)";
            if (resultEntity.getSubtitle() == null && !type.equals(""))
                resultEntity.setSubtitle(type);
            else resultEntity.setSubtitle(resultEntity.getSubtitle() + type);
        }
        if (matchedResource.getResource().getLabel() == null) {
            resultEntity.setTitle(extractTitleFromIri(matchedResource.getResource().getIri()));
        }
        return resultEntity;
    }

    private String extractTitleFromIri(String iri) {
        return iri.substring(iri.lastIndexOf("/") + 1).replace('_', ' ');
    }

    private static IResourceExtractor setupNewExtractor() throws Exception {
        IResourceExtractor extractor = new TreeResourceExtractor();
        try (IResourceReader reader = new ResourceCache("cache", true)) {
            System.err.println("Loading resource-extractor from cache...");
            long t1 = System.currentTimeMillis();
            extractor.setup(reader, 10000);
            //extractor.setup(reader, 1000000);
            System.err.printf("resource-extractor loaded from cache in %,d miliseconds\n", (System.currentTimeMillis() - t1));
        }
        return extractor;
    }
}
