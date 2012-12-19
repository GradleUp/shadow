package org.gradle.api.plugins.shadow.pom;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
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

//package org.apache.maven.model.io.jdom;

//---------------------------------/
//- Imported classes and packages -/
//---------------------------------/

import org.apache.maven.model.*;
import org.apache.maven.model.Parent;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jdom.*;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.*;

/**
 * Class MavenJDOMWriter.
 *
 * @version $Revision: 1300048 $ $Date: 2012-03-13 05:09:35 -0500 (Tue, 13 Mar 2012) $
 */
public class MavenJDOMWriter
{

    // --------------------------/
    // - Class/Member Variables -/
    // --------------------------/

    /**
     * Field factory
     */
    private DefaultJDOMFactory factory;

    /**
     * Field lineSeparator
     */
    private String lineSeparator;

    // ----------------/
    // - Constructors -/
    // ----------------/

    public MavenJDOMWriter()
    {
        factory = new DefaultJDOMFactory();
        lineSeparator = "\n";
    } // -- org.apache.maven.model.io.jdom.MavenJDOMWriter()

    // -----------/
    // - Methods -/
    // -----------/

    /**
     * Method findAndReplaceProperties
     *
     * @param counter
     * @param props
     * @param name
     * @param parent
     */
    protected Element findAndReplaceProperties( Counter counter, Element parent, String name, Map props )
    {
        boolean shouldExist = props != null && !props.isEmpty();
        Element element = updateElement( counter, parent, name, shouldExist );
        if ( shouldExist )
        {
            Iterator it = props.keySet().iterator();
            Counter innerCounter = new Counter( counter.getDepth() + 1 );
            while ( it.hasNext() )
            {
                String key = (String) it.next();
                findAndReplaceSimpleElement( innerCounter, element, key, (String) props.get( key ), null );
            }
            ArrayList lst = new ArrayList( props.keySet() );
            it = element.getChildren().iterator();
            while ( it.hasNext() )
            {
                Element elem = (Element) it.next();
                String key = elem.getName();
                if ( !lst.contains( key ) )
                {
                    it.remove();
                }
            }
        }
        return element;
    } // -- Element findAndReplaceProperties(Counter, Element, String, Map)

    /**
     * Method findAndReplaceSimpleElement
     *
     * @param counter
     * @param defaultValue
     * @param text
     * @param name
     * @param parent
     */
    protected Element findAndReplaceSimpleElement( Counter counter, Element parent, String name, String text,
                                                   String defaultValue )
    {
        if ( defaultValue != null && text != null && defaultValue.equals( text ) )
        {
            Element element = parent.getChild( name, parent.getNamespace() );
            // if exist and is default value or if doesn't exist.. just keep the way it is..
            if ( ( element != null && defaultValue.equals( element.getText() ) ) || element == null )
            {
                return element;
            }
        }
        boolean shouldExist = text != null && text.trim().length() > 0;
        Element element = updateElement( counter, parent, name, shouldExist );
        if ( shouldExist )
        {
            element.setText( text );
        }
        return element;
    } // -- Element findAndReplaceSimpleElement(Counter, Element, String, String, String)

    /**
     * Method findAndReplaceSimpleLists
     *
     * @param counter
     * @param childName
     * @param parentName
     * @param list
     * @param parent
     */
    protected Element findAndReplaceSimpleLists( Counter counter, Element parent, Collection list,
                                                 String parentName, String childName )
    {
        boolean shouldExist = list != null && list.size() > 0;
        Element element = updateElement( counter, parent, parentName, shouldExist );
        if ( shouldExist )
        {
            Iterator it = list.iterator();
            Iterator elIt = element.getChildren( childName, element.getNamespace() ).iterator();
            if ( !elIt.hasNext() )
            {
                elIt = null;
            }
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            while ( it.hasNext() )
            {
                String value = (String) it.next();
                Element el;
                if ( elIt != null && elIt.hasNext() )
                {
                    el = (Element) elIt.next();
                    if ( !elIt.hasNext() )
                    {
                        elIt = null;
                    }
                }
                else
                {
                    el = factory.element( childName, element.getNamespace() );
                    insertAtPreferredLocation( element, el, innerCount );
                }
                el.setText( value );
                innerCount.increaseCount();
            }
            if ( elIt != null )
            {
                while ( elIt.hasNext() )
                {
                    elIt.next();
                    elIt.remove();
                }
            }
        }
        return element;
    } // -- Element findAndReplaceSimpleLists(Counter, Element, java.util.Collection, String, String)

    /**
     * Method findAndReplaceXpp3DOM
     *
     * @param counter
     * @param dom
     * @param name
     * @param parent
     */
    protected Element findAndReplaceXpp3DOM( Counter counter, Element parent, String name, Xpp3Dom dom )
    {
        boolean shouldExist = dom != null && ( dom.getChildCount() > 0 || dom.getValue() != null );
        Element element = updateElement( counter, parent, name, shouldExist );
        if ( shouldExist )
        {
            replaceXpp3DOM( element, dom, new Counter( counter.getDepth() + 1 ) );
        }
        return element;
    } // -- Element findAndReplaceXpp3DOM(Counter, Element, String, Xpp3Dom)

    /**
     * Method insertAtPreferredLocation
     *
     * @param parent
     * @param counter
     * @param child
     */
    protected void insertAtPreferredLocation( Element parent, Element child, Counter counter )
    {
        int contentIndex = 0;
        int elementCounter = 0;
        Iterator it = parent.getContent().iterator();
        Text lastText = null;
        int offset = 0;
        while ( it.hasNext() && elementCounter <= counter.getCurrentIndex() )
        {
            Object next = it.next();
            offset = offset + 1;
            if ( next instanceof Element )
            {
                elementCounter = elementCounter + 1;
                contentIndex = contentIndex + offset;
                offset = 0;
            }
            if ( next instanceof Text && it.hasNext() )
            {
                lastText = (Text) next;
            }
        }
        if ( lastText != null && lastText.getTextTrim().length() == 0 )
        {
            lastText = (Text) lastText.clone();
        }
        else
        {
            String starter = lineSeparator;
            for ( int i = 0; i < counter.getDepth(); i++ )
            {
                starter = starter + "    "; // TODO make settable?
            }
            lastText = factory.text( starter );
        }
        if ( parent.getContentSize() == 0 )
        {
            Text finalText = (Text) lastText.clone();
            finalText.setText( finalText.getText().substring( 0, finalText.getText().length() - "    ".length() ) );
            parent.addContent( contentIndex, finalText );
        }
        parent.addContent( contentIndex, child );
        parent.addContent( contentIndex, lastText );
    } // -- void insertAtPreferredLocation(Element, Element, Counter)

    /**
     * Method iterateContributor
     *
     * @param counter
     * @param childTag
     * @param parentTag
     * @param list
     * @param parent
     */
    protected void iterateContributor( Counter counter, Element parent, Collection list,
                                       String parentTag, String childTag )
    {
        boolean shouldExist = list != null && list.size() > 0;
        Element element = updateElement( counter, parent, parentTag, shouldExist );
        if ( shouldExist )
        {
            Iterator it = list.iterator();
            Iterator elIt = element.getChildren( childTag, element.getNamespace() ).iterator();
            if ( !elIt.hasNext() )
            {
                elIt = null;
            }
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            while ( it.hasNext() )
            {
                Contributor value = (Contributor) it.next();
                Element el;
                if ( elIt != null && elIt.hasNext() )
                {
                    el = (Element) elIt.next();
                    if ( !elIt.hasNext() )
                    {
                        elIt = null;
                    }
                }
                else
                {
                    el = factory.element( childTag, element.getNamespace() );
                    insertAtPreferredLocation( element, el, innerCount );
                }
                updateContributor( value, childTag, innerCount, el );
                innerCount.increaseCount();
            }
            if ( elIt != null )
            {
                while ( elIt.hasNext() )
                {
                    elIt.next();
                    elIt.remove();
                }
            }
        }
    } // -- void iterateContributor(Counter, Element, java.util.Collection, java.lang.String, java.lang.String)

    /**
     * Method iterateDependency
     *
     * @param counter
     * @param childTag
     * @param parentTag
     * @param list
     * @param parent
     */
    protected void iterateDependency( Counter counter, Element parent, Collection list,
                                      String parentTag, String childTag )
    {
        boolean shouldExist = list != null && list.size() > 0;
        Element element = updateElement( counter, parent, parentTag, shouldExist );
        if ( shouldExist )
        {
            Iterator it = list.iterator();
            Iterator elIt = element.getChildren( childTag, element.getNamespace() ).iterator();
            if ( !elIt.hasNext() )
            {
                elIt = null;
            }
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            while ( it.hasNext() )
            {
                Dependency value = (Dependency) it.next();
                Element el;
                if ( elIt != null && elIt.hasNext() )
                {
                    el = (Element) elIt.next();
                    if ( !elIt.hasNext() )
                    {
                        elIt = null;
                    }
                }
                else
                {
                    el = factory.element( childTag, element.getNamespace() );
                    insertAtPreferredLocation( element, el, innerCount );
                }
                updateDependency( value, childTag, innerCount, el );
                innerCount.increaseCount();
            }
            if ( elIt != null )
            {
                while ( elIt.hasNext() )
                {
                    elIt.next();
                    elIt.remove();
                }
            }
        }
    } // -- void iterateDependency(Counter, Element, java.util.Collection, java.lang.String, java.lang.String)

    /**
     * Method iterateDeveloper
     *
     * @param counter
     * @param childTag
     * @param parentTag
     * @param list
     * @param parent
     */
    protected void iterateDeveloper( Counter counter, Element parent, Collection list,
                                     String parentTag, String childTag )
    {
        boolean shouldExist = list != null && list.size() > 0;
        Element element = updateElement( counter, parent, parentTag, shouldExist );
        if ( shouldExist )
        {
            Iterator it = list.iterator();
            Iterator elIt = element.getChildren( childTag, element.getNamespace() ).iterator();
            if ( !elIt.hasNext() )
            {
                elIt = null;
            }
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            while ( it.hasNext() )
            {
                Developer value = (Developer) it.next();
                Element el;
                if ( elIt != null && elIt.hasNext() )
                {
                    el = (Element) elIt.next();
                    if ( !elIt.hasNext() )
                    {
                        elIt = null;
                    }
                }
                else
                {
                    el = factory.element( childTag, element.getNamespace() );
                    insertAtPreferredLocation( element, el, innerCount );
                }
                updateDeveloper( value, childTag, innerCount, el );
                innerCount.increaseCount();
            }
            if ( elIt != null )
            {
                while ( elIt.hasNext() )
                {
                    elIt.next();
                    elIt.remove();
                }
            }
        }
    } // -- void iterateDeveloper(Counter, Element, java.util.Collection, java.lang.String, java.lang.String)

    /**
     * Method iterateExclusion
     *
     * @param counter
     * @param childTag
     * @param parentTag
     * @param list
     * @param parent
     */
    protected void iterateExclusion( Counter counter, Element parent, Collection list,
                                     String parentTag, String childTag )
    {
        boolean shouldExist = list != null && list.size() > 0;
        Element element = updateElement( counter, parent, parentTag, shouldExist );
        if ( shouldExist )
        {
            Iterator it = list.iterator();
            Iterator elIt = element.getChildren( childTag, element.getNamespace() ).iterator();
            if ( !elIt.hasNext() )
            {
                elIt = null;
            }
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            while ( it.hasNext() )
            {
                Exclusion value = (Exclusion) it.next();
                Element el;
                if ( elIt != null && elIt.hasNext() )
                {
                    el = (Element) elIt.next();
                    if ( !elIt.hasNext() )
                    {
                        elIt = null;
                    }
                }
                else
                {
                    el = factory.element( childTag, element.getNamespace() );
                    insertAtPreferredLocation( element, el, innerCount );
                }
                updateExclusion( value, childTag, innerCount, el );
                innerCount.increaseCount();
            }
            if ( elIt != null )
            {
                while ( elIt.hasNext() )
                {
                    elIt.next();
                    elIt.remove();
                }
            }
        }
    } // -- void iterateExclusion(Counter, Element, java.util.Collection, java.lang.String, java.lang.String)

    /**
     * Method iterateExtension
     *
     * @param counter
     * @param childTag
     * @param parentTag
     * @param list
     * @param parent
     */
    protected void iterateExtension( Counter counter, Element parent, Collection list,
                                     String parentTag, String childTag )
    {
        boolean shouldExist = list != null && list.size() > 0;
        Element element = updateElement( counter, parent, parentTag, shouldExist );
        if ( shouldExist )
        {
            Iterator it = list.iterator();
            Iterator elIt = element.getChildren( childTag, element.getNamespace() ).iterator();
            if ( !elIt.hasNext() )
            {
                elIt = null;
            }
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            while ( it.hasNext() )
            {
                Extension value = (Extension) it.next();
                Element el;
                if ( elIt != null && elIt.hasNext() )
                {
                    el = (Element) elIt.next();
                    if ( !elIt.hasNext() )
                    {
                        elIt = null;
                    }
                }
                else
                {
                    el = factory.element( childTag, element.getNamespace() );
                    insertAtPreferredLocation( element, el, innerCount );
                }
                updateExtension( value, childTag, innerCount, el );
                innerCount.increaseCount();
            }
            if ( elIt != null )
            {
                while ( elIt.hasNext() )
                {
                    elIt.next();
                    elIt.remove();
                }
            }
        }
    } // -- void iterateExtension(Counter, Element, java.util.Collection, java.lang.String, java.lang.String)

    /**
     * Method iterateLicense
     *
     * @param counter
     * @param childTag
     * @param parentTag
     * @param list
     * @param parent
     */
    protected void iterateLicense( Counter counter, Element parent, Collection list,
                                   String parentTag, String childTag )
    {
        boolean shouldExist = list != null && list.size() > 0;
        Element element = updateElement( counter, parent, parentTag, shouldExist );
        if ( shouldExist )
        {
            Iterator it = list.iterator();
            Iterator elIt = element.getChildren( childTag, element.getNamespace() ).iterator();
            if ( !elIt.hasNext() )
            {
                elIt = null;
            }
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            while ( it.hasNext() )
            {
                License value = (License) it.next();
                Element el;
                if ( elIt != null && elIt.hasNext() )
                {
                    el = (Element) elIt.next();
                    if ( !elIt.hasNext() )
                    {
                        elIt = null;
                    }
                }
                else
                {
                    el = factory.element( childTag, element.getNamespace() );
                    insertAtPreferredLocation( element, el, innerCount );
                }
                updateLicense( value, childTag, innerCount, el );
                innerCount.increaseCount();
            }
            if ( elIt != null )
            {
                while ( elIt.hasNext() )
                {
                    elIt.next();
                    elIt.remove();
                }
            }
        }
    } // -- void iterateLicense(Counter, Element, java.util.Collection, java.lang.String, java.lang.String)

    /**
     * Method iterateMailingList
     *
     * @param counter
     * @param childTag
     * @param parentTag
     * @param list
     * @param parent
     */
    protected void iterateMailingList( Counter counter, Element parent, Collection list,
                                       String parentTag, String childTag )
    {
        boolean shouldExist = list != null && list.size() > 0;
        Element element = updateElement( counter, parent, parentTag, shouldExist );
        if ( shouldExist )
        {
            Iterator it = list.iterator();
            Iterator elIt = element.getChildren( childTag, element.getNamespace() ).iterator();
            if ( !elIt.hasNext() )
            {
                elIt = null;
            }
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            while ( it.hasNext() )
            {
                MailingList value = (MailingList) it.next();
                Element el;
                if ( elIt != null && elIt.hasNext() )
                {
                    el = (Element) elIt.next();
                    if ( !elIt.hasNext() )
                    {
                        elIt = null;
                    }
                }
                else
                {
                    el = factory.element( childTag, element.getNamespace() );
                    insertAtPreferredLocation( element, el, innerCount );
                }
                updateMailingList( value, childTag, innerCount, el );
                innerCount.increaseCount();
            }
            if ( elIt != null )
            {
                while ( elIt.hasNext() )
                {
                    elIt.next();
                    elIt.remove();
                }
            }
        }
    } // -- void iterateMailingList(Counter, Element, java.util.Collection, java.lang.String, java.lang.String)

    /**
     * Method iterateNotifier
     *
     * @param counter
     * @param childTag
     * @param parentTag
     * @param list
     * @param parent
     */
    protected void iterateNotifier( Counter counter, Element parent, Collection list,
                                    String parentTag, String childTag )
    {
        boolean shouldExist = list != null && list.size() > 0;
        Element element = updateElement( counter, parent, parentTag, shouldExist );
        if ( shouldExist )
        {
            Iterator it = list.iterator();
            Iterator elIt = element.getChildren( childTag, element.getNamespace() ).iterator();
            if ( !elIt.hasNext() )
            {
                elIt = null;
            }
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            while ( it.hasNext() )
            {
                Notifier value = (Notifier) it.next();
                Element el;
                if ( elIt != null && elIt.hasNext() )
                {
                    el = (Element) elIt.next();
                    if ( !elIt.hasNext() )
                    {
                        elIt = null;
                    }
                }
                else
                {
                    el = factory.element( childTag, element.getNamespace() );
                    insertAtPreferredLocation( element, el, innerCount );
                }
                updateNotifier( value, childTag, innerCount, el );
                innerCount.increaseCount();
            }
            if ( elIt != null )
            {
                while ( elIt.hasNext() )
                {
                    elIt.next();
                    elIt.remove();
                }
            }
        }
    } // -- void iterateNotifier(Counter, Element, java.util.Collection, java.lang.String, java.lang.String)

    /**
     * Method iteratePlugin
     *
     * @param counter
     * @param childTag
     * @param parentTag
     * @param list
     * @param parent
     */
    protected void iteratePlugin( Counter counter, Element parent, Collection list,
                                  String parentTag, String childTag )
    {
        boolean shouldExist = list != null && list.size() > 0;
        Element element = updateElement( counter, parent, parentTag, shouldExist );
        if ( shouldExist )
        {
            Iterator it = list.iterator();
            Iterator elIt = element.getChildren( childTag, element.getNamespace() ).iterator();
            if ( !elIt.hasNext() )
            {
                elIt = null;
            }
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            while ( it.hasNext() )
            {
                Plugin value = (Plugin) it.next();
                Element el;
                if ( elIt != null && elIt.hasNext() )
                {
                    el = (Element) elIt.next();
                    if ( !elIt.hasNext() )
                    {
                        elIt = null;
                    }
                }
                else
                {
                    el = factory.element( childTag, element.getNamespace() );
                    insertAtPreferredLocation( element, el, innerCount );
                }
                updatePlugin( value, childTag, innerCount, el );
                innerCount.increaseCount();
            }
            if ( elIt != null )
            {
                while ( elIt.hasNext() )
                {
                    elIt.next();
                    elIt.remove();
                }
            }
        }
    } // -- void iteratePlugin(Counter, Element, java.util.Collection, java.lang.String, java.lang.String)

    /**
     * Method iteratePluginExecution
     *
     * @param counter
     * @param childTag
     * @param parentTag
     * @param list
     * @param parent
     */
    protected void iteratePluginExecution( Counter counter, Element parent, Collection list,
                                           String parentTag, String childTag )
    {
        boolean shouldExist = list != null && list.size() > 0;
        Element element = updateElement( counter, parent, parentTag, shouldExist );
        if ( shouldExist )
        {
            Iterator it = list.iterator();
            Iterator elIt = element.getChildren( childTag, element.getNamespace() ).iterator();
            if ( !elIt.hasNext() )
            {
                elIt = null;
            }
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            while ( it.hasNext() )
            {
                PluginExecution value = (PluginExecution) it.next();
                Element el;
                if ( elIt != null && elIt.hasNext() )
                {
                    el = (Element) elIt.next();
                    if ( !elIt.hasNext() )
                    {
                        elIt = null;
                    }
                }
                else
                {
                    el = factory.element( childTag, element.getNamespace() );
                    insertAtPreferredLocation( element, el, innerCount );
                }
                updatePluginExecution( value, childTag, innerCount, el );
                innerCount.increaseCount();
            }
            if ( elIt != null )
            {
                while ( elIt.hasNext() )
                {
                    elIt.next();
                    elIt.remove();
                }
            }
        }
    } // -- void iteratePluginExecution(Counter, Element, java.util.Collection, java.lang.String, java.lang.String)

    /**
     * Method iterateProfile
     *
     * @param counter
     * @param childTag
     * @param parentTag
     * @param list
     * @param parent
     */
    protected void iterateProfile( Counter counter, Element parent, Collection list,
                                   String parentTag, String childTag )
    {
        boolean shouldExist = list != null && list.size() > 0;
        Element element = updateElement( counter, parent, parentTag, shouldExist );
        if ( shouldExist )
        {
            Iterator it = list.iterator();
            Iterator elIt = element.getChildren( childTag, element.getNamespace() ).iterator();
            if ( !elIt.hasNext() )
            {
                elIt = null;
            }
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            while ( it.hasNext() )
            {
                Profile value = (Profile) it.next();
                Element el;
                if ( elIt != null && elIt.hasNext() )
                {
                    el = (Element) elIt.next();
                    if ( !elIt.hasNext() )
                    {
                        elIt = null;
                    }
                }
                else
                {
                    el = factory.element( childTag, element.getNamespace() );
                    insertAtPreferredLocation( element, el, innerCount );
                }
                updateProfile( value, childTag, innerCount, el );
                innerCount.increaseCount();
            }
            if ( elIt != null )
            {
                while ( elIt.hasNext() )
                {
                    elIt.next();
                    elIt.remove();
                }
            }
        }
    } // -- void iterateProfile(Counter, Element, java.util.Collection, java.lang.String, java.lang.String)

    /**
     * Method iterateReportPlugin
     *
     * @param counter
     * @param childTag
     * @param parentTag
     * @param list
     * @param parent
     */
    protected void iterateReportPlugin( Counter counter, Element parent, Collection list,
                                        String parentTag, String childTag )
    {
        boolean shouldExist = list != null && list.size() > 0;
        Element element = updateElement( counter, parent, parentTag, shouldExist );
        if ( shouldExist )
        {
            Iterator it = list.iterator();
            Iterator elIt = element.getChildren( childTag, element.getNamespace() ).iterator();
            if ( !elIt.hasNext() )
            {
                elIt = null;
            }
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            while ( it.hasNext() )
            {
                ReportPlugin value = (ReportPlugin) it.next();
                Element el;
                if ( elIt != null && elIt.hasNext() )
                {
                    el = (Element) elIt.next();
                    if ( !elIt.hasNext() )
                    {
                        elIt = null;
                    }
                }
                else
                {
                    el = factory.element( childTag, element.getNamespace() );
                    insertAtPreferredLocation( element, el, innerCount );
                }
                updateReportPlugin( value, childTag, innerCount, el );
                innerCount.increaseCount();
            }
            if ( elIt != null )
            {
                while ( elIt.hasNext() )
                {
                    elIt.next();
                    elIt.remove();
                }
            }
        }
    } // -- void iterateReportPlugin(Counter, Element, java.util.Collection, java.lang.String, java.lang.String)

    /**
     * Method iterateReportSet
     *
     * @param counter
     * @param childTag
     * @param parentTag
     * @param list
     * @param parent
     */
    protected void iterateReportSet( Counter counter, Element parent, Collection list,
                                     String parentTag, String childTag )
    {
        boolean shouldExist = list != null && list.size() > 0;
        Element element = updateElement( counter, parent, parentTag, shouldExist );
        if ( shouldExist )
        {
            Iterator it = list.iterator();
            Iterator elIt = element.getChildren( childTag, element.getNamespace() ).iterator();
            if ( !elIt.hasNext() )
            {
                elIt = null;
            }
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            while ( it.hasNext() )
            {
                ReportSet value = (ReportSet) it.next();
                Element el;
                if ( elIt != null && elIt.hasNext() )
                {
                    el = (Element) elIt.next();
                    if ( !elIt.hasNext() )
                    {
                        elIt = null;
                    }
                }
                else
                {
                    el = factory.element( childTag, element.getNamespace() );
                    insertAtPreferredLocation( element, el, innerCount );
                }
                updateReportSet( value, childTag, innerCount, el );
                innerCount.increaseCount();
            }
            if ( elIt != null )
            {
                while ( elIt.hasNext() )
                {
                    elIt.next();
                    elIt.remove();
                }
            }
        }
    } // -- void iterateReportSet(Counter, Element, java.util.Collection, java.lang.String, java.lang.String)

    /**
     * Method iterateRepository
     *
     * @param counter
     * @param childTag
     * @param parentTag
     * @param list
     * @param parent
     */
    protected void iterateRepository( Counter counter, Element parent, Collection list,
                                      String parentTag, String childTag )
    {
        boolean shouldExist = list != null && list.size() > 0;
        Element element = updateElement( counter, parent, parentTag, shouldExist );
        if ( shouldExist )
        {
            Iterator it = list.iterator();
            Iterator elIt = element.getChildren( childTag, element.getNamespace() ).iterator();
            if ( !elIt.hasNext() )
            {
                elIt = null;
            }
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            while ( it.hasNext() )
            {
                Repository value = (Repository) it.next();
                Element el;
                if ( elIt != null && elIt.hasNext() )
                {
                    el = (Element) elIt.next();
                    if ( !elIt.hasNext() )
                    {
                        elIt = null;
                    }
                }
                else
                {
                    el = factory.element( childTag, element.getNamespace() );
                    insertAtPreferredLocation( element, el, innerCount );
                }
                updateRepository( value, childTag, innerCount, el );
                innerCount.increaseCount();
            }
            if ( elIt != null )
            {
                while ( elIt.hasNext() )
                {
                    elIt.next();
                    elIt.remove();
                }
            }
        }
    } // -- void iterateRepository(Counter, Element, java.util.Collection, java.lang.String, java.lang.String)

    /**
     * Method iterateResource
     *
     * @param counter
     * @param childTag
     * @param parentTag
     * @param list
     * @param parent
     */
    protected void iterateResource( Counter counter, Element parent, Collection list,
                                    String parentTag, String childTag )
    {
        boolean shouldExist = list != null && list.size() > 0;
        Element element = updateElement( counter, parent, parentTag, shouldExist );
        if ( shouldExist )
        {
            Iterator it = list.iterator();
            Iterator elIt = element.getChildren( childTag, element.getNamespace() ).iterator();
            if ( !elIt.hasNext() )
            {
                elIt = null;
            }
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            while ( it.hasNext() )
            {
                Resource value = (Resource) it.next();
                Element el;
                if ( elIt != null && elIt.hasNext() )
                {
                    el = (Element) elIt.next();
                    if ( !elIt.hasNext() )
                    {
                        elIt = null;
                    }
                }
                else
                {
                    el = factory.element( childTag, element.getNamespace() );
                    insertAtPreferredLocation( element, el, innerCount );
                }
                updateResource( value, childTag, innerCount, el );
                innerCount.increaseCount();
            }
            if ( elIt != null )
            {
                while ( elIt.hasNext() )
                {
                    elIt.next();
                    elIt.remove();
                }
            }
        }
    } // -- void iterateResource(Counter, Element, java.util.Collection, java.lang.String, java.lang.String)

    /**
     * Method replaceXpp3DOM
     *
     * @param parent
     * @param counter
     * @param parentDom
     */
    protected void replaceXpp3DOM( Element parent, Xpp3Dom parentDom, Counter counter )
    {
        if ( parentDom.getChildCount() > 0 )
        {
            Xpp3Dom[] childs = parentDom.getChildren();
            Collection domChilds = new ArrayList();
            for ( int i = 0; i < childs.length; i++ )
            {
                domChilds.add( childs[i] );
            }
            // int domIndex = 0;
            ListIterator it = parent.getChildren().listIterator();
            while ( it.hasNext() )
            {
                Element elem = (Element) it.next();
                Iterator it2 = domChilds.iterator();
                Xpp3Dom corrDom = null;
                while ( it2.hasNext() )
                {
                    Xpp3Dom dm = (Xpp3Dom) it2.next();
                    if ( dm.getName().equals( elem.getName() ) )
                    {
                        corrDom = dm;
                        break;
                    }
                }
                if ( corrDom != null )
                {
                    domChilds.remove( corrDom );
                    replaceXpp3DOM( elem, corrDom, new Counter( counter.getDepth() + 1 ) );
                    counter.increaseCount();
                }
                else
                {
                    parent.removeContent( elem );
                }
            }
            Iterator it2 = domChilds.iterator();
            while ( it2.hasNext() )
            {
                Xpp3Dom dm = (Xpp3Dom) it2.next();
                Element elem = factory.element( dm.getName(), parent.getNamespace() );
                insertAtPreferredLocation( parent, elem, counter );
                counter.increaseCount();
                replaceXpp3DOM( elem, dm, new Counter( counter.getDepth() + 1 ) );
            }
        }
        else if ( parentDom.getValue() != null )
        {
            parent.setText( parentDom.getValue() );
        }
    } // -- void replaceXpp3DOM(Element, Xpp3Dom, Counter)

    /**
     * Method updateActivation
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    /*
     * protected void updateActivation(Activation value, String xmlTag, Counter counter, Element element) { boolean
     * shouldExist = value != null; Element root = updateElement(counter, element, xmlTag, shouldExist); if
     * (shouldExist) { Counter innerCount = new Counter(counter.getDepth() + 1); findAndReplaceSimpleElement(innerCount,
     * root, "activeByDefault", !value.isActiveByDefault() ? null : String.valueOf( value.isActiveByDefault() ),
     * "false"); findAndReplaceSimpleElement(innerCount, root, "jdk", value.getJdk(), null); updateActivationOS(
     * value.getOs(), "os", innerCount, root); updateActivationProperty( value.getProperty(), "property", innerCount,
     * root); updateActivationFile( value.getFile(), "file", innerCount, root); updateActivationCustom(
     * value.getCustom(), "custom", innerCount, root); } } //-- void updateActivation(Activation, String, Counter,
     * Element)
     */

    /**
     * Method updateActivationCustom
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    /*
     * protected void updateActivationCustom(ActivationCustom value, String xmlTag, Counter counter, Element element) {
     * boolean shouldExist = value != null; Element root = updateElement(counter, element, xmlTag, shouldExist); if
     * (shouldExist) { Counter innerCount = new Counter(counter.getDepth() + 1); findAndReplaceXpp3DOM(innerCount, root,
     * "configuration", (Xpp3Dom)value.getConfiguration()); findAndReplaceSimpleElement(innerCount, root, "type",
     * value.getType(), null); } } //-- void updateActivationCustom(ActivationCustom, String, Counter, Element)
     */

    /**
     * Method updateActivationFile
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateActivationFile( ActivationFile value, String xmlTag, Counter counter, Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            findAndReplaceSimpleElement( innerCount, root, "missing", value.getMissing(), null );
            findAndReplaceSimpleElement( innerCount, root, "exists", value.getExists(), null );
        }
    } // -- void updateActivationFile(ActivationFile, String, Counter, Element)

    /**
     * Method updateActivationOS
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateActivationOS( ActivationOS value, String xmlTag, Counter counter, Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            findAndReplaceSimpleElement( innerCount, root, "name", value.getName(), null );
            findAndReplaceSimpleElement( innerCount, root, "family", value.getFamily(), null );
            findAndReplaceSimpleElement( innerCount, root, "arch", value.getArch(), null );
            findAndReplaceSimpleElement( innerCount, root, "version", value.getVersion(), null );
        }
    } // -- void updateActivationOS(ActivationOS, String, Counter, Element)

    /**
     * Method updateActivationProperty
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateActivationProperty( ActivationProperty value, String xmlTag, Counter counter, Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            findAndReplaceSimpleElement( innerCount, root, "name", value.getName(), null );
            findAndReplaceSimpleElement( innerCount, root, "value", value.getValue(), null );
        }
    } // -- void updateActivationProperty(ActivationProperty, String, Counter, Element)

    /**
     * Method updateBuild
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateBuild( Build value, String xmlTag, Counter counter, Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            findAndReplaceSimpleElement( innerCount, root, "sourceDirectory", value.getSourceDirectory(), null );
            findAndReplaceSimpleElement( innerCount, root, "scriptSourceDirectory", value.getScriptSourceDirectory(),
                                         null );
            findAndReplaceSimpleElement( innerCount, root, "testSourceDirectory", value.getTestSourceDirectory(),
                                         null );
            findAndReplaceSimpleElement( innerCount, root, "outputDirectory", value.getOutputDirectory(), null );
            findAndReplaceSimpleElement( innerCount, root, "testOutputDirectory", value.getTestOutputDirectory(),
                                         null );
            iterateExtension( innerCount, root, value.getExtensions(), "extensions", "extension" );
            findAndReplaceSimpleElement( innerCount, root, "defaultGoal", value.getDefaultGoal(), null );
            iterateResource( innerCount, root, value.getResources(), "resources", "resource" );
            iterateResource( innerCount, root, value.getTestResources(), "testResources", "testResource" );
            findAndReplaceSimpleElement( innerCount, root, "directory", value.getDirectory(), null );
            findAndReplaceSimpleElement( innerCount, root, "finalName", value.getFinalName(), null );
            findAndReplaceSimpleLists( innerCount, root, value.getFilters(), "filters", "filter" );
            updatePluginManagement( value.getPluginManagement(), "pluginManagement", innerCount, root );
            iteratePlugin( innerCount, root, value.getPlugins(), "plugins", "plugin" );
        }
    } // -- void updateBuild(Build, String, Counter, Element)

    /**
     * Method updateBuildBase
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateBuildBase( BuildBase value, String xmlTag, Counter counter, Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            findAndReplaceSimpleElement( innerCount, root, "defaultGoal", value.getDefaultGoal(), null );
            iterateResource( innerCount, root, value.getResources(), "resources", "resource" );
            iterateResource( innerCount, root, value.getTestResources(), "testResources", "testResource" );
            findAndReplaceSimpleElement( innerCount, root, "directory", value.getDirectory(), null );
            findAndReplaceSimpleElement( innerCount, root, "finalName", value.getFinalName(), null );
            findAndReplaceSimpleLists( innerCount, root, value.getFilters(), "filters", "filter" );
            updatePluginManagement( value.getPluginManagement(), "pluginManagement", innerCount, root );
            iteratePlugin( innerCount, root, value.getPlugins(), "plugins", "plugin" );
        }
    } // -- void updateBuildBase(BuildBase, String, Counter, Element)

    /**
     * Method updateCiManagement
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateCiManagement( CiManagement value, String xmlTag, Counter counter, Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            findAndReplaceSimpleElement( innerCount, root, "system", value.getSystem(), null );
            findAndReplaceSimpleElement( innerCount, root, "url", value.getUrl(), null );
            iterateNotifier( innerCount, root, value.getNotifiers(), "notifiers", "notifier" );
        }
    } // -- void updateCiManagement(CiManagement, String, Counter, Element)

    /**
     * Method updateConfigurationContainer
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateConfigurationContainer( ConfigurationContainer value, String xmlTag, Counter counter,
                                                 Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            findAndReplaceSimpleElement( innerCount, root, "inherited", value.getInherited(), null );
            findAndReplaceXpp3DOM( innerCount, root, "configuration", (Xpp3Dom) value.getConfiguration() );
        }
    } // -- void updateConfigurationContainer(ConfigurationContainer, String, Counter, Element)

    /**
     * Method updateContributor
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateContributor( Contributor value, String xmlTag, Counter counter, Element element )
    {
        Element root = element;
        Counter innerCount = new Counter( counter.getDepth() + 1 );
        findAndReplaceSimpleElement( innerCount, root, "name", value.getName(), null );
        findAndReplaceSimpleElement( innerCount, root, "email", value.getEmail(), null );
        findAndReplaceSimpleElement( innerCount, root, "url", value.getUrl(), null );
        findAndReplaceSimpleElement( innerCount, root, "organization", value.getOrganization(), null );
        findAndReplaceSimpleElement( innerCount, root, "organizationUrl", value.getOrganizationUrl(), null );
        findAndReplaceSimpleLists( innerCount, root, value.getRoles(), "roles", "role" );
        findAndReplaceSimpleElement( innerCount, root, "timezone", value.getTimezone(), null );
        findAndReplaceProperties( innerCount, root, "properties", value.getProperties() );
    } // -- void updateContributor(Contributor, String, Counter, Element)

    /**
     * Method updateDependency
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateDependency( Dependency value, String xmlTag, Counter counter, Element element )
    {
        Element root = element;
        Counter innerCount = new Counter( counter.getDepth() + 1 );
        findAndReplaceSimpleElement( innerCount, root, "groupId", value.getGroupId(), null );
        findAndReplaceSimpleElement( innerCount, root, "artifactId", value.getArtifactId(), null );
        findAndReplaceSimpleElement( innerCount, root, "version", value.getVersion(), null );
        findAndReplaceSimpleElement( innerCount, root, "type", value.getType(), "jar" );
        findAndReplaceSimpleElement( innerCount, root, "classifier", value.getClassifier(), null );
        findAndReplaceSimpleElement( innerCount, root, "scope", value.getScope(), null );
        findAndReplaceSimpleElement( innerCount, root, "systemPath", value.getSystemPath(), null );
        iterateExclusion( innerCount, root, value.getExclusions(), "exclusions", "exclusion" );
        findAndReplaceSimpleElement( innerCount, root, "optional",
                                     !value.isOptional() ? null : String.valueOf( value.isOptional() ), "false" );
    } // -- void updateDependency(Dependency, String, Counter, Element)

    /**
     * Method updateDependencyManagement
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateDependencyManagement( DependencyManagement value, String xmlTag, Counter counter,
                                               Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            iterateDependency( innerCount, root, value.getDependencies(), "dependencies", "dependency" );
        }
    } // -- void updateDependencyManagement(DependencyManagement, String, Counter, Element)

    /**
     * Method updateDeploymentRepository
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateDeploymentRepository( DeploymentRepository value, String xmlTag, Counter counter,
                                               Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            findAndReplaceSimpleElement( innerCount, root, "uniqueVersion",
                                         value.isUniqueVersion() ? null : String.valueOf( value.isUniqueVersion() ),
                                         "true" );
            findAndReplaceSimpleElement( innerCount, root, "id", value.getId(), null );
            findAndReplaceSimpleElement( innerCount, root, "name", value.getName(), null );
            findAndReplaceSimpleElement( innerCount, root, "url", value.getUrl(), null );
            findAndReplaceSimpleElement( innerCount, root, "layout", value.getLayout(), "default" );
        }
    } // -- void updateDeploymentRepository(DeploymentRepository, String, Counter, Element)

    /**
     * Method updateDeveloper
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateDeveloper( Developer value, String xmlTag, Counter counter, Element element )
    {
        Element root = element;
        Counter innerCount = new Counter( counter.getDepth() + 1 );
        findAndReplaceSimpleElement( innerCount, root, "id", value.getId(), null );
        findAndReplaceSimpleElement( innerCount, root, "name", value.getName(), null );
        findAndReplaceSimpleElement( innerCount, root, "email", value.getEmail(), null );
        findAndReplaceSimpleElement( innerCount, root, "url", value.getUrl(), null );
        findAndReplaceSimpleElement( innerCount, root, "organization", value.getOrganization(), null );
        findAndReplaceSimpleElement( innerCount, root, "organizationUrl", value.getOrganizationUrl(), null );
        findAndReplaceSimpleLists( innerCount, root, value.getRoles(), "roles", "role" );
        findAndReplaceSimpleElement( innerCount, root, "timezone", value.getTimezone(), null );
        findAndReplaceProperties( innerCount, root, "properties", value.getProperties() );
    } // -- void updateDeveloper(Developer, String, Counter, Element)

    /**
     * Method updateDistributionManagement
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateDistributionManagement( DistributionManagement value, String xmlTag, Counter counter,
                                                 Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            updateDeploymentRepository( value.getRepository(), "repository", innerCount, root );
            updateDeploymentRepository( value.getSnapshotRepository(), "snapshotRepository", innerCount, root );
            updateSite( value.getSite(), "site", innerCount, root );
            findAndReplaceSimpleElement( innerCount, root, "downloadUrl", value.getDownloadUrl(), null );
            updateRelocation( value.getRelocation(), "relocation", innerCount, root );
            findAndReplaceSimpleElement( innerCount, root, "status", value.getStatus(), null );
        }
    } // -- void updateDistributionManagement(DistributionManagement, String, Counter, Element)

    /**
     * Method updateElement
     *
     * @param counter
     * @param shouldExist
     * @param name
     * @param parent
     */
    protected Element updateElement( Counter counter, Element parent, String name, boolean shouldExist )
    {
        Element element = parent.getChild( name, parent.getNamespace() );
        if ( element != null && shouldExist )
        {
            counter.increaseCount();
        }
        if ( element == null && shouldExist )
        {
            element = factory.element( name, parent.getNamespace() );
            insertAtPreferredLocation( parent, element, counter );
            counter.increaseCount();
        }
        if ( !shouldExist && element != null )
        {
            int index = parent.indexOf( element );
            if ( index > 0 )
            {
                Content previous = parent.getContent( index - 1 );
                if ( previous instanceof Text )
                {
                    Text txt = (Text) previous;
                    if ( txt.getTextTrim().length() == 0 )
                    {
                        parent.removeContent( txt );
                    }
                }
            }
            parent.removeContent( element );
        }
        return element;
    } // -- Element updateElement(Counter, Element, String, boolean)

    /**
     * Method updateExclusion
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateExclusion( Exclusion value, String xmlTag, Counter counter, Element element )
    {
        Element root = element;
        Counter innerCount = new Counter( counter.getDepth() + 1 );
        findAndReplaceSimpleElement( innerCount, root, "artifactId", value.getArtifactId(), null );
        findAndReplaceSimpleElement( innerCount, root, "groupId", value.getGroupId(), null );
    } // -- void updateExclusion(Exclusion, String, Counter, Element)

    /**
     * Method updateExtension
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateExtension( Extension value, String xmlTag, Counter counter, Element element )
    {
        Element root = element;
        Counter innerCount = new Counter( counter.getDepth() + 1 );
        findAndReplaceSimpleElement( innerCount, root, "groupId", value.getGroupId(), null );
        findAndReplaceSimpleElement( innerCount, root, "artifactId", value.getArtifactId(), null );
        findAndReplaceSimpleElement( innerCount, root, "version", value.getVersion(), null );
    } // -- void updateExtension(Extension, String, Counter, Element)

    /**
     * Method updateFileSet
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateFileSet( FileSet value, String xmlTag, Counter counter, Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            findAndReplaceSimpleElement( innerCount, root, "directory", value.getDirectory(), null );
            findAndReplaceSimpleLists( innerCount, root, value.getIncludes(), "includes", "include" );
            findAndReplaceSimpleLists( innerCount, root, value.getExcludes(), "excludes", "exclude" );
        }
    } // -- void updateFileSet(FileSet, String, Counter, Element)

    /**
     * Method updateIssueManagement
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateIssueManagement( IssueManagement value, String xmlTag, Counter counter, Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            findAndReplaceSimpleElement( innerCount, root, "system", value.getSystem(), null );
            findAndReplaceSimpleElement( innerCount, root, "url", value.getUrl(), null );
        }
    } // -- void updateIssueManagement(IssueManagement, String, Counter, Element)

    /**
     * Method updateLicense
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateLicense( License value, String xmlTag, Counter counter, Element element )
    {
        Element root = element;
        Counter innerCount = new Counter( counter.getDepth() + 1 );
        findAndReplaceSimpleElement( innerCount, root, "name", value.getName(), null );
        findAndReplaceSimpleElement( innerCount, root, "url", value.getUrl(), null );
        findAndReplaceSimpleElement( innerCount, root, "distribution", value.getDistribution(), null );
        findAndReplaceSimpleElement( innerCount, root, "comments", value.getComments(), null );
    } // -- void updateLicense(License, String, Counter, Element)

    /**
     * Method updateMailingList
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateMailingList( MailingList value, String xmlTag, Counter counter, Element element )
    {
        Element root = element;
        Counter innerCount = new Counter( counter.getDepth() + 1 );
        findAndReplaceSimpleElement( innerCount, root, "name", value.getName(), null );
        findAndReplaceSimpleElement( innerCount, root, "subscribe", value.getSubscribe(), null );
        findAndReplaceSimpleElement( innerCount, root, "unsubscribe", value.getUnsubscribe(), null );
        findAndReplaceSimpleElement( innerCount, root, "post", value.getPost(), null );
        findAndReplaceSimpleElement( innerCount, root, "archive", value.getArchive(), null );
        findAndReplaceSimpleLists( innerCount, root, value.getOtherArchives(), "otherArchives", "otherArchive" );
    } // -- void updateMailingList(MailingList, String, Counter, Element)

    /**
     * Method updateModel
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateModel( Model value, String xmlTag, Counter counter, Element element )
    {
        Element root = element;
        Counter innerCount = new Counter( counter.getDepth() + 1 );
        updateParent( value.getParent(), "parent", innerCount, root );
        findAndReplaceSimpleElement( innerCount, root, "modelVersion", value.getModelVersion(), null );
        findAndReplaceSimpleElement( innerCount, root, "groupId", value.getGroupId(), null );
        findAndReplaceSimpleElement( innerCount, root, "artifactId", value.getArtifactId(), null );
        findAndReplaceSimpleElement( innerCount, root, "packaging", value.getPackaging(), "jar" );
        findAndReplaceSimpleElement( innerCount, root, "name", value.getName(), null );
        findAndReplaceSimpleElement( innerCount, root, "version", value.getVersion(), null );
        findAndReplaceSimpleElement( innerCount, root, "description", value.getDescription(), null );
        findAndReplaceSimpleElement( innerCount, root, "url", value.getUrl(), null );
        updatePrerequisites( value.getPrerequisites(), "prerequisites", innerCount, root );
        updateIssueManagement( value.getIssueManagement(), "issueManagement", innerCount, root );
        updateCiManagement( value.getCiManagement(), "ciManagement", innerCount, root );
        findAndReplaceSimpleElement( innerCount, root, "inceptionYear", value.getInceptionYear(), null );
        iterateMailingList( innerCount, root, value.getMailingLists(), "mailingLists", "mailingList" );
        iterateDeveloper( innerCount, root, value.getDevelopers(), "developers", "developer" );
        iterateContributor( innerCount, root, value.getContributors(), "contributors", "contributor" );
        iterateLicense( innerCount, root, value.getLicenses(), "licenses", "license" );
        updateScm( value.getScm(), "scm", innerCount, root );
        updateOrganization( value.getOrganization(), "organization", innerCount, root );
        updateBuild( value.getBuild(), "build", innerCount, root );
        iterateProfile( innerCount, root, value.getProfiles(), "profiles", "profile" );
        findAndReplaceSimpleLists( innerCount, root, value.getModules(), "modules", "module" );
        iterateRepository( innerCount, root, value.getRepositories(), "repositories", "repository" );
        iterateRepository( innerCount, root, value.getPluginRepositories(), "pluginRepositories", "pluginRepository" );
        iterateDependency( innerCount, root, value.getDependencies(), "dependencies", "dependency" );
        findAndReplaceXpp3DOM( innerCount, root, "reports", (Xpp3Dom) value.getReports() );
        updateReporting( value.getReporting(), "reporting", innerCount, root );
        updateDependencyManagement( value.getDependencyManagement(), "dependencyManagement", innerCount, root );
        updateDistributionManagement( value.getDistributionManagement(), "distributionManagement", innerCount, root );
        findAndReplaceProperties( innerCount, root, "properties", value.getProperties() );
    } // -- void updateModel(Model, String, Counter, Element)

    /**
     * Method updateModelBase
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateModelBase( ModelBase value, String xmlTag, Counter counter, Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            findAndReplaceSimpleLists( innerCount, root, value.getModules(), "modules", "module" );
            iterateRepository( innerCount, root, value.getRepositories(), "repositories", "repository" );
            iterateRepository( innerCount, root, value.getPluginRepositories(), "pluginRepositories",
                               "pluginRepository" );
            iterateDependency( innerCount, root, value.getDependencies(), "dependencies", "dependency" );
            findAndReplaceXpp3DOM( innerCount, root, "reports", (Xpp3Dom) value.getReports() );
            updateReporting( value.getReporting(), "reporting", innerCount, root );
            updateDependencyManagement( value.getDependencyManagement(), "dependencyManagement", innerCount, root );
            updateDistributionManagement( value.getDistributionManagement(), "distributionManagement", innerCount,
                                          root );
            findAndReplaceProperties( innerCount, root, "properties", value.getProperties() );
        }
    } // -- void updateModelBase(ModelBase, String, Counter, Element)

    /**
     * Method updateNotifier
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateNotifier( Notifier value, String xmlTag, Counter counter, Element element )
    {
        Element root = element;
        Counter innerCount = new Counter( counter.getDepth() + 1 );
        findAndReplaceSimpleElement( innerCount, root, "type", value.getType(), "mail" );
        findAndReplaceSimpleElement( innerCount, root, "sendOnError",
                                     value.isSendOnError() ? null : String.valueOf( value.isSendOnError() ), "true" );
        findAndReplaceSimpleElement( innerCount, root, "sendOnFailure",
                                     value.isSendOnFailure() ? null : String.valueOf( value.isSendOnFailure() ),
                                     "true" );
        findAndReplaceSimpleElement( innerCount, root, "sendOnSuccess",
                                     value.isSendOnSuccess() ? null : String.valueOf( value.isSendOnSuccess() ),
                                     "true" );
        findAndReplaceSimpleElement( innerCount, root, "sendOnWarning",
                                     value.isSendOnWarning() ? null : String.valueOf( value.isSendOnWarning() ),
                                     "true" );
        findAndReplaceSimpleElement( innerCount, root, "address", value.getAddress(), null );
        findAndReplaceProperties( innerCount, root, "configuration", value.getConfiguration() );
    } // -- void updateNotifier(Notifier, String, Counter, Element)

    /**
     * Method updateOrganization
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateOrganization( Organization value, String xmlTag, Counter counter, Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            findAndReplaceSimpleElement( innerCount, root, "name", value.getName(), null );
            findAndReplaceSimpleElement( innerCount, root, "url", value.getUrl(), null );
        }
    } // -- void updateOrganization(Organization, String, Counter, Element)

    /**
     * Method updateParent
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateParent( Parent value, String xmlTag, Counter counter, Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            findAndReplaceSimpleElement( innerCount, root, "artifactId", value.getArtifactId(), null );
            findAndReplaceSimpleElement( innerCount, root, "groupId", value.getGroupId(), null );
            findAndReplaceSimpleElement( innerCount, root, "version", value.getVersion(), null );
            findAndReplaceSimpleElement( innerCount, root, "relativePath", value.getRelativePath(), "../pom.xml" );
        }
    } // -- void updateParent(Parent, String, Counter, Element)

    /**
     * Method updatePatternSet
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updatePatternSet( PatternSet value, String xmlTag, Counter counter, Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            findAndReplaceSimpleLists( innerCount, root, value.getIncludes(), "includes", "include" );
            findAndReplaceSimpleLists( innerCount, root, value.getExcludes(), "excludes", "exclude" );
        }
    } // -- void updatePatternSet(PatternSet, String, Counter, Element)

    /**
     * Method updatePlugin
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updatePlugin( Plugin value, String xmlTag, Counter counter, Element element )
    {
        Element root = element;
        Counter innerCount = new Counter( counter.getDepth() + 1 );
        findAndReplaceSimpleElement( innerCount, root, "groupId", value.getGroupId(), "org.apache.maven.plugins" );
        findAndReplaceSimpleElement( innerCount, root, "artifactId", value.getArtifactId(), null );
        findAndReplaceSimpleElement( innerCount, root, "version", value.getVersion(), null );
        findAndReplaceSimpleElement( innerCount, root, "extensions",
                                     !value.isExtensions() ? null : String.valueOf( value.isExtensions() ), "false" );
        iteratePluginExecution( innerCount, root, value.getExecutions(), "executions", "execution" );
        iterateDependency( innerCount, root, value.getDependencies(), "dependencies", "dependency" );
        findAndReplaceXpp3DOM( innerCount, root, "goals", (Xpp3Dom) value.getGoals() );
        findAndReplaceSimpleElement( innerCount, root, "inherited", value.getInherited(), null );
        findAndReplaceXpp3DOM( innerCount, root, "configuration", (Xpp3Dom) value.getConfiguration() );
    } // -- void updatePlugin(Plugin, String, Counter, Element)

    /**
     * Method updatePluginConfiguration
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updatePluginConfiguration( PluginConfiguration value, String xmlTag, Counter counter,
                                              Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            updatePluginManagement( value.getPluginManagement(), "pluginManagement", innerCount, root );
            iteratePlugin( innerCount, root, value.getPlugins(), "plugins", "plugin" );
        }
    } // -- void updatePluginConfiguration(PluginConfiguration, String, Counter, Element)

    /**
     * Method updatePluginContainer
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updatePluginContainer( PluginContainer value, String xmlTag, Counter counter, Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            iteratePlugin( innerCount, root, value.getPlugins(), "plugins", "plugin" );
        }
    } // -- void updatePluginContainer(PluginContainer, String, Counter, Element)

    /**
     * Method updatePluginExecution
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updatePluginExecution( PluginExecution value, String xmlTag, Counter counter, Element element )
    {
        Element root = element;
        Counter innerCount = new Counter( counter.getDepth() + 1 );
        findAndReplaceSimpleElement( innerCount, root, "id", value.getId(), "default" );
        findAndReplaceSimpleElement( innerCount, root, "phase", value.getPhase(), null );
        findAndReplaceSimpleLists( innerCount, root, value.getGoals(), "goals", "goal" );
        findAndReplaceSimpleElement( innerCount, root, "inherited", value.getInherited(), null );
        findAndReplaceXpp3DOM( innerCount, root, "configuration", (Xpp3Dom) value.getConfiguration() );
    } // -- void updatePluginExecution(PluginExecution, String, Counter, Element)

    /**
     * Method updatePluginManagement
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updatePluginManagement( PluginManagement value, String xmlTag, Counter counter, Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            iteratePlugin( innerCount, root, value.getPlugins(), "plugins", "plugin" );
        }
    } // -- void updatePluginManagement(PluginManagement, String, Counter, Element)

    /**
     * Method updatePrerequisites
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updatePrerequisites( Prerequisites value, String xmlTag, Counter counter, Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            findAndReplaceSimpleElement( innerCount, root, "maven", value.getMaven(), "2.0" );
        }
    } // -- void updatePrerequisites(Prerequisites, String, Counter, Element)

    /**
     * Method updateProfile
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateProfile( Profile value, String xmlTag, Counter counter, Element element )
    {
        Element root = element;
        Counter innerCount = new Counter( counter.getDepth() + 1 );
        findAndReplaceSimpleElement( innerCount, root, "id", value.getId(), "default" );
        // updateActivation( value.getActivation(), "activation", innerCount, root);
        updateBuildBase( value.getBuild(), "build", innerCount, root );
        findAndReplaceSimpleLists( innerCount, root, value.getModules(), "modules", "module" );
        iterateRepository( innerCount, root, value.getRepositories(), "repositories", "repository" );
        iterateRepository( innerCount, root, value.getPluginRepositories(), "pluginRepositories", "pluginRepository" );
        iterateDependency( innerCount, root, value.getDependencies(), "dependencies", "dependency" );
        findAndReplaceXpp3DOM( innerCount, root, "reports", (Xpp3Dom) value.getReports() );
        updateReporting( value.getReporting(), "reporting", innerCount, root );
        updateDependencyManagement( value.getDependencyManagement(), "dependencyManagement", innerCount, root );
        updateDistributionManagement( value.getDistributionManagement(), "distributionManagement", innerCount, root );
        findAndReplaceProperties( innerCount, root, "properties", value.getProperties() );
    } // -- void updateProfile(Profile, String, Counter, Element)

    /**
     * Method updateRelocation
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateRelocation( Relocation value, String xmlTag, Counter counter, Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            findAndReplaceSimpleElement( innerCount, root, "groupId", value.getGroupId(), null );
            findAndReplaceSimpleElement( innerCount, root, "artifactId", value.getArtifactId(), null );
            findAndReplaceSimpleElement( innerCount, root, "version", value.getVersion(), null );
            findAndReplaceSimpleElement( innerCount, root, "message", value.getMessage(), null );
        }
    } // -- void updateRelocation(Relocation, String, Counter, Element)

    /**
     * Method updateReportPlugin
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateReportPlugin( ReportPlugin value, String xmlTag, Counter counter, Element element )
    {
        Element root = element;
        Counter innerCount = new Counter( counter.getDepth() + 1 );
        findAndReplaceSimpleElement( innerCount, root, "groupId", value.getGroupId(), "org.apache.maven.plugins" );
        findAndReplaceSimpleElement( innerCount, root, "artifactId", value.getArtifactId(), null );
        findAndReplaceSimpleElement( innerCount, root, "version", value.getVersion(), null );
        findAndReplaceSimpleElement( innerCount, root, "inherited", value.getInherited(), null );
        findAndReplaceXpp3DOM( innerCount, root, "configuration", (Xpp3Dom) value.getConfiguration() );
        iterateReportSet( innerCount, root, value.getReportSets(), "reportSets", "reportSet" );
    } // -- void updateReportPlugin(ReportPlugin, String, Counter, Element)

    /**
     * Method updateReportSet
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateReportSet( ReportSet value, String xmlTag, Counter counter, Element element )
    {
        Element root = element;
        Counter innerCount = new Counter( counter.getDepth() + 1 );
        findAndReplaceSimpleElement( innerCount, root, "id", value.getId(), "default" );
        findAndReplaceXpp3DOM( innerCount, root, "configuration", (Xpp3Dom) value.getConfiguration() );
        findAndReplaceSimpleElement( innerCount, root, "inherited", value.getInherited(), null );
        findAndReplaceSimpleLists( innerCount, root, value.getReports(), "reports", "report" );
    } // -- void updateReportSet(ReportSet, String, Counter, Element)

    /**
     * Method updateReporting
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateReporting( Reporting value, String xmlTag, Counter counter, Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            findAndReplaceSimpleElement( innerCount, root, "excludeDefaults", !value.isExcludeDefaults()
                ? null
                : String.valueOf( value.isExcludeDefaults() ), "false" );
            findAndReplaceSimpleElement( innerCount, root, "outputDirectory", value.getOutputDirectory(), null );
            iterateReportPlugin( innerCount, root, value.getPlugins(), "plugins", "plugin" );
        }
    } // -- void updateReporting(Reporting, String, Counter, Element)

    /**
     * Method updateRepository
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateRepository( Repository value, String xmlTag, Counter counter, Element element )
    {
        Element root = element;
        Counter innerCount = new Counter( counter.getDepth() + 1 );
        updateRepositoryPolicy( value.getReleases(), "releases", innerCount, root );
        updateRepositoryPolicy( value.getSnapshots(), "snapshots", innerCount, root );
        findAndReplaceSimpleElement( innerCount, root, "id", value.getId(), null );
        findAndReplaceSimpleElement( innerCount, root, "name", value.getName(), null );
        findAndReplaceSimpleElement( innerCount, root, "url", value.getUrl(), null );
        findAndReplaceSimpleElement( innerCount, root, "layout", value.getLayout(), "default" );
    } // -- void updateRepository(Repository, String, Counter, Element)

    /**
     * Method updateRepositoryBase
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateRepositoryBase( RepositoryBase value, String xmlTag, Counter counter, Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            findAndReplaceSimpleElement( innerCount, root, "id", value.getId(), null );
            findAndReplaceSimpleElement( innerCount, root, "name", value.getName(), null );
            findAndReplaceSimpleElement( innerCount, root, "url", value.getUrl(), null );
            findAndReplaceSimpleElement( innerCount, root, "layout", value.getLayout(), "default" );
        }
    } // -- void updateRepositoryBase(RepositoryBase, String, Counter, Element)

    /**
     * Method updateRepositoryPolicy
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateRepositoryPolicy( RepositoryPolicy value, String xmlTag, Counter counter, Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            findAndReplaceSimpleElement( innerCount, root, "enabled",
                                         value.isEnabled() ? null : String.valueOf( value.isEnabled() ), "true" );
            findAndReplaceSimpleElement( innerCount, root, "updatePolicy", value.getUpdatePolicy(), null );
            findAndReplaceSimpleElement( innerCount, root, "checksumPolicy", value.getChecksumPolicy(), null );
        }
    } // -- void updateRepositoryPolicy(RepositoryPolicy, String, Counter, Element)

    /**
     * Method updateResource
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateResource( Resource value, String xmlTag, Counter counter, Element element )
    {
        Element root = element;
        Counter innerCount = new Counter( counter.getDepth() + 1 );
        findAndReplaceSimpleElement( innerCount, root, "targetPath", value.getTargetPath(), null );
        findAndReplaceSimpleElement( innerCount, root, "filtering",
                                     !value.isFiltering() ? null : String.valueOf( value.isFiltering() ), "false" );
        findAndReplaceSimpleElement( innerCount, root, "directory", value.getDirectory(), null );
        findAndReplaceSimpleLists( innerCount, root, value.getIncludes(), "includes", "include" );
        findAndReplaceSimpleLists( innerCount, root, value.getExcludes(), "excludes", "exclude" );
    } // -- void updateResource(Resource, String, Counter, Element)

    /**
     * Method updateScm
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateScm( Scm value, String xmlTag, Counter counter, Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            findAndReplaceSimpleElement( innerCount, root, "connection", value.getConnection(), null );
            findAndReplaceSimpleElement( innerCount, root, "developerConnection", value.getDeveloperConnection(),
                                         null );
            findAndReplaceSimpleElement( innerCount, root, "tag", value.getTag(), "HEAD" );
            findAndReplaceSimpleElement( innerCount, root, "url", value.getUrl(), null );
        }
    } // -- void updateScm(Scm, String, Counter, Element)

    /**
     * Method updateSite
     *
     * @param value
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateSite( Site value, String xmlTag, Counter counter, Element element )
    {
        boolean shouldExist = value != null;
        Element root = updateElement( counter, element, xmlTag, shouldExist );
        if ( shouldExist )
        {
            Counter innerCount = new Counter( counter.getDepth() + 1 );
            findAndReplaceSimpleElement( innerCount, root, "id", value.getId(), null );
            findAndReplaceSimpleElement( innerCount, root, "name", value.getName(), null );
            findAndReplaceSimpleElement( innerCount, root, "url", value.getUrl(), null );
        }
    } // -- void updateSite(Site, String, Counter, Element)

    /**
     * Method write
     *
     * @param project
     * @param stream
     * @param document
     * @deprecated
     */
    public void write( Model project, Document document, OutputStream stream )
        throws java.io.IOException
    {
        updateModel( project, "project", new Counter( 0 ), document.getRootElement() );
        XMLOutputter outputter = new XMLOutputter();
        Format format = Format.getPrettyFormat();
        format.setIndent( "    " ).setLineSeparator( System.getProperty( "line.separator" ) );
        outputter.setFormat( format );
        outputter.output( document, stream );
    } // -- void write(Model, Document, OutputStream)

    /**
     * Method write
     *
     * @param project
     * @param writer
     * @param document
     */
    public void write( Model project, Document document, OutputStreamWriter writer )
        throws java.io.IOException
    {
        Format format = Format.getRawFormat();
        format.setEncoding( writer.getEncoding() ).setLineSeparator( System.getProperty( "line.separator" ) );
        write( project, document, writer, format );
    } // -- void write(Model, Document, OutputStreamWriter)

    /**
     * Method write
     *
     * @param project
     * @param jdomFormat
     * @param writer
     * @param document
     */
    public void write( Model project, Document document, Writer writer, Format jdomFormat )
        throws java.io.IOException
    {
        updateModel( project, "project", new Counter( 0 ), document.getRootElement() );
        XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat( jdomFormat );
        outputter.output( document, writer );
    } // -- void write(Model, Document, Writer, Format)

    // -----------------/
    // - Inner Classes -/
    // -----------------/

    /**
     * Class Counter.
     *
     * @version $Revision: 1300048 $ $Date: 2012-03-13 05:09:35 -0500 (Tue, 13 Mar 2012) $
     */
    public class Counter
    {

        // --------------------------/
        // - Class/Member Variables -/
        // --------------------------/

        /**
         * Field currentIndex
         */
        private int currentIndex = 0;

        /**
         * Field level
         */
        private int level;

        // ----------------/
        // - Constructors -/
        // ----------------/

        public Counter( int depthLevel )
        {
            level = depthLevel;
        } // -- org.apache.maven.model.io.jdom.Counter(int)

        // -----------/
        // - Methods -/
        // -----------/

        /**
         * Method getCurrentIndex
         */
        public int getCurrentIndex()
        {
            return currentIndex;
        } // -- int getCurrentIndex()

        /**
         * Method getDepth
         */
        public int getDepth()
        {
            return level;
        } // -- int getDepth()

        /**
         * Method increaseCount
         */
        public void increaseCount()
        {
            currentIndex = currentIndex + 1;
        } // -- void increaseCount()

    }

}
