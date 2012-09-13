package org.cipango.plugin;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.cipango.sipapp.SipAppContext;
import org.eclipse.jetty.plus.webapp.EnvConfiguration;
import org.eclipse.jetty.util.LazyList;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.webapp.Configuration;

public class CipangoSipAppContext extends SipAppContext
{
	private static final Logger LOG = Log.getLogger(CipangoSipAppContext.class);
	
    private static final String WEB_INF_CLASSES_PREFIX = "/WEB-INF/classes";
    private static final String WEB_INF_LIB_PREFIX = "/WEB-INF/lib";
    
    private List<File> classpathFiles;
    private File jettyEnvXmlFile;
    private File webXmlFile;
    private boolean baseAppFirst = true;

    private String[] configs = 
    	new String[]{
    		"org.mortbay.jetty.plugin.MavenWebInfConfiguration",
    		"org.eclipse.jetty.webapp.WebXmlConfiguration",
    		"org.eclipse.jetty.webapp.MetaInfConfiguration",
    		"org.eclipse.jetty.webapp.FragmentConfiguration",
    		"org.eclipse.jetty.plus.webapp.EnvConfiguration",
    		"org.eclipse.jetty.webapp.JettyWebXmlConfiguration",
    		"org.eclipse.jetty.webapp.TagLibConfiguration",
    		"org.cipango.sipapp.SipXmlConfiguration",
    		"org.cipango.plus.sipapp.PlusConfiguration"
    };
    
    private String jettyEnvXml;
    private List<Resource> overlays;
    
    /**
     * @deprecated The value of this parameter will be ignored by the plugin. Overlays will always be unpacked.
     */
    private boolean unpackOverlays;
    private File classes = null;
    private File testClasses = null;
    private List<File> webInfClasses = new ArrayList<File>();
    private List<File> webInfJars = new ArrayList<File>();
    private Map<String, File> webInfJarMap = new HashMap<String, File>();
    
    public CipangoSipAppContext()
    {
        super();
        setConfigurationClasses(configs);
    }
    
    @Override
    public void setConfigurationClasses(String[] c)
    {
    	super.setConfigurationClasses(c);
    	configs = c;
    }
    
    public void addConfiguration(String configuration)
    {
    	 if (isRunning())
             throw new IllegalStateException("Running");
    	 configs = (String[]) LazyList.addToArray(configs, configuration, String.class);
    	 setConfigurationClasses(configs);
    }

    
    public void setClassPathFiles(List<File> classpathFiles)
    {
        this.classpathFiles = classpathFiles;
    }

    public List<File> getClassPathFiles()
    {
        return this.classpathFiles;
    }
    
    public void setWebXmlFile(File webXmlFile)
    {
        this.webXmlFile = webXmlFile;
    }
    
    public File getWebXmlFile()
    {
        return this.webXmlFile;
    }
    
    public void setJettyEnvXmlFile (File jettyEnvXmlFile)
    {
        this.jettyEnvXmlFile = jettyEnvXmlFile;
    }
    
    public File getJettyEnvXmlFile()
    {
        return this.jettyEnvXmlFile;
    }
    

    @Override
    public void doStart () throws Exception
    {
        //Set up the pattern that tells us where the jars are that need scanning for
        //stuff like taglibs so we can tell jasper about it (see TagLibConfiguration)
        String tmp = (String)getAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern");
       
        tmp = addPattern(tmp, ".*/.*jsp-api-[^/]*\\.jar$");
        tmp = addPattern(tmp, ".*/.*jsp-[^/]*\\.jar$");  
        tmp = addPattern(tmp, ".*/.*taglibs[^/]*\\.jar$");
        tmp = addPattern(tmp, ".*/.*jstl[^/]*\\.jar$");
        tmp = addPattern(tmp, ".*/.*jsf-impl-[^/]*\\.jar$"); // add in 2 most popular jsf impls
        tmp = addPattern(tmp, ".*/.*javax.faces-[^/]*\\.jar$");
        tmp = addPattern(tmp, ".*/.*myfaces-impl-[^/]*\\.jar$");

        setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern", tmp);
   
        //Set up the classes dirs that comprises the equivalent of WEB-INF/classes
        if (testClasses != null)
            webInfClasses.add(testClasses);
        if (classes != null)
            webInfClasses.add(classes);
        
        // Set up the classpath
        classpathFiles = new ArrayList<File>();
        classpathFiles.addAll(webInfClasses);
        classpathFiles.addAll(webInfJars);

        
        // Initialize map containing all jars in /WEB-INF/lib
        webInfJarMap.clear();
        for (File file : webInfJars)
        {
            // Return all jar files from class path
            String fileName = file.getName();
            if (fileName.endsWith(".jar"))
                webInfJarMap.put(fileName, file);
        }
        
        setShutdown(false);

        loadConfigurations();
    	Configuration[] configurations = getConfigurations();
    	for (int i = 0; i < configurations.length; i++)
    	{
            if (this.jettyEnvXmlFile != null && configurations[i] instanceof  EnvConfiguration)
            	((EnvConfiguration) configurations[i]).setJettyEnvXml(this.jettyEnvXmlFile.toURL());
	   }
        
        super.doStart();
    }
     
    public void doStop () throws Exception
    {
        setShutdown(true);
        //just wait a little while to ensure no requests are still being processed
        Thread.sleep(500L);
        super.doStop();
    }

	public boolean isAnnotationsEnabled()
	{
		for (int i = 0; i < configs.length; i++)
			if (configs[i].equals("org.cipango.plugin.MavenAnnotationConfiguration"))
				return true;
		return false;
	}

	public void setAnnotationsEnabled(boolean annotationsEnabled)
	{
		if (annotationsEnabled)
			addConfiguration("org.cipango.plugin.MavenAnnotationConfiguration");
	}
	
	public boolean getUnpackOverlays()
    {
        return unpackOverlays;
    }

    public void setUnpackOverlays(boolean unpackOverlays)
    {
        this.unpackOverlays = unpackOverlays;
    }
	
    public void setOverlays (List<Resource> overlays)
    {
        this.overlays = overlays;
    }
    
    public List<Resource> getOverlays ()
    {
        return this.overlays;
    }
    
    public void setJettyEnvXml (String jettyEnvXml)
    {
        this.jettyEnvXml = jettyEnvXml;
    }
    
    public String getJettyEnvXml()
    {
        return this.jettyEnvXml;
    }
    
    public void setWebInfClasses(List<File> dirs)
    {
        webInfClasses.addAll(dirs);
    }
    
    public List<File> getWebInfClasses()
    {
        return webInfClasses;
    }
    
    public void setClasses(File dir)
    {
        classes = dir;
    }
    
    public File getClasses()
    {
        return classes;
    }
    
    public void setWebInfLib (List<File> jars)
    {
        webInfJars.addAll(jars);
    }    
    
    public void setTestClasses (File dir)
    {
        testClasses = dir;
    }
    
    
    public File getTestClasses ()
    {
        return testClasses;
    }
    
    /* ------------------------------------------------------------ */
    public void setBaseAppFirst(boolean value)
    {
        baseAppFirst = value;
    }

    /* ------------------------------------------------------------ */
    public boolean getBaseAppFirst()
    {
        return baseAppFirst;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * This method is provided as a conveniance for jetty maven plugin configuration 
     * @param resourceBases Array of resources strings to set as a {@link ResourceCollection}. Each resource string may be a comma separated list of resources
     * @see Resource
     */
    public void setResourceBases(String[] resourceBases)
    {
        List<String> resources = new ArrayList<String>();
        for (String rl:resourceBases)
        {
            String[] rs = rl.split(" *, *");
            for (String r:rs)
                resources.add(r);
        }
        
        setBaseResource(new ResourceCollection(resources.toArray(new String[resources.size()])));
    }

    public List<File> getWebInfLib()
    {
        return webInfJars;
    }
    
    @Override
    public Resource getResource(String uriInContext) throws MalformedURLException
    {
        Resource resource = null;
        // Try to get regular resource
        resource = super.getResource(uriInContext);

        // If no regular resource exists check for access to /WEB-INF/lib or /WEB-INF/classes
        if ((resource == null || !resource.exists()) && uriInContext != null && classes != null)
        {
            String uri = URIUtil.canonicalPath(uriInContext);
            if (uri == null)
                return null;

            try
            {
                // Replace /WEB-INF/classes with candidates for the classpath
                if (uri.startsWith(WEB_INF_CLASSES_PREFIX))
                {
                    if (uri.equalsIgnoreCase(WEB_INF_CLASSES_PREFIX) || uri.equalsIgnoreCase(WEB_INF_CLASSES_PREFIX+"/"))
                    {
                        //exact match for a WEB-INF/classes, so preferentially return the resource matching the web-inf classes
                        //rather than the test classes
                        if (classes != null)
                            return Resource.newResource(classes);
                        else if (testClasses != null)
                            return Resource.newResource(testClasses);
                    }
                    else
                    {
                        //try matching                       
                        Resource res = null;
                        int i=0;
                        while (res == null && (i < webInfClasses.size()))
                        {
                            String newPath = uri.replace(WEB_INF_CLASSES_PREFIX, webInfClasses.get(i).getPath());
                            res = Resource.newResource(newPath);
                            if (!res.exists())
                            {
                                res = null; 
                                i++;
                            }
                        }
                        return res;
                    }
                }       
                else if (uri.startsWith(WEB_INF_LIB_PREFIX))
                {
                    // Return the real jar file for all accesses to
                    // /WEB-INF/lib/*.jar
                    String jarName = uri.replace(WEB_INF_LIB_PREFIX, "");
                    if (jarName.startsWith("/") || jarName.startsWith("\\")) 
                        jarName = jarName.substring(1);
                    if (jarName.length()==0) 
                        return null;
                    File jarFile = webInfJarMap.get(jarName);
                    if (jarFile != null)
                        return Resource.newResource(jarFile.getPath());

                    return null;
                }
            }
            catch (MalformedURLException e)
            {
                throw e;
            }
            catch (IOException e)
            {
                LOG.ignore(e);
            }
        }
        return resource;
    }

    @Override
    public Set<String> getResourcePaths(String path)
    {
        // Try to get regular resource paths
        Set<String> paths = super.getResourcePaths(path);

        // If no paths are returned check for virtual paths /WEB-INF/classes and /WEB-INF/lib
        if (paths.isEmpty() && path != null)
        {
            path = URIUtil.canonicalPath(path);
            if (path.startsWith(WEB_INF_LIB_PREFIX))
            {
                paths = new TreeSet<String>();
                for (String fileName : webInfJarMap.keySet())
                {
                    // Return all jar files from class path
                    paths.add(WEB_INF_LIB_PREFIX + "/" + fileName);
                }
            }
            else if (path.startsWith(WEB_INF_CLASSES_PREFIX))
            {
                int i=0;
               
                while (paths.isEmpty() && (i < webInfClasses.size()))
                {
                    String newPath = path.replace(WEB_INF_CLASSES_PREFIX, webInfClasses.get(i).getPath());
                    paths = super.getResourcePaths(newPath);
                    i++;
                }
            }
        }
        return paths;
    }
    
    public String addPattern (String s, String pattern)
    {
        if (s == null)
            s = "";
        else
            s = s.trim();
        
        if (!s.contains(pattern))
        {
            if (s.length() != 0)
                s = s + "|";
            s = s + pattern;
        }
        
        return s;
    }

}
