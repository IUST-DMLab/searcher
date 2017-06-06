package ir.ac.iust.dml.kg.search.logic;

import com.google.common.base.Strings;
import ir.ac.iust.dml.kg.resource.extractor.*;
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

        //Answering predicate-subject phrases
        boolean haveAnyPatternAnswer = false;
        try {
            List<MatchedResource> matchedResourcesUnfiltered = extractor.search(queryText, false);

            List<Resource> properties = matchedResourcesUnfiltered.stream()
                    .filter(mR -> mR.getResource() != null)
                    .map(mR -> mR.getResource())
                    .filter(r -> r.getType() != null)
                    .filter(r -> r.getType().toString().contains("Property"))
                    .collect(Collectors.toList());

            List<Resource> disambiguatedProperties = matchedResourcesUnfiltered.stream()
                    .filter(mR -> mR.getAmbiguities() != null && mR.getAmbiguities().size() > 0)
                    .flatMap(mR -> mR.getAmbiguities().stream())
                    .map(r -> {
                        if (Strings.isNullOrEmpty(r.getLabel())) r.setLabel("بدون برچسب");
                        else r.setLabel(r.getLabel() + " (ابهام‌زدایی‌شده)");
                        return r;
                    })
                    .collect(Collectors.toList());

            properties.addAll(disambiguatedProperties);
            List<Resource> finalProperties = properties.stream().distinct().collect(Collectors.toList());

            List<Resource> entities = matchedResourcesUnfiltered.stream()
                    .filter(mR -> mR.getResource() != null)
                    .map(mR -> mR.getResource())
                    .filter(r -> !finalProperties.contains(r))
                    .distinct()
                    .collect(Collectors.toList());

            for (Resource subjectR : entities) {
                for (Resource propertyR : finalProperties) {
                    try {
                        System.out.println("Trying combinatios for " + subjectR.getIri() + "\t & \t" + propertyR.getIri());
                        Map<String, String> objectLables = kgFetcher.fetchSubjPropObjQuery(subjectR.getIri(), propertyR.getIri());
                        for (Map.Entry<String, String> olEntry : objectLables.entrySet()) {
                            System.out.printf("Object: %s\t%s\n", olEntry.getKey(), olEntry.getValue());
                            ResultEntity resultEntity = new ResultEntity();
                            resultEntity.setLink(olEntry.getKey());
                            resultEntity.setTitle(olEntry.getValue());
                            resultEntity.setDescription("نتیجه‌ی گزاره‌ای");
                            result.getEntities().add(resultEntity);
                            haveAnyPatternAnswer = true;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        //Output individual entities
        try {
            boolean shouldRemoveSubset = haveAnyPatternAnswer;
            List<MatchedResource> matchedResources = extractor.search(queryText, shouldRemoveSubset);
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
        resultEntity.setSubtitle(kgFetcher.fetchLabel(matchedResource.getResource().getInstanceOf(), true));
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

    private static String extractTitleFromIri(String iri) {
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
