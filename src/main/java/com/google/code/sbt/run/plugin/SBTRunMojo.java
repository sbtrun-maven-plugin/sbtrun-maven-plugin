/*
 * Copyright 2014-2017 Grzegorz Slowikowski (gslowikowski at gmail dot com)
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

import java.io.File;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.types.Path;

import org.codehaus.plexus.util.Os;

/**
 * Run SBT
 * 
 * @author <a href="mailto:gslowikowski@gmail.com">Grzegorz Slowikowski</a>
 * @since 1.0.0
 */
@Mojo( name = "run", requiresProject = false )
public class SBTRunMojo
    extends AbstractAntJavaBasedMojo
{
    private static final String LAUNCHER_GROUP_ID = "org.scala-sbt";

    private static final String LAUNCHER_ARTIFACT_ID = "launcher";

    private static final String LAUNCHER_MAIN_CLASS = "xsbt.boot.Boot";

    private static final String SBT_QUIT_COMMAND = "q";

    /**
     * Allows SBT run to be skipped.
     * 
     * @since 1.0.0
     */
    @Parameter( property = "sbtrun.skip", defaultValue = "false" )
    private boolean skip;

    /**
     * SBT process working directory.
     * 
     * @since 1.0.0
     */
    @Parameter( property = "sbtrun.basedir", defaultValue = "${project.basedir}" )
    private File basedir;

    /**
     * SBT process arguments.
     * 
     * @since 1.0.0
     */
    @Parameter( property = "sbtrun.args", defaultValue = "" )
    private String args;

    /**
     * SBT process JVM arguments.
     * 
     * @since 1.0.0
     */
    @Parameter( property = "sbtrun.jvmArgs", defaultValue = "" )
    private String jvmArgs;

    /**
     * SBT process execution timeout in milliseconds.
     * 
     * @since 1.0.0
     */
    @Parameter( property = "sbtrun.timeout", defaultValue = "0" )
    private int timeout;

    /**
     * List of artifacts this plugin depends on.
     */
    @Parameter( property = "plugin.artifacts", required = true, readonly = true )
    private List<Artifact> pluginArtifacts;

    /**
     * Launches SBT process.
     * 
     * @throws MojoExecutionException if unexpected problem occurs
     */
    @Override
    public void execute() throws MojoExecutionException
    {
        if ( skip )
        {
            getLog().info( "Skipping SBT execution" );
            return;
        }
        
        Java javaTask = prepareAntJavaTask();

        getLog().debug( "Launching SBT" );

        if ( timeout <= 0 )
        {
            try
            {
                javaTask.execute();
            }
            catch ( BuildException e )
            {
                throw new MojoExecutionException( "SBT execution exception", e );
            }
        }
        else
        {
            JavaRunnable runnable = new JavaRunnable( javaTask );
            Thread t = new Thread( runnable, "SBT runner" );
            t.setDaemon( true );
            t.start();
            try
            {
                t.join( timeout );
            }
            catch ( InterruptedException e )
            {
                t.interrupt();
                throw new MojoExecutionException( "SBT runner interrupted", e );
            }
            BuildException runnerException = runnable.getException();
            if ( runnerException != null )
            {
                // If there is an exception, the thread is not alive anymore
                throw new MojoExecutionException( "SBT runner exception", runnerException );
            }
            if ( !runnable.isExecuted() ) // Thread still alive
            {
                t.interrupt();
                throw new MojoExecutionException( "SBT runner timed out" );
            }
        }
    }

    private Java prepareAntJavaTask()
        throws MojoExecutionException
    {
        Project antProject = createProject();
        Path classPath = getProjectClassPath( antProject );

        Java javaTask = new Java();
        javaTask.setTaskName( "sbt" );
        javaTask.setProject( antProject );
        javaTask.setClassname( LAUNCHER_MAIN_CLASS );
        javaTask.setClasspath( classPath );
        javaTask.setFork( true );
        javaTask.createArg().setLine( args );
        javaTask.setDir( basedir != null ? basedir : new File( "." ) );
        javaTask.setFailonerror( true );
        javaTask.setInputString( SBT_QUIT_COMMAND ); // automatically choose "quit" option in case of error prompt

        if ( getLog().isDebugEnabled() )
        {
            String arg = "--debug";
            javaTask.createArg().setValue( arg );
            getLog().debug( "  Adding arg '" + arg + "'" );
        }

        // Workaround for https://github.com/jline/jline2/issues/103
        if ( Os.isFamily( Os.FAMILY_WINDOWS ) )
        {
            String jvmArg = "-Djline.WindowsTerminal.directConsole=false";
            javaTask.createJvmarg().setValue( jvmArg );
            getLog().debug( "  Adding jvmarg '" + jvmArg + "'" );
        }

        if ( jvmArgs != null )
        {
            jvmArgs = jvmArgs.trim();
            if ( jvmArgs.length() > 0 )
            {
                String[] jvmArgsArray = jvmArgs.split( " " );
                for ( String jvmArg : jvmArgsArray )
                {
                    javaTask.createJvmarg().setValue( jvmArg );
                    getLog().debug( "  Adding jvmarg '" + jvmArg + "'" );
                }
            }
        }
        return javaTask;
    }

    private Path getProjectClassPath( Project antProject )
        throws MojoExecutionException
    {
        Artifact resolvedLauncherArtifact = getPluginArtifact( LAUNCHER_GROUP_ID, LAUNCHER_ARTIFACT_ID, "jar" );

        // contains "sbt.boot.properties" resource
        Artifact resolvedConfigArtifact =
            getPluginArtifact( "com.google.code.sbtrun-maven-plugin", "sbtrun-maven-plugin", "maven-plugin" );

        Path classPath = new Path( antProject );
        classPath.createPathElement().setLocation( resolvedLauncherArtifact.getFile() );
        classPath.createPathElement().setLocation( resolvedConfigArtifact.getFile() );

        return classPath;
    }

    private Artifact getPluginArtifact( String groupId, String artifactId, String type )
        throws MojoExecutionException
    {
        Artifact result = null;
        for ( Artifact artifact : pluginArtifacts )
        {
            if ( artifact.getGroupId().equals( groupId ) && artifact.getArtifactId().equals( artifactId )
                && type.equals( artifact.getType() ) )
            {
                result = artifact;
                break;
            }
        }
        if ( result == null )
        {
            throw new MojoExecutionException(
                                              String.format( "Unable to locate '%s:%s' in the list of plugin artifacts",
                                                             groupId, artifactId ) );
        }
        return result;
    }

}
