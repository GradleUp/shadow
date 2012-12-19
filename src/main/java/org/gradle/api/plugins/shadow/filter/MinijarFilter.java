package org.gradle.api.plugins.shadow.filter;

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

import org.apache.commons.logging.Log;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.IOUtil;
import org.vafer.jdependency.Clazz;
import org.vafer.jdependency.Clazzpath;
import org.vafer.jdependency.ClazzpathUnit;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * A filter that prevents the inclusion of classes not required in the final jar.
 *
 * @author Torsten Curdt
 */
public class MinijarFilter
    implements Filter
{

    private Log log;

    private Set<Clazz> removable;

    private int classesKept;

    private int classesRemoved;

    public MinijarFilter( MavenProject project, Log log )
        throws IOException
    {
        this( project, log, Collections.<SimpleFilter>emptyList() );
    }

    /**
     *
     * @since 1.6
     */
    @SuppressWarnings( { "unchecked", "rawtypes" } )
    public MinijarFilter( MavenProject project, Log log, List<SimpleFilter> simpleFilters )
        throws IOException
    {

        this.log = log;

        Clazzpath cp = new Clazzpath();

        ClazzpathUnit artifactUnit =
            cp.addClazzpathUnit( new FileInputStream( project.getArtifact().getFile() ), project.toString() );

        for ( Iterator it = project.getArtifacts().iterator(); it.hasNext(); )
        {
            Artifact dependency = (Artifact) it.next();
            addDependencyToClasspath( cp, dependency );
        }

        removable = cp.getClazzes();
        removePackages( artifactUnit );
        removable.removeAll( artifactUnit.getClazzes() );
        removable.removeAll( artifactUnit.getTransitiveDependencies() );
        removeSpecificallyIncludedClasses( project, simpleFilters == null
            ? Collections.<SimpleFilter>emptyList()
            : simpleFilters );
    }

    private ClazzpathUnit addDependencyToClasspath( Clazzpath cp, Artifact dependency ) throws IOException
    {
        InputStream is = null;
        ClazzpathUnit clazzpathUnit = null;
        try
        {
            is = new FileInputStream( dependency.getFile() );
            clazzpathUnit = cp.addClazzpathUnit( is, dependency.toString() );
        }
        catch( ArrayIndexOutOfBoundsException e )
        {
            //trap ArrayIndexOutOfBoundsExceptions caused by malformed dependency classes (MSHADE-107)
            log.warn( dependency.toString() + " could not be analyzed for minimization; dependency is probably malformed." );
        }
        finally
        {
            IOUtil.close( is );
        }
        
        return clazzpathUnit;
    }
    
    private void removePackages( ClazzpathUnit artifactUnit )
    {
        Set<String> packageNames = new HashSet<String>();
        removePackages( artifactUnit.getClazzes(), packageNames );
        removePackages( artifactUnit.getTransitiveDependencies(), packageNames );
    }

    @SuppressWarnings( "rawtypes" )
    private void removePackages( Set clazzes, Set<String> packageNames )
    {
        Iterator it = clazzes.iterator();
        while ( it.hasNext() )
        {
            Clazz clazz = (Clazz) it.next();
            String name = clazz.getName();
            while ( name.contains( "." ) )
            {
                name = name.substring( 0, name.lastIndexOf( '.' ) );
                if ( packageNames.add( name ) )
                {
                    removable.remove( new Clazz( name + ".package-info" ) );
                }
            }
        }
    }

    @SuppressWarnings( "rawtypes" )
    private void removeSpecificallyIncludedClasses( MavenProject project, List<SimpleFilter> simpleFilters )
        throws IOException
    {
        //remove classes specifically included in filters
        Clazzpath checkCp = new Clazzpath();
        for ( Iterator it = project.getArtifacts().iterator(); it.hasNext(); )
        {
            Artifact dependency = (Artifact) it.next();
            File jar = dependency.getFile();

            for ( Iterator<SimpleFilter> i = simpleFilters.iterator(); i.hasNext(); )
            {
                SimpleFilter simpleFilter = i.next();
                if ( simpleFilter.canFilter( jar ) )
                {
                    ClazzpathUnit depClazzpathUnit = addDependencyToClasspath( checkCp, dependency );
                    if ( depClazzpathUnit != null )
                    {
                        Iterator<Clazz> j = removable.iterator();
                        while ( j.hasNext() )
                        {
                            Clazz clazz = j.next();

                            if ( depClazzpathUnit.getClazzes().contains( clazz ) && simpleFilter.isSpecificallyIncluded(
                                clazz.getName().replace( '.', '/' ) ) )
                            {
                                log.info( clazz.getName() + " not removed because it was specifically included" );
                                j.remove();
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean canFilter( File jar )
    {
        return true;
    }

    public boolean isFiltered( String classFile )
    {
        String className = classFile.replace( '/', '.' ).replaceFirst( "\\.class$", "" );
        Clazz clazz = new Clazz( className );

        if ( removable.contains( clazz ) )
        {
            log.debug( "Removing " + className );
            classesRemoved += 1;
            return true;
        }

        classesKept += 1;
        return false;
    }

    public void finished()
    {
        int classes_total = classesRemoved + classesKept;
        log.info(
            "Minimized " + classes_total + " -> " + classesKept + " (" + (int) ( 100 * classesKept / classes_total )
                + "%)" );
    }
}
