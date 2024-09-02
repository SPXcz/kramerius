/*
 * Copyright (C) Jan 14, 2024 Pavel Stastny
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package cz.incad.kramerius.rest.oai;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URLEncoder;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.sun.jersey.api.client.Client;

import cz.incad.kramerius.FedoraAccess;
import cz.incad.kramerius.SolrAccess;
import cz.incad.kramerius.rest.apiNew.client.v60.libs.Instances;
import cz.incad.kramerius.rest.apiNew.client.v60.libs.OneInstance;
import cz.incad.kramerius.rest.apiNew.client.v60.redirection.ProxyHandlerException;
import cz.incad.kramerius.rest.apiNew.client.v60.redirection.item.ProxyItemHandler;
import cz.incad.kramerius.security.User;
import cz.incad.kramerius.utils.IPAddressUtils;
import cz.incad.kramerius.utils.XMLUtils;
import cz.incad.kramerius.utils.conf.KConfiguration;
import cz.incad.kramerius.utils.pid.LexerException;

public class OAIRecord {
    
    public static final Logger LOGGER = Logger.getLogger(OAIRecord.class.getName());
    
    private String identifier;
    private String solrIdentifier;
    
    // CDK extension
    
    private List<String> cdkCollections = new ArrayList<>();
    
    private String dateTimeStamp;
    
    
    public OAIRecord(String solrIdentifier, String identifier, String dateTimeStamp) {
        super();
        this.solrIdentifier = solrIdentifier;
        this.identifier = identifier;
        this.dateTimeStamp = dateTimeStamp;
    }
    
    public String getIdentifier() {
        return identifier;
    }
    
    public String getSolrIdentifier() {
        return solrIdentifier;
    }
    
    
    public List<String> getCdkCollections() {
        return cdkCollections;
    }

    public void setCdkCollections(List<String> cdkCollections) {
        this.cdkCollections = cdkCollections;
    }

    /** find oai record */
    public static OAIRecord findRecord(SolrAccess solrAccess,String oaiIdentifier) throws IOException, ParserConfigurationException, SAXException  {
        String pid = OAITools.pidFromOAIIdentifier(oaiIdentifier);

        String encodedQuery = URLEncoder.encode(String.format("pid:\"%s\"", pid),"UTF-8");
        String query = String.format("q=%s", encodedQuery);
        String solrResponseXml = solrAccess.requestWithSelectReturningString(query, "xml");
        Document document = XMLUtils.parseDocument(new StringReader(solrResponseXml));
        
        Element result = XMLUtils.findElement(document.getDocumentElement(), new XMLUtils.ElementsFilter() {
            @Override
            public boolean acceptElement(Element element) {
                return element.getNodeName().equals("result");
            }
        });
        
        if (result != null) {
            List<Element> docs = XMLUtils.getElements(result, new XMLUtils.ElementsFilter() {
                @Override
                public boolean acceptElement(Element element) {
                    return element.getNodeName().equals("doc");
                }
            });

            if (docs.size() > 0) {
                Element doc = docs.get(0);
                
                Element pidElm = XMLUtils.findElement(doc, new XMLUtils.ElementsFilter() {
                    @Override
                    public boolean acceptElement(Element element) { 
                        String name = element.getAttribute("name");
                        return name.equals("pid");
                        
                    }
                });
                
                List<Element> collections  = XMLUtils.getElements(doc, new XMLUtils.ElementsFilter() {
                    @Override
                    public boolean acceptElement(Element element) { 
                        String name = element.getAttribute("name");
                        return name.equals("cdk.collection");
                        
                    }
                });

                Element dateElm = XMLUtils.findElement(doc, new XMLUtils.ElementsFilter() {
                    @Override
                    public boolean acceptElement(Element element) { 
                        String name = element.getAttribute("name");
                        return name.equals("indexed");
                        
                    }
                });

                OAIRecord oaiRecord = new OAIRecord(pidElm.getTextContent(), oaiIdentifier, dateElm != null ? dateElm.getTextContent() : "");
                oaiRecord.setCdkCollections(collections.stream().map(Element::getTextContent).collect(Collectors.toList()));
                return oaiRecord;
                
            } else return null;
        } else {
            return null;
        }

    }

    /** render metadata */
    public Element toMetadata(SolrAccess solrAccess,Provider<User> userProvider, Provider<Client> clientProvider, Instances instances, HttpServletRequest request,   Document owningDocument, String oaiIdentifier, MetadataExport export, OAISet set) {
        return export.performOnCDKSide(solrAccess,userProvider,  clientProvider, instances, request,   owningDocument, this,  set);
    }
//  
//    /** render header 
//     * @throws IOException */
//    public Element toHeader(Document doc, FedoraAccess fa, OAISet set ) throws IOException {
//
//        Element header = doc.createElement("header");
//        Element identifier = doc.createElement("identifier");
//        identifier.setTextContent(this.identifier);
//        header.appendChild(identifier);
//        
//        //OffsetDateTime now = OffsetDateTime.now();
//        Element datestamp = doc.createElement("datestamp");
//        datestamp.setTextContent(this.dateTimeStamp);
//        header.appendChild(datestamp);
//        
//        if (set != null) {
//            Element setSpecElm = doc.createElement("setSpec");
//            setSpecElm.setTextContent(set.getSetSpec());
//            header.appendChild(setSpecElm);
//        }
//
//        String pid = OAITools.pidFromOAIIdentifier(this.identifier);
//        if (!fa.isObjectAvailable(pid) && (!pid.contains("_"))) {
//            header.setAttribute("status", "deleted");
//        }
//        return header;
//    }

    
    /** render header */
    public Element toHeader(Document doc, OAISet set, SolrAccess solrAccess,Provider<User> userProvider, Provider<Client> clientProvider, Instances instances, HttpServletRequest request, String source) {

        Element header = doc.createElement("header");

        Element identifier = doc.createElement("identifier");
        identifier.setTextContent(this.identifier);
        header.appendChild(identifier);
        
        //OffsetDateTime now = OffsetDateTime.now();
        Element datestamp = doc.createElement("datestamp");
        header.appendChild(datestamp);
        datestamp.setTextContent(this.dateTimeStamp);
        
        if (set != null) {
            Element setSpecElm = doc.createElement("setSpec");
            setSpecElm.setTextContent(set.getSetSpec());
            header.appendChild(setSpecElm);
        }
        
        try {
            String pid = OAITools.pidFromOAIIdentifier(this.identifier);
            ProxyItemHandler redirectHandler = MetadataExport.findRedirectHandler(solrAccess, userProvider, clientProvider, instances, request, pid, null);
            if (!pid.contains("_")) {
                if (redirectHandler != null && !redirectHandler.isStreamBiblioModsAvaiable()) {
                    header.setAttribute("status", "deleted");
                // co s tim ??
                } else if (redirectHandler == null){
                    header.setAttribute("status", "deleted");
                    
                }
            }
        } catch (DOMException | LexerException | IOException | ProxyHandlerException e) {
            LOGGER.log(Level.SEVERE,e.getMessage(),e);
        }
        return header;
    }
}
