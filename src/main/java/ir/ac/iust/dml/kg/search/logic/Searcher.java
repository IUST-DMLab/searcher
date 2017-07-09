package ir.ac.iust.dml.kg.search.logic;

import com.google.common.base.Strings;
import ir.ac.iust.dml.kg.resource.extractor.*;
import ir.ac.iust.dml.kg.resource.extractor.tree.TreeResourceExtractor;
import ir.ac.iust.dml.kg.search.logic.data.ResultEntity;
import ir.ac.iust.dml.kg.search.logic.data.SearchResult;
import knowledgegraph.normalizer.PersianCharNormalizer;

import java.util.*;
import java.util.stream.Collectors;

public class Searcher {
    private final IResourceExtractor extractor;
    private final KGFetcher kgFetcher;
    private static final PersianCharNormalizer normalizer = new PersianCharNormalizer();


    private static String semaphore = "Semaphore";
    private static Searcher instance;

    public static Searcher getInstance() throws Exception {
        synchronized (semaphore) {
            System.out.println("getting instance of Searcher");
            if (instance != null) return instance;
            System.out.println("creating instance of Searcher");
            return new Searcher();
        }
    }

    public Searcher() throws Exception {
        instance = this;
        extractor = setupNewExtractor();
        kgFetcher = new KGFetcher();
    }

    public SearchResult search(String keyword) {
        String queryText = normalizer.normalize(keyword);
        final SearchResult result = new SearchResult();

        //Answering predicate-subject phrases
        try {
            List<MatchedResource> matchedResourcesUnfiltered = extractor.search(queryText, false);

            List<Resource> properties = matchedResourcesUnfiltered.stream()
                    .filter(mR -> mR.getResource() != null)
                    .map(mR -> mR.getResource())
                    .filter(r -> r.getType() != null)
                    .filter(r -> r.getType().toString().contains("Property"))
                    .collect(Collectors.toList());

            List<Resource> disambiguatedResources = matchedResourcesUnfiltered.stream()
                    .filter(mR -> mR.getSubsetOf() == null) //for entities, remove Subsets
                    .filter(mR -> mR.getAmbiguities() != null && mR.getAmbiguities().size() > 0)
                    .flatMap(mR -> mR.getAmbiguities().stream())
                    .map(r -> {
                        if (Strings.isNullOrEmpty(r.getLabel())) r.setLabel("بدون برچسب");
                        else r.setLabel(r.getLabel() + " (ابهام‌زدایی‌شده)");
                        return r;
                    })
                    .collect(Collectors.toList());

            List<Resource> disambiguatedProperties = disambiguatedResources.stream()
                    .filter(r -> r.getType() != null)
                    .filter(r -> r.getType().toString().contains("Property"))
                    .collect(Collectors.toList());

            properties.addAll(disambiguatedProperties);
            List<Resource> finalProperties = properties.stream()
                    .filter(r -> r.getIri() != null)
                    .filter(Util.distinctByKey(r -> r.getIri())) //distinct by Iri
                    .collect(Collectors.toList());

            List<Resource> entities = matchedResourcesUnfiltered.stream()
                    .filter(mR -> mR.getSubsetOf() == null) //for entities, remove Subsets
                    .filter(mR -> mR.getResource() != null)
                    .map(mR -> mR.getResource())
                    .collect(Collectors.toList());

            entities.addAll(disambiguatedResources);
            List<Resource> finalEntities = entities.stream()
                    .filter(r -> !finalProperties.contains(r))
                    .filter(r -> r.getIri() != null)
                    .filter(Util.distinctByKey(r -> r.getIri())) //distinct by Iri
                    .collect(Collectors.toList());

            for (Resource subjectR : finalEntities) {
                for (Resource propertyR : finalProperties) {
                    try {
                        System.out.println("Trying combinatios for " + subjectR.getIri() + "\t & \t" + propertyR.getIri());
                        Map<String, String> objectLables = kgFetcher.fetchSubjPropObjQuery(subjectR.getIri(), propertyR.getIri());
                        for (Map.Entry<String, String> olEntry : objectLables.entrySet()) {
                            System.out.printf("Object: %s\t%s\n", olEntry.getKey(), olEntry.getValue());
                            ResultEntity resultEntity = new ResultEntity();
                            resultEntity.setLink(olEntry.getKey());
                            resultEntity.setReferenceUri(subjectR.getIri());
                            resultEntity.setTitle(olEntry.getValue());
                            resultEntity.setDescription("نتیجه‌ی گزاره‌ای");
                            resultEntity.setResultType(ResultEntity.ResultType.RelationalResult);
                            if (!(Strings.isNullOrEmpty(subjectR.getLabel()) || Strings.isNullOrEmpty(propertyR.getLabel()))) {
                                resultEntity.setDescription(resultEntity.getDescription() + ": [" + subjectR.getLabel() + "] / [" + propertyR.getLabel() + "]");
                            }
                            result.getEntities().add(resultEntity);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            //Output individual entities
            Set<String> uriOfEntities = new HashSet<String>();
            for (Resource entity : entities) {
                try {
                    ResultEntity resultEntity = matchedResourceToResultEntity(entity);
                    if (uriOfEntities.contains(resultEntity.getLink()))
                        continue;
                    uriOfEntities.add(resultEntity.getLink());
                    result.getEntities().add(resultEntity);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    private ResultEntity matchedResourceToResultEntity(Resource resource) {;
        ResultEntity resultEntity = new ResultEntity();
        resultEntity.setTitle(resource.getLabel());
        //resultEntity.setSubtitle(kgFetcher.fetchLabel(resource.getInstanceOf(), true));
        resultEntity.setSubtitle(extractor.getResourceByIRI(resource.getInstanceOf()).getLabel());
        resultEntity.setLink(resource.getIri());
                    /*String wikiPage = kgFetcher.fetchWikiPage(resource.getIri());
                    if (wikiPage != null)
                        resultEntity.setLink(wikiPage);*/

        if (resource.getType() != null) {
            String type = "Type: " + resource.getType().toString();
            if (resource.getType() == ResourceType.Property) {
                type = " (خصیصه)";
                resultEntity.setResultType(ResultEntity.ResultType.Property);
            }
            if (resource.getType() == ResourceType.Entity) {
                type = " (موجودیت)";
                resultEntity.setResultType(ResultEntity.ResultType.Entity);
            }
            if (resultEntity.getSubtitle() == null && !type.equals(""))
                resultEntity.setSubtitle(type);
            else resultEntity.setSubtitle(resultEntity.getSubtitle() + type);
        }
        if (resource.getLabel() == null) {
            resultEntity.setTitle(extractTitleFromIri(resource.getIri()));
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
    public IResourceExtractor getExtractor() {
        return extractor;
    }



}
