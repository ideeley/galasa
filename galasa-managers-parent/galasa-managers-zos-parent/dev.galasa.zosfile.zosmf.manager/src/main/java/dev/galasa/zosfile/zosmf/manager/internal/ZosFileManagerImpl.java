/*
 * Licensed Materials - Property of IBM
 * 
 * (c) Copyright IBM Corp. 2019.
 */
package dev.galasa.zosfile.zosmf.manager.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.validation.constraints.NotNull;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.annotations.Component;

import dev.galasa.ManagerException;
import dev.galasa.framework.spi.AbstractManager;
import dev.galasa.framework.spi.AnnotatedField;
import dev.galasa.framework.spi.ConfigurationPropertyStoreException;
import dev.galasa.framework.spi.GenerateAnnotatedField;
import dev.galasa.framework.spi.IFramework;
import dev.galasa.framework.spi.IManager;
import dev.galasa.framework.spi.ResourceUnavailableException;
import dev.galasa.zos.IZosImage;
import dev.galasa.zos.ZosManagerException;
import dev.galasa.zos.spi.IZosManagerSpi;
import dev.galasa.zosfile.IZosFileHandler;
import dev.galasa.zosfile.ZosFileField;
import dev.galasa.zosfile.ZosFileHandler;
import dev.galasa.zosfile.ZosFileManagerException;
import dev.galasa.zosfile.spi.IZosFileSpi;
import dev.galasa.zosfile.zosmf.manager.internal.properties.ZosFileZosmfPropertiesSingleton;
import dev.galasa.zosmf.spi.IZosmfManagerSpi;

/**
 * zOS File Manager implemented using zOS/MF
 *
 */
@Component(service = { IManager.class })
public class ZosFileManagerImpl extends AbstractManager implements IZosFileSpi {
    protected static final String NAMESPACE = "zosfile";
    
    private static final Log logger = LogFactory.getLog(ZosFileManagerImpl.class);

    protected static IZosManagerSpi zosManager;
    public static void setZosManager(IZosManagerSpi zosManager) {
        ZosFileManagerImpl.zosManager = zosManager;
    }
    
    protected static IZosmfManagerSpi zosmfManager;
    public static void setZosmfManager(IZosmfManagerSpi zosmfManager) {
        ZosFileManagerImpl.zosmfManager = zosmfManager;
    }

    private static final Map<String, ZosFileHandlerImpl> zosFileHandlers = new HashMap<>();

    private static final String ZOS_DATASETS = "zOS_Datasets";
    
    private static final String ZOS_VSAM_DATASETS = "zOS_VSAM_Datasets";
    
    private static final String ZOS_UNIX_PATHS = "zOS_Unix_Paths";

    private static final String PROVISIONING = "provisioning";

    private static String runId;
    protected static void setRunId(String id) {
        runId = id;
    }
    protected static String getRunId() {
        return runId;
    }

    private Path artifactsRoot;

    private boolean provisionCleanupComplete;

    protected static Path datasetArtifactRoot;
    protected static void setDatasetArtifactRoot(Path path) {
        datasetArtifactRoot = path;
    }
    protected static Path getDatasetArtifactRoot() {
        return datasetArtifactRoot;
    }

    protected static Path vsamDatasetArtifactRoot;
    protected static void setVsamDatasetArtifactRoot(Path path) {
        vsamDatasetArtifactRoot = path;
    }
    protected static Path getVsamDatasetArtifactRoot() {
        return vsamDatasetArtifactRoot;
    }

    protected static Path unixPathArtifactRoot;
    protected static void setUnixPathArtifactRoot(Path path) {
        unixPathArtifactRoot = path;
    }
    protected static Path getUnixPathArtifactRoot() {
        return unixPathArtifactRoot;
    }
    
    protected static String currentTestMethodArchiveFolderName;
    public static void setCurrentTestMethodArchiveFolderName(String folderName) {
        ZosFileManagerImpl.currentTestMethodArchiveFolderName = folderName;
    }
    
    /* (non-Javadoc)
     * @see dev.galasa.framework.spi.AbstractManager#initialise(dev.galasa.framework.spi.IFramework, java.util.List, java.util.List, java.lang.Class)
     */
    @Override
    public void initialise(@NotNull IFramework framework, @NotNull List<IManager> allManagers,
            @NotNull List<IManager> activeManagers, @NotNull Class<?> testClass) throws ManagerException {
        super.initialise(framework, allManagers, activeManagers, testClass);
        try {
            ZosFileZosmfPropertiesSingleton.setCps(framework.getConfigurationPropertyService(NAMESPACE));
        } catch (ConfigurationPropertyStoreException e) {
            throw new ZosFileManagerException("Unable to request framework services", e);
        }

        //*** Check to see if any of our annotations are present in the test class
        //*** If there is,  we need to activate
        List<AnnotatedField> ourFields = findAnnotatedFields(ZosFileField.class);
        if (!ourFields.isEmpty()) {
            youAreRequired(allManagers, activeManagers);
        }
        
        setRunId(getFramework().getTestRunName());
        
        artifactsRoot = getFramework().getResultArchiveStore().getStoredArtifactsRoot();
        
        setDatasetArtifactRoot(artifactsRoot.resolve(ZOS_DATASETS));        
        setVsamDatasetArtifactRoot(artifactsRoot.resolve(ZOS_VSAM_DATASETS));        
        setUnixPathArtifactRoot(artifactsRoot.resolve(ZOS_UNIX_PATHS));
    }
        
    
    /* (non-Javadoc)
     * @see dev.galasa.framework.spi.AbstractManager#provisionGenerate()
     */
    @Override
    public void provisionGenerate() throws ManagerException, ResourceUnavailableException {
        generateAnnotatedFields(ZosFileField.class);
    }


    /* (non-Javadoc)
     * @see dev.galasa.framework.spi.AbstractManager#youAreRequired()
     */
    @Override
    public void youAreRequired(@NotNull List<IManager> allManagers, @NotNull List<IManager> activeManagers)
            throws ManagerException {
        if (activeManagers.contains(this)) {
            return;
        }

        activeManagers.add(this);
        setZosManager(addDependentManager(allManagers, activeManagers, IZosManagerSpi.class));
        if (zosManager == null) {
            throw new ZosFileManagerException("The zOS Manager is not available");
        }
        setZosmfManager(addDependentManager(allManagers, activeManagers, IZosmfManagerSpi.class));
        if (zosmfManager == null) {
            throw new ZosFileManagerException("The zOSMF Manager is not available");
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see dev.galasa.framework.spi.IManager#areYouProvisionalDependentOn(dev.galasa.framework.spi.IManager)
     */
    @Override
    public boolean areYouProvisionalDependentOn(@NotNull IManager otherManager) {
        return otherManager instanceof IZosManagerSpi ||
               otherManager instanceof IZosmfManagerSpi;
    }

    /*
     * (non-Javadoc)
     * 
     * @see dev.galasa.framework.spi.IManager#provisionBuild()
     */
    @Override
    public void provisionBuild() throws ManagerException, ResourceUnavailableException {
        setDatasetArtifactRoot(artifactsRoot.resolve(PROVISIONING).resolve(ZOS_DATASETS));        
        setVsamDatasetArtifactRoot(artifactsRoot.resolve(PROVISIONING).resolve(ZOS_VSAM_DATASETS));        
        setUnixPathArtifactRoot(artifactsRoot.resolve(PROVISIONING).resolve(ZOS_UNIX_PATHS));
        setCurrentTestMethodArchiveFolderName("preTest");
    }

    /*
     * (non-Javadoc)
     * 
     * @see dev.galasa.framework.spi.IManager#startOfTestClass()
     */
    @Override
    public void startOfTestClass() throws ManagerException {
        cleanup(false);
    }

    /*
     * (non-Javadoc)
     * 
     * @see dev.galasa.framework.spi.IManager#startOfTestMethod()
     */
    @Override
    public void startOfTestMethod(@NotNull Method testExecutionMethod, Method testMethod) throws ManagerException {
        if (!provisionCleanupComplete) {
            cleanup(false);
            provisionCleanupComplete = true;
        }
        setDatasetArtifactRoot(artifactsRoot.resolve(ZOS_DATASETS));        
        setVsamDatasetArtifactRoot(artifactsRoot.resolve(ZOS_VSAM_DATASETS));        
        setUnixPathArtifactRoot(artifactsRoot.resolve(ZOS_UNIX_PATHS));
        if (testMethod != null) {
            setCurrentTestMethodArchiveFolderName(testMethod.getName() + "." + testExecutionMethod.getName());
        } else {
            setCurrentTestMethodArchiveFolderName(testExecutionMethod.getName());
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see dev.galasa.framework.spi.IManager#endOfTestMethod(java.lang.reflect.Method,java.lang.String,java.lang.Throwable)
     */
    @Override
    public String endOfTestMethod(@NotNull Method testMethod, @NotNull String currentResult, Throwable currentException) throws ManagerException {
        cleanup(false);
        
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see dev.galasa.framework.spi.IManager#endOfTestClass(java.lang.String,java.lang.Throwable)
     */
    @Override
    public String endOfTestClass(@NotNull String currentResult, Throwable currentException) throws ManagerException {
        cleanup(true);
        setDatasetArtifactRoot(artifactsRoot.resolve(PROVISIONING).resolve(ZOS_DATASETS));        
        setVsamDatasetArtifactRoot(artifactsRoot.resolve(PROVISIONING).resolve(ZOS_VSAM_DATASETS));        
        setUnixPathArtifactRoot(artifactsRoot.resolve(PROVISIONING).resolve(ZOS_UNIX_PATHS));
        setCurrentTestMethodArchiveFolderName("postTest");
        
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see dev.galasa.framework.spi.IManager#provisionDiscard()
     */
    @Override
    public void provisionDiscard() {
        try {
            cleanup(true);
        } catch (ZosFileManagerException e) {
            logger.error("Problem in provisionDiscard()", e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see dev.galasa.framework.spi.IManager#endOfTestRun()
     */
    @Override
    public void endOfTestRun() {
        try {
            cleanup(true);
        } catch (ZosFileManagerException e) {
            logger.error("Problem in endOfTestRun()", e);
        }
    }
    
    protected void cleanup(boolean testComplete) throws ZosFileManagerException {
        for (Entry<String, ZosFileHandlerImpl> entry : zosFileHandlers.entrySet()) {
            entry.getValue().cleanup(testComplete);
        }
    }
    
    @GenerateAnnotatedField(annotation=ZosFileHandler.class)
    public IZosFileHandler generateZosFileHandler(Field field, List<Annotation> annotations) {
        ZosFileHandlerImpl zosFileHandlerImpl = new ZosFileHandlerImpl(field.getName());
        zosFileHandlers.put(zosFileHandlerImpl.toString(), zosFileHandlerImpl);        
        return zosFileHandlerImpl;
    }
    
    public static IZosFileHandler newZosFileHandler() {
        ZosFileHandlerImpl zosFileHandlerImpl;
        if (zosFileHandlers.get("INTERNAL") == null) {
            zosFileHandlerImpl = new ZosFileHandlerImpl();
            zosFileHandlers.put(zosFileHandlerImpl.toString(), zosFileHandlerImpl);
        }
        return zosFileHandlers.get("INTERNAL");
    }
    
    public static String getRunDatasetHLQ(IZosImage image) throws ZosFileManagerException {
        try {
            return zosManager.getRunDatasetHLQ(image);
        } catch (ZosManagerException e) {
            throw new ZosFileManagerException(e);
        }
    }
    
    @Override
    public @NotNull IZosFileHandler getZosFileHandler() throws ZosFileManagerException {
        return newZosFileHandler();
    }
}
