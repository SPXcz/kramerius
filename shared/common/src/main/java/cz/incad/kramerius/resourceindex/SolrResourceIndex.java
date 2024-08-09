package cz.incad.kramerius.resourceindex;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.ibm.icu.text.SimpleDateFormat;
import cz.incad.kramerius.FedoraNamespaces;
import cz.incad.kramerius.ObjectPidsPath;
import cz.incad.kramerius.security.SpecialObjects;
import cz.incad.kramerius.utils.StringUtils;
import cz.incad.kramerius.utils.solr.SolrUtils;
import cz.incad.kramerius.virtualcollections.CollectionPidUtils;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SOLR implemenation of the resource index;
 * Stores relation and information from RELS-EXT
 *
 * @author pstastny
 */
public class SolrResourceIndex implements IResourceIndex {


    public static final SimpleDateFormat XSD_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    public static final String DATE_FORMAT_TYPE = "http://www.w3.org/2001/XMLSchema#dateTime";


    private SolrClient solrClient;

    @Inject
    public SolrResourceIndex(@Named("processingQuery") SolrClient solrClient) {
        super();
        this.solrClient = solrClient;
    }


    @Override
    public List<Map<String, String>> search(String query, int limit, int offset) throws ResourceIndexException {
        String[] words = query.split("\\s+");
        StringBuilder builder = new StringBuilder("type:\"description\"");
        for (int i = 0, ll = words.length; i < ll; i++) {
            String word = words[i];
            if (word.trim().equals("")) continue;

            String escapedWord = SolrUtils.escapeQuery(word);
            builder.append(" AND ");
            String q = word.toLowerCase().startsWith("uuid") ?
                    "(source:\"" + word + "\" OR source_edge:" + escapedWord + " )" :
                    "(dc.title_czech:" + escapedWord + " OR dc.title_edge:" + escapedWord + " )";
            builder.append(q);
        }
        SolrQuery solrQuery = new SolrQuery(builder.toString());
        try {
            List<Map<String, String>> retvals = new ArrayList<>();
            QueryResponse response = this.solrClient.query(solrQuery);
            for (SolrDocument doc : response.getResults()) {
                retvals.add(createMap(doc));

            }
            return retvals;
        } catch (SolrServerException e) {
            throw new ResourceIndexException(e);
        } catch (IOException e) {
            throw new ResourceIndexException(e);
        }
    }

    private Map<String, String> createMap(SolrDocument doc) {
        Map<String, String> m = new HashMap<>();
        doc.keySet().stream().filter(item -> !item.equals("pid")).forEach((item) -> {
            m.put(item, doc.getFieldValue(item).toString());
        });
        return m;
    }

    public List<Map<String, String>> getSubjects(String pid) throws ResourceIndexException {
        try {
            int limit = 1000;
            int start = 0;
            List<Map<String, String>> retvals = new ArrayList<>();

            QueryResponse response = this.solrClient.query(new SolrQuery("targetPid:\"" + pid + "\" AND type:description").setRows(limit).setStart(start));
            long found = response.getResults().getNumFound();
            while (start < found) {
                for (SolrDocument sDoc : response.getResults()) {
                    retvals.add(createMap(sDoc));
                }

                response = this.solrClient.query(new SolrQuery("targetPid:\"" + pid + "\" AND type:description").setRows(limit).setStart(start));
                start += limit;
            }
            return retvals;
        } catch (SolrServerException e) {
            throw new ResourceIndexException(e);
        } catch (IOException e) {
            throw new ResourceIndexException(e);
        }
    }

    @Override
    public List<String> getObjectsByModel(String model, int limit, int offset, String orderby, String orderDir) throws ResourceIndexException {
        try {
            List<String> retvals = new ArrayList<>();
            QueryResponse response = this.solrClient.query(new SolrQuery("model:\"" + model + "\" AND type:description").setRows(limit).setStart(offset));
            SolrDocumentList results = response.getResults();
            for (SolrDocument sDoc : results) {
                retvals.add(sDoc.getFieldValue("source").toString());
            }
            return retvals;
        } catch (SolrServerException e) {
            throw new ResourceIndexException(e);
        } catch (IOException e) {
            throw new ResourceIndexException(e);
        }
    }


    
    public List<Pair<String,Long>> getAllFedoraModelsAsList() throws ResourceIndexException {
        try {
            QueryResponse response = this.solrClient.query(new SolrQuery("type:description").setRows(0).setFacet(true).addFacetField("model"));
            List<Count> values = response.getFacetField("model").getValues();
            return values.stream().map(c-> {
                long count = c.getCount();
                String cname = c.getName();
                return Pair.of(cname, count);
            }).collect(Collectors.toList());
        } catch (SolrServerException | IOException e) {
            throw new ResourceIndexException(e);
        }
    }
    

    @Override
    // TODO: rewrite it
    public Document getFedoraModels() throws ResourceIndexException {
        try {
            //QueryResponse response = this.solrClient.query(new SolrQuery("type:description").setRows(0).setFacet(true).addFacetField("model"));
            List<Pair<String,Long>> allModels = getAllFedoraModelsAsList();
            
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.newDocument();
            Element rootElement = doc.createElementNS(FedoraNamespaces.SPARQL_NAMESPACE_URI, "sparql");
            rootElement.appendChild(createHeader(doc, "object", "title"));
            rootElement.appendChild(createModelResults(doc, allModels));
            doc.appendChild(rootElement);
            return doc;
        } catch (ParserConfigurationException e) {
            throw new ResourceIndexException(e);
        } catch (ResourceIndexException e) {
            throw e;
        }
    }

    private Element createHeader(Document doc, String... variables) {
        Element head = doc.createElementNS(FedoraNamespaces.SPARQL_NAMESPACE_URI, "head");
        for (String var : variables) {
            Element variable = doc.createElementNS(FedoraNamespaces.SPARQL_NAMESPACE_URI, "variable");
            variable.setAttribute("name", var);
            head.appendChild(variable);
        }
        return head;
    }

    private Element createModelResults(Document doc, List<Pair<String,Long>> values) {
        Element results = doc.createElementNS(FedoraNamespaces.SPARQL_NAMESPACE_URI, "results");
        //for (Count count : values) {
        for (Pair<String,Long> count : values) {
            Element result = doc.createElementNS(FedoraNamespaces.SPARQL_NAMESPACE_URI, "result");
            Element object = doc.createElementNS(FedoraNamespaces.SPARQL_NAMESPACE_URI, "object");
            //page info:fedora/model:page
            object.setAttribute("uri", "info:fedora/model:" + count.getLeft());
            result.appendChild(object);

            Element title = doc.createElementNS(FedoraNamespaces.SPARQL_NAMESPACE_URI, "title");
            title.setTextContent(count.getLeft());
            result.appendChild(title);

            results.appendChild(result);
        }
        return results;
    }


    @Override
    public List<String> getParentsPids(String pid) throws ResourceIndexException {
        try {
            List<String> retvals = new ArrayList<>();
            QueryResponse response = this.solrClient.query(new SolrQuery("targetPid:\"" + pid + "\" AND type:relation").setRows(1000));
            SolrDocumentList results = response.getResults();
            for (SolrDocument sDoc : results) {
                retvals.add(sDoc.getFieldValue("source").toString());
            }
            return retvals;
        } catch (SolrServerException e) {
            throw new ResourceIndexException(e);
        } catch (IOException e) {
            throw new ResourceIndexException(e);
        }
    }

    @Override
    public ObjectPidsPath[] getPaths(String pid) throws ResourceIndexException {
        if (SpecialObjects.isSpecialObject(pid))
            return new ObjectPidsPath[]{ObjectPidsPath.REPOSITORY_PATH};
        if (CollectionPidUtils.isCollectionPid(pid)) {
            return new ObjectPidsPath[]{new ObjectPidsPath(pid)};
        }
        List<String> parentsPids = getParentsPids(pid);
        return new ObjectPidsPath[]{new ObjectPidsPath(parentsPids.toArray(new String[]{}))};
    }

    @Override
    public List<String> getObjectsInCollection(String collection, int limit, int offset) throws ResourceIndexException {
        List<String> returnList = new ArrayList<>();
        try {
            String queryString = "type:\"description\" AND model:\"model:collection\"";
            SolrQuery solrQuery = new SolrQuery(queryString);
            solrQuery.setFields("source");
            if (limit > 0) solrQuery.setRows(limit);
            if (offset > 0) solrQuery.setStart(offset);
            QueryResponse response = this.solrClient.query(solrQuery);
            SolrDocumentList results = response.getResults();
            for (SolrDocument doc : results) {
                returnList.add(doc.get("source").toString());
            }
            return returnList;
        } catch (SolrServerException e) {
            throw new ResourceIndexException(e);
        } catch (IOException e) {
            throw new ResourceIndexException(e);
        }
    }

    @Override
    public boolean existsPid(String pid) throws ResourceIndexException {
        try {
            String queryString = "type:\"description\" AND source:\"" + pid + "\"";
            SolrQuery solrQuery = new SolrQuery(queryString);
            solrQuery.setFields("source");
            QueryResponse query = this.solrClient.query(solrQuery);
            long numFound = query.getResults().getNumFound();
            return numFound > 0;
        } catch (SolrServerException e) {
            throw new ResourceIndexException(e);
        } catch (IOException e) {
            throw new ResourceIndexException(e);
        }
    }

    @Override
    public Document getVirtualCollections() throws ResourceIndexException {

        throw new UnsupportedOperationException("unsupported");
    }


    @Override
    public List<String> getFedoraPidsFromModel(String model, int limit, int offset) throws ResourceIndexException {
        throw new UnsupportedOperationException("unsupported");
    }


    @Override
    public List<String> getCollections() throws ResourceIndexException {
        List<String> retlist = new ArrayList<>();
        long numberOfResults = Long.MAX_VALUE;
        int page = 1000;
        int start = 0;
        try {
            while (start < numberOfResults) {
                QueryResponse response = this.solrClient.query(new SolrQuery("model:\"model:collection\" AND type:description").setRows(page).setStart(start).setFields("source"));
                SolrDocumentList results = response.getResults();
                numberOfResults = results.getNumFound();
                for (SolrDocument doc : results) {
                    Object source = doc.getFieldValue("source");
                    retlist.add(source.toString());
                }
                start += page;
            }
            return retlist;
        } catch (SolrServerException e) {
            throw new ResourceIndexException(e);
        } catch (IOException e) {
            throw new ResourceIndexException(e);
        }
    }

    @Override
    public List<Map<String, String>> getObjects(String model, int limit, int offset, String orderby, String orderDir) throws ResourceIndexException {
        try {
            List<Map<String, String>> list = new ArrayList<>();
            QueryResponse response = (StringUtils.isAnyString(orderby)) ?
                    this.solrClient.query(new SolrQuery("model:\"" + model + "\" AND type:description").setSort(orderby, SolrQuery.ORDER.valueOf(orderDir)).setRows(limit).setStart(offset)) :
                    this.solrClient.query(new SolrQuery("model:\"" + model + "\" AND type:description").setRows(limit).setStart(offset));

            SolrDocumentList results = response.getResults();
            for (SolrDocument doc : results) {
                list.add(createMap(doc));
            }
            return list;
        } catch (SolrServerException e) {
            throw new ResourceIndexException(e);
        } catch (IOException e) {
            throw new ResourceIndexException(e);
        }
    }


    private Element createDocumentResults(Document doc, SolrDocumentList doclist) {
        Element results = doc.createElementNS(FedoraNamespaces.SPARQL_NAMESPACE_URI, "results");
        for (SolrDocument solrDocument : doclist) {
            Element result = doc.createElementNS(FedoraNamespaces.SPARQL_NAMESPACE_URI, "result");

            Element title = doc.createElementNS(FedoraNamespaces.SPARQL_NAMESPACE_URI, "title");
            title.setTextContent(solrDocument.get("dc.title").toString());
            result.appendChild(title);

            Element date = doc.createElementNS(FedoraNamespaces.SPARQL_NAMESPACE_URI, "date");
            date.setTextContent(solrDocument.get("date").toString());
            result.appendChild(date);

            Element object = doc.createElementNS(FedoraNamespaces.SPARQL_NAMESPACE_URI, "object");

            Object ref = solrDocument.getFieldValue("ref");
            if (ref != null) {
                object.setAttribute("uri", ref.toString());
            }
            result.appendChild(object);

            results.appendChild(result);
        }
        return results;
    }
}
