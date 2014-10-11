/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.jdom.*
import org.jdom.input.SAXBuilder
import org.jdom.output.Format
import org.jdom.output.XMLOutputter
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import org.xml.sax.SAXException

/**
 * Appends multiple occurrences of some XML file.
 *
 * Modified from org.apache.maven.plugins.shade.resource.XmlAppendingTransformer.java
 *
 * Modifications
 * @author John Engelman
 */
class XmlAppendingTransformer implements Transformer {
    static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance"

    boolean ignoreDtd = true

    String resource

    Document doc

    boolean canTransformResource(FileTreeElement element) {
        def path = element.relativePath.pathString
        if (resource != null && resource.equalsIgnoreCase(path)) {
            return true
        }

        return false
    }

    void transform(String jarName, String path, InputStream is, List<Relocator> relocators) {
        Document r
        try {
            SAXBuilder builder = new SAXBuilder(false)
            builder.setExpandEntities(false)
            if (ignoreDtd) {
                builder.setEntityResolver(new EntityResolver() {
                    InputSource resolveEntity(String publicId, String systemId)
                    throws SAXException, IOException {
                        return new InputSource(new StringReader(""))
                    }
                })
            }
            r = builder.build(is)
        }
        catch (JDOMException e) {
            throw new RuntimeException("Error processing resource " + resource + ": " + e.getMessage(), e)
        }

        if (doc == null) {
            doc = r
        } else {
            Element root = r.getRootElement()

            root.attributes.each { Attribute a ->

                Element mergedEl = doc.getRootElement()
                Attribute mergedAtt = mergedEl.getAttribute(a.getName(), a.getNamespace())
                if (mergedAtt == null) {
                    mergedEl.setAttribute(a)
                }
            }

            root.children.each { Content n ->
                doc.getRootElement().addContent(n.clone())
            }
        }
    }

    boolean hasTransformedResource() {
        return doc != null
    }

    void modifyOutputStream(ZipOutputStream os) {
        os.putNextEntry(new ZipEntry(resource))
        new XMLOutputter(Format.getPrettyFormat()).output(doc, os)

        doc = null
    }
}
