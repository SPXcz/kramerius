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
import java.io.StringReader;
import java.net.URLEncoder;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import cz.incad.kramerius.FedoraAccess;
import cz.incad.kramerius.SolrAccess;
import cz.incad.kramerius.utils.XMLUtils;

public class OAIRecord {
    
    private String identifier;
    private String solrIdentifier;
    
    public OAIRecord(String solrIdentifier, String identifier) {
        super();
        this.solrIdentifier = solrIdentifier;
        this.identifier = identifier;
    }
    
    public String getIdentifier() {
        return identifier;
    }
    
    public String getSolrIdentifier() {
        return solrIdentifier;
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
                Element pidElm = XMLUtils.findElement(docs.get(0), "str");
                return new OAIRecord(pidElm.getTextContent(), oaiIdentifier);
                
            } else return null;
        } else {
            return null;
        }

    }

    /** render metadata */
    public Element toMetadata(HttpServletRequest request, FedoraAccess fa, Document doc, MetadataExport export) {
        return export.perform(request, fa, doc, identifier);
    }
    
    /** render header */
    public Element toHeader(Document doc, String setSpec) {
        Element header = doc.createElement("header");

        Element identifier = doc.createElement("identifier");
        identifier.setTextContent(this.identifier);
        header.appendChild(identifier);
        
        OffsetDateTime now = OffsetDateTime.now();
        Element datestamp = doc.createElement("datestamp");
        datestamp.setTextContent(now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        header.appendChild(datestamp);
        
        if (setSpec != null) {
            Element setSpecElm = doc.createElement("setSpec");
            setSpecElm.setTextContent(setSpec);
            header.appendChild(setSpecElm);
        }
        
        return header;
    }
}
