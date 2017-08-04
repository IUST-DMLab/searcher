package ir.ac.iust.dml.kg.search.logic;

import com.google.common.base.Strings;
import ir.ac.iust.dml.kg.resource.extractor.*;
import ir.ac.iust.dml.kg.resource.extractor.tree.TreeResourceExtractor;
import ir.ac.iust.dml.kg.search.logic.data.ResultEntity;
import ir.ac.iust.dml.kg.search.logic.data.SearchResult;
import knowledgegraph.normalizer.PersianCharNormalizer;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Searcher {
    private static final String BLACKLIST_FILE_NAME = "black_list.txt";
    private final IResourceExtractor extractor;
    private final KGFetcher kgFetcher;
    private static final PersianCharNormalizer normalizer = customizeNormalizer();



    private final Set<String> blacklist = new HashSet<String>();



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
        blacklist.addAll(Files.readAllLines(Paths.get(BLACKLIST_FILE_NAME)));
        extractor = setupNewExtractor();
        kgFetcher = new KGFetcher();
    }

    public SearchResult search(String keyword) {
        String queryText = normalizer.normalize(keyword);
        final SearchResult result = new SearchResult();
        System.out.println(new Date() + " PROCESSING QUERY: " + keyword);
        //Answering predicate-subject phrases
        try {
            List<MatchedResource> matchedResourcesUnfiltered = extractor.search(queryText, true);

            List<Resource> allMatchedResources = matchedResourcesUnfiltered.stream()
                    .flatMap(mR -> {
                        List<Resource> list = new ArrayList<>();
                        if (mR.getResource() != null)
                            list.add(mR.getResource());
                        if (mR.getAmbiguities() != null)
                            list.addAll(mR.getAmbiguities());

                        //if there is a detected property, skip all other entities
                        if(list.stream().anyMatch(r -> r.getType() != null && r.getType().toString().contains("Property")))
                            return list.stream().filter(r -> r.getType() != null && r.getType().toString().contains("Property"));
                        return list.stream();
                    })
                    .filter(r -> r.getIri() != null)
                    .filter(Util.distinctByKey(Resource::getIri)) //distinct by Iri
                    .filter(r -> !blacklist.contains(r.getIri()))
                    .collect(Collectors.toList());

            List<Resource> properties = allMatchedResources.stream()
                    .filter(r -> r.getType() != null)
                    .filter(r -> r.getType().toString().contains("Property"))
                    .filter(r -> !blacklist.contains(r.getIri()))
                    .collect(Collectors.toList());

            /*List<Resource> allEntities = allMatchedResources.stream()
                    .filter(r -> r.getType() != null)
                    .filter(r -> !properties.contains(r))
                    .collect(Collectors.toList());*/

            List<Resource> disambiguatedResources = matchedResourcesUnfiltered.stream()
                    .filter(mR -> mR.getSubsetOf() == null) //for entities, remove Subsets
                    .filter(mR -> mR.getAmbiguities() != null && mR.getAmbiguities().size() > 0)
                    .flatMap(mR -> mR.getAmbiguities().stream())
                    .map(r -> {
                        if (Strings.isNullOrEmpty(r.getLabel())) r.setLabel(Util.iriToLabel(r.getIri()));
                        else r.setLabel(r.getLabel() + " (ابهام‌زدایی شده)");
                        return r;
                    })
                    .filter(r -> !blacklist.contains(r.getIri()))
                    .collect(Collectors.toList());

            List<Resource> entities = matchedResourcesUnfiltered.stream()
                    .filter(mR -> mR.getSubsetOf() == null) //for entities, remove Subsets
                    .filter(mR -> mR.getResource() != null)
                    .map(MatchedResource::getResource)
                    .filter(r -> !blacklist.contains(r.getIri()))
                    .collect(Collectors.toList());

            entities.addAll(disambiguatedResources);
            List<Resource> finalEntities = entities.stream()
                    .filter(r -> !properties.contains(r))
                    .filter(r -> r.getIri() != null)
                    .filter(Util.distinctByKey(Resource::getIri)) //distinct by Iri
                    .sorted((o1, o2) -> ((Double) kgFetcher.getRank(o2.getIri())).compareTo(kgFetcher.getRank(o1.getIri())))
                    .collect(Collectors.toList());


            // وای وای چه کار زشتی!
            doManualCorrections(properties,queryText);

            for (Resource subjectR : entities) {
                for (Resource propertyR : properties) {
                    try {
                        System.out.println("Trying combinatios for " + subjectR.getIri() + "\t & \t" + propertyR.getIri());
                        Map<String, String> objectLables = kgFetcher.fetchSubjPropObjQuery(subjectR.getIri(), propertyR.getIri(),selectDirection(subjectR.getIri(),propertyR.getIri(),queryText));
                        System.out.println("\t RESULTS FOUND: " + objectLables.keySet().size());
                        for (Map.Entry<String, String> olEntry : objectLables.entrySet().stream().sorted((o1, o2) -> ((Double) kgFetcher.getRank(o2.getKey())).compareTo(kgFetcher.getRank(o1.getKey()))).collect(Collectors.toList())) {
                            System.out.printf("Object: %s\t%s\n", olEntry.getKey(), olEntry.getValue());
                            ResultEntity resultEntity = new ResultEntity();
                            if(olEntry.getKey().startsWith("http"))
                                resultEntity.setLink(olEntry.getKey());
                            resultEntity.setReferenceUri(subjectR.getIri());
                            resultEntity.setTitle(olEntry.getValue());
                            resultEntity.setDescription("نتیجه‌ی گزاره‌ای");
                            resultEntity.setPhotoUrls(kgFetcher.fetchPhotoUrls(resultEntity.getLink()));
                            resultEntity.setResultType(ResultEntity.ResultType.RelationalResult);
                            if (!(Strings.isNullOrEmpty(subjectR.getLabel()) || Strings.isNullOrEmpty(propertyR.getLabel()))) {
                                resultEntity.setDescription(resultEntity.getDescription() + ": [" + subjectR.getLabel() + "] / [" + propertyR.getLabel() + "]");
                            }
                            result.getEntities().add(resultEntity);
                        }
                    } catch (Exception e) {
                        String emsg = e.getMessage().toLowerCase();
                        e.printStackTrace();
                        if(emsg.contains("broken pipe") || emsg.contains("connection refused")){
                            System.err.println((new Date()) + "\t EXITING!");
                            System.exit(1);
                        }
                    }
                }
            }

            //Output individual entities
            Set<String> uriOfEntities = new HashSet<>();
            for (Resource entity : finalEntities) {
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




            for(ResultEntity r : result.getEntities()) {
                if (r.getLink().contains(")"))
                    r.setTitle(Util.iriToLabel(r.getLink()));
                if(r.getTitle().contains("/"))
                    r.setTitle(Util.iriToLabel(r.getTitle()));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private SearchDirection selectDirection(String subjectIri, String propertyIri, String queryText) {
        if(propertyIri.contains("ontology/starring") || propertyIri.contains("ontology/Province"))
            return SearchDirection.BOTH;
        return SearchDirection.SUBJ_PROP;
    }

    /**
     *  وای وای چه کار زشتی!
     *  Adds undetected properties according to the patterns.
     * @param properties
     * @param queryText
     */
    private void doManualCorrections(List<Resource> properties, String queryText) {
        if((queryText.contains("فیلم") ||  queryText.contains("سریال"))
                && properties.stream().noneMatch(r -> r.getIri().contains("ontology/starring"))) {
            properties.add(new Resource("http://fkg.iust.ac.ir/ontology/starring", "فیلم‌"));
            properties.add(new Resource("http://fkg.iust.ac.ir/ontology/director","کارگردان"));
        }

        if((queryText.contains("درامد") ||  queryText.contains("درآمد"))
                && properties.stream().noneMatch(r -> r.getIri().contains("ontology/revenue")))
            properties.add(new Resource("http://fkg.iust.ac.ir/ontology/revenue","درآمد"));

        if((queryText.contains("ترکیبات اصلی"))
                && properties.stream().noneMatch(r -> r.getIri().contains("ontology/ingredient")))
            properties.add(new Resource("http://fkg.iust.ac.ir/ontology/ingredient","ترکیبات اصلی"));

        if((queryText.contains("کارکنان") || queryText.contains("پرسنل") || queryText.contains("کارمندان"))
                && properties.stream().noneMatch(r -> r.getIri().contains("ontology/numberOfEmployees")))
            properties.add(new Resource("http://fkg.iust.ac.ir/ontology/numberOfEmployees","تعداد پرسنل"));

        if((queryText.contains("باشگاه") || queryText.contains("تیم"))
                && properties.stream().noneMatch(r -> r.getIri().contains("ontology/team")))
            properties.add(new Resource("http://fkg.iust.ac.ir/ontology/team","تیم"));


        if((queryText.contains("ساخت") && queryText.contains("دوره"))
                && properties.stream().noneMatch(r -> r.getIri().contains("property/دیرینگی")))
            properties.add(new Resource("http://fkg.iust.ac.ir/property/دیرینگی","دوره ساخت (دیرینگی)"));
    }

    private ResultEntity matchedResourceToResultEntity(Resource resource) {
        ResultEntity resultEntity = new ResultEntity();
        resultEntity.setTitle(resource.getLabel());

        //resultEntity.setSubtitle(kgFetcher.fetchLabel(resource.getInstanceOf(), true));
        Resource ontologyClass = extractor.getResourceByIRI(resource.getInstanceOf());
        if(ontologyClass != null && ontologyClass.getLabel() != null && !ontologyClass.getLabel().isEmpty())
            resultEntity.setSubtitle(ontologyClass.getLabel());

        resultEntity.setLink(resource.getIri());
                    /*String wikiPage = kgFetcher.fetchWikiPage(resource.getIri());
                    if (wikiPage != null)
                        resultEntity.setLink(wikiPage);*/

        resultEntity.setPhotoUrls(kgFetcher.fetchPhotoUrls(resource.getIri()));

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

    private static PersianCharNormalizer customizeNormalizer() {
        List<PersianCharNormalizer.Option> options = new ArrayList<>();

        options.add(PersianCharNormalizer.Option.NORMAL_HE);
        options.add(PersianCharNormalizer.Option.NORMAL_KAF);
        options.add(PersianCharNormalizer.Option.NORMAL_NUMBERS);
        options.add(PersianCharNormalizer.Option.NORMAL_WAW);
        options.add(PersianCharNormalizer.Option.NORMAL_YEH);

        return new PersianCharNormalizer(options);
    }

// this line is for git sync test!

}
