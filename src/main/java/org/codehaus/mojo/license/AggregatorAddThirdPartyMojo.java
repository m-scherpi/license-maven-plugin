package org.codehaus.mojo.license;

/*
 * #%L
 * License Maven Plugin
 * %%
 * Copyright (C) 2008 - 2011 CodeLutin, Codehaus, Tony Chemit
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.commons.collections.CollectionUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.codehaus.mojo.license.model.LicenseMap;
import org.codehaus.mojo.license.utils.SortedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This goal forks executions of the add-third-party goal for all the leaf projects
 * of the tree of modules below the point where it is executed. Note that this
 * plugin sets a specific name, 'add-third-party', for the forked executions in the
 * individual projects. From command level, then
 * even though the execution of this goal is named 'default-cli', the forked executions
 * have the name 'add-third-party'. Thus, to use the <tt>pluginManagement</tt> element of
 * the POM to set options, you have to name the execution 'add-third-party',
 * not 'default-cli'.
 *
 * @author tchemit dev@tchemit.fr
 * @since 1.0
 */
@Mojo( name = "aggregate-add-third-party", aggregator = true, defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
        requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true )
public class AggregatorAddThirdPartyMojo extends AbstractAddThirdPartyMojo
{
    private static final Logger LOG = LoggerFactory.getLogger( AggregatorAddThirdPartyMojo.class );

    // ----------------------------------------------------------------------
    // Mojo Parameters
    // ----------------------------------------------------------------------

    /**
     * The projects in the reactor.
     *
     * @since 1.0
     */
    @Parameter( property = "reactorProjects", readonly = true, required = true )
    private List<MavenProject> reactorProjects;

    /**
     * To skip execution of this mojo.
     *
     * @since 1.5
     */
    @Parameter( property = "license.skipAggregateAddThirdParty", defaultValue = "false" )
    private boolean skipAggregateAddThirdParty;

    /**
     * To resolve third party licenses from an artifact.
     *
     * @since 1.11
     * @deprecated since 1.14, please use now {@link #missingLicensesFileArtifact}
     */
    @Deprecated
    @Parameter( property = "license.aggregateMissingLicensesFileArtifact" )
    private String aggregateMissingLicensesFileArtifact;

    /**
     * To resolve third party licenses from a file.
     *
     * @since 1.11
     * @deprecated since 1.14, please use now {@link #missingFile}.
     */
    @Deprecated
    @Parameter( property = "license.aggregateMissingLicensesFile" )
    private File aggregateMissingLicensesFile;

    // ----------------------------------------------------------------------
    // AbstractLicenseMojo Implementaton
    // ----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSkip()
    {
        return skipAggregateAddThirdParty;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean checkPackaging()
    {
        return acceptPackaging( "pom" );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean checkSkip()
    {
        if ( !doGenerate && !doGenerateBundle )
        {

            LOG.info( "All files are up to date, skip goal execution." );
            return false;
        }
        return super.checkSkip();
    }

    @Override
    protected void init() throws Exception
    {
        // CHECKSTYLE_OFF: LineLength
        if ( aggregateMissingLicensesFile != null && !aggregateMissingLicensesFile.equals( missingFile ) )
        {
            LOG.warn( "" );
            LOG.warn( "You should use *missingFile* parameter instead of deprecated *aggregateMissingLicensesFile*." );
            LOG.warn( "" );
            missingFile = aggregateMissingLicensesFile;
        }

        if ( aggregateMissingLicensesFileArtifact != null
                && !aggregateMissingLicensesFileArtifact.equals( missingLicensesFileArtifact ) )
        {
            LOG.warn( "" );
            LOG.warn( "You should use *missingLicensesFileArtifact* parameter instead of deprecated *aggregateMissingLicensesFileArtifact*." );
            LOG.warn( "" );
            missingLicensesFileArtifact = aggregateMissingLicensesFileArtifact;
        }
        // CHECKSTYLE_ON: LineLength
        super.init();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doAction()
            throws Exception
    {
        if ( isVerbose() )
        {
            LOG.info( "After executing on {} project(s)", reactorProjects.size() );
        }

        licenseMap = new LicenseMap();

        Artifact pluginArtifact = (Artifact) project.getPluginArtifactMap()
                .get( "org.codehaus.mojo:license-maven-plugin" );

        String groupId = null;
        String artifactId = null;
        String version = null;
        if ( pluginArtifact == null )
        {
            Plugin plugin = (Plugin) project.getPluginManagement().getPluginsAsMap()
                    .get( "org.codehaus.mojo:license-maven-plugin" );
            if ( plugin != null )
            {
                groupId = plugin.getGroupId();
                artifactId = plugin.getArtifactId();
                version = plugin.getVersion();
            }
        }
        else
        {
            groupId = pluginArtifact.getGroupId();
            artifactId = pluginArtifact.getArtifactId();
            version = pluginArtifact.getVersion();
        }
        if ( groupId == null )
        {
            try
            {
                final PluginDescriptor pd = ( PluginDescriptor ) getPluginContext().get( "pluginDescriptor" );
                groupId = pd.getGroupId();
                artifactId = pd.getArtifactId();
                version = pd.getVersion();
            }
            catch ( ClassCastException e )
            {
                LOG.warn( "Failed to access PluginDescriptor", e );
            }

            if ( groupId == null )
            {
                throw new IllegalStateException( "Failed to determine the license-maven-plugin artifact."
                    +
                    "Please add it to your parent POM." );
            }
        }

        String addThirdPartyRoleHint = groupId + ":" + artifactId + ":" + version + ":" + "add-third-party";

        LOG.info( "The default plugin hint is: " + addThirdPartyRoleHint );

        for ( MavenProject reactorProject : reactorProjects )
        {
            if ( getProject().equals( reactorProject ) && !acceptPomPackaging )
            {
                // does not process this pom unless specified
                continue;
            }

            AddThirdPartyMojo mojo = (AddThirdPartyMojo) getSession()
                    .lookup( AddThirdPartyMojo.ROLE, addThirdPartyRoleHint );

            mojo.initFromMojo( this, reactorProject, new ArrayList<>( this.reactorProjects ) );

            LicenseMap childLicenseMap = mojo.licenseMap;
            if ( isVerbose() )
            {
                LOG.info( "Found {} license(s) in module {}:{}",
                        childLicenseMap.size(), mojo.project.getGroupId(), mojo.project.getArtifactId() );
            }
            licenseMap.putAll( childLicenseMap );

        }

        LOG.info( "Detected {} license(s).", licenseMap.size() );
        if ( isVerbose() )
        {
            for ( Map.Entry<String, SortedSet<MavenProject>> entry: licenseMap.entrySet() )
            {
                LOG.info( " - {} for {} artifact(s).", entry.getKey(), entry.getValue().size() );
            }
        }

        consolidate();

        // ----------------------------------------------------------------------
        // Select a specified license for a library that has multiple licenses, based on a config file
        // ----------------------------------------------------------------------

        // Create new map of all libs with multiple licenses
        // (instead of the given map of licenses with all libs using them)
        // LIBRARY is key
        Map<String, ArrayList<String>> libsWithMultiLicense = createMultipleLicensesMap();

        // Create a map of preferred licenses for libs, configured in a file
        // LIBRARY is key
        Map<String, String> licensesToSelect = readLicensesToSelect();

        // Collect remove candidates, sorted by license again for better comparability with final result map
        // LICENSE is key
        Map<String, ArrayList<String>> removeCandidates =
            calculateRemoveCandidates( libsWithMultiLicense, licensesToSelect );

        // Remove libs that have been marked as candidates
        removeObsoleteLibraries( removeCandidates );

        // ----------------------------------------------------------------------

        checkUnsafeDependencies();

        boolean safeLicense = checkForbiddenLicenses();

        checkBlacklist( safeLicense );

        writeThirdPartyFile();

        checkMissing( CollectionUtils.isNotEmpty( unsafeDependencies ) );
    }


    /*
     * Create new map of all libs with their licenses (instead of a map of licenses with the libs using them)
     * Contains only libs with multiple licenses
     */
    private Map<String, ArrayList<String>> createMultipleLicensesMap()
    {
        Map<String, ArrayList<String>> mapOfLibraries = new HashMap<>();
        //this.licenseMap is the global container used by license-maven-plugin, listing all libs for a specific license
        for ( String licenseName : licenseMap.keySet() )
        {
            SortedSet<MavenProject> libsWithGivenLicense = licenseMap.get( licenseName );
            for ( MavenProject library : libsWithGivenLicense )
            {
                String libraryKey = library.toString();
                //accumulate licenses for libs
                addEntryToMap( mapOfLibraries, libraryKey, licenseName );
            }
        }

        // remove libs with only one license to just have a map with multiple licenses
        Iterator<Map.Entry<String, ArrayList<String>>> iter = mapOfLibraries.entrySet().iterator();
        while ( iter.hasNext() )
        {
            Map.Entry<String, ArrayList<String>> entry = iter.next();
            if ( entry.getValue().size() == 1 )
            {
                iter.remove();
            }
        }
        // LIBRARY is key
        return mapOfLibraries;
    }

    /*
     * Prepare an assignment library <-> license that should be preferred if the lib has multiple licenses
     * It is read from a properties file with the same syntax as THIRD-PARTY.properties
     */
    private Map<String, String> readLicensesToSelect() throws IOException
    {
        SortedProperties selection = new SortedProperties( encoding );
        if ( selectionFile.exists() )
        {
            // load the selection file
            selection.load( selectionFile );
        }

        Map<String, String> licensesToSelect = new HashMap<>();
        for ( String libraryWithVersion : selection.stringPropertyNames() )
        {
            String license = selection.getProperty( libraryWithVersion );
            String libKey = libraryWithVersion.replace( "--", ":" );
            //mock MavenProject.toString() for better comparability
            licensesToSelect.put( "MavenProject: " + libKey + " @ ", license );
        }
        // LIBRARY is key
        return licensesToSelect;
    }


    /*
     * Find remove candidates that should not be written in result file.
     * Remove candidate means that a selection for a given library is present, so any other license is obsolete
     */
    private Map<String, ArrayList<String>> calculateRemoveCandidates(
        Map<String, ArrayList<String>> libsWithMultiLicense, Map<String, String> licensesToSelectForLibs )
    {
        Map<String, ArrayList<String>> libsToRemove = new HashMap<>();
        for ( String libraryKey : licensesToSelectForLibs.keySet() )
        {
            if ( libsWithMultiLicense.containsKey( libraryKey ) )
            {
                ArrayList<String> listOfLibsForLicense = libsWithMultiLicense.get( libraryKey );
                String selectedLicense = licensesToSelectForLibs.get( libraryKey );
                // selected license does not match any present license for lib, so no removal at all
                if ( !listOfLibsForLicense.remove( selectedLicense ) )
                {
                    continue;
                }
                // Any license left is not selected, so it has to be removed
                for ( String licenseName: listOfLibsForLicense )
                {
                    //accumulate libs for licenses
                    addEntryToMap( libsToRemove, licenseName, libraryKey );
                }

                libsWithMultiLicense.remove( libraryKey );
            }
        }

        if ( libsWithMultiLicense.size() > 0 )
        {
            LOG.info( "Some libraries have multiple licenses. Choose one and add it to " + selectionFile );
        }
        for ( String libRemaining : libsWithMultiLicense.keySet() )
        {
            ArrayList<String> licensesRemaining = libsWithMultiLicense.get( libRemaining );
            LOG.warn( libRemaining.replace( "MavenProject: ", "" )
                .replace( " @", " ->" )
                + licensesRemaining.toString() );
        }

        // LICENSE is key
        return libsToRemove;
    }

    /*
     * Licenses that have been marked to be removed from global container will be removed
     * Any entry left will be written to result file
     */
    private void removeObsoleteLibraries( Map<String, ArrayList<String>> removeCandidates )
    {
        for ( String licenseKey: removeCandidates.keySet() )
        {
            //this.licenseMap is the global container used by license-maven-plugin,
            // listing all libs for a specific license
            if ( licenseMap.containsKey( licenseKey ) )
            {
                ArrayList<String> librariesToRemove = removeCandidates.get( licenseKey );
                SortedSet<MavenProject> librariesForGivenLicense = licenseMap.get( licenseKey );

                List<MavenProject> toRemove = new ArrayList<>();
                for ( MavenProject library : librariesForGivenLicense )
                {
                    if ( librariesToRemove.contains( library.toString() ) )
                    {
                        toRemove.add( library ) ;
                    }
                }
                for ( MavenProject removeCandidate: toRemove )
                {
                    librariesForGivenLicense.remove( removeCandidate );
                }
            }
        }
    }

    /*
     * Match a given key with an entry, accumulate entries if the key already has some
     */
    private void addEntryToMap( Map<String, ArrayList<String>> resultMap, String key, String entry )
    {
        if ( !resultMap.containsKey( key ) )
        {
            ArrayList<String> initialListOfEntries = new ArrayList<>();
            initialListOfEntries.add( entry );
            resultMap.put( key, initialListOfEntries );
        }
        else
        {
            ArrayList<String> listOfEntries = resultMap.get( key );
            listOfEntries.add( entry );
        }
    }

    // ----------------------------------------------------------------------
    // AbstractAddThirdPartyMojo Implementaton
    // ----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    protected SortedMap<String, MavenProject> loadDependencies()
    {
        // use the cache filled by modules in reactor
        return getHelper().getArtifactCache();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected SortedProperties createUnsafeMapping()
      throws ProjectBuildingException, IOException, MojoExecutionException
    {

        String path =
            missingFile.getAbsolutePath().substring( getProject().getBasedir().getAbsolutePath().length() + 1 );

        if ( isVerbose() )
        {
            LOG.info( "Use missing file path: {}", path );
        }

        SortedProperties unsafeMappings = new SortedProperties( getEncoding() );

        for ( Object o : reactorProjects )
        {
            MavenProject p = (MavenProject) o;

            File file = new File( p.getBasedir(), path );

            if ( file.exists() )
            {

                SortedProperties tmp = getHelper().loadUnsafeMapping( licenseMap, file, null, projectDependencies );
                unsafeMappings.putAll( tmp );
            }

            SortedSet<MavenProject> unsafe = getHelper().getProjectsWithNoLicense( licenseMap );
            if ( CollectionUtils.isEmpty( unsafe ) )
            {

                // no more unsafe dependencies, can break
                break;
            }
        }
        return unsafeMappings;
    }

}
