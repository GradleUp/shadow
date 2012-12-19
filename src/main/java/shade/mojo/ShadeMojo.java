package shade.mojo;

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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.plugins.shade.ShadeRequest;
import org.apache.maven.plugins.shade.Shader;
import org.apache.maven.plugins.shade.filter.Filter;
import org.apache.maven.plugins.shade.filter.MinijarFilter;
import org.apache.maven.plugins.shade.filter.SimpleFilter;
import org.apache.maven.plugins.shade.pom.PomWriter;
import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.relocation.SimpleRelocator;
import org.apache.maven.plugins.shade.resource.ResourceTransformer;
import org.apache.maven.project.*;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.WriterFactory;

import java.io.*;
import java.util.*;

/**
 * Mojo that performs shading delegating to the Shader component.
 *
 * @author Jason van Zyl
 * @author Mauro Talevi
 * @author David Blevins
 * @author Hiram Chirino
 */
@Mojo( name = "shade", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true,
       requiresDependencyResolution = ResolutionScope.RUNTIME )
public class ShadeMojo
    extends AbstractMojo
    implements Contextualizable
{
    /**
     * The current Maven session.
     */
    @Component
    private MavenSession session;

    /**
     * The current Maven project.
     */
    @Component
    private MavenProject project;

    @Component
    private MavenProjectHelper projectHelper;

    @Component( hint = "default", role = org.apache.maven.plugins.shade.Shader.class )
    private Shader shader;

    /**
     * The dependency graph builder to use.
     */
    @Component
    private DependencyGraphBuilder dependencyGraphBuilder;

    /**
     * ProjectBuilder, needed to create projects from the artifacts.
     */
    @Component
    private ProjectBuilder projectBuilder;

    /**
     * The artifact metadata source to use.
     */
    @Component
    private ArtifactMetadataSource artifactMetadataSource;

    /**
     * Remote repositories which will be searched for source attachments.
     */
    @Parameter( readonly = true, required = true, defaultValue = "${project.remoteArtifactRepositories}" )
    protected List<ArtifactRepository> remoteArtifactRepositories;

    /**
     * Local maven repository.
     */
    @Parameter( readonly = true, required = true, defaultValue = "${localRepository}" )
    protected ArtifactRepository localRepository;

    /**
     * Artifact factory, needed to download source jars for inclusion in classpath.
     */
    @Component
    protected ArtifactFactory artifactFactory;

    /**
     * Artifact resolver, needed to download source jars for inclusion in classpath.
     */
    @Component
    protected ArtifactResolver artifactResolver;

    /**
     * Artifacts to include/exclude from the final artifact. Artifacts are denoted by composite identifiers of the
     * general form <code>groupId:artifactId:type:classifier</code>. Since version 1.3, the wildcard characters '*' and
     * '?' can be used within the sub parts of those composite identifiers to do pattern matching. For convenience, the
     * syntax <code>groupId</code> is equivalent to <code>groupId:*:*:*</code>, <code>groupId:artifactId</code> is
     * equivalent to <code>groupId:artifactId:*:*</code> and <code>groupId:artifactId:classifier</code> is equivalent to
     * <code>groupId:artifactId:*:classifier</code>. For example:
     * <pre>
     * &lt;artifactSet&gt;
     *   &lt;includes&gt;
     *     &lt;include&gt;org.apache.maven:*&lt;/include&gt;
     *   &lt;/includes&gt;
     *   &lt;excludes&gt;
     *     &lt;exclude&gt;*:maven-core&lt;/exclude&gt;
     *   &lt;/excludes&gt;
     * &lt;/artifactSet&gt;
     * </pre>
     */
    @Parameter
    private ArtifactSet artifactSet;

    /**
     * Packages to be relocated. For example:
     * <pre>
     * &lt;relocations&gt;
     *   &lt;relocation&gt;
     *     &lt;pattern&gt;org.apache&lt;/pattern&gt;
     *     &lt;shadedPattern&gt;hidden.org.apache&lt;/shadedPattern&gt;
     *     &lt;includes&gt;
     *       &lt;include&gt;org.apache.maven.*&lt;/include&gt;
     *     &lt;/includes&gt;
     *     &lt;excludes&gt;
     *       &lt;exclude&gt;org.apache.maven.Public*&lt;/exclude&gt;
     *     &lt;/excludes&gt;
     *   &lt;/relocation&gt;
     * &lt;/relocations&gt;
     * </pre>
     * <em>Note:</em> Support for includes exists only since version 1.4.
     */
    @Parameter
    private PackageRelocation[] relocations;

    /**
     * Resource transformers to be used. Please see the "Examples" section for more information on available
     * transformers and their configuration.
     */
    @Parameter
    private ResourceTransformer[] transformers;

    /**
     * Archive Filters to be used. Allows you to specify an artifact in the form of a composite identifier as used by
     * {@link #artifactSet} and a set of include/exclude file patterns for filtering which contents of the archive are
     * added to the shaded jar. From a logical perspective, includes are processed before excludes, thus it's possible
     * to use an include to collect a set of files from the archive then use excludes to further reduce the set. By
     * default, all files are included and no files are excluded. If multiple filters apply to an artifact, the
     * intersection of the matched files will be included in the final JAR. For example:
     * <pre>
     * &lt;filters&gt;
     *   &lt;filter&gt;
     *     &lt;artifact&gt;junit:junit&lt;/artifact&gt;
     *     &lt;includes&gt;
     *       &lt;include&gt;org/junit/**&lt;/include&gt;
     *     &lt;/includes&gt;
     *     &lt;excludes&gt;
     *       &lt;exclude&gt;org/junit/experimental/**&lt;/exclude&gt;
     *     &lt;/excludes&gt;
     *   &lt;/filter&gt;
     * &lt;/filters&gt;
     * </pre>
     */
    @Parameter
    private ArchiveFilter[] filters;

    /**
     * The destination directory for the shaded artifact.
     */
    @Parameter( defaultValue = "${project.build.directory}" )
    private File outputDirectory;

    /**
     * The name of the shaded artifactId.
     * <p/>
     * If you like to change the name of the native artifact, you may use the &lt;build>&lt;finalName> setting.
     * If this is set to something different than &lt;build>&lt;finalName>, no file replacement
     * will be performed, even if shadedArtifactAttached is being used.
     */
    @Parameter
    private String finalName;

    /**
     * The name of the shaded artifactId. So you may want to use a different artifactId and keep
     * the standard version. If the original artifactId was "foo" then the final artifact would
     * be something like foo-1.0.jar. So if you change the artifactId you might have something
     * like foo-special-1.0.jar.
     */
    @Parameter( defaultValue = "${project.artifactId}" )
    private String shadedArtifactId;

    /**
     * If specified, this will include only artifacts which have groupIds which
     * start with this.
     */
    @Parameter
    private String shadedGroupFilter;

    /**
     * Defines whether the shaded artifact should be attached as classifier to
     * the original artifact.  If false, the shaded jar will be the main artifact
     * of the project
     */
    @Parameter
    private boolean shadedArtifactAttached;

    /**
     * Flag whether to generate a simplified POM for the shaded artifact. If set to <code>true</code>, dependencies that
     * have been included into the uber JAR will be removed from the <code>&lt;dependencies&gt;</code> section of the
     * generated POM. The reduced POM will be named <code>dependency-reduced-pom.xml</code> and is stored into the same
     * directory as the shaded artifact. Unless you also specify dependencyReducedPomLocation, the plugin will
     * create a temporary file named <code>dependency-reduced-pom.xml</code> in the project basedir.
     */
    @Parameter( defaultValue = "true" )
    private boolean createDependencyReducedPom;


    /**
     * Where to put the dependency reduced pom.
     * Note: setting a value for this parameter with a directory other than ${basedir} will change the value of ${basedir}
     * for all executions that come after the shade execution. This is often not what you want. This is considered
     * an open issue with this plugin.
     *
     * @since 1.7
     */
    @Parameter( defaultValue = "${basedir}/dependency-reduced-pom.xml" )
    private File dependencyReducedPomLocation;

    /**
     * Create a dependency-reduced POM in ${basedir}/drp-UNIQUE.pom. This avoids build collisions
     * of parallel builds without moving the dependency-reduced POM to a different directory.
     * The property maven.shade.dependency-reduced-pom is set to the generated filename.
     *
     * @since 1.7.2
     */
    @Parameter( defaultValue = "false" )
    private boolean generateUniqueDependencyReducedPom;

    /**
     * When true, dependencies are kept in the pom but with scope 'provided'; when false,
     * the dependency is removed.
     */
    @Parameter
    private boolean keepDependenciesWithProvidedScope;

    /**
     * When true, transitive deps of removed dependencies are promoted to direct dependencies.
     * This should allow the drop in replacement of the removed deps with the new shaded
     * jar and everything should still work.
     */
    @Parameter
    private boolean promoteTransitiveDependencies;

    /**
     * The name of the classifier used in case the shaded artifact is attached.
     */
    @Parameter( defaultValue = "shaded" )
    private String shadedClassifierName;

    /**
     * When true, it will attempt to create a sources jar as well
     */
    @Parameter
    private boolean createSourcesJar;

    /**
     * When true, it will attempt to shade the contents of the java source files when creating the sources jar.
     * When false, it will just relocate the java source files to the shaded paths, but will not modify the
     * actual contents of the java source files.
     *
     */
    @Parameter(property = "shadeSourcesContent", defaultValue = "false")
    private boolean shadeSourcesContent;

    /**
     * When true, dependencies will be stripped down on the class level to only the transitive hull required for the
     * artifact. <em>Note:</em> Usage of this feature requires Java 1.5 or higher.
     *
     * @since 1.4
     */
    @Parameter
    private boolean minimizeJar;

    /**
     * The path to the output file for the shaded artifact. When this parameter is set, the created archive will neither
     * replace the project's main artifact nor will it be attached. Hence, this parameter causes the parameters
     * {@link #finalName}, {@link #shadedArtifactAttached}, {@link #shadedClassifierName} and
     * {@link #createDependencyReducedPom} to be ignored when used.
     *
     * @since 1.3
     */
    @Parameter
    private File outputFile;

    /**
     * You can pass here the roleHint about your own Shader implementation plexus component.
     *
     * @since 1.6
     */
    @Parameter
    private String shaderHint;

    /**
     * @since 1.6
     */
    private PlexusContainer plexusContainer;

    public void contextualize( Context context )
        throws ContextException
    {
        plexusContainer = (PlexusContainer) context.get( PlexusConstants.PLEXUS_KEY );
    }

    /**
     * @throws MojoExecutionException
     */
    public void execute()
        throws MojoExecutionException
    {

        if ( shaderHint != null )
        {
            try
            {
                shader = (Shader) plexusContainer.lookup( Shader.ROLE, shaderHint );
            }
            catch ( ComponentLookupException e )
            {
                throw new MojoExecutionException(
                    "unable to lookup own Shader implementation with hint:'" + shaderHint + "'", e );
            }
        }

        Set<File> artifacts = new LinkedHashSet<File>();
        Set<String> artifactIds = new LinkedHashSet<String>();
        Set<File> sourceArtifacts = new LinkedHashSet<File>();

        ArtifactSelector artifactSelector =
            new ArtifactSelector( project.getArtifact(), artifactSet, shadedGroupFilter );

        if ( artifactSelector.isSelected( project.getArtifact() ) && !"pom".equals( project.getArtifact().getType() ) )
        {
            if ( invalidMainArtifact() )
            {
                getLog().error( "The project main artifact does not exist. This could have the following" );
                getLog().error( "reasons:" );
                getLog().error( "- You have invoked the goal directly from the command line. This is not" );
                getLog().error( "  supported. Please add the goal to the default lifecycle via an" );
                getLog().error( "  <execution> element in your POM and use \"mvn package\" to have it run." );
                getLog().error( "- You have bound the goal to a lifecycle phase before \"package\". Please" );
                getLog().error( "  remove this binding from your POM such that the goal will be run in" );
                getLog().error( "  the proper phase." );
                getLog().error(
                    "- You removed the configuration of the maven-jar-plugin that produces the main artifact." );
                throw new MojoExecutionException(
                    "Failed to create shaded artifact, " + "project main artifact does not exist." );
            }

            artifacts.add( project.getArtifact().getFile() );

            if ( createSourcesJar )
            {
                File file = shadedSourcesArtifactFile();
                if ( file.isFile() )
                {
                    sourceArtifacts.add( file );
                }
            }
        }

        for ( Artifact artifact : project.getArtifacts() )
        {
            if ( !artifactSelector.isSelected( artifact ) )
            {
                getLog().info( "Excluding " + artifact.getId() + " from the shaded jar." );

                continue;
            }

            if ( "pom".equals( artifact.getType() ) )
            {
                getLog().info( "Skipping pom dependency " + artifact.getId() + " in the shaded jar." );
                continue;
            }

            getLog().info( "Including " + artifact.getId() + " in the shaded jar." );

            artifacts.add( artifact.getFile() );
            artifactIds.add( getId( artifact ) );

            if ( createSourcesJar )
            {
                File file = resolveArtifactSources( artifact );
                if ( file != null )
                {
                    sourceArtifacts.add( file );
                }
            }
        }

        File outputJar = ( outputFile != null ) ? outputFile : shadedArtifactFileWithClassifier();
        File sourcesJar = shadedSourceArtifactFileWithClassifier();

        // Now add our extra resources
        try
        {
            List<Filter> filters = getFilters();

            List<Relocator> relocators = getRelocators();

            List<ResourceTransformer> resourceTransformers = getResourceTransformers();

            ShadeRequest shadeRequest = new ShadeRequest();
            shadeRequest.setJars( artifacts );
            shadeRequest.setUberJar( outputJar );
            shadeRequest.setFilters( filters );
            shadeRequest.setRelocators( relocators );
            shadeRequest.setResourceTransformers( resourceTransformers );

            shader.shade( shadeRequest );

            if ( createSourcesJar )
            {
                ShadeRequest shadeSourcesRequest = new ShadeRequest();
                shadeSourcesRequest.setJars( sourceArtifacts );
                shadeSourcesRequest.setUberJar( sourcesJar );
                shadeSourcesRequest.setFilters( filters );
                shadeSourcesRequest.setRelocators( relocators );
                shadeSourcesRequest.setResourceTransformers( resourceTransformers );
                shadeSourcesRequest.setShadeSourcesContent( shadeSourcesContent );

                shader.shade( shadeSourcesRequest );
            }

            if ( outputFile == null )
            {
                boolean renamed = false;

                // rename the output file if a specific finalName is set
                // but don't rename if the finalName is the <build><finalName>
                // because this will be handled implicitly later
                if ( finalName != null && finalName.length() > 0 && !finalName.equals(
                    project.getBuild().getFinalName() ) )
                {
                    String finalFileName = finalName + "." + project.getArtifact().getArtifactHandler().getExtension();
                    File finalFile = new File( outputDirectory, finalFileName );
                    replaceFile( finalFile, outputJar );
                    outputJar = finalFile;

                    renamed = true;
                }

                if ( shadedArtifactAttached )
                {
                    getLog().info( "Attaching shaded artifact." );
                    projectHelper.attachArtifact( project, project.getArtifact().getType(), shadedClassifierName,
                                                  outputJar );
                    if ( createSourcesJar )
                    {
                        projectHelper.attachArtifact( project, "jar", shadedClassifierName + "-sources", sourcesJar );
                    }
                }
                else if ( !renamed )
                {
                    getLog().info( "Replacing original artifact with shaded artifact." );
                    File originalArtifact = project.getArtifact().getFile();
                    replaceFile( originalArtifact, outputJar );

                    if ( createSourcesJar )
                    {
                        File shadedSources = shadedSourcesArtifactFile();

                        replaceFile( shadedSources, sourcesJar );

                        projectHelper.attachArtifact( project, "jar", "sources", shadedSources );
                    }

                    if ( createDependencyReducedPom )
                    {
                        createDependencyReducedPom( artifactIds );
                    }
                }
            }
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Error creating shaded jar: " + e.getMessage(), e );
        }
    }

    private boolean invalidMainArtifact()
    {
        return project.getArtifact().getFile() == null || !project.getArtifact().getFile().isFile();
    }

    private void replaceFile( File oldFile, File newFile )
        throws MojoExecutionException
    {
        getLog().info( "Replacing " + oldFile + " with " + newFile );

        File origFile = new File( outputDirectory, "original-" + oldFile.getName() );
        if ( oldFile.exists() && !oldFile.renameTo( origFile ) )
        {
            //try a gc to see if an unclosed stream needs garbage collecting
            System.gc();
            System.gc();

            if ( !oldFile.renameTo( origFile ) )
            {
                // Still didn't work.   We'll do a copy
                try
                {
                    FileOutputStream fout = new FileOutputStream( origFile );
                    FileInputStream fin = new FileInputStream( oldFile );
                    try
                    {
                        IOUtil.copy( fin, fout );
                    }
                    finally
                    {
                        IOUtil.close( fin );
                        IOUtil.close( fout );
                    }
                }
                catch ( IOException ex )
                {
                    //kind of ignorable here.   We're just trying to save the original
                    getLog().warn( ex );
                }
            }
        }
        if ( !newFile.renameTo( oldFile ) )
        {
            //try a gc to see if an unclosed stream needs garbage collecting
            System.gc();
            System.gc();

            if ( !newFile.renameTo( oldFile ) )
            {
                // Still didn't work.   We'll do a copy
                try
                {
                    FileOutputStream fout = new FileOutputStream( oldFile );
                    FileInputStream fin = new FileInputStream( newFile );
                    try
                    {
                        IOUtil.copy( fin, fout );
                    }
                    finally
                    {
                        IOUtil.close( fin );
                        IOUtil.close( fout );
                    }
                }
                catch ( IOException ex )
                {
                    throw new MojoExecutionException( "Could not replace original artifact with shaded artifact!", ex );
                }
            }
        }
    }

    private File resolveArtifactSources( Artifact artifact )
    {

        Artifact resolvedArtifact =
            artifactFactory.createArtifactWithClassifier( artifact.getGroupId(), artifact.getArtifactId(),
                                                          artifact.getVersion(), "java-source", "sources" );

        try
        {
            artifactResolver.resolve( resolvedArtifact, remoteArtifactRepositories, localRepository );
        }
        catch ( ArtifactNotFoundException e )
        {
            // ignore, the jar has not been found
        }
        catch ( ArtifactResolutionException e )
        {
            getLog().warn( "Could not get sources for " + artifact );
        }

        if ( resolvedArtifact.isResolved() )
        {
            return resolvedArtifact.getFile();
        }
        return null;
    }

    private List<Relocator> getRelocators()
    {
        List<Relocator> relocators = new ArrayList<Relocator>();

        if ( relocations == null )
        {
            return relocators;
        }

        for ( int i = 0; i < relocations.length; i++ )
        {
            PackageRelocation r = relocations[i];

            relocators.add( new SimpleRelocator( r.getPattern(), r.getShadedPattern(), r.getIncludes(), r.getExcludes(),
                                                 r.isRawString() ) );
        }

        return relocators;
    }

    private List<ResourceTransformer> getResourceTransformers()
    {
        if ( transformers == null )
        {
            return Collections.emptyList();
        }

        return Arrays.asList( transformers );
    }

    private List<Filter> getFilters()
        throws MojoExecutionException
    {
        List<Filter> filters = new ArrayList<Filter>();
        List<SimpleFilter> simpleFilters = new ArrayList<SimpleFilter>();

        if ( this.filters != null && this.filters.length > 0 )
        {
            Map<Artifact, ArtifactId> artifacts = new HashMap<Artifact, ArtifactId>();

            artifacts.put( project.getArtifact(), new ArtifactId( project.getArtifact() ) );

            for ( Artifact artifact : project.getArtifacts() )
            {
                artifacts.put( artifact, new ArtifactId( artifact ) );
            }

            for ( ArchiveFilter filter : this.filters )
            {
                ArtifactId pattern = new ArtifactId( filter.getArtifact() );

                Set<File> jars = new HashSet<File>();

                for ( Map.Entry<Artifact, ArtifactId> entry : artifacts.entrySet() )
                {
                    if ( entry.getValue().matches( pattern ) )
                    {
                        Artifact artifact = entry.getKey();

                        jars.add( artifact.getFile() );

                        if ( createSourcesJar )
                        {
                            File file = resolveArtifactSources( artifact );
                            if ( file != null )
                            {
                                jars.add( file );
                            }
                        }
                    }
                }

                if ( jars.isEmpty() )
                {
                    getLog().info( "No artifact matching filter " + filter.getArtifact() );

                    continue;
                }

                simpleFilters.add( new SimpleFilter( jars, filter.getIncludes(), filter.getExcludes() ) );
            }
        }

        filters.addAll( simpleFilters );

        if ( minimizeJar )
        {
            getLog().info( "Minimizing jar " + project.getArtifact() );

            try
            {
                filters.add( new MinijarFilter( project, getLog(), simpleFilters ) );
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( "Failed to analyze class dependencies", e );
            }
        }

        return filters;
    }

    private File shadedArtifactFileWithClassifier()
    {
        Artifact artifact = project.getArtifact();
        final String shadedName = shadedArtifactId + "-" + artifact.getVersion() + "-" + shadedClassifierName + "."
            + artifact.getArtifactHandler().getExtension();
        return new File( outputDirectory, shadedName );
    }

    private File shadedSourceArtifactFileWithClassifier()
    {
        Artifact artifact = project.getArtifact();
        final String shadedName =
            shadedArtifactId + "-" + artifact.getVersion() + "-" + shadedClassifierName + "-sources."
                + artifact.getArtifactHandler().getExtension();
        return new File( outputDirectory, shadedName );
    }

    private File shadedSourcesArtifactFile()
    {
        Artifact artifact = project.getArtifact();

        String shadedName;

        if ( project.getBuild().getFinalName() != null )
        {
            shadedName = project.getBuild().getFinalName() + "-sources." + artifact.getArtifactHandler().getExtension();
        }
        else
        {
            shadedName = shadedArtifactId + "-" + artifact.getVersion() + "-sources."
                + artifact.getArtifactHandler().getExtension();
        }

        return new File( outputDirectory, shadedName );
    }

    // We need to find the direct dependencies that have been included in the uber JAR so that we can modify the
    // POM accordingly.
    private void createDependencyReducedPom( Set<String> artifactsToRemove )
        throws IOException, DependencyGraphBuilderException, ProjectBuildingException
    {
        Model model = project.getOriginalModel();
        List<Dependency> dependencies = new ArrayList<Dependency>();

        boolean modified = false;

        List<Dependency> transitiveDeps = new ArrayList<Dependency>();

        for ( Artifact artifact : project.getArtifacts() )
        {
            if ( "pom".equals( artifact.getType() ) )
            {
                // don't include pom type dependencies in dependency reduced pom
                continue;
            }

            //promote
            Dependency dep = new Dependency();
            dep.setArtifactId( artifact.getArtifactId() );
            if ( artifact.hasClassifier() )
            {
                dep.setClassifier( artifact.getClassifier() );
            }
            dep.setGroupId( artifact.getGroupId() );
            dep.setOptional( artifact.isOptional() );
            dep.setScope( artifact.getScope() );
            dep.setType( artifact.getType() );
            dep.setVersion( artifact.getVersion() );

            //we'll figure out the exclusions in a bit.

            transitiveDeps.add( dep );
        }
        List<Dependency> origDeps = project.getDependencies();

        if ( promoteTransitiveDependencies )
        {
            origDeps = transitiveDeps;
        }

        for ( Dependency d : origDeps )
        {
            dependencies.add( d );

            String id = getId( d );

            if ( artifactsToRemove.contains( id ) )
            {
                modified = true;

                if ( keepDependenciesWithProvidedScope )
                {
                    d.setScope( "provided" );
                }
                else
                {
                    dependencies.remove( d );
                }
            }
        }

        // Check to see if we have a reduction and if so rewrite the POM.
        if ( modified )
        {
            while ( modified )
            {

                model.setDependencies( dependencies );

                if ( generateUniqueDependencyReducedPom )
                {
                    dependencyReducedPomLocation = File.createTempFile( "dependency-reduced-pom-", ".xml", project.getBasedir() );
                    project.getProperties().setProperty( "maven.shade.dependency-reduced-pom", dependencyReducedPomLocation.getAbsolutePath() );
                }
                else
                {
                    if ( dependencyReducedPomLocation == null )
                    {
                        // MSHADE-123: We can't default to 'target' because it messes up uses of ${project.basedir}
                        dependencyReducedPomLocation = new File( project.getBasedir(), "dependency-reduced-pom.xml" );
                    }
                }

                File f = dependencyReducedPomLocation;
                getLog().info( "Dependency-reduced POM written at: " + f.getAbsolutePath() );

                if ( f.exists() )
                {
                    f.delete();
                }

                Writer w = WriterFactory.newXmlWriter( f );

                String origRelativePath = null;
                String replaceRelativePath = null;
                if ( model.getParent() != null )
                {
                    origRelativePath = model.getParent().getRelativePath();

                }
                replaceRelativePath = origRelativePath;

                if ( origRelativePath == null )
                {
                    origRelativePath = "../pom.xml";
                }

                if ( model.getParent() != null )
                {
                    File parentFile =
                        new File( project.getBasedir(), model.getParent().getRelativePath() ).getCanonicalFile();
                    if ( !parentFile.isFile() )
                    {
                        parentFile = new File( parentFile, "pom.xml" );
                    }

                    parentFile = parentFile.getCanonicalFile();

                    String relPath = RelativizePath.convertToRelativePath( parentFile, f );
                    model.getParent().setRelativePath( relPath );
                }

                try
                {
                    PomWriter.write( w, model, true );
                }
                finally
                {
                    if ( model.getParent() != null )
                    {
                        model.getParent().setRelativePath( replaceRelativePath );
                    }
                    w.close();
                }

                ProjectBuildingRequest projectBuildingRequest =
                    new DefaultProjectBuildingRequest( session.getProjectBuildingRequest() );
                projectBuildingRequest.setLocalRepository( localRepository );
                projectBuildingRequest.setRemoteRepositories( remoteArtifactRepositories );

                ProjectBuildingResult result = projectBuilder.build( f, projectBuildingRequest );

                modified = updateExcludesInDeps( result.getProject(), dependencies, transitiveDeps );
            }

            project.setFile( dependencyReducedPomLocation );
        }
    }

    private String getId( Artifact artifact )
    {
        return getId( artifact.getGroupId(), artifact.getArtifactId(), artifact.getType(), artifact.getClassifier() );
    }

    private String getId( Dependency dependency )
    {
        return getId( dependency.getGroupId(), dependency.getArtifactId(), dependency.getType(),
                      dependency.getClassifier() );
    }

    private String getId( String groupId, String artifactId, String type, String classifier )
    {
        return groupId + ":" + artifactId + ":" + type + ":" + ( ( classifier != null ) ? classifier : "" );
    }

    public boolean updateExcludesInDeps( MavenProject project, List<Dependency> dependencies,
                                         List<Dependency> transitiveDeps )
        throws DependencyGraphBuilderException
    {
        DependencyNode node = dependencyGraphBuilder.buildDependencyGraph( project, null );
        boolean modified = false;
        for ( DependencyNode n2 : node.getChildren() )
        {
            for ( DependencyNode n3 : n2.getChildren() )
            {
                //check if it really isn't in the list of original dependencies.  Maven
                //prior to 2.0.8 may grab versions from transients instead of
                //from the direct deps in which case they would be marked included
                //instead of OMITTED_FOR_DUPLICATE

                //also, if not promoting the transitives, level 2's would be included
                boolean found = false;
                for ( Dependency dep : transitiveDeps )
                {
                    if ( dep.getArtifactId().equals( n3.getArtifact().getArtifactId() )
                        && dep.getGroupId().equals( n3.getArtifact().getGroupId() ) )
                    {
                        found = true;
                        break;
                    }
                }

                if ( !found )
                {
                    for ( Dependency dep : dependencies )
                    {
                        if ( dep.getArtifactId().equals( n2.getArtifact().getArtifactId() )
                            && dep.getGroupId().equals( n2.getArtifact().getGroupId() ) )
                        {
                            Exclusion exclusion = new Exclusion();
                            exclusion.setArtifactId( n3.getArtifact().getArtifactId() );
                            exclusion.setGroupId( n3.getArtifact().getGroupId() );
                            dep.addExclusion( exclusion );
                            modified = true;
                            break;
                        }
                    }
                }
            }
        }
        return modified;
    }
}
