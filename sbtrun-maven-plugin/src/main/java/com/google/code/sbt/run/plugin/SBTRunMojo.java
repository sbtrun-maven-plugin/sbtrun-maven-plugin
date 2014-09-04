/*
 * Copyright 2014 Grzegorz Slowikowski (gslowikowski at gmail dot com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

package com.google.code.sbt.run.plugin;

//import java.io.File;
//import java.io.IOException;

//import org.apache.maven.plugin.AbstractMojo;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.filter.AndArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
//import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.types.Path;

/**
 * ...
 * 
 * @author <a href="mailto:gslowikowski@gmail.com">Grzegorz Slowikowski</a>
 * @since 1.0.0
 */
@Mojo( name = "run", requiresProject = false )
public class SBTRunMojo
    extends AbstractAntJavaBasedMojo/*AbstractMojo*/
{

    /**
     * Allows the server startup to be skipped.
     * 
     * @since 1.0.0
     */
    @Parameter( property = "sbtrun.skip", defaultValue = "false" )
    private boolean skip;

// Not ready yet
//    /**
//     * Run in forked Java process.
//     * 
//     * @since 1.0.0
//     */
//    @Parameter( property = "sbtrun.fork", defaultValue = "true" )
//    private boolean fork;

    /**
     * Arguments
     * 
     * @since 1.0.0
     */
    @Parameter( property = "sbtrun.args", defaultValue = "" )
     String args;

    /**
     * Additional JVM arguments passed to Play! server's JVM
     * 
     * @since 1.0.0
     */
    @Parameter( property = "sbtrun.jvmArgs", defaultValue = "" )
    private String jvmArgs;

    /**
     * Maven project builder used to resolve artifacts.
     */
    @Component
    protected MavenProjectBuilder mavenProjectBuilder;

    /**
     * Artifact factory used to look up artifacts in the remote repository.
     */
    @Component
    protected ArtifactFactory factory;

    /**
     * Artifact resolver used to resolve artifacts.
     */
    @Component
    protected ArtifactResolver resolver;

    /**
     * Location of the local repository.
     */
    @Parameter( property = "localRepository", readonly = true, required = true )
    protected ArtifactRepository localRepo;

    /**
     * Remote repositories used by the resolver
     */
    @Parameter( property = "project.remoteArtifactRepositories", readonly = true, required = true )
    protected List<ArtifactRepository> remoteRepos;

    /**
     * Launches SBT.
     * 
     * @throws MojoExecutionException if unexpected problem occurs
     */
    public void execute() throws MojoExecutionException
    {
        if ( skip )
        {
            // add log message
            return;
        }
        
        //????
        if ( "pom".equals( project.getPackaging() ) )
        {
            return;
        }

        try
        {
//        if ( fork )
//        {
            Java javaTask = prepareAntJavaTask( true/*fork*/ );
            javaTask.setFailonerror( true );

            JavaRunnable runner = new JavaRunnable( javaTask );
            // maybe just like this:
            getLog().info( "Launching SBT" );
            runner.run();
            /*Thread t = new Thread( runner, "SBT runner" );
            getLog().info( "Launching SBT" );
            t.start();
            try
            {
                t.join(); // waiting for Ctrl+C if forked, joins immediately if not forking
            }
            catch ( InterruptedException e )
            {
                throw new MojoExecutionException( "?", e );
            }*/
            Exception runException = runner.getException();
            if ( runException != null )
            {
                throw new MojoExecutionException( "?", runException );
            }
//        }
//        else // !fork
//        {
            /*does not work String[] argsArray = {};
            if ( args != null )
            {
                String trimmedArgs = args.trim();
                if ( !trimmedArgs.isEmpty() )
                {
                    argsArray = trimmedArgs.split( " " );
                }
                
            }
            xsbt.boot.Boot.main( argsArray );*/ // nie dziala uruchomione z classloaderem z Maven'a, trzeba podac inny, ale musi on miec parent'a
//        }
        }
        catch ( ArtifactResolutionException e )
        {
            throw new MojoExecutionException( "?", e );
        }
        catch ( ArtifactNotFoundException e )
        {
            throw new MojoExecutionException( "?", e );
        }
        catch ( InvalidDependencyVersionException e )
        {
            throw new MojoExecutionException( "?", e );
        }
        catch ( ProjectBuildingException e )
        {
            throw new MojoExecutionException( "?", e );
        }
    }

    protected Java prepareAntJavaTask( boolean fork )
        throws ArtifactResolutionException, ArtifactNotFoundException, InvalidDependencyVersionException, ProjectBuildingException//, IOException
    {
        File baseDir = project.getBasedir();

        Project antProject = createProject();
        Path classPath = getProjectClassPath( antProject );

        Java javaTask = new Java();
        javaTask.setTaskName( "sbt" );
        javaTask.setProject( antProject );
        javaTask.setClassname( "xsbt.boot.Boot" );
        javaTask.setClasspath( classPath );
        javaTask.setFork( fork );
        javaTask.createArg().setLine( args );
        if ( fork )
        {
            javaTask.setDir( baseDir );

            if ( jvmArgs != null )
            {
                jvmArgs = jvmArgs.trim();
                if ( jvmArgs.length() > 0 )
                {
                    String[] jvmArgsArray = jvmArgs.split( " " );
                    for ( String arg : jvmArgsArray )
                    {
                        javaTask.createJvmarg().setValue( arg );
                        getLog().debug( "  Adding jvmarg '" + arg + "'" );
                    }
                }
            }
        }
        else
        {
            // find and add all system properties in "jvmArgs"
            if ( jvmArgs != null )
            {
                jvmArgs = jvmArgs.trim();
                if ( jvmArgs.length() > 0 )
                {
                    String[] jvmArgsArray = jvmArgs.split( " " );
                    for ( String arg : jvmArgsArray )
                    {
                        if ( arg.startsWith( "-D" ) )
                        {
                            arg = arg.substring( 2 );
                            int p = arg.indexOf( '=' );
                            if ( p >= 0 )
                            {
                                String key = arg.substring( 0, p );
                                String value = arg.substring( p + 1 );
                                getLog().debug( "  Adding system property '" + arg + "'" );
                                addSystemProperty( javaTask, key, value );
                            }
                            /*else
                            {
                                // TODO - throw an exception
                            }*/
                        }
                    }
                }
            }
        }
        return javaTask;
    }

    private Path getProjectClassPath( Project antProject )
        throws ArtifactResolutionException, ArtifactNotFoundException, InvalidDependencyVersionException, ProjectBuildingException//, IOException
    {
        Artifact launcherArtifact =
            getResolvedArtifact( "com.google.code.sbtrun-maven-plugin.org.scala-sbt", "launcher", "0.13.5" );
            //getPluginArtifact( "com.google.code.sbtrun-maven-plugin.org.scala-sbt",
            //               "launcher", "jar" );
        Set<Artifact> launcherDependencies = getAllDependencies( launcherArtifact );

        Path classPath = new Path( antProject );
        classPath.createPathElement().setLocation( launcherArtifact.getFile() );
        for ( Artifact dependencyArtifact: launcherDependencies )
        {
            getLog().debug( String.format( "CP: %s:%s:%s (%s)", dependencyArtifact.getGroupId(),
                                           dependencyArtifact.getArtifactId(), dependencyArtifact.getType(),
                                           dependencyArtifact.getScope() ) );
            classPath.createPathElement().setLocation( dependencyArtifact.getFile() );
        }
//                    classPath.createPathElement().setLocation( getPluginArtifact( "com.google.code.sbtrun-maven-plugin.org.scala-sbt",
//                                                                                  "launcher", "jar" ).getFile() );

                    /*??? Artifact projectArtifact = getProject().getArtifact();
                    File projectArtifactFile = projectArtifact.getFile();
                    if ( projectArtifactFile == null )
                    {
                        File classesDirectory = new File( getProject().getBuild().getOutputDirectory() );
                        if ( !classesDirectory.isDirectory() )
                        {
                            throw new MojoExecutionException( "Project artifact file not available" ); // TODO improve message
                        }
                        projectArtifactFile = classesDirectory;
                        // TODO - add warning, that classes may be not up to date
                    }
                    getLog().debug( String.format( "CP: %s:%s:%s (%s)", projectArtifact.getGroupId(),
                                                   projectArtifact.getArtifactId(), projectArtifact.getType(),
                                                   projectArtifact.getScope() ) );
                    classPath.createPathElement().setLocation( projectArtifactFile );

                    Set<?> classPathArtifacts = getProject().getArtifacts();
                    for ( Iterator<?> iter = classPathArtifacts.iterator(); iter.hasNext(); )
                    {
                        Artifact artifact = (Artifact) iter.next();
                        getLog().debug( String.format( "CP: %s:%s:%s (%s)", artifact.getGroupId(), artifact.getArtifactId(),
                                                       artifact.getType(), artifact.getScope() ) );
                        classPath.createPathElement().setLocation( artifact.getFile() );
                    }????*/
        return classPath;
    }

    // Private utility methods

    private Artifact getResolvedArtifact( String groupId, String artifactId, String version )
        throws ArtifactNotFoundException, ArtifactResolutionException
    {
        Artifact artifact = factory.createArtifact( groupId, artifactId, version, Artifact.SCOPE_RUNTIME, "jar" );
        resolver.resolve( artifact, remoteRepos, localRepo );
        return artifact;
    }

    private Set<Artifact> getAllDependencies( Artifact artifact )
        throws ArtifactNotFoundException, ArtifactResolutionException, InvalidDependencyVersionException,
        ProjectBuildingException
    {
        Set<Artifact> result = new HashSet<Artifact>();
        MavenProject p = mavenProjectBuilder.buildFromRepository( artifact, remoteRepos, localRepo );
        Set<Artifact> d = resolveDependencyArtifacts( p );
        result.addAll( d );
        for ( Artifact dependency : d )
        {
            Set<Artifact> transitive = getAllDependencies( dependency );
            result.addAll( transitive );
        }
        return result;
    }

    /**
     * This method resolves the dependency artifacts from the project.
     * 
     * @param theProject The POM.
     * @return resolved set of dependency artifacts.
     * @throws ArtifactResolutionException
     * @throws ArtifactNotFoundException
     * @throws InvalidDependencyVersionException
     */
    private Set<Artifact> resolveDependencyArtifacts( MavenProject theProject )
        throws ArtifactNotFoundException, ArtifactResolutionException, InvalidDependencyVersionException
    {
        AndArtifactFilter filter = new AndArtifactFilter();
        filter.add( new ScopeArtifactFilter( Artifact.SCOPE_TEST ) );
        filter.add( new NonOptionalArtifactFilter() );
        // TODO follow the dependenciesManagement and override rules
        Set<Artifact> artifacts = theProject.createArtifacts( factory, Artifact.SCOPE_RUNTIME, filter );
        for ( Artifact artifact : artifacts )
        {
            resolver.resolve( artifact, remoteRepos, localRepo );
        }
        return artifacts;
    }

    private static class NonOptionalArtifactFilter
        implements ArtifactFilter
    {
        public boolean include( Artifact artifact )
        {
            return !artifact.isOptional();
        }
    }

}
