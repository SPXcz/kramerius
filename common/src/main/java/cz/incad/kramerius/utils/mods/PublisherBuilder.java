/*
 * Copyright (C) 2012 Pavel Stastny
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
package cz.incad.kramerius.utils.mods;

import java.util.List;
import java.util.Map;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import cz.incad.kramerius.FedoraNamespaceContext;

public class PublisherBuilder extends AbstractBuilder {

    public static final String MODS_PUBLISHER="mods:publisher";
    public static final String MODS_DATE="mods:publisherDate";
    
    @Override
    public void build(Document document, Map<String, List<String>> map, String model) throws XPathExpressionException {
        XPath xpath = getFactory().newXPath();
        xpath.setNamespaceContext(new FedoraNamespaceContext());
        
        XPathExpression expr = xpath.compile("//mods:originInfo[@transliteration='publisher']/mods:publisher/text()");
        Node node = (Node) expr.evaluate(document, XPathConstants.NODE);
        if (node != null) {
            add(MODS_PUBLISHER, ((Text)node).getData(), map);
        }        

        expr = xpath.compile("//mods:originInfo[@transliteration='publisher']/mods:dateIssued/text()");
        node = (Node) expr.evaluate(document, XPathConstants.NODE);
        if (node != null) {
            add(MODS_DATE, ((Text)node).getData(), map);
        }        
        
    }

    
}
