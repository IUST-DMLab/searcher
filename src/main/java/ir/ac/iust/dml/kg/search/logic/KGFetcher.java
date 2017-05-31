package ir.ac.iust.dml.kg.search.logic;

import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import ir.ac.iust.dml.kg.raw.utils.ConfigReader;
import ir.ac.iust.dml.kg.virtuoso.jena.driver.VirtGraph;

import java.util.Map;
import java.util.TreeMap;

/**
 * Created by ali on 4/16/17.
 */
public class KGFetcher {
    private static final String KB_PREFIX = "http://fkg.iust.ac.ir/resource/";
    private VirtGraph graph = null;
    private Model model = null;

    public KGFetcher() {
        System.err.println("Loading KGFetcher...");
        long t1 = System.currentTimeMillis();
        final String virtuosoServer = ConfigReader.INSTANCE.getString("virtuoso.address",
                "localhost:1111");
        final String virtuosoUser = ConfigReader.INSTANCE.getString("virtuoso.user", "dba");
        final String virtuosoPass = ConfigReader.INSTANCE.getString("virtuoso.password", "fkgVIRTUOSO2017");
        graph = new VirtGraph("http://fkg.iust.ac.ir/new",
                "jdbc:virtuoso://" + virtuosoServer, virtuosoUser, virtuosoPass);
        model = ModelFactory.createModelForGraph(graph);
        System.err.printf("KGFetcher loaded in %,d ms", (System.currentTimeMillis() - t1));
    }

    public String fetchLabel(String uri, boolean filterNonPersian) {
        String queryString =
                "SELECT ?o \n" +
                        "WHERE {\n" +
                        "<" +
                        uri +
                        "> <http://www.w3.org/2000/01/rdf-schema#label> ?o. \n";
        if (filterNonPersian)
            queryString += "FILTER (lang(?o) = \"fa\")";

        queryString += "}";

        final Query query = QueryFactory.create(queryString);
        final QueryExecution qexec = QueryExecutionFactory.create(query, model);
        final ResultSet results = qexec.execSelect();

        String resultText = "";
        while (results.hasNext()) {
            final QuerySolution binding = results.nextSolution();
            final RDFNode o = binding.get("o");
            resultText += o.toString();
            if (results.hasNext())
                resultText += "، ";
        }
        resultText = resultText.replaceAll("@fa", "");
        if (resultText.equals(""))
            resultText = uri;

        if (resultText.contains(KB_PREFIX))
            resultText = resultText.replace(KB_PREFIX, "").replace('_', ' ');

        return resultText;
    }

    public String fetchWikiPage(String uri) {
        String queryString =
                "SELECT ?o \n" +
                        "WHERE {\n" +
                        "<" +
                        uri +
                        "> <http://fkg.iust.ac.ir/ontology/wikipageredirects> ?o. \n" +
                        "}";

        final Query query = QueryFactory.create(queryString);
        final QueryExecution qexec = QueryExecutionFactory.create(query, model);
        final ResultSet results = qexec.execSelect();

        String resultText = null;
        if (results.hasNext()) {
            final QuerySolution binding = results.nextSolution();
            final RDFNode o = binding.get("o");
            resultText = o.toString();
        }
        return resultText;
    }

    /**
     * Returns the (uri,label) pairs for each object statisfying subj-property-object-
     *
     * @param subjectUri
     * @param propertyUri
     * @return
     */
    public Map<String, String> fetchSubjPropObjQuery(String subjectUri, String propertyUri) {
        Map<String, String> matchedObjectLabels = new TreeMap<String, String>();
        String queryString =
                "SELECT ?o " + //?l " +
                        "WHERE {\n" +
                        "<" +
                        subjectUri +
                        "> <" + propertyUri + "> ?o. \n" +
                        //"?o <http://www.w3.org/2000/01/rdf-schema#label> ?l\n" +
                        //"FILTER (lang(?o) = \"fa\")" +
                        "}";
        final Query query = QueryFactory.create(queryString);
        final QueryExecution qexec = QueryExecutionFactory.create(query, model);
        final ResultSet results = qexec.execSelect();

        String resultText = "";
        while (results.hasNext()) {
            final QuerySolution binding = results.nextSolution();
            final RDFNode o = binding.get("o");
            String objectUri = o.toString();
            //final RDFNode l = binding.get("l");
            String objectLabel = objectUri;
            try {
                objectLabel = fetchLabel(objectUri, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
            matchedObjectLabels.put(objectUri, objectLabel);

        }
        return matchedObjectLabels;
    }


    /*SELECT  ?o
WHERE { <http://fkg.iust.ac.ir/resources/کیمیا_(مجموعه_تلویزیونی)> <http://fkg.iust.ac.ir/ontology/starring> ?o. }
*/

}
